package fun.commons.framework4j.accesstoken.interceptor;

import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.accesstoken.context.TokenContext;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.accesstoken.core.TokenKeyBuilder;
import fun.commons.framework4j.accesstoken.exception.AuthException;
import fun.commons.framework4j.accesstoken.exception.AuthExceptionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Refresh token 前置校验策略
 * <p>
 * 仅做"前置校验"（poison 状态 + family hash 存在性）。真正的轮转（一次性 + 重用检测 + 全族撤销）
 * 由消费者在 Controller 调用 RefreshTokenService.refreshAccessToken() 完成。
 *
 * @since 2.0.0
 */
@Slf4j
public class RefreshTokenValidationStrategy {

    private final AccessTokenGenerator generator;
    private final StringRedisTemplate redisTemplate;

    public RefreshTokenValidationStrategy(AccessTokenGenerator generator, StringRedisTemplate redisTemplate) {
        this.generator = generator;
        this.redisTemplate = redisTemplate;
    }

    /**
     * @param annotation @RequiresToken（type=refresh）
     * @param payload    TokenUtils.parseToken 返回的 claims
     */
    public boolean validate(RequiresToken annotation, Map<String, Object> payload, HttpServletRequest request) throws Exception {
        String type = (String) payload.get("type");
        if (!"refresh".equals(type)) {
            AuthExceptionFactory.throwCustom(annotation, 10211, "token 类型不是 refresh: " + type);
            return false;
        }
        String familyId = (String) payload.get("family");
        String jti = (String) payload.get("jti");
        if (!StringUtils.hasText(familyId) || !StringUtils.hasText(jti)) {
            AuthExceptionFactory.throwCustom(annotation, 10211, "refresh token 缺少 family/jti 字段");
            return false;
        }

        String appNs = generator.getAppName();

        // 毒丸
        try {
            String revokedKey = TokenKeyBuilder.refreshRevokedPoison(appNs, familyId);
            if (Boolean.TRUE.equals(redisTemplate.hasKey(revokedKey))) {
                AuthExceptionFactory.throwCustom(annotation, 10211, "family 已被撤销（重用检测）");
                return false;
            }
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.warn("检查 refresh 撤销状态失败: {}", e.getMessage());
        }

        // family hash 存在性
        // 注：consumed 字段不在 interceptor 校验，由 RefreshTokenService.refreshAccessToken 的 Lua 脚本原子检测。
        // 这是设计决策：interceptor 只做前置过滤（poison + 存在性），真正的轮转/重用检测在 Service 层。
        try {
            String familyKey = TokenKeyBuilder.refreshFamily(appNs, familyId);
            Boolean hasJti = redisTemplate.opsForHash().hasKey(familyKey, jti);
            if (!Boolean.TRUE.equals(hasJti)) {
                AuthExceptionFactory.throwCustom(annotation, 10210, "refresh jti 不在 family 中（已过期或被轮转）");
                return false;
            }
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.warn("检查 family hash 失败: {}", e.getMessage());
        }

        TokenContext.set("refresh", payload);
        return true;
    }

}
