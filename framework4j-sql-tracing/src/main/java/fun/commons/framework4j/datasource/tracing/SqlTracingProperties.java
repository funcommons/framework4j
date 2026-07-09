package fun.commons.framework4j.datasource.tracing;

import lombok.Data;

/**
 * SQL 追踪配置属性
 * <p>
 * 用于配置 SQL 追踪功能的开关和参数。
 *
 * @since 1.0.0
 */
@Data
public class SqlTracingProperties {

    /**
     * 追踪模式
     * <p>
     * 可选值:
     * - DISABLED: 不开启追踪
     * - WRITE_ONLY: 仅追踪写操作 (INSERT, UPDATE, DELETE)
     * - ALL: 追踪所有 SQL (SELECT, INSERT, UPDATE, DELETE 等) (默认)
     */
    private TracingMode mode = TracingMode.ALL;

    /**
     * 应用名称 (topic)
     * 如果不配置，将自动从 spring.application.name 获取
     */
    private String topic;

    /**
     * TraceId 提供者类型
     * <p>
     * v2.1: 此字段当前未生效（BPP 固定用 DefaultTraceIdProvider 从 MDC 读）。
     * 保留为 API 兼容字段，未来版本按此值用 @ConditionalOnProperty 装配不同 Provider。
     *
     * @deprecated v2.1 未生效，未来版本实现。当前固定用 MDC 模式。
     */
    @Deprecated(since = "2.1.0", forRemoval = false)
    private String provider = "auto";

    /**
     * SQL 追踪模式枚举
     */
    public enum TracingMode {
        /**
         * 不开启追踪
         */
        DISABLED,

        /**
         * 仅追踪写操作 (INSERT, UPDATE, DELETE)
         */
        WRITE_ONLY,

        /**
         * 追踪所有 SQL (SELECT, INSERT, UPDATE, DELETE 等)
         */
        ALL
    }

    /**
     * 判断是否启用追踪
     */
    public boolean isEnabled() {
        return mode != null && mode != TracingMode.DISABLED;
    }

    /**
     * 判断是否仅追踪写操作
     */
    public boolean isWriteOnly() {
        return mode == TracingMode.WRITE_ONLY;
    }

    /**
     * 判断是否追踪所有 SQL
     */
    public boolean isAll() {
        return mode == TracingMode.ALL;
    }
}
