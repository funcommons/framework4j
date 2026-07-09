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

    /** tryAcquire 返回结果 */
    public record AcquireResult(boolean allowed, int currentCount, int limit, long resetAtMs) {

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

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** v2.1 P1: 暴露 fallback 计数（业务方可用于监控告警） */
    public long getFallbackCount() {
        return fallbackCount.get();
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
            recordFallback();
            log.warn("[RateLimit] Redis sliding_window failed (total fallbacks={}): {}",
                    fallbackCount.get(), e.getMessage());
            // 兜底：Redis 故障时放行（避免拖垮业务）
            // v2.1 P1 修复（第4轮）：currentCount=0 让 Remaining=limit（真实表达"未限流、配额满"）
            return new AcquireResult(true, 0, limit, now + windowMs);
        }

        if (result == null || result.size() < 4) {
            recordFallback();
            log.warn("[RateLimit] Lua returned unexpected result (total fallbacks={}): {}",
                    fallbackCount.get(), result);
            return new AcquireResult(true, 0, limit, now + windowMs);
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
                return new AcquireResult(true, 0, limit, System.currentTimeMillis() + windowSeconds * 1000);
            }
            long allowed = toLong(result.get(0));
            long current = toLong(result.get(1));
            long ttl = toLong(result.get(2));
            return new AcquireResult(allowed == 1L, (int) current, limit,
                    System.currentTimeMillis() + ttl * 1000);
        } catch (Exception e) {
            recordFallback();
            log.warn("[RateLimit] fixed_window failed: {}", e.getMessage());
            return new AcquireResult(true, 0, limit, System.currentTimeMillis() + windowSeconds * 1000);
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
     */
    private void recordFallback() {
        long count = fallbackCount.incrementAndGet();
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
