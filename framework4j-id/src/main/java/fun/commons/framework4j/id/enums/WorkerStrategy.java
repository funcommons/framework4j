package fun.commons.framework4j.id.enums;

/**
 * WorkerID 分配策略
 *
 * @since 2.1.0
 */
public enum WorkerStrategy {

    /**
     * Redis 租约模式（推荐，适合 K8s 环境）
     */
    REDIS,

    /**
     * IP Hash 模式（无 Redis 时的降级方案）
     */
    IP;

    /**
     * 大小写不敏感解析，未知值抛 IllegalArgumentException（防止拼写错误静默走 Redis）
     */
    public static WorkerStrategy fromString(String value) {
        if (value == null || value.isBlank()) {
            return REDIS;
        }
        try {
            return WorkerStrategy.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown worker strategy: '" + value + "'. Valid values: REDIS, IP");
        }
    }
}
