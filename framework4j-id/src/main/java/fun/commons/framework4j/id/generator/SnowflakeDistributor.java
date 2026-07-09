package fun.commons.framework4j.id.generator;

import cn.hutool.core.lang.Snowflake;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 雪花算法核心分发器
 * <p>
 * 基于 Hutool Snowflake 实现，提供高性能的分布式唯一 ID 生成
 * <p>
 * ID 结构 (64-bit):
 * <pre>
 * +------+----------------------+------------+------------+--------------+
 * | 1bit |       41bits         |   5bits    |   5bits    |    12bits    |
 * | sign |     timestamp        | datacenter |   worker   |   sequence   |
 * +------+----------------------+------------+------------+--------------+
 * </pre>
 *
 * @since 1.0.0
 */
@Slf4j
public class SnowflakeDistributor {

    /**
     * 起始时间戳: 2024-01-01 00:00:00 UTC+8
     */
    public static final long HARDCODED_EPOCH = 1704067200000L;

    /**
     * Hutool Snowflake 实例
     */
    private final Snowflake hutoolSnowflake;

    /**
     * SDK 分配的 WorkerID (0-1023)
     */
    private final long sdkWorkerId;

    /**
     * 构造函数
     *
     * @param sdkWorkerId 范围 0 - 1023 (由策略分配)
     */
    public SnowflakeDistributor(long sdkWorkerId) {
        // 校验范围
        long maxSdkWorkerId = 1023; // 2^10 - 1
        if (sdkWorkerId > maxSdkWorkerId || sdkWorkerId < 0) {
            throw new IllegalArgumentException(
                    String.format("WorkerID must be between 0 and %d, got: %d", maxSdkWorkerId, sdkWorkerId));
        }
        this.sdkWorkerId = sdkWorkerId;

        // Hutool 配置: workerId (5 bit) + datacenterId (5 bit) = 10 bit
        // 将 sdkWorkerId (10 bit) 拆解填入
        long workerId = sdkWorkerId & 31;      // 低 5 位 (0-31)
        long datacenterId = sdkWorkerId >> 5;  // 高 5 位 (0-31)

        // 初始化 Hutool Snowflake
        this.hutoolSnowflake = new Snowflake(new Date(HARDCODED_EPOCH), workerId, datacenterId, false);

        log.info("[ID-SDK] Snowflake initialized. SDK_WorkerId: {} -> (workerId:{}, datacenterId:{}), Epoch: {}",
                sdkWorkerId, workerId, datacenterId, HARDCODED_EPOCH);
    }

    /**
     * 生成下一个唯一 ID
     *
     * @return 64位唯一ID
     */
    public long nextId() {
        return hutoolSnowflake.nextId();
    }

    /**
     * 批量生成唯一 ID
     *
     * @param count 数量
     * @return ID列表
     */
    public List<Long> nextIds(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be positive");
        }
        List<Long> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(nextId());
        }
        return ids;
    }

    /**
     * 获取 SDK WorkerID
     */
    public long getSdkWorkerId() {
        return sdkWorkerId;
    }

    /**
     * 获取 Hutool WorkerID (低5位)
     */
    public long getHutoolWorkerId() {
        return sdkWorkerId & 31;
    }

    /**
     * 获取 Hutool DatacenterId (高5位)
     */
    public long getHutoolDatacenterId() {
        return sdkWorkerId >> 5;
    }
}
