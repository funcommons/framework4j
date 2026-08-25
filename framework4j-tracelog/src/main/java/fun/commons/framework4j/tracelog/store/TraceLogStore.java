package fun.commons.framework4j.tracelog.store;

import fun.commons.framework4j.redis.manager.MultiRedisManager;
import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis 存储层（SETNX + Pipeline + Lua）。
 * <p>
 * 单批写入流程：
 * <ol>
 *   <li>RPUSH 日志到 {@code trace_log:{traceId}}</li>
 *   <li>LTRIM 限制单 trace 最大条数</li>
 *   <li>首次写入：SETNX {@code trace_log:{traceId}:meta}（分布式首次标记）</li>
 *   <li>SETNX 成功 → 执行 Lua（EXPIRE + RPUSH 到全局队列 + 容量裁剪）</li>
 * </ol>
 *
 * <p>Cluster 模式下 {@code trace_global_queue} 按 {@code global-queue-shards} 分片，
 * 写入时按 traceId hash 选 shard。
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §3.2 / §3.4.3</a>
 */
@Slf4j
public class TraceLogStore {

    private final StringRedisTemplate redis;
    private final TraceLogProperties props;
    private final DefaultRedisScript<Long> capacityScript;
    /** 租户解析器（启用多租户时 Redis Key 加前缀） */
    private final java.util.function.Function<String, String> tenantKeyPrefixer;

    public TraceLogStore(StringRedisTemplate redis, TraceLogProperties props) {
        this(redis, props, tenant -> null);
    }

    public TraceLogStore(StringRedisTemplate redis,
                         TraceLogProperties props,
                         java.util.function.Function<String, String> tenantKeyPrefixer) {
        this.redis = redis;
        this.props = props;
        this.tenantKeyPrefixer = tenantKeyPrefixer;
        this.capacityScript = new DefaultRedisScript<>();
        this.capacityScript.setScriptText(TraceLogLua.CAPACITY_SCRIPT);
        this.capacityScript.setResultType(Long.class);
    }

    /**
     * 批量刷写一批日志到 Redis（Pipeline + Lua）。
     *
     * @param batch 同一 worker 累积的批（按 traceId 分组后已聚合）
     * @return 成功条数（用于指标）
     * @throws RuntimeException Redis 调用异常时抛出，调用方负责降级到 {@link LocalFallbackWriter}
     */
    public int flushBatch(List<TraceLogStore.LogItem> batch) {
        if (batch == null || batch.isEmpty()) return 0;

        final TraceLogProperties.Storage storage = props.getStorage();
        final int globalShards = storage.getGlobalQueueShards();
        final long traceTtl = storage.getTraceTtlSeconds();
        final int singleMax = storage.getSingleTraceMaxLogs();
        final int globalMax = storage.getGlobalMaxTraces();
        final String keyPrefix = storage.getKeyPrefix();

        return redis.executePipelined((RedisCallback<Object>) connection -> {
            for (LogItem item : batch) {
                // 多租户隔离：Redis Key 加 <tenant>: 前缀
                String tenantPrefix = tenantKeyPrefixer.apply(item.traceId());
                String fullKeyPrefix = tenantPrefix == null ? keyPrefix : (tenantPrefix + ":" + keyPrefix);
                byte[] logKey = (fullKeyPrefix + ":" + item.traceId()).getBytes(StandardCharsets.UTF_8);

                // 1. RPUSH 日志到 List
                connection.listCommands().rPush(logKey, item.json().getBytes(StandardCharsets.UTF_8));

                // 2. LTRIM 限制单 trace 最大条数
                connection.listCommands().lTrim(logKey, -singleMax, -1);

                // 3. SETNX 分布式首次标记
                if (item.firstSeen()) {
                    byte[] metaKey = (fullKeyPrefix + ":" + item.traceId() + TraceLogLua.FIRST_TIME_MARKER_KEY_SUFFIX)
                            .getBytes(StandardCharsets.UTF_8);
                    connection.stringCommands().set(
                            metaKey,
                            "1".getBytes(StandardCharsets.UTF_8),
                            org.springframework.data.redis.core.types.Expiration.seconds(traceTtl),
                            org.springframework.data.redis.connection.RedisStringCommands.SetOption.SET_IF_ABSENT);

                    // 4. Lua: EXPIRE trace_log + RPUSH 到全局队列 + 容量裁剪
                    byte[] globalQueueKey = globalQueueKey(item.traceId(), globalShards, tenantPrefix)
                            .getBytes(StandardCharsets.UTF_8);
                    connection.scriptingCommands().eval(
                            TraceLogLua.CAPACITY_SCRIPT.getBytes(StandardCharsets.UTF_8),
                            ReturnType.INTEGER,
                            2,
                            logKey,
                            globalQueueKey,
                            String.valueOf(globalMax).getBytes(StandardCharsets.UTF_8),
                            String.valueOf(traceTtl).getBytes(StandardCharsets.UTF_8));
                }
            }
            return null;
        }).size();
    }

