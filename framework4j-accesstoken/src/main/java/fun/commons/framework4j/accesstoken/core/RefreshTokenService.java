package fun.commons.framework4j.accesstoken.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.accesstoken.config.AccessTokenProperties;
import fun.commons.framework4j.accesstoken.exception.AuthException;
import fun.commons.framework4j.accesstoken.util.TokenUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Refresh token family 管理器
 * <p>
 * 从 {@link AccessTokenGenerator} 拆分，专注 refresh token 的：
 * <ul>
 *   <li>family 状态写入（generateTokenPair 时）</li>
 *   <li>原子轮转（refreshAccessToken 时）</li>
 *   <li>重用检测 + 全族撤销（poison pill）</li>
 *   <li>显式撤销 family（revokeFamily）</li>
 * </ul>
 *
 * <p>错误码（参考 mc-api-spec §7）：
 * <ul>
 *   <li>10210 REFRESH_EXPIRED — refresh 过期或 family 不存在</li>
 *   <li>10211 REFRESH_INVALID — 类型错、缺字段、被重用</li>
 *   <li>10212 REFRESH_ROTATION_EXCEEDED — 轮转超 maxRotations</li>
 * </ul>
 *
 * @since 2.0.0
 */
@Slf4j
public class RefreshTokenService {

    /** Refresh token 类型标识 */
    public static final String TYPE_REFRESH = "refresh";

    /**
     * Refresh 轮转原子 Lua 脚本（v2.1 P0 修复：合并标记 consumed + 写新 jti + expire 为单原子操作）
     * <p>原实现 Lua 只标记旧 jti consumed，新 jti 用 opsForHash().put + expire 两步非原子写入，
     * 进程崩溃或并发 revokeFamily 删 family hash 时客户端已拿新 refresh token 但 family 无记录 → 下次 10210。
     * <p>KEYS[1] = refresh:family:{familyId}
     * <p>ARGV[1] = 当前 jti
     * <p>ARGV[2] = maxRotations
     * <p>ARGV[3] = 新 jti
     * <p>ARGV[4] = 新 jti 对应的 JSON 数据
     * <p>ARGV[5] = refreshTtlSeconds
     * <p>返回 cjson.encode(data)；错误用 redis.error_reply 抛出
     */
    private static final String REFRESH_LUA = String.join("\n",
            "local val = redis.call('HGET', KEYS[1], ARGV[1])",
            "if not val then return redis.error_reply('EXPIRED') end",
            "local data = cjson.decode(val)",
            "if data.consumed then return redis.error_reply('REUSED') end",
            "if data.generation and tonumber(data.generation) >= tonumber(ARGV[2]) then",
            "  return redis.error_reply('ROTATION_EXCEEDED')",
            "end",
            "data.consumed = true",
            "redis.call('HSET', KEYS[1], ARGV[1], cjson.encode(data))",
            // v2.1 P0：同事务写新 jti + 设过期，避免崩溃窗口
            "if ARGV[3] and ARGV[4] then",
            "  redis.call('HSET', KEYS[1], ARGV[3], ARGV[4])",
            "  redis.call('EXPIRE', KEYS[1], ARGV[5])",
            "end",
            "return cjson.encode(data)"
    );

    private static final DefaultRedisScript<String> REFRESH_SCRIPT =
            new DefaultRedisScript<>(REFRESH_LUA, String.class);

    /** v2.1 P1 修复：共享 TokenUtils.MAPPER 单例，避免重复创建 */
    private static final ObjectMapper MAPPER = TokenUtils.MAPPER;

    /** 默认 refresh TTL（秒）：mc-java-security 铁律 3 要求 ≤ 30d */
    private static final long DEFAULT_REFRESH_TTL_SECONDS = 2592000L;

    /** 默认家族轮转上限 */
    private static final int DEFAULT_MAX_ROTATIONS = 20;

    private final AccessTokenProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final AccessTokenGenerator accessGenerator;
    private final String appName;

