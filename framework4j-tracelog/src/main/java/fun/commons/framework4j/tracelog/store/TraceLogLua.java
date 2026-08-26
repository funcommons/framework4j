package fun.commons.framework4j.tracelog.store;

/**
 * 容量管控 Lua 脚本（首次写入时执行）。
 * <p>
 * 通过 {@code ARGV} 接收所有阈值，零硬编码。
 *
 * <pre>
 * KEYS[1] = 当前 traceId 的 key (trace_log:{traceId})
 * KEYS[2] = 全局队列 key (trace_global_queue 或 trace_global_queue:{shard})
 * KEYS[3] = 分布式首次标记 key (trace_log:{traceId}:meta)
 * ARGV[1] = 全局最大容量 (来自 storage.global-max-traces)
 * ARGV[2] = 过期秒数   (来自 storage.trace-ttl-seconds)
 * </pre>
 *
 * <p>流程（v1.3.3：SETNX 判定移入脚本内，保证原子 + 单次 pipeline）：
 * <ol>
 *   <li>EXPIRE 当前 traceId 的 TTL（幂等，每次刷新）</li>
 *   <li>{@code SET meta 1 EX ttl NX} —— 仅当该 traceId 全局首次时成功</li>
 *   <li>首次才 RPUSH 到全局队列（多节点重复触发只入队一次）</li>
 *   <li>LLEN 超阈值则 LPOP 队头 + DEL（FIFO 淘汰最老链路）</li>
 * </ol>
 *
 * <p><b>为什么 SETNX 必须在脚本内</b>：若在 pipeline 里单独发 SETNX 再盲发本脚本，
 * SETNX 的返回值无从消费（pipeline 异步），脚本每次仍会 RPUSH —— 多节点场景下
 * 同一 traceId 会重复入队（v1.3.2 实测 bug，见 TraceLogStoreIntegrationTest#setnxFirstTime）。
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §3.2.3 / §3.2.4</a>
 */
public final class TraceLogLua {

    private TraceLogLua() {}

    public static final String CAPACITY_SCRIPT = """
            -- KEYS[1] = 当前 traceId 的 key (trace_log:{traceId})
            -- KEYS[2] = 全局队列 key
            -- KEYS[3] = 分布式首次标记 key (trace_log:{traceId}:meta)
            -- ARGV[1] = 全局最大容量
            -- ARGV[2] = 过期秒数

            redis.call('EXPIRE', KEYS[1], ARGV[2])

            local first = redis.call('SET', KEYS[3], '1', 'EX', tonumber(ARGV[2]), 'NX')
            if first then
                redis.call('RPUSH', KEYS[2], KEYS[1])

                local len = redis.call('LLEN', KEYS[2])
                if len > tonumber(ARGV[1]) then
                    local oldest_key = redis.call('LPOP', KEYS[2])
                    if oldest_key then
                        redis.call('DEL', oldest_key)
                    end
                end
            end
            return 1
            """;

    /**
     * 分布式首次写入标记（SETNX，在 {@link #CAPACITY_SCRIPT} 内部执行）。
     * <p>
     * 防止多节点同时首次写同一 traceId 时重复入队全局队列。
     */
    public static final String FIRST_TIME_MARKER_KEY_SUFFIX = ":meta";
}