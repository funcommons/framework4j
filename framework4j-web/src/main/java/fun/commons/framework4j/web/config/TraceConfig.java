package fun.commons.framework4j.web.config;

import fun.commons.framework4j.web.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全链路追踪配置
 * <p>
 * 配置过滤器，将 Micrometer 生成的 Trace ID 写入 HTTP 响应头，方便前端调试。
 * <p>
 * v1.6.0(Issue #21/ADR-0009 缺口1):装配顺序修复 —— 本类带 @ConditionalOnBean(Tracer.class),
 * 此前经 WebAutoConfiguration @Import 以普通 @Configuration 注册,@ConditionalOnBean 在
 * 普通配置类上按处理顺序评估、不保证晚于 MicrometerTracingAutoConfiguration →
 * Tracer 尚未装配时本类被静默跳过(traceIdResponseHeaderFilter 永不注册)。
 * 改为 @AutoConfiguration(afterName=MicrometerTracingAutoConfiguration) +
 * AutoConfiguration.imports 直接注册,保证评估顺序在 Tracer 之后。
 *
 * @since 1.0.0
 */
@Slf4j
@org.springframework.boot.autoconfigure.AutoConfiguration(
        afterName = "org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration")
@ConditionalOnClass(Tracer.class)
@ConditionalOnBean(Tracer.class)
@ConditionalOnProperty(
        prefix = "framework4j.api.trace",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@RequiredArgsConstructor
public class TraceConfig {

    public static final String DEFAULT_TRACE_ID_HEADER = "X-Trace-Id";

    private final Tracer tracer;

    /**
     * 启动时把 Tracer 注入到 {@link TraceContext}，让非 Spring 管理的对象（如 ApiResponse 静态工厂）能用。
     */
    @jakarta.annotation.PostConstruct
    void injectTracer() {
        TraceContext.setTracer(tracer);
    }

    /**
     * 配置响应头 TraceId 过滤器
     *
     * @param headerName 响应头名（默认 X-Trace-Id，可配置 framework4j.api.trace.header-name）
     */
    @Bean
    public Filter traceIdResponseHeaderFilter(
            @Value("${framework4j.api.trace.header-name:" + DEFAULT_TRACE_ID_HEADER + "}") String headerName) {
        log.info("【API】traceIdResponseHeaderFilter，TraceId 响应头过滤器，header={}", headerName);
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                            jakarta.servlet.http.HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                // v2.1 修复：优先用 TraceContext.getTraceId()（MDC 优先，支持网关透传 setTraceId）
                // 原直接调 tracer.currentSpan()，无 Tracer Bean 时 X-Trace-Id 丢失
                String traceId = TraceContext.getTraceId();
                if (traceId != null && !traceId.isBlank()) {
                    response.setHeader(headerName, traceId);
                }
                filterChain.doFilter(request, response);
            }
        };
    }
}