    public RefreshTokenService(AccessTokenProperties properties,
                               StringRedisTemplate redisTemplate,
                               AccessTokenGenerator accessGenerator,
                               String appName) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.accessGenerator = accessGenerator;
        this.appName = appName;
    }

    /**
     * 生成 Access + Refresh Token 对
     */
    public AccessTokenGenerator.TokenPair generateTokenPair(Map<String, Object> claims) {
        if (claims == null) {
            throw new AuthException(10200, "Claims 不能为 null");
        }

        // 1. 复用 access token 路径，拿 access token + 写入 access 元数据
        String accessToken = accessGenerator.generateToken(extractTokenType(claims), claims);

        Map<String, Object> accessPayload = TokenUtils.parseToken(accessToken, properties.getSecretKey());
        String accessJti = (String) accessPayload.get("jti");
        long accessExp = ((Number) accessPayload.get("exp")).longValue();
        long accessTtl = Math.max(0, (accessExp - System.currentTimeMillis()) / 1000);

        String tokenType = (String) accessPayload.get("type");
        AccessTokenProperties.Policy policy = properties.getPolicies().get(tokenType);
        long refreshTtl = policy != null && policy.getRefreshExpireTime() != null
                ? policy.getRefreshExpireTime() : DEFAULT_REFRESH_TTL_SECONDS;
        int maxRotations = policy != null && policy.getMaxRotations() != null
                ? policy.getMaxRotations() : DEFAULT_MAX_ROTATIONS;

        // 2. 构造 refresh token + family 状态
        String familyId = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long refreshExp = now + refreshTtl * 1000;

        String refreshToken = TokenUtils.createToken(
                TYPE_REFRESH, null, null,
                appName, now, refreshExp, refreshJti,
                familyId, properties.getSecretKey());

        // 3. 写 family Hash
        String familyKey = TokenKeyBuilder.refreshFamily(appName, familyId);
        Map<String, Object> familyValue = new HashMap<>();
        familyValue.put("exp", refreshExp);
        familyValue.put("consumed", false);
        familyValue.put("accessJti", accessJti);
        familyValue.put("generation", 0);
        familyValue.put("maxRotations", maxRotations);
        familyValue.put("type", tokenType);
        familyValue.put("claims", claims);

        try {
            redisTemplate.opsForHash().put(familyKey, refreshJti, toJson(familyValue));
            redisTemplate.expire(familyKey, Duration.ofSeconds(refreshTtl));
        } catch (Exception e) {
            log.warn("Refresh token 状态存储失败: {}", e.getMessage(), e);
            throw new AuthException(10500, "Token 存储失败，请稍后重试");
        }

        return new AccessTokenGenerator.TokenPair(accessToken, refreshToken, familyId, accessTtl, refreshTtl);
    }

    /**
     * 用 refresh token 换新的 access + refresh pair（一次性，原子轮转）
     */
    public AccessTokenGenerator.TokenPair refreshAccessToken(String refreshTokenString) {
        if (!StringUtils.hasText(refreshTokenString)) {
            throw new AuthException(10211, "refresh token 不能为空");
        }

        // 1. 解析 + 验签
        Map<String, Object> payload;
        try {
            payload = TokenUtils.parseToken(refreshTokenString, properties.getSecretKey());
        } catch (AuthException e) {
            throw new AuthException(10211, "refresh token 无效: " + e.getMessage());
        }

        String type = (String) payload.get("type");
        if (!TYPE_REFRESH.equals(type)) {
            throw new AuthException(10211, "token 类型不是 refresh: " + type);
        }
        String familyId = (String) payload.get("family");
        String jti = (String) payload.get("jti");
        if (!StringUtils.hasText(familyId) || !StringUtils.hasText(jti)) {
            throw new AuthException(10211, "refresh token 缺少 family/jti 字段");
        }

        // 2. 检查毒丸
        String revokedKey = TokenKeyBuilder.refreshRevokedPoison(appName, familyId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(revokedKey))) {
            throw new AuthException(10211, "family 已被撤销（重用检测）");
        }

        // 3. 预读 family claims（用于生成新 access token）
        // v2.1 P0 修复：原 Lua 之后二次读 Redis 有竞态。改为 Lua 前预读，
        // Lua 中只做原子标记 consumed + 写新 jti，不再依赖 Lua 之后的 Redis 读取。
        // 若 Lua 前后 family 被 revokeFamily 删，Lua 会返 EXPIRED，新 token 丢弃，客户端重试。
        String familyKey = TokenKeyBuilder.refreshFamily(appName, familyId);
        String originalType = type;
        Map<String, Object> originalClaims = null;
        int preGeneration = 0;
        String preOldAccessJti = null;
        try {
            Object claimsRaw = redisTemplate.opsForHash().get(familyKey, jti);
            if (claimsRaw != null) {
                Map<String, Object> stored = fromJson(String.valueOf(claimsRaw));
                Object t = stored.get("type");
                if (t != null) originalType = t.toString();
                Object cs = stored.get("claims");
                if (cs instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> casted = (Map<String, Object>) cs;
                    originalClaims = casted;
                }
                Object gen = stored.get("generation");
                if (gen instanceof Number) preGeneration = ((Number) gen).intValue();
                Object aj = stored.get("accessJti");
                if (aj != null) preOldAccessJti = aj.toString();
            }
        } catch (Exception e) {
            log.warn("预读 family claims 失败: {}", e.getMessage());
        }
        if (originalClaims == null) {
            originalClaims = new HashMap<>();
            originalClaims.put("type", originalType);
        }

        // 4. 生成新 pair（在 Lua 之前生成，便于把新 jti 数据传入 Lua 原子写入）
        long now = System.currentTimeMillis();
        AccessTokenProperties.Policy policy = properties.getPolicies().get(originalType);
        long refreshTtl = policy != null && policy.getRefreshExpireTime() != null
                ? policy.getRefreshExpireTime() : DEFAULT_REFRESH_TTL_SECONDS;
        long refreshExp = now + refreshTtl * 1000;

        String newRefreshJti = UUID.randomUUID().toString();
        String newAccessToken = accessGenerator.generateToken(originalType, originalClaims);
        Map<String, Object> newAccessPayload = TokenUtils.parseToken(newAccessToken, properties.getSecretKey());
        String newAccessJti = (String) newAccessPayload.get("jti");

        String newRefreshToken = TokenUtils.createToken(
                TYPE_REFRESH, null, null,
                appName, now, refreshExp, newRefreshJti,
                familyId, properties.getSecretKey());

        // 构造新 jti 的 family 数据
        Map<String, Object> newFamilyValue = new HashMap<>();
        newFamilyValue.put("exp", refreshExp);
        newFamilyValue.put("consumed", false);
        newFamilyValue.put("accessJti", newAccessJti);
        newFamilyValue.put("generation", preGeneration + 1);
        int maxRotations = policy != null && policy.getMaxRotations() != null
                ? policy.getMaxRotations() : DEFAULT_MAX_ROTATIONS;
        newFamilyValue.put("maxRotations", maxRotations);
        newFamilyValue.put("type", originalType);
        newFamilyValue.put("claims", originalClaims);
        String newFamilyJson = toJson(newFamilyValue);

        // 5. Lua 原子轮转：标记旧 jti consumed + 写新 jti + expire（单事务）
        try {
            redisTemplate.execute(
                    REFRESH_SCRIPT,
                    Collections.singletonList(familyKey),
                    jti, String.valueOf(maxRotations),
                    newRefreshJti, newFamilyJson, String.valueOf(refreshTtl));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                msg = e.getCause().getMessage();
            }
            log.warn("[RefreshToken] Lua 异常: msg={}", msg);
            if (msg.contains("EXPIRED")) {
                throw new AuthException(10210, "refresh 已过期或 family 不存在");
            }
            if (msg.contains("REUSED")) {
                poisonFamily(familyId);
                revokeFamily(familyId);
                throw new AuthException(10211, "refresh 已被使用，全族撤销");
            }
            if (msg.contains("ROTATION_EXCEEDED")) {
                revokeFamily(familyId);
                throw new AuthException(10212, "family 轮转次数超限，请重新登录");
            }
            throw new AuthException(10211, "refresh 轮转失败: " + msg);
        }

        // 6. 解析 Lua 返回，提取旧 accessJti（用于撤销）
        String oldAccessJti = preOldAccessJti;

        // v2.1 P0 修复：原 newAccessTtl = refreshTtl（最长 30 天）当 accessExpiresInSeconds 返回，客户端误判 access 30 天后过期。
        // 改为从 newAccessPayload 算真实 access TTL（≤ 2h）。
        long newAccessExp = ((Number) newAccessPayload.get("exp")).longValue();
        long newAccessTtl = Math.max(0, (newAccessExp - System.currentTimeMillis()) / 1000);

        // v2.1 P1 修复：轮转后撤销旧 access token，防止旧 access token 在自然过期前继续可用。
        // 旧 accessJti 从 Lua 返回的 family hash 数据中提取（consumed=true 后字段仍保留 accessJti）。
        if (oldAccessJti != null && !oldAccessJti.equals(newAccessJti)) {
            try {
                String accessRevokedKey = TokenKeyBuilder.accessRevokedSet(appName);
                redisTemplate.opsForSet().add(accessRevokedKey, oldAccessJti);
                // v2.1 P0 修复：单调延长 TTL（8 天 = access 自然过期 2h + 缓冲），避免覆盖短 TTL
                accessGenerator.extendRevokedSetExpire(accessRevokedKey, TimeUnit.DAYS.toSeconds(8));
                log.info("【RefreshToken】轮转撤销旧 access jti={}", oldAccessJti);
            } catch (Exception e) {
                log.warn("【RefreshToken】撤销旧 access jti={} 失败: {}", oldAccessJti, e.getMessage());
            }
        }

        return new AccessTokenGenerator.TokenPair(newAccessToken, newRefreshToken, familyId, newAccessTtl, refreshTtl);
    }

    /**
     * 撤销整个 family：把 family 所有 accessJti 加入 access 撤销 Set，删除 family hash。
     * <p>v2.1 修复：原实现把 hash field 名（refresh jti）加入 revoked set，
     * 但 isRevoked 检查的是 access jti，导致 access token 不被撤销（安全 bug）。
     * 现在遍历 hash values，取 accessJti 字段加入 revoked set。
     */
    public void revokeFamily(String familyId) {
        if (!StringUtils.hasText(familyId)) {
            return;
        }
        try {
            String familyKey = TokenKeyBuilder.refreshFamily(appName, familyId);
            Set<Object> refreshJtis = redisTemplate.opsForHash().keys(familyKey);
            if (refreshJtis != null && !refreshJtis.isEmpty()) {
                String revokedKey = TokenKeyBuilder.accessRevokedSet(appName);
                int accessCount = 0;
                for (Object refreshJti : refreshJtis) {
                    Object raw = redisTemplate.opsForHash().get(familyKey, refreshJti);
                    if (raw == null) continue;
                    try {
                        Map<String, Object> data = fromJson(String.valueOf(raw));
                        Object accessJti = data.get("accessJti");
                        if (accessJti != null) {
                            redisTemplate.opsForSet().add(revokedKey, accessJti.toString());
                            accessCount++;
                        }
                    } catch (Exception parseEx) {
                        log.warn("解析 family hash jti={} 数据失败: {}", refreshJti, parseEx.getMessage());
                    }
                }
                // v2.1 P0 修复：用单调延长 helper，避免覆盖短 TTL
                accessGenerator.extendRevokedSetExpire(revokedKey, TimeUnit.DAYS.toSeconds(7));
                log.info("【RefreshToken】已撤销 family={} ({} 个 refresh jti, {} 个 access jti)",
                        familyId, refreshJtis.size(), accessCount);
            }
            redisTemplate.delete(familyKey);
        } catch (Exception e) {
            log.warn("撤销 family {} 失败: {}", familyId, e.getMessage(), e);
        }
    }

    /** 写毒丸 key（family 被重用时调用） */
    private void poisonFamily(String familyId) {
        try {
            String revokedKey = TokenKeyBuilder.refreshRevokedPoison(appName, familyId);
            long ttl = TimeUnit.DAYS.toSeconds(30);
            redisTemplate.opsForValue().set(revokedKey, "1", Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("Poison family {} 失败: {}", familyId, e.getMessage());
        }
    }

    private String extractTokenType(Map<String, Object> claims) {
        Object t = claims.get("type");
        return t != null ? t.toString() : "DEFAULT";
    }

    private static String toJson(Map<String, Object> data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalStateException("序列化失败", e);
        }
    }

    private static Map<String, Object> fromJson(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("反序列化失败: " + json, e);
        }
    }
}
