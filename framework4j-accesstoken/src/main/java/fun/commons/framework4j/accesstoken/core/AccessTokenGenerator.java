package fun.commons.framework4j.accesstoken.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.accesstoken.config.AccessTokenProperties;
import fun.commons.framework4j.accesstoken.exception.AuthException;
import fun.commons.framework4j.accesstoken.util.TokenUtils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    private String extractKeyValue(List<String> keys, Map<String, Object> claims) {
        List<String> values = new ArrayList<>();
        for (String k : keys) {
            Object val = claims.get(k);
            if (val == null) {
                throw new AuthException(10200, "生成 Token 失败：Claims 中缺少必要的 Key 字段 [" + k + "]");
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
