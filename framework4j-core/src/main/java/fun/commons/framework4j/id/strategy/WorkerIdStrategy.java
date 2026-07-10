package fun.commons.framework4j.id.strategy;

/**
 * WorkerID 分配策略接口
 * <p>
 * 不同环境使用不同的 WorkerID 分配策略:
 * <ul>
 *   <li>{@link RedisWorkerIdStrategy} - Redis 租约模式 (推荐)</li>
 *   <li>{@link IpHashWorkerIdStrategy} - IP Hash 模式 (降级方案)</li>
 * </ul>
 *
 * @author LDX2T
 * @since 1.0.0
 */
public interface WorkerIdStrategy {

    /**
     * 获取 WorkerID
     * <p>
     * WorkerID 范围: 0-1023 (10 bits)
     *
     * @return WorkerID
     */
    long getWorkerId();
}
