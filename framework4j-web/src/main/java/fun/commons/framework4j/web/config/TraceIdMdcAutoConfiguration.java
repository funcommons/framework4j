package fun.commons.framework4j.web.config;

import fun.commons.framework4j.web.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * TraceId MDC 兜底过滤器：请求入口保证 MDC 里有 traceId。
 * <p>
 * 背景：{@link TraceContext#getTraceId()} 只从 MDC / Micrometer Tracer 读；
 * 未引入 micrometer-tracing 的应用 MDC 为空，导致：
 * <ul>
 *   <li>日志 pattern {@code %X{traceId}} 恒空，响应 trace_id 与日志无法关联</li>
 *   <li>framework4j-tracelog 的 AsyncRedisLogAppender 依赖 MDC traceId 采集，
 *       无 MDC 则一条都采不到</li>
 * </ul>
 * 本过滤器在请求入口检查 MDC，缺失则生成 UUID 写入（请求结束清理），
 * 有 Micrometer Tracing 时其已写入 MDC，本过滤器不覆盖。
 *
 * @since 1.3.2
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "framework4j.api.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TraceIdMdcAutoConfiguration {

    private static final String MDC_TRACE_ID_KEY = "traceId";

    @Bean
    @ConditionalOnMissingBean(name = "traceIdMdcFilter")
    public FilterRegistrationBean<OncePerRequestFilter> traceIdMdcFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                String existing = MDC.get(MDC_TRACE_ID_KEY);
                if (existing != null && !existing.isBlank()) {
                    // Micrometer Tracing 或网关透传已写入, 不覆盖不清理
                    filterChain.doFilter(request, response);
                    return;
                }
                try {
                    MDC.put(MDC_TRACE_ID_KEY, UUID.randomUUID().toString());
                    filterChain.doFilter(request, response);
                } finally {
                    MDC.remove(MDC_TRACE_ID_KEY);
                }
            }
        });
        // 最高优先级: 保证业务 Filter/Interceptor/Controller 的日志都带 traceId
        registration.setOrder(Integer.MIN_VALUE + 100);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