    /**
     * 读取单 trace 全量日志（控制台查询接口使用）。
     */
    public List<String> rangeTraceLogs(String traceId) {
        return rangeTraceLogs(traceId, 0, -1, null);
    }

    /**
     * 读取指定范围（导出接口使用，避免 OOM）。
     */
    public List<String> rangeTraceLogs(String traceId, long start, long end) {
        return rangeTraceLogs(traceId, start, end, null);
    }

    /**
     * 多租户版本：带 tenant 前缀读取。
     */
    public List<String> rangeTraceLogs(String traceId, String tenantPrefix) {
        return rangeTraceLogs(traceId, 0, -1, tenantPrefix);
    }

    public List<String> rangeTraceLogs(String traceId, long start, long end, String tenantPrefix) {
        String key = buildLogKey(traceId, tenantPrefix);
        List<String> logs = redis.opsForList().range(key, start, end);
        return logs == null ? List.of() : logs;
    }

    /**
     * 获取单 trace 的日志条数。
     */
    public Long traceSize(String traceId) {
        return traceSize(traceId, null);
    }

    public Long traceSize(String traceId, String tenantPrefix) {
        String key = buildLogKey(traceId, tenantPrefix);
        return redis.opsForList().size(key);
    }

    private String buildLogKey(String traceId, String tenantPrefix) {
        // 与写入侧 (AsyncRedisLogAppender) 一致: 先归一化为 32-hex,
        // 否则查询带横线的 UUID 拼出的 key 与写入 key 不匹配, 永远查不到
        String normalized = TraceIdNormalizer.normalize(traceId);
        String id = normalized != null ? normalized : traceId;
        String keyPrefix = props.getStorage().getKeyPrefix();
        String fullKeyPrefix = tenantPrefix == null ? keyPrefix : (tenantPrefix + ":" + keyPrefix);
        return fullKeyPrefix + ":" + id;
    }

    /**
     * 计算全局队列 key（Cluster 模式下按 traceId hash 分片）。
     */
    private String globalQueueKey(String traceId, int shards, String tenantPrefix) {
        String base = props.getStorage().getGlobalQueueKey();
        if (tenantPrefix != null) base = tenantPrefix + ":" + base;
        if (shards <= 1) return base;
        int shard = Math.abs(traceId.hashCode()) % shards;
        return base + ":" + shard;
    }

    /**
     * 单条日志条目（Worker → Store 传输对象）。
     * <p>
     * {@code tenantPrefix} 用于多租户隔离（null = 单租户）。
     */
    public record LogItem(String traceId, String json, boolean firstSeen, String tenantPrefix) {
        public LogItem(String traceId, String json, boolean firstSeen) {
            this(traceId, json, firstSeen, null);
        }
    }
}