package fun.commons.framework4j.datasource.health;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 数据源健康检查结果
 *
 * @since 1.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HealthCheckResult {

    /**
     * 数据源名称
     */
    private String datasourceName;

    /**
     * 是否健康
     */
    private boolean healthy;

    /**
     * 响应时间（毫秒）
     */
    private long responseTime;

    /**
     * 错误信息（如果不健康）
     */
    private String errorMessage;

    /**
     * 最后检查时间
     */
    private LocalDateTime lastCheckTime;

    /**
     * 数据源类型
     */
    private String datasourceType;

    /**
     * 连接池状态
     */
    private PoolStatus poolStatus;

    /**
     * 创建健康结果（成功）
     */
    public static HealthCheckResult healthy(String datasourceName, long responseTime, String datasourceType, PoolStatus poolStatus) {
        return new HealthCheckResult(
                datasourceName,
                true,
                responseTime,
                null,
                LocalDateTime.now(),
                datasourceType,
                poolStatus
        );
    }

    /**
     * 创建健康结果（失败）
     */
    public static HealthCheckResult unhealthy(String datasourceName, long responseTime, String errorMessage, String datasourceType) {
        return new HealthCheckResult(
                datasourceName,
                false,
                responseTime,
                errorMessage,
                LocalDateTime.now(),
                datasourceType,
                null
        );
    }

    /**
     * 获取健康状态描述
     */
    public String getStatusDescription() {
        return healthy ? "健康" : "异常";
    }

    /**
     * 连接池状态枚举
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PoolStatus {
        /**
         * 活跃连接数
         */
        private int activeConnections;

        /**
         * 空闲连接数
         */
        private int idleConnections;

        /**
         * 总连接数
         */
        private int totalConnections;

        /**
         * 最大连接数
         */
        private int maxConnections;

        /**
         * 连接池使用率
         */
        private double utilizationRate;

        /**
         * 计算使用率
         */
        public double calculateUtilizationRate() {
            if (maxConnections <= 0) return 0.0;
            return (double) activeConnections / maxConnections * 100;
        }
    }
}