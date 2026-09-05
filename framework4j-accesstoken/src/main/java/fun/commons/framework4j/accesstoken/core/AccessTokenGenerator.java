package fun.commons.framework4j.accesstoken.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.accesstoken.config.AccessTokenProperties;
import fun.commons.framework4j.accesstoken.exception.AuthException;
import fun.commons.framework4j.accesstoken.util.TokenUtils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Access token 生成器
 * <p>
 * 仅负责 access token 路径：生成 / 撤销 / 检查 jti 黑名单。Refresh token family 逻辑见 {@link RefreshTokenService}。
 *
 * @since 2.0.0（refresh 逻辑拆到 RefreshTokenService）
 */
@Slf4j
public class AccessTokenGenerator {

    /** Access token 类型标识 */
    public static final String TYPE_ACCESS = "access";

    /** v2.1 P1 修复：共享 TokenUtils.MAPPER 单例，避免重复创建 */
    private static final ObjectMapper MAPPER = TokenUtils.MAPPER;

    /**
     * v2.1 P0 修复：撤销 Set TTL 单调延长 Lua。
     * <p>原实现 revokeToken/revokeFamily/refreshAccessToken 各自 expire 共享 Set，
     * 后撤销的短 TTL token 会把 Set 提前过期，导致尚未过期的 jti 从撤销名单消失 → 安全绕过。
     * <p>Lua 逻辑：仅当 key 当前无 TTL (-1) 或 TTL < newTtl 时才 expire，保证 TTL 单调不减。
     * <p>KEYS[1] = revokedKey; ARGV[1] = newTtlSeconds
     */
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Void> EXTEND_EXPIRE_SCRIPT =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                    "local ttl = redis.call('TTL', KEYS[1]); " +
                    "if ttl < 0 or ttl < tonumber(ARGV[1]) then " +
                    "  redis.call('EXPIRE', KEYS[1], ARGV[1]); " +
                    "end " +
                    "return nil",
                    Void.class);

    private final AccessTokenProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final String appName;

    public AccessTokenGenerator(AccessTokenProperties properties,
                                StringRedisTemplate redisTemplate,
                                String appName) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.appName = appName;
    }

    /**
     * 生成 Access Token
     */
    public String generateToken(String tokenType, Map<String, Object> claims) {
        return generateToken(tokenType, claims, false);
    }

    /**
     * 生成 Access Token(Issue #23):可选把业务 claims 嵌入 JWT payload。
     * <p>
     * claims 以 <b>嵌套 {@code payload.claims} 键</b>写入,不拍平 —— 业务 claim 与系统字段
     * (type/jti/nonce/hash/iss/sub/iat/exp/family)同名时会静默覆盖、破坏 token 语义,嵌套彻底规避。
     * <p>
     * 仅影响 payload 可见性,<b>不影响信任语义</b>:校验链路
     * ({@code AccessTokenValidationStrategy})继续从 Redis 会话 metadata 读 claims 填充
     * TokenContext,可撤销 / claims 热更(updateClaims)语义完整保留;
     * payload 中的 claims 仅供持有方解码读取(token 自包含),不作为服务端信任源。
     *
     * @param embedInPayload true 时业务 claims 嵌入 payload;false 即 1.6.x 行为(仅存会话)
     */
    public String generateToken(String tokenType, Map<String, Object> claims, boolean embedInPayload) {
        if (claims == null) {
            throw new AuthException(10200, "Claims 不能为 null");
        }

        AccessTokenProperties.Policy policy = properties.getPolicies().get(tokenType);
        if (policy == null) {
            throw new AuthException(10200, "未定义的 TokenType: " + tokenType);
        }
        if (policy.getKey() == null || policy.getKey().isEmpty()) {
            throw new AuthException(10200, "TokenType [" + tokenType + "] 必须配置 key 字段 (安全要求)");
        }
        if (tokenType.length() > 100) {
            throw new AuthException(10200, "TokenType 长度不能超过100个字符");
        }

        String keyValue = extractKeyValue(policy.getKey(), claims);
        String keyHash = TokenUtils.calculateKeyHash(keyValue, properties.getHashSalt());
        String redisKey = TokenKeyBuilder.accessMetadata(appName, tokenType, keyHash);

        String nonce = UUID.randomUUID().toString();
        String jti = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long expireSeconds = policy.getExpireTime() != null ? policy.getExpireTime() : properties.getExpireTime();
        long exp = now + (expireSeconds * 1000);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", tokenType);
        metadata.put("jti", jti);
        metadata.put("nonce", nonce);
        metadata.put("issuedAt", now);
        metadata.put("hardExpireAt", exp);
        metadata.put("claims", claims);
        metadata.put("policySnapshot", policy);

        try {
            redisTemplate.opsForValue().set(redisKey, toJson(metadata), Duration.ofSeconds(expireSeconds));
        } catch (Exception e) {
            log.warn("Token 存储失败: {}", e.getMessage(), e);
            throw new AuthException(10500, "Token 存储失败，请稍后重试");
        }

        // v2.1 修复：activationTimeLimit 用独立计数 Key，不再覆盖主 metadata Key 的 TTL。
        // 原实现直接 expire 主 Key 会让 token 在 hardExpireAt 之前失效，与 metadata 矛盾。
        // 现在用独立 activation key（首次使用前需激活的场景），主 Key 保持 expireSeconds TTL。
        if (policy.getActivationTimeLimit() != null && policy.getActivationTimeLimit() > 0) {
            String activationKey = TokenKeyBuilder.accessActivation(appName, tokenType, keyHash);
            redisTemplate.opsForValue().set(activationKey, "PENDING",
                    Duration.ofSeconds(policy.getActivationTimeLimit()));
        }

        return TokenUtils.createToken(
                tokenType, nonce, keyHash,
                appName, now, exp, jti,
                null, embedInPayload ? claims : null,
                properties.getSecretKey());
    }

    /**
     * 注销 Access Token
     */
    public void revokeToken(String token) {
        try {
            Map<String, Object> payload = TokenUtils.parseToken(token, properties.getSecretKey());
            String type = (String) payload.get("type");
            String hash = (String) payload.get("hash");
            String jti = (String) payload.get("jti");
            Long exp = ((Number) payload.get("exp")).longValue();

            if (StringUtils.hasText(type) && StringUtils.hasText(hash) && StringUtils.hasText(jti)) {
                String redisKey = TokenKeyBuilder.accessMetadata(appName, type, hash);
                redisTemplate.delete(redisKey);

                String revokedKey = TokenKeyBuilder.accessRevokedSet(appName);
                long ttlSec = Math.max(
                        (exp - System.currentTimeMillis()) / 1000,
                        TimeUnit.DAYS.toSeconds(1));
                redisTemplate.opsForSet().add(revokedKey, jti);
                // v2.1 P0 修复：TTL 单调延长，避免短 TTL token 撤销时把整个 Set 提前过期
                extendRevokedSetExpire(revokedKey, ttlSec);
            }
        } catch (Exception e) {
            // 忽略解析异常（已失效的 token 再撤销无意义）
        }
    }

    /**
     * 检查 access token jti 是否已被撤销
     */
    public boolean isRevoked(String jti) {
        try {
            String revokedKey = TokenKeyBuilder.accessRevokedSet(appName);
            Boolean isMember = redisTemplate.opsForSet().isMember(revokedKey, jti);
            return Boolean.TRUE.equals(isMember);
        } catch (Exception e) {
            log.warn("检查 token 撤销状态失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * v2.1 P0 修复：单调延长撤销 Set 的 TTL。
     * <p>仅当当前 TTL 小于 newTtlSec 或无 TTL 时才 expire，避免短 TTL 撤销覆盖长 TTL。
     * <p>供本类 revokeToken + RefreshTokenService.revokeFamily/refreshAccessToken 共用。
     */
    public void extendRevokedSetExpire(String revokedKey, long newTtlSec) {
        try {
            redisTemplate.execute(
                    EXTEND_EXPIRE_SCRIPT,
                    java.util.Collections.singletonList(revokedKey),
                    String.valueOf(newTtlSec));
        } catch (Exception e) {
            // 兜底：直接 expire（保留原行为）
            try {
                redisTemplate.expire(revokedKey, Duration.ofSeconds(newTtlSec));
            } catch (Exception ignore) {
                log.warn("延长撤销 Set TTL 失败 key={}", revokedKey);
            }
        }
    }

    public String buildRedisKey(String tokenType, String hash) {
        return TokenKeyBuilder.accessMetadata(appName, tokenType, hash);
    }

    public String getAppName() {
        return appName;
    }

    /**
     * v2.1 功能增强：按用户 UID + token 类型踢出所有设备（强退）
     * <p>遍历 Redis 中的 token key，删除该用户所有 session + jti 加入撤销 Set
     *
     * @param tokenType token 类型（如 "WEB" / "ADMIN"）
     * @param uid 用户 ID
     * @return 被踢出的 token 数量
     */
    public int revokeByUser(String tokenType, String uid) {
        AccessTokenProperties.Policy policy = properties.getPolicies().get(tokenType);
        if (policy == null) {
            log.warn("[AccessToken] revokeByUser: 未知 tokenType={}", tokenType);
            return 0;
        }

        // 构造 Redis key pattern（与 generateToken 一致的 key 构造逻辑）
        String keyValue = extractKeyValue(policy.getKey(), Map.of("uid", uid));
        String keyHash = TokenUtils.calculateKeyHash(keyValue, properties.getHashSalt());
        String redisKeyPattern = TokenKeyBuilder.accessMetadata(appName, tokenType, keyHash + "*");

        int revoked = 0;
        try {
            Set<String> keys = redisTemplate.keys(redisKeyPattern);
            if (keys != null) {
                for (String key : keys) {
                    // 取 metadata 拿 jti
                    String meta = redisTemplate.opsForValue().get(key);
                    if (meta != null) {
                        try {
                            Map<String, Object> data = new ObjectMapper().readValue(meta, Map.class);
                            Object jti = data.get("jti");
                            if (jti != null) {
                                String revokedKey = TokenKeyBuilder.accessRevokedSet(appName);
                                redisTemplate.opsForSet().add(revokedKey, jti.toString());
                            }
                        } catch (Exception ignored) {}
                    }
                    redisTemplate.delete(key);
                    revoked++;
                }
            }
            log.info("[AccessToken] revokeByUser: type={} uid={} revoked={}", tokenType, uid, revoked);
        } catch (Exception e) {
            log.error("[AccessToken] revokeByUser failed: {}", e.getMessage(), e);
        }
        return revoked;
    }

    /**
     * v1.4.1（Issue #16）：更新指定用户当前 token 的 Redis claims（角色变更实时生效，无需重签 / 重登）。
     * <p>
     * 校验链路每次请求都从该用户级 metadata key 读取 claims（而非只信 JWT payload），
     * 因此调用本方法后，用户已持有的 token 在下一个请求即携带新 claims（含 {@code roles}）。
     * <ul>
     *   <li>整包替换 claims map（业务侧为角色事实来源，全量覆盖语义）</li>
     *   <li>metadata 其余字段（jti / nonce / hardExpireAt / policySnapshot）不变，token 本身无需重签</li>
     *   <li>TTL 原样保留（SET KEEPTTL，Redis 6.0+）</li>
     * </ul>
     * 与 {@link #revokeByUser} 同约定：仅适用于 policy key 只含 {@code uid} 的 token 类型（如 WEB / ADMIN）；
     * 多字段 key（如 APP 的 uid+dev）会因缺少 key 字段抛 10200。
     *
     * @param tokenType token 类型（如 "WEB"）
     * @param uid       用户 ID
     * @param claims    新的完整 claims（含 {@code roles}）
     * @return true=更新成功；false=该用户当前无有效 token（未登录或已过期）
     */
    public boolean updateClaims(String tokenType, String uid, Map<String, Object> claims) {
        AccessTokenProperties.Policy policy = properties.getPolicies().get(tokenType);
        if (policy == null) {
            log.warn("[AccessToken] updateClaims: 未知 tokenType={}", tokenType);
            return false;
        }
        if (claims == null) {
            throw new AuthException(10200, "Claims 不能为 null");
        }

        String keyValue = extractKeyValue(policy.getKey(), Map.of("uid", uid));
        String keyHash = TokenUtils.calculateKeyHash(keyValue, properties.getHashSalt());
        String redisKey = TokenKeyBuilder.accessMetadata(appName, tokenType, keyHash);

        String metadata = redisTemplate.opsForValue().get(redisKey);
        if (!StringUtils.hasText(metadata)) {
            log.info("[AccessToken] updateClaims: 用户无有效 token type={} uid={}", tokenType, uid);
            return false;
        }

        Map<String, Object> data = fromJson(metadata);
        data.put("claims", claims);
        try {
            redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Boolean>) connection -> connection.set(
                    redisKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    toJson(data).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    Expiration.keepTtl(),
                    RedisStringCommands.SetOption.UPSERT));
            log.info("[AccessToken] updateClaims: type={} uid={} claimKeys={}", tokenType, uid, claims.keySet());
            return true;
        } catch (Exception e) {
            log.warn("[AccessToken] updateClaims: 存储失败 {}", e.getMessage(), e);
            throw new AuthException(10500, "Token 存储失败，请稍后重试");
        }
    }

    private String extractKeyValue(List<String> keys, Map<String, Object> claims) {
        List<String> values = new ArrayList<>();
        for (String k : keys) {
            Object val = claims.get(k);
            if (val == null) {
                // Issue #20: 报错文案区分「claims 字段名」与「配置值」—— policy.key 是 claims 必需
                // 字段名列表(决定会话互斥维度),不是签名密钥;误配时旧文案会把配置值回显成缺失字段名
                throw new AuthException(10200, "生成 Token 失败:claims 缺少 policy.key 声明的必需字段 [" + k
                        + "](policy.key 是 claims 字段名列表,不是签名密钥;请在 generateToken 的 claims 中带上该字段)");
            }
            values.add(String.valueOf(val));
        }
        return String.join("_", values);
    }

    /**
     * Access + Refresh token pair（保留 record 以兼容调用方）
     */
    public record TokenPair(
            String accessToken,
            String refreshToken,
            String familyId,
            long accessExpiresInSeconds,
            long refreshExpiresInSeconds
    ) {}

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
