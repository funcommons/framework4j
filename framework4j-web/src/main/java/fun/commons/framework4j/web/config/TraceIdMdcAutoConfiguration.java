package fun.commons.framework4j.web.config;

import fun.commons.framework4j.web.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * TraceId 链路过滤器：入站采纳 → MDC 写入 → 响应头回显（issue #25 mc-monitor 契约）。
 * <p>
 * v1.3.2 背景未引入 micrometer-tracing 的应用 MDC 为空，导致日志 pattern {@code %X{traceId}} 恒空、
 * framework4j-tracelog 采集不到日志——本过滤器保证请求线程 MDC 必有 traceId。
 * <p>
 * v1.8 (issue #25) 三项契约增强：
 * <ol>
 *   <li><b>入站采纳</b>：上游/网关传入的 {@code X-Trace-Id}（header 名可配
 *       {@code framework4j.api.trace.header-name}）经 sanitize（trim 后 16~64 位、字符白名单
 *       {@code [A-Za-z0-9._-]}）合法即采纳，不再另行生成——跨服务链路不断裂；
 *       非法/缺失时本地生成 UUID（有 micrometer-tracing 时其已写入 MDC，仍不覆盖）</li>
 *   <li><b>响应头回显</b>：{@code chain.doFilter} 之前回写响应头——controller 提前提交响应、
 *       抛异常路径也带 trace_id，排障对得上</li>
 *   <li><b>MDC key 可配置</b>：{@code framework4j.api.trace.mdc-key}（默认 {@code traceId}），
 *       注入 {@link TraceContext}，framework4j-tracelog 采集侧同步生效，双 key 双写不再必要</li>
 * </ol>
 *
 * @since 1.3.2
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "framework4j.api.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TraceIdMdcAutoConfiguration {

    public static final String DEFAULT_TRACE_HEADER = "X-Trace-Id";
    public static final String DEFAULT_MDC_KEY = "traceId";

    /** mc-monitor 契约：合法入站 trace id 为 16~64 位的 [A-Za-z0-9._-] */
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._\\-]{16,64}");

    @Bean
    @ConditionalOnMissingBean(name = "traceIdMdcFilter")
    public FilterRegistrationBean<OncePerRequestFilter> traceIdMdcFilter(
            @Value("${framework4j.api.trace.header-name:" + DEFAULT_TRACE_HEADER + "}") String headerName,
            @Value("${framework4j.api.trace.mdc-key:" + DEFAULT_MDC_KEY + "}") String mdcKey) {

        // issue #25：把配置的 MDC key 注入 TraceContext，tracelog 采集/拦截器侧经 getMdcKey() 同步
        TraceContext.setMdcKey(mdcKey);
        log.info("【API】traceIdMdcFilter，入站采纳+响应回显，header={}，mdcKey={}", headerName, mdcKey);

        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                String existing = MDC.get(mdcKey);
                if (existing != null && !existing.isBlank()) {
                    // Micrometer Tracing 或前置组件已写入: 不覆盖不清理（生命周期归其所有），仅补响应头回显
                    response.setHeader(headerName, existing);
                    filterChain.doFilter(request, response);
                    return;
                }
                String traceId = sanitize(request.getHeader(headerName));
                if (traceId == null) {
                    if (request.getHeader(headerName) != null) {
                        log.debug("【API】入站 {} 不合法（需 16~64 位 [A-Za-z0-9._-]），本地重新生成 traceId",
                                headerName);
                    }
                    traceId = UUID.randomUUID().toString();
                }
                try {
                    MDC.put(mdcKey, traceId);
                    // chain 之前回显：controller 提前提交响应/抛异常的路径也带 trace_id
                    response.setHeader(headerName, traceId);
                    filterChain.doFilter(request, response);
                } finally {
                    MDC.remove(mdcKey);
                }
            }
        });
        // 最高优先级: 保证业务 Filter/Interceptor/Controller 的日志都带 traceId
        registration.setOrder(Integer.MIN_VALUE + 100);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * 入站 trace id sanitize（issue #25）：trim 后长度 16~64、字符白名单 {@code [A-Za-z0-9._-]}；
     * 不合法返回 null（由调用方重新生成）。
     */
    static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return SAFE_TRACE_ID.matcher(trimmed).matches() ? trimmed : null;
    }
}
