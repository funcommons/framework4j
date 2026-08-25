package fun.commons.framework4j.tracelog.rate;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 单 traceId 写入速率限制（令牌桶）。
 * <p>
 * 防业务死循环 / 日志炸弹。每个 traceId 独立令牌桶，默认 {@code 200 条/秒}。
 *
 * <p>实现：每个 traceId 一个 {@link TokenBucket}，按需懒加载。
 * 使用 {@link ConcurrentHashMap} 保证线程安全；超过 {@code maxKeys} 时清理最早的条目。
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §3.4.6</a>
 */
public class PerTraceRateLimiter {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final int permitsPerSecond;
    private final int burstCapacity;
    private final int maxKeys;

    public PerTraceRateLimiter(int permitsPerSecond) {
        this.permitsPerSecond = permitsPerSecond;
        this.burstCapacity = permitsPerSecond * 2; // 突发容量 = 2x 速率
        this.maxKeys = 50_000;
    }

    /**
     * 尝试获取一个令牌。返回 false 时表示超限，调用方应丢弃日志并计数。
     */
    public boolean tryAcquire(String traceId) {
        if (permitsPerSecond <= 0) return true; // 未启用限流
        if (traceId == null) return true;

        TokenBucket bucket = buckets.computeIfAbsent(traceId, k -> new TokenBucket(burstCapacity));
        boolean ok = bucket.tryConsume();

        // 定期清理（O(1) 摊销）
        if (buckets.size() > maxKeys) {
            buckets.entrySet().removeIf(e -> e.getValue().isIdle());
        }
        return ok;
    }

    public int getPermitsPerSecond() {
        return permitsPerSecond;
    }

    /**
     * 令牌桶（基于纳秒级时间戳的简单实现）。
     */
    static final class TokenBucket {
        private final double capacity;
        private final double refillRatePerNano;
        private double tokens;
        private long lastRefillNano;
        private volatile long lastAccessNano;

        TokenBucket(int capacity) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.refillRatePerNano = capacity / 1_000_000_000.0 / 2.0; // 默认每秒补充 capacity/2
            this.lastRefillNano = System.nanoTime();
            this.lastAccessNano = lastRefillNano;
        }

        synchronized boolean tryConsume() {
            long now = System.nanoTime();
            lastAccessNano = now;
            // 补充令牌
            double elapsed = now - lastRefillNano;
            tokens = Math.min(capacity, tokens + elapsed * refillRatePerNano);
            lastRefillNano = now;

            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        boolean isIdle() {
            return System.nanoTime() - lastAccessNano > 60_000_000_000L; // 60 秒未访问
        }
    }
}