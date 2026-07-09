package fun.commons.framework4j.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多级缓存配置
 *
 * @since 2.1.0
 */
@Data
@ConfigurationProperties(prefix = "framework4j.cache")
public class CacheProperties {

    private boolean enabled = true;

    private String redisName = "default";

    /** 默认 TTL（秒） */
    private long defaultTtlSeconds = 3600;

    /** 空值缓存 TTL（秒），防穿透 */
    private long nullTtlSeconds = 30;

    /** Key 前缀 */
    private String keyPrefix = "cache";

    private L1Config l1 = new L1Config();

    private SingleFlightConfig singleFlight = new SingleFlightConfig();

    @Data
    public static class L1Config {
        private boolean enabled = true;
        /** 每 prefix 最多缓存条目数 */
        private int maxSize = 10000;
        /** Caffeine 写后过期（秒） */
        private long expireAfterWrite = 600;
    }

    @Data
    public static class SingleFlightConfig {
        private boolean enabled = true;
        /** 锁 TTL（秒） */
        private long lockTtlSeconds = 3;
        /** 等待重试间隔（毫秒） */
        private long waitMillis = 200;
        /** 最大重试次数 */
        private int maxRetry = 10;
    }
}
