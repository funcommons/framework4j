package fun.commons.framework4j.web;

import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;

/**
 * Micrometer Tracer 上下文工具
 * <p>
 * 用于在非 Spring 管理的对象（如 {@link ApiResponse} 静态工厂）中获取 Trace ID。
 *
 * <p>v2.0 重构：去除 @Component + ApplicationContextAware 反模式，改为由 {@link TraceConfig}
 * 显式调用 {@link #setTracer(Tracer)} 注入。
 *
 * <p>v2.1 优化：优先从 MDC 读 traceId（Micrometer 自动写入，比 Tracer.currentSpan() 快 3-5 倍）。
 * 网关透传场景可手动 {@link #setTraceId(String)} 覆盖。
 *
 * @since 1.0.0
 */
public final class TraceContext {

    /** MDC 中 traceId 的标准键名（Micrometer 默认） */
    private static final String MDC_TRACE_ID_KEY = "traceId";

    private static volatile Tracer tracer;

    private TraceContext() {}

    /**
     * 由 {@link TraceConfig} 注入 Tracer 实例。
     */
    public static void setTracer(Tracer t) {
        tracer = t;
    }

    /**
     * 手动覆盖 traceId（网关透传场景）。
     * <p>写入 MDC，后续 {@link #getTraceId()} 优先读到。
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(MDC_TRACE_ID_KEY, traceId);
        } else {
            MDC.remove(MDC_TRACE_ID_KEY);
        }
    }

    /**
     * 清除 Tracer 引用 + MDC（测试场景用，避免上下文串扰）
     */
    public static void clear() {
        tracer = null;
        MDC.remove(MDC_TRACE_ID_KEY);
    }

    /**
     * 获取当前的 Trace ID
     * <p>优先级：MDC（最快，Micrometer 自动写入）→ Tracer.currentSpan() → null
     *
     * @return traceId 或 null
     */
    public static String getTraceId() {
        // 1. MDC 优先（ThreadLocal 直读，~10ns）
        String mdcTraceId = MDC.get(MDC_TRACE_ID_KEY);
        if (mdcTraceId != null && !mdcTraceId.isBlank()) {
            return mdcTraceId;
        }
        // 2. Tracer 兜底（创建 Span 上下文对象，~100ns）
        Tracer t = tracer;
        if (t == null || t.currentSpan() == null) {
            return null;
        }
        return t.currentSpan().context().traceId();
    }

    /**
     * 检查 Tracer 是否可用
     */
    public static boolean isTracerAvailable() {
        return tracer != null;
    }
}
