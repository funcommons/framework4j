package fun.commons.framework4j.accesstoken.interceptor;

import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.accesstoken.config.AccessTokenProperties;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.accesstoken.exception.AuthException;
import fun.commons.framework4j.accesstoken.exception.AuthExceptionFactory;
import fun.commons.framework4j.accesstoken.util.TokenUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * 核心鉴权拦截器
 * <p>
 * 仅做：①HandlerMethod 检查 ②注解提取 ③Token 提取 ④parseToken 验签。
 * 业务校验委托给 {@link AccessTokenValidationStrategy} 或 {@link RefreshTokenValidationStrategy}。
 *
 * @since 2.0.0（拆分出 access/refresh strategy）
 */
@Slf4j
public class TokenInterceptor implements HandlerInterceptor {

    private final AccessTokenGenerator generator;
    private final AccessTokenProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final AccessTokenValidationStrategy accessStrategy;
    private final RefreshTokenValidationStrategy refreshStrategy;
    /** v1.4.1（Issue #16）：token 校验通过后的角色校验扩展点 */
    private final RoleAuthorizer roleAuthorizer;

    public TokenInterceptor(AccessTokenGenerator generator,
                            AccessTokenProperties properties,
                            StringRedisTemplate redisTemplate) {
        this.generator = generator;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.accessStrategy = new AccessTokenValidationStrategy(generator, redisTemplate);
        this.refreshStrategy = new RefreshTokenValidationStrategy(generator, redisTemplate);
        this.roleAuthorizer = new RoleAuthorizer();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;

        RequiresToken annotation = handlerMethod.getMethodAnnotation(RequiresToken.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequiresToken.class);
        }
        if (annotation == null) {
            return true;
        }

        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            AuthExceptionFactory.throwCustom(annotation, 10200, "未提供认证令牌");
            return false;
        }

        Map<String, Object> payload;
        try {
            payload = TokenUtils.parseToken(token, properties.getSecretKey());
        } catch (AuthException e) {
            AuthExceptionFactory.throwCustom(annotation, e.getCode(), e.getMessage());
            return false;
        }

        if ("refresh".equalsIgnoreCase(annotation.type())) {
            // v1.4.1（Issue #16）：refresh 端点仅做 family 状态校验，不做角色校验
            return refreshStrategy.validate(annotation, payload, request);
        }
        boolean ok = accessStrategy.validate(annotation, payload, request);
        // v1.4.1（Issue #16 方案 A）：token 校验通过后做角色校验（roles/anyRole 均空时放行）。
        // 此时 AccessTokenValidationStrategy 已把 Redis claims 注入 TokenContext。
        if (ok) {
            roleAuthorizer.check(annotation);
        }
        // v2.1 功能增强：access token 校验通过后设置 X-Token-Expire-At 响应头
        // 客户端可在剩余 < 5min 时主动调 /v1/auth/refresh 续期
        if (ok) {
            long expireSeconds = fun.commons.framework4j.accesstoken.context.TokenContext.getExpireSeconds();
            if (expireSeconds > 0) {
                response.setHeader("X-Token-Expire-At", String.valueOf(expireSeconds));
            }
        }
        return ok;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        fun.commons.framework4j.accesstoken.context.TokenContext.clear();
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

}
