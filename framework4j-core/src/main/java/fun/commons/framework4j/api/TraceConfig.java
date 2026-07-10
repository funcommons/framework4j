package fun.commons.framework4j.api;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全链路追踪配置
 * <p>
 * 配置过滤器，将 Micrometer 生成的 Trace ID 写入 HTTP 响应头，方便前端调试。
 *
 * @author LDX2T
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnClass(Tracer.class)
@ConditionalOnBean(Tracer.class)
@RequiredArgsConstructor
public class TraceConfig {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final Tracer tracer;

    /**
     * 配置响应头 TraceId 过滤器
     */
    @Bean
    public Filter traceResponseFilter() {
        log.info("【Trace】traceResponseFilter，全链路追踪过滤器，自动将TraceID写入响应头{}", TRACE_ID_HEADER);
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                            jakarta.servlet.http.HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {

                // 获取当前 Span 的 Trace ID 并写入响应头
                if (tracer.currentSpan() != null) {
                    response.setHeader(TRACE_ID_HEADER, tracer.currentSpan().context().traceId());
                }

                filterChain.doFilter(request, response);
            }
        };
    }
}
