package fun.commons.framework4j.accesstoken.interceptor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.accesstoken.context.TokenContext;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.accesstoken.core.TokenKeyBuilder;
import fun.commons.framework4j.accesstoken.exception.AuthException;
import fun.commons.framework4j.accesstoken.exception.AuthExceptionFactory;
import fun.commons.framework4j.accesstoken.util.TokenUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Access token 校验策略
 * <p>
 * 从 TokenInterceptor 拆出，专注 access token 路径：验签 → 撤销 → 类型匹配 → Redis 强一致 →
 * nonce → 绝对过期 → 限次 → 自动续期 → 注入上下文。
 *
 * @since 2.0.0
 */
@Slf4j
public class AccessTokenValidationStrategy {

    /** v2.1 P1 修复：共享 TokenUtils.MAPPER 单例，避免重复创建 */
    private static final ObjectMapper MAPPER = TokenUtils.MAPPER;

    private final AccessTokenGenerator generator;
    private final StringRedisTemplate redisTemplate;

    public AccessTokenValidationStrategy(AccessTokenGenerator generator, StringRedisTemplate redisTemplate) {
        this.generator = generator;
        this.redisTemplate = redisTemplate;
    }

    /**
     * @param annotation @RequiresToken（type=access）
     * @param payload    TokenUtils.parseToken 返回的 claims
     * @return true 通过；false 应改由 throwAuthException 抛异常
     */
    public boolean validate(RequiresToken annotation, Map<String, Object> payload, HttpServletRequest request) throws Exception {
        String type = (String) payload.get("type");
        String nonce = (String) payload.get("nonce");
        String hash = (String) payload.get("hash");
        String jti = (String) payload.get("jti");

        // 撤销检查
        if (StringUtils.hasText(jti) && generator.isRevoked(jti)) {
            AuthExceptionFactory.throwCustom(annotation, 10208, "令牌已注销");
            return false;
        }

        // 类型匹配
        if (!annotation.value().equals(type)) {
            AuthExceptionFactory.throwCustom(annotation, 10300, "令牌类型不匹配");
            return false;
        }

        // Redis 强一致
        String redisKey = generator.buildRedisKey(type, hash);
        String redisValueStr = redisTemplate.opsForValue().get(redisKey);

        if (!StringUtils.hasText(redisValueStr)) {
            AuthExceptionFactory.throwCustom(annotation, 10201, "令牌已过期或不存在");
            return false;
        }

        Map<String, Object> redisData = fromJson(redisValueStr);
        String serverNonce = (String) redisData.get("nonce");
        long hardExpireAt = asLong(redisData.get("hardExpireAt"));

        // Nonce
        if (!StringUtils.hasText(serverNonce) || !serverNonce.equals(nonce)) {
            AuthExceptionFactory.throwCustom(annotation, 10205, "账号已在别处登录");
            return false;
        }

        // 绝对过期
        if (System.currentTimeMillis() > hardExpireAt) {
            redisTemplate.delete(redisKey);
            AuthExceptionFactory.throwCustom(annotation, 10201, "会话已达最大时长，请重新登录");
            return false;
        }

        Map<String, Object> policySnapshot = asMap(redisData.get("policySnapshot"));

        // 限次
        Integer maxUsage = asInteger(policySnapshot.get("maxUsage"));
        String statsKey = null;
        if (maxUsage != null && maxUsage > 0) {
            statsKey = TokenKeyBuilder.accessUsageStats(redisKey);
            Long currentUsage = redisTemplate.opsForValue().increment(statsKey);
            // v2.1 修复：主 key 已过期时 getExpire 返回 -2，expire(statsKey, -2) 会异常或永不过期（内存泄漏）
            Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
            if (ttl == null || ttl <= 0) {
                // v2.1 P0 修复：主 key 已过期/不存在，statsKey 残留会永久计数 + 重放攻击
                // 删除 statsKey 避免泄漏，抛 10201 让客户端重新登录
                redisTemplate.delete(statsKey);
                AuthExceptionFactory.throwCustom(annotation, 10201, "令牌已过期或不存在");
                return false;
            } else {
                redisTemplate.expire(statsKey, ttl, TimeUnit.SECONDS);
            }
            if (currentUsage != null && currentUsage > maxUsage) {
                AuthExceptionFactory.throwCustom(annotation, 10201, "令牌使用次数超限");
                return false;
            }
        }

        // 续期
        Boolean autoRenew = asBoolean(policySnapshot.get("autoRenew"));
        Long renewIncrement = asLong(policySnapshot.get("renewIncrement"));
        Long renewedTtl = null;
        if (Boolean.TRUE.equals(autoRenew) && renewIncrement != null) {
            redisTemplate.expire(redisKey, renewIncrement, TimeUnit.SECONDS);
            renewedTtl = renewIncrement;
        } else {
            Long policyExpire = asLong(policySnapshot.get("expireTime"));
            Long currentTtl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
            if (policyExpire != null && currentTtl != null && currentTtl >= 0 && currentTtl < policyExpire) {
                redisTemplate.expire(redisKey, policyExpire, TimeUnit.SECONDS);
                renewedTtl = policyExpire;
            }
        }
        // v2.1 P1 修复：主 key 续期后同步续期 statsKey，避免 statsKey 先过期导致计数器归零、攻击者突破 maxUsage。
        if (statsKey != null && renewedTtl != null && renewedTtl > 0) {
            redisTemplate.expire(statsKey, renewedTtl, TimeUnit.SECONDS);
        }

        // v2.1 功能增强：获取剩余 TTL 供 TokenInterceptor 设置 X-Token-Expire-At 响应头
        Long finalTtl = renewedTtl;
        if (finalTtl == null) {
            finalTtl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        }

        // 注入上下文
        Map<String, Object> claims = asMap(redisData.get("claims"));
        TokenContext.set(type, claims, finalTtl != null ? finalTtl : -1);
        return true;
    }


    private static Map<String, Object> fromJson(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new AuthException(10207, "Token 元数据解析失败");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object raw) {
        if (raw instanceof Map) return (Map<String, Object>) raw;
        return Map.of();
    }

    private static long asLong(Object raw) {
        if (raw instanceof Number) return ((Number) raw).longValue();
        if (raw instanceof String s && !s.isEmpty()) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignore) {}
        }
        return 0L;
    }

    private static Integer asInteger(Object raw) {
        if (raw instanceof Number) return ((Number) raw).intValue();
        if (raw instanceof String s && !s.isEmpty()) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignore) {}
        }
        return null;
    }

    private static Boolean asBoolean(Object raw) {
        if (raw instanceof Boolean) return (Boolean) raw;
        if (raw instanceof String s) return Boolean.parseBoolean(s);
        return null;
    }
}
