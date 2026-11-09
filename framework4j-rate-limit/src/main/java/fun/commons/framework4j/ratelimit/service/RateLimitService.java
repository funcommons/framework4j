package fun.commons.framework4j.ratelimit.service;

import fun.commons.framework4j.ratelimit.exception.RateLimitException;
import fun.commons.framework4j.ratelimit.lua.RateLimitLuaScripts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * 限流服务（sliding_window 默认实现）
 * <p>
 * 对齐 mc-api-spec §8.5 + Java开发准则 §3.1（Lua 原子化）。
 *
 * @since 2.1.0
 */
@Slf4j
public class RateLimitService {

    /**
     * tryAcquire 返回结果
     * <p>
     * v1.10.1 (issue #33-①)：新增 {@code fallback} 标志位——此前 fail-open 放行形
     * {@code (allowed=true, currentCount=0)} 与正常放行形在 API 契约上不可区分，
     * 调用方只能靠 {@code currentCount==0} 指纹判别（依赖 Lua 内部实现细节，非契约）。
     * <p>
     * API 契约：正常放行 {@code fallback=false} 且 {@code currentCount >= 1}（Lua 首次即 1）；
     * 降级放行（Redis 故障/Lua 返回异常）{@code fallback=true} 且 {@code currentCount=0}。
     */
    public record AcquireResult(boolean allowed, int currentCount, int limit, long resetAtMs, boolean fallback) {

        /** 1.9 及之前的四参构造（fallback=false 正常形），保持源码兼容 */
        public AcquireResult(boolean allowed, int currentCount, int limit, long resetAtMs) {
            this(allowed, currentCount, limit, resetAtMs, false);
        }

        /** 计算距重置的秒数（用于 Retry-After） */
        public long retryAfterSeconds() {
            long now = System.currentTimeMillis();
            long diff = resetAtMs - now;
            return diff > 0 ? (diff + 999) / 1000 : 1;  // 向上取整
        }
    }

    private final StringRedisTemplate redisTemplate;

    /** v2.1 P1: Redis 故障 fallback 计数（高 QPS 时 log.warn 会被淹没，用计数 + 摘要日志） */
    private final java.util.concurrent.atomic.AtomicLong fallbackCount = new java.util.concurrent.atomic.AtomicLong(0);
    private volatile long lastFallbackLogTime = System.currentTimeMillis();

    /**
     * v1.10.1 (issue #33-②)：按限流键的 fallback 计数——全局计数无法定位哪个业务键在降级
     * （注册限流/接口限流/导出限流共用一个 Service 时告警无法路由到业务 owner）。
     * 键数上限 {@value #MAX_KEYED_ENTRIES}（超限只计全局，防 Redis 长时故障 + 高基数 key 撑爆内存）。
     */
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong> fallbackCountByKey =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_KEYED_ENTRIES = 10_000;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** v2.1 P1: 暴露 fallback 计数（业务方可用于监控告警） */
    public long getFallbackCount() {
        return fallbackCount.get();
    }

    /** v1.10.1 (issue #33-②)：按限流键的 fallback 计数（Redis 正常或未触发过降级时为 0） */
    public long getFallbackCount(String key) {
        java.util.concurrent.atomic.AtomicLong c = fallbackCountByKey.get(key);
        return c == null ? 0 : c.get();
    }

    /** v1.10.1 (issue #33-②)：全部键化 fallback 计数快照（监控导出用） */
    public java.util.Map<String, Long> getFallbackCountsByKey() {
        java.util.Map<String, Long> snapshot = new java.util.LinkedHashMap<>();
        fallbackCountByKey.forEach((k, v) -> snapshot.put(k, v.get()));
        return snapshot;
    }

