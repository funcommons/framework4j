package fun.commons.framework4j.idempotency;

import fun.commons.framework4j.web.cache.CachedBodyRequestWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * 缓存 request body + response body 的 servlet 过滤器。
 * <p>
 * v2.1 修复：
 * <ul>
 *   <li>移除 @Component，改为 AutoConfiguration @Bean 注册（受 enabled 开关控制）</li>
 *   <li>主动 readAllBytes 触发 ContentCachingRequestWrapper 缓存（修 preHandle 时 buf 为空的 bug）</li>
 *   <li>同时 wrap response，让拦截器 afterCompletion 能拿到响应体（修回放空响应 bug）</li>
 * </ul>
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdempotencyBodyCacheFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // v2.1 P0 修复：用 CachedBodyRequestWrapper 替代 ContentCachingRequestWrapper。
        // 原 ContentCachingRequestWrapper 不重放 InputStream，拦截器 readAllBytes 后 Controller @RequestBody 拿空 body。
        CachedBodyRequestWrapper wrappedRequest = (request instanceof CachedBodyRequestWrapper w)
                ? w : new CachedBodyRequestWrapper(request);
        // 预读 + 缓存 body（拦截器 hashBody 直接拿 getContentAsByteArray，不消费流）
        wrappedRequest.cacheBody();
        // 包装 response（如未包装）— afterCompletion 阶段才能拿到响应体
        ContentCachingResponseWrapper wrappedResponse = (response instanceof ContentCachingResponseWrapper r)
                ? r : new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            // v2.1 P0 修复：始终 copyBodyToResponse。
            // 拦截器回放分支写入 ContentCachingResponseWrapper 缓冲区，由这里统一刷到真实 response。
            // （原实现用 ATTR_REPLAY_WRITTEN 标记跳过 copy，导致客户端收到空响应体）
            wrappedResponse.copyBodyToResponse();
        }
    }
}
