package fun.commons.framework4j.datasource.tracing;

import org.slf4j.MDC;

/**
 * 默认 TraceId 提供者
 * <p>
 * 优先从 MDC (Mapped Diagnostic Context) 获取 TraceId。
 * 支持常见的 traceId 键名: traceId, trace_id, X-B3-TraceId, traceparent 等。
 * <p>
 * 如果没有配置链路追踪系统，也可以通过手动在 MDC 中设置 traceId 来使用。
 *
 * @since 1.0.0
 */
public class DefaultTraceIdProvider implements TraceIdProvider {

    /**
     * 常见的 TraceId MDC 键名
     */
    private static final String[] TRACE_ID_KEYS = {
            "traceId",
            "trace_id",
            "X-B3-TraceId",
            "X-Request-Id",
            "requestId",
            "request_id"
    };

    @Override
    public String getTraceId() {
        // 优先尝试常见的 traceId 键名
        for (String key : TRACE_ID_KEYS) {
            String traceId = MDC.get(key);
            if (traceId != null && !traceId.isBlank()) {
                return traceId;
            }
        }
        return null;
    }
}