    /**
     * 尝试获取一个配额（sliding_window）
     *
     * @param key    限流 key（含 scope 维度）
     * @param limit  窗口内最大请求数
     * @param windowMs 窗口大小（毫秒）
     * @return AcquireResult
     */
    public AcquireResult tryAcquire(String key, int limit, long windowMs) {
        long now = System.currentTimeMillis();
        List<?> result;
        try {
            // v2.1 P0 修复（第5轮）：Redis Cluster 下 Lua 多 key 必须落同一 slot
            // 用 hash tag {rateKey} 包裹保证 KEYS[1] 和 KEYS[2] 同 slot
            String hashTag = "{" + key + "}";
            String rateKey = hashTag;
            String seqKey = hashTag + ":seq";
            result = redisTemplate.execute(
                    RateLimitLuaScripts.SLIDING_WINDOW,
                    List.of(rateKey, seqKey),
                    String.valueOf(windowMs),
                    String.valueOf(limit),
                    String.valueOf(now));
        } catch (Exception e) {
            recordFallback(key);
            log.warn("[RateLimit] Redis sliding_window failed (total fallbacks={}): {}",
                    fallbackCount.get(), e.getMessage());
            // 兜底：Redis 故障时放行（避免拖垮业务）
            // v2.1 P1 修复（第4轮）：currentCount=0 让 Remaining=limit（真实表达"未限流、配额满"）
            // v1.10.1 (issue #33-①)：fallback=true 显式标记降级放行，调用方不再靠 currentCount==0 指纹判别
            return new AcquireResult(true, 0, limit, now + windowMs, true);
        }

        if (result == null || result.size() < 4) {
            recordFallback(key);
            log.warn("[RateLimit] Lua returned unexpected result (total fallbacks={}): {}",
                    fallbackCount.get(), result);
            return new AcquireResult(true, 0, limit, now + windowMs, true);
        }

        long allowed = toLong(result.get(0));
        long current = toLong(result.get(1));
        long resetAt = toLong(result.get(2));
        long maxLimit = toLong(result.get(3));

        boolean allow = allowed == 1L;
        return new AcquireResult(allow, (int) current, (int) maxLimit, resetAt);
    }

    /**
     * v2.1 功能增强：固定窗口限流（Lua INCR + EXPIRE）
     *
     * @param key 限流 key
     * @param limit 窗口内最大请求数
     * @param windowSeconds 窗口大小（秒）
     */
    public AcquireResult tryAcquireFixedWindow(String key, int limit, long windowSeconds) {
        try {
            String hashTag = "{" + key + "}";
            List<?> result = redisTemplate.execute(
                    RateLimitLuaScripts.FIXED_WINDOW,
                    List.of(hashTag),
                    String.valueOf(limit),
                    String.valueOf(windowSeconds));
            if (result == null || result.size() < 3) {
                // v1.10.1 (issue #33)：补齐降级计数（此前此分支漏计）+ fallback 标志
                recordFallback(key);
                return new AcquireResult(true, 0, limit, System.currentTimeMillis() + windowSeconds * 1000, true);
            }
            long allowed = toLong(result.get(0));
            long current = toLong(result.get(1));
            long ttl = toLong(result.get(2));
            return new AcquireResult(allowed == 1L, (int) current, limit,
                    System.currentTimeMillis() + ttl * 1000);
        } catch (Exception e) {
            recordFallback(key);
            log.warn("[RateLimit] fixed_window failed: {}", e.getMessage());
            return new AcquireResult(true, 0, limit, System.currentTimeMillis() + windowSeconds * 1000, true);
        }
    }

    /**
     * 触发限流异常（业务调用方在 allow=false 时使用）
     */
    public RateLimitException buildException(AcquireResult r) {
        return new RateLimitException(r.retryAfterSeconds(), r.currentCount(), r.limit(), r.resetAtMs());
    }

    /**
     * v2.1 P1: fallback 摘要日志（CAS 节流，防多线程重复 ERROR）
     * <p>v1.10.1 (issue #33-②)：增加按键计数（键数超 {@value #MAX_KEYED_ENTRIES} 只计全局防撑爆）。
     */
    private void recordFallback(String key) {
        long count = fallbackCount.incrementAndGet();
        if (key != null && fallbackCountByKey.size() < MAX_KEYED_ENTRIES) {
            fallbackCountByKey.computeIfAbsent(key, k -> new java.util.concurrent.atomic.AtomicLong())
                    .incrementAndGet();
        }
        long now = System.currentTimeMillis();
        long last = lastFallbackLogTime;
        if (now - last > 30_000L) {
            // v2.1 P1: CAS 节流（多线程下只有一个线程能进入日志）
            if (UPDATER.compareAndSet(this, last, now)) {
                log.error("[RateLimit] Redis fallback summary: total={} in recent window", count);
            }
        }
    }

    private static final java.util.concurrent.atomic.AtomicLongFieldUpdater<RateLimitService> UPDATER =
            java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater(RateLimitService.class, "lastFallbackLogTime");

    /** Lua 返回的数字可能是 Long 或 String，统一转 long */
    private static long toLong(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
