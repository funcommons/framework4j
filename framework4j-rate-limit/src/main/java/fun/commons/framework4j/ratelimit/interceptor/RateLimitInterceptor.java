package fun.commons.framework4j.ratelimit.interceptor;

import fun.commons.framework4j.api.ApiCode;
import fun.commons.framework4j.ratelimit.annotation.RateLimit;
import fun.commons.framework4j.ratelimit.config.RateLimitProperties;
import fun.commons.framework4j.ratelimit.exception.RateLimitException;
import fun.commons.framework4j.ratelimit.service.RateLimitKeyResolver;
import fun.commons.framework4j.ratelimit.service.RateLimitService;
import fun.commons.framework4j.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;

/**
 * 限流拦截器
 * <p>
 * 优先级低于 Signature 拦截器（防止未授权调用消耗限流计数）。
 * <p>
 * 响应头三件套（mc-api-spec §8.5）：
 * <ul>
 *   <li>X-RateLimit-Limit</li>
 *   <li>X-RateLimit-Remaining</li>
 *   <li>X-RateLimit-Reset（Unix 秒）</li>
 *   <li>Retry-After（被限流时必返）</li>
 * </ul>
 *
 * @since 2.1.0
 */
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitProperties properties;

    public RateLimitInterceptor(RateLimitService rateLimitService,
                                RateLimitKeyResolver keyResolver,
                                RateLimitProperties properties) {
        this.rateLimitService = rateLimitService;
        this.keyResolver = keyResolver;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // v2.1 功能增强：白名单路径 + IP 豁免
        if (isWhitelisted(request)) {
            return true;
        }

        // 仅对 Controller 方法生效
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 解析 @RateLimit 注解（方法级 > 类级 > 全局默认）
        RateLimit annotation = resolveAnnotation(handlerMethod);
        int limit = annotation != null ? annotation.limit() : properties.getDefaultLimit();
        String window = annotation != null && !annotation.window().isEmpty()
                ? annotation.window() : properties.getDefaultWindow();
        String scope = annotation != null && !annotation.scope().isEmpty()
                ? annotation.scope() : properties.getDefaultScope();

        long windowMs;
        try {
            windowMs = parseWindowMs(window);
        } catch (IllegalArgumentException e) {
            // v2.1 P1: window 格式错误返 400（配置错误，非限流触发）
            response.setStatus(400);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":" + ApiCode.PARAM_FORMAT_ERROR.getCode()
                    + ",\"message\":\"window 格式错误: " + window + "\"}");
            return false;
        }
        String key = keyResolver.resolve(request, scope);

        RateLimitService.AcquireResult result = rateLimitService.tryAcquire(key, limit, windowMs);

        // 设置响应头
        if (properties.isIncludeHeaders()) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
            response.setHeader("X-RateLimit-Remaining",
                    String.valueOf(Math.max(0, result.limit() - result.currentCount())));
            response.setHeader("X-RateLimit-Reset",
                    String.valueOf(result.resetAtMs() / 1000));
        }

        if (!result.allowed()) {
            // v2.1 P0: 真正抛出异常（原 buildException 仅构造不抛）
            // 由 writeTooManyRequests 包装为 HTTP 429 + 信封 10500 + Retry-After
            RateLimitException ex = rateLimitService.buildException(result);
            if (properties.isIncludeHeaders()) {
                response.setHeader("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
            }
            writeTooManyRequests(response, ex);
            return false;
        }

        return true;
    }

    private RateLimit resolveAnnotation(HandlerMethod handlerMethod) {
        RateLimit methodAnno = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (methodAnno != null) return methodAnno;
        return handlerMethod.getBeanType().getAnnotation(RateLimit.class);
    }

    /** v2.1 功能增强：检查白名单路径 + IP 豁免 */
    private boolean isWhitelisted(HttpServletRequest request) {
        String path = request.getRequestURI();
        String ip = resolveClientIp(request);

        // 白名单路径（Ant 风格匹配）
        if (properties.getWhitelistPaths() != null) {
            for (String pattern : properties.getWhitelistPaths()) {
                if (matchPath(pattern, path)) return true;
            }
        }

        // 白名单 IP
        if (properties.getWhitelistIps() != null && ip != null) {
            for (String whitelistIp : properties.getWhitelistIps()) {
                if (whitelistIp.equals(ip)) return true;
            }
        }

        return false;
    }

    /** 简单 Ant 匹配（/actuator/** 匹配 /actuator/health） */
    private static boolean matchPath(String pattern, String path) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix);
        }
        return pattern.equals(path);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return request.getRemoteAddr();
    }

    /** v2.1 P1: window 解析容错 */
    private static long parseWindowMs(String window) {
        String trimmed = window.trim();
        try {
            // 兼容 Spring DurationStyle.detect()
            return org.springframework.boot.convert.DurationStyle.detect(trimmed).parse(trimmed).toMillis();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid window format: '" + window + "'. 支持 1s/1m/1h 或 PT1M/PT1H", e);
        }
    }

    /** v2.1 P1: ObjectMapper 单例（避免每次请求 new） */
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private void writeTooManyRequests(HttpServletResponse response, RateLimitException e) throws IOException {
        ApiResponse<?> body = ApiResponse.fail(ApiCode.TOO_MANY_REQUESTS,
                "请求过于频繁，请 " + e.getRetryAfterSeconds() + " 秒后重试");
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        // v2.1 P1: trace_id 双通道（信封 + Header）
        String traceId = org.slf4j.MDC.get("trace_id");
        if (traceId != null && !traceId.isBlank()) {
            response.setHeader("X-Trace-Id", traceId);
        }
        try {
            response.getWriter().write(MAPPER.writeValueAsString(body));
        } catch (Exception ex) {
            response.getWriter().write("{\"code\":" + ApiCode.TOO_MANY_REQUESTS.getCode()
                    + ",\"message\":\"Rate limited\"}");
        }
    }
}
