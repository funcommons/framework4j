package fun.commons.framework4j.api;

import io.micrometer.tracing.Tracer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Micrometer Tracer 上下文工具
 * <p>
 * 用于在非 Spring 管理的对象（如 ApiResponse）中获取 Trace ID
 *
 * @author LDX2T
 * @since 1.0.0
 */
@Component
public class TraceContext implements ApplicationContextAware {

    private static Tracer tracer;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        // 尝试注入 Micrometer 的 Tracer Bean
        try {
            TraceContext.tracer = applicationContext.getBean(Tracer.class);
        } catch (Exception e) {
            // Tracer Bean 不存在时忽略
            TraceContext.tracer = null;
        }
    }

    /**
     * 获取当前的 Trace ID
     *
     * @return traceId 或 null
     */
    public static String getTraceId() {
        if (tracer == null || tracer.currentSpan() == null) {
            return null;
        }
        return tracer.currentSpan().context().traceId();
    }

    /**
     * 检查 Tracer 是否可用
     *
     * @return true 如果 Tracer 已配置
     */
    public static boolean isTracerAvailable() {
        return tracer != null;
    }
}
