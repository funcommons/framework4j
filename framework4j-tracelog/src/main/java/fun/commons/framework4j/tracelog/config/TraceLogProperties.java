package fun.commons.framework4j.tracelog.config;

import ch.qos.logback.classic.Level;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

/**
 * 动态追踪日志 SDK 配置属性。
 * <p>
 * 配置前缀 {@code framework4j.tracelog.*}，所有阈值均可配置，零硬编码。
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §四 配置参考</a>
 */
@Data
@ConfigurationProperties(prefix = "framework4j.tracelog")
public class TraceLogProperties {

    // ==================== 开关与依赖 ====================

    /** 全局开关（opt-in，默认关闭） */
    private boolean enabled = false;

    /** framework4j-redis 数据源名 */
    private String redisName = "default";

    // ==================== 存储与容量 ====================

    @NestedConfigurationProperty
    private Storage storage = new Storage();

    @Data
    public static class Storage {
        /** Redis Key 前缀（完整格式 {prefix}:{traceId}） */
        private String keyPrefix = "trace_log";

        /** 全局队列 Key（Cluster 下分片为 {prefix}:{shard}） */
        private String globalQueueKey = "trace_global_queue";

        /** Cluster 模式下分片数（>1 时启用 hashtag 分片） */
        private int globalQueueShards = 1;

        /** 全局最大追踪数（Lua 容量阈值） */
        private int globalMaxTraces = 100_000;

        /** 单 trace 过期时间（默认 24h） */
        private long traceTtlSeconds = 86_400L;

        /** 单 trace 最大日志条数（LTRIM 阈值） */
        private int singleTraceMaxLogs = 5_000;
    }

    // ==================== 异步采集 ====================

    @NestedConfigurationProperty
    private Collection collection = new Collection();

    @Data
    public static class Collection {
        /** Worker 线程数（Disruptor 实例数），0 表示自动取 availableProcessors() */
        private int workerCount = 0;

        /** 每个 RingBuffer 容量（必须 2 的幂） */
        private int disruptorBufferSize = 65_536;

        /** 单批刷盘大小（达到即 flush） */
        private int flushBatchSize = 500;

        /** 单批最大等待时长（ms） */
        private long flushIntervalMs = 500L;

        /** 单 traceId 写入速率限制（条/秒），0 表示不限制 */
        private int rateLimitPerTracePerSecond = 200;

        /** 本地 traceId 防抖缓存容量（SETNX 失败时 fallback） */
        private int dedupCacheSize = 50_000;

        /** 本地 traceId 防抖 TTL（秒） */
        private long dedupCacheTtlSeconds = 120L;

        /** Graceful shutdown drain 超时（秒） */
        private long shutdownDrainTimeoutSeconds = 10L;

        /** Redis 故障时本地降级目录（容器化需 hostPath/PVC） */
        private String fallbackDir = "/var/log/framework4j-tracelog/fallback";

        /** Redis 恢复后回灌探测周期（秒） */
        private long fallbackReplayIntervalSeconds = 30L;

        /** 是否对采集日志做敏感字段脱敏（按 key 匹配，值替换为 ******） */
        private boolean maskSensitive = true;

        /** 脱敏 key 列表（不区分大小写；匹配 JSON 字段名与 message 中 key=value 形式） */
        private List<String> maskKeys = List.of(
                "password", "passwd", "pwd", "token", "access_token", "refresh_token",
                "authorization", "secret", "api_key", "apikey", "cookie", "set-cookie");
    }

    // ==================== 开关同步 ====================

    @NestedConfigurationProperty
    private Switch sync = new Switch();

    @Data
    public static class Switch {
        /** Pub/Sub 频道名 */
        private String channel = "channel:log_switch";

        /** 传输方式：pubsub（默认）或 streams */
        private String transport = "pubsub";

        /** 开关最长有效期（秒，上限 3600） */
        private long maxTtlSeconds = 3600L;

        /** Pub/Sub 断连重拉周期（秒，默认 5s） */
        private long resyncIntervalSeconds = 5L;

        /** 本地 Caffeine 规则缓存容量 */
        private int ruleCacheSize = 100_000;
    }

    // ==================== 提权 ====================

    @NestedConfigurationProperty
    private Elevation elevation = new Elevation();

    @Data
    public static class Elevation {
        /** 仅提权这些包路径，第三方库保持原级别 */
        private List<String> allowedPackages = List.of("com.yourcompany");

        /** 提权默认目标级别 */
        private Level defaultLevel = Level.DEBUG;
    }

    // ==================== API ====================

    @NestedConfigurationProperty
    private Api api = new Api();

    @Data
    public static class Api {
        /** 是否强制鉴权（生产建议开启） */
        private boolean requireAuth = true;

        /** TraceLogAuthValidator Bean 名（未配置启动 fail-fast） */
        private String authValidatorBean;

        /** 查询 API 路径 */
        private List<String> queryPathPatterns = List.of("/api/logs/trace/**");

        /** 控制 API 路径 */
        private List<String> switchPathPatterns = List.of("/api/logs/switch");

        /** 导出 API 路径 */
        private List<String> exportPathPatterns = List.of("/api/logs/trace/*/export");

        /** IP 白名单（非空则仅白名单可访问） */
        private List<String> ipWhitelist = List.of();

        /** IP 黑名单（永久拒绝） */
        private List<String> ipBlacklist = List.of();

        /** 查询接口单次返回上限 */
        private int maxReturnLogs = 1000;

        /** 同一维度每分钟开关次数 */
        private int switchRateLimitPerMinute = 1;
    }

    // ==================== 多租户 ====================

    @NestedConfigurationProperty
    private Tenant tenant = new Tenant();

    @Data
    public static class Tenant {
        /** 是否启用多租户隔离 */
        private boolean enabled = false;

        /** 租户 Key 取值 SpEL（如 #userInfo.tenantId） */
        private String keySpel;

        /** 租户 Header 名 */
        private String headerName = "X-Tenant-Id";
    }

    // ==================== 导出 ====================

    @NestedConfigurationProperty
    private Export export = new Export();

    @Data
    public static class Export {
        /** 是否启用导出接口 */
        private boolean enabled = true;

        /** 是否 gzip 压缩 */
        private boolean compress = true;

        /** 单 trace 最大导出大小（MB） */
        private int maxSizeMb = 10;

        /** 单用户每分钟导出次数 */
        private int rateLimitPerMinute = 5;
    }

    // ==================== 控制台 ====================

    @NestedConfigurationProperty
    private Console console = new Console();

    @Data
    public static class Console {
        /** 是否暴露前端页面 */
        private boolean enabled = true;

        /** 前端页面访问路径 */
        private String path = "/tracelog.html";

        /** 页面标题 */
        private String title = "Trace Log Console";
    }
}