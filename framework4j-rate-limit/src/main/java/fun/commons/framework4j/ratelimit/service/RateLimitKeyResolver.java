package fun.commons.framework4j.ratelimit.service;

import fun.commons.framework4j.ratelimit.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 限流 Key 解析器
 * <p>
 * 按 scope 计算限流维度：
 * <ul>
 *   <li>ip - X-Forwarded-For 第一个值或 remoteAddr</li>
 *   <li>user - X-User-Id 或 TokenContext uid（如有）</li>
 *   <li>app - X-Access-Key</li>
 *   <li>global - 固定 "global"</li>
 * </ul>
 *
 * @since 2.1.0
 */
public class RateLimitKeyResolver {

    private final RateLimitProperties properties;

    public RateLimitKeyResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    /**
     * 解析限流 key
     * <p>v2.1 P0 修复：scope=user 未登录时回退到 IP，避免所有匿名用户共享一个桶被恶意 IP 耗尽配额
     *
     * @param request HTTP 请求
     * @param scope   ip / user / app / global
     * @return 完整 Redis key（含前缀 + scope + 维度值 + path）
     */
    public String resolve(HttpServletRequest request, String scope) {
        String effectiveScope = (scope == null || scope.isEmpty())
                ? properties.getDefaultScope() : scope;
        ResolveResult result = resolveDimension(request, effectiveScope);
        // 若 scope=user 但未登录（result.fallback=true），降级 scope 到 ip
        String finalScope = result.fallback ? "ip" : effectiveScope;
        return properties.getKeyPrefix()
                + ":" + finalScope
                + ":" + result.dimension
                + ":" + request.getRequestURI();
    }

    private static class ResolveResult {
        final String dimension;
        final boolean fallback;  // 是否因数据缺失触发了回退
        ResolveResult(String dimension, boolean fallback) {
            this.dimension = dimension;
            this.fallback = fallback;
        }
    }

    private ResolveResult resolveDimension(HttpServletRequest request, String scope) {
        switch (scope) {
            case "user": {
                String uid = request.getHeader("X-User-Id");
                if (StringUtils.hasText(uid)) return new ResolveResult(uid, false);
                // v2.1 P0: 未登录回退 IP（原 anonymous 让所有匿名共享桶）
                return new ResolveResult(resolveIp(request), true);
            }
            case "app": {
                String appKey = request.getHeader("X-Access-Key");
                // app 缺失用 unknown 标记（开放 API 场景 AK 必填，缺失通常表示攻击）
                return new ResolveResult(
                        StringUtils.hasText(appKey) ? appKey : "unknown", false);
            }
            case "global":
                return new ResolveResult("global", false);
            case "ip":
            default:
                return new ResolveResult(resolveIp(request), false);
        }
    }

    /** X-Forwarded-For 优先（取第一个 IP），否则 remoteAddr */
    private String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            String ip = comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
            if (StringUtils.hasText(ip)) return ip;
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) return realIp;
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }
}
