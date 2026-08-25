package fun.commons.framework4j.tracelog.util;

import fun.commons.framework4j.tracelog.store.TraceIdNormalizer;
import fun.commons.framework4j.web.TraceContext;
import org.slf4j.MDC;

import java.util.Map;

/**
 * TraceId 多源解析器。
 * <p>
 * 优先级：{@link TraceContext#getTraceId()} → MDC(traceId) → null。
 * 最终统一调用 {@link TraceIdNormalizer#normalize(String)} 输出 32-hex。
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §2.3 / §3.1.5</a>
 */
public final class TraceIdResolver {

    private TraceIdResolver() {}

    /**
     * 从 Logback ILoggingEvent 的 MDC 中解析并标准化 traceId。
     */
    public static String fromEvent(Map<String, String> mdc) {
        if (mdc == null) return null;
        String raw = mdc.get("traceId");
        return TraceIdNormalizer.normalize(raw);
    }

    /**
     * 从当前线程上下文解析并标准化 traceId（用于 Interceptor）。
     */
    public static String current() {
        String raw = TraceContext.getTraceId();
        if (raw != null) return TraceIdNormalizer.normalize(raw);
        return TraceIdNormalizer.normalize(MDC.get("traceId"));
    }
}