package fun.commons.framework4j.tracelog.store;

/**
 * 容量管控 Lua 脚本（首次写入时执行）。
 * <p>
 * 通过 {@code ARGV} 接收所有阈值，零硬编码。
 *
 * <pre>
 * KEYS[1] = 当前 traceId 的 key (trace_log:{traceId})
 * KEYS[2] = 全局队列 key (trace_global_queue 或 trace_global_queue:{shard})
 * ARGV[1] = 全局最大容量 (来自 storage.global-max-traces)
 * ARGV[2] = 过期秒数   (来自 storage.trace-ttl-seconds)
 * </pre>
 *
 * <p>流程：
 * <ol>
 *   <li>EXPIRE 当前 traceId 的 TTL</li>
 *   <li>RPUSH 到全局队列</li>
 *   <li>LLEN 判断是否超阈值，超则 LPOP 队头 + DEL</li>
 * </ol>
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §3.2.3</a>
 */
public final class TraceLogLua {

    private TraceLogLua() {}

    public static final String CAPACITY_SCRIPT = """
            -- KEYS[1] = 当前 traceId 的 key (trace_log:{traceId})
            -- KEYS[2] = 全局队列 key
            -- ARGV[1] = 全局最大容量
            -- ARGV[2] = 过期秒数

            redis.call('EXPIRE', KEYS[1], ARGV[2])
            redis.call('RPUSH', KEYS[2], KEYS[1])

            local len = redis.call('LLEN', KEYS[2])
            if len > tonumber(ARGV[1]) then
                local oldest_key = redis.call('LPOP', KEYS[2])
                if oldest_key then
                    redis.call('DEL', oldest_key)
                end
            end
            return 1
            """;

    /**
     * 分布式首次写入标记（SETNX）。
     * <p>
     * 防止多节点同时首次写同一 traceId 时重复触发 Lua 脚本。
     */
    public static final String FIRST_TIME_MARKER_KEY_SUFFIX = ":meta";
}