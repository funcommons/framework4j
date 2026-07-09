package fun.commons.framework4j.id.util;

import fun.commons.framework4j.id.generator.SnowflakeDistributor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * ID 反解析工具
 * <p>
 * 从雪花算法生成的 ID 中提取信息:
 * <ul>
 *   <li>生成时间 (精确到毫秒)</li>
 *   <li>WorkerID</li>
 *   <li>DatacenterId</li>
 *   <li>序列号</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class IdAnalysisTool {

    private IdAnalysisTool() {
        // 工具类禁止实例化
    }

    /**
     * 从 ID 提取生成时间
     *
     * @param snowflakeId 雪花算法ID
     * @return 生成时间
     */
    public static LocalDateTime extractTime(long snowflakeId) {
        long timeDelta = snowflakeId >> 22;
        long timestamp = timeDelta + SnowflakeDistributor.HARDCODED_EPOCH;
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    /**
     * 从 ID 提取时间戳 (毫秒)
     *
     * @param snowflakeId 雪花算法ID
     * @return 毫秒时间戳
     */
    public static long extractTimestamp(long snowflakeId) {
        long timeDelta = snowflakeId >> 22;
        return timeDelta + SnowflakeDistributor.HARDCODED_EPOCH;
    }

    /**
     * 从 ID 提取 WorkerID (低5位)
     *
     * @param snowflakeId 雪花算法ID
     * @return WorkerID (0-31)
     */
    public static long extractWorkerId(long snowflakeId) {
        return (snowflakeId >> 12) & 0x1F; // 5 bits
    }

    /**
     * 从 ID 提取 DatacenterId (高5位)
     *
     * @param snowflakeId 雪花算法ID
     * @return DatacenterId (0-31)
     */
    public static long extractDatacenterId(long snowflakeId) {
        return (snowflakeId >> 17) & 0x1F; // 5 bits
    }

    /**
     * 从 ID 提取 SDK WorkerID (完整10位)
     *
     * @param snowflakeId 雪花算法ID
     * @return SDK WorkerID (0-1023)
     */
    public static long extractSdkWorkerId(long snowflakeId) {
        long workerId = extractWorkerId(snowflakeId);
        long datacenterId = extractDatacenterId(snowflakeId);
        return (datacenterId << 5) | workerId;
    }

    /**
     * 从 ID 提取序列号
     *
     * @param snowflakeId 雪花算法ID
     * @return 序列号 (0-4095)
     */
    public static long extractSequence(long snowflakeId) {
        return snowflakeId & 0xFFF; // 12 bits
    }

    /**
     * 解析 ID 的完整信息
     *
     * @param snowflakeId 雪花算法ID
     * @return ID信息对象
     */
    public static IdInfo parse(long snowflakeId) {
        return new IdInfo(
                snowflakeId,
                extractTimestamp(snowflakeId),
                extractTime(snowflakeId),
                extractSdkWorkerId(snowflakeId),
                extractWorkerId(snowflakeId),
                extractDatacenterId(snowflakeId),
                extractSequence(snowflakeId)
        );
    }

    /**
     * ID 信息封装类
     */
    public record IdInfo(
            long id,
            long timestamp,
            LocalDateTime dateTime,
            long sdkWorkerId,
            long workerId,
            long datacenterId,
            long sequence
    ) {
        @Override
        public String toString() {
            return String.format(
                    "IdInfo{id=%d, dateTime=%s, sdkWorkerId=%d (worker=%d, datacenter=%d), sequence=%d}",
                    id, dateTime, sdkWorkerId, workerId, datacenterId, sequence
            );
        }
    }
}
