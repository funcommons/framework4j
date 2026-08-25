package fun.commons.framework4j.tracelog.switcher;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

/**
 * 开关频控器：同一维度每分钟限制开启次数（默认 1 次）。
 * <p>
 * 使用 Caffeine 记录最近开启时间，过期后自动清除。
 */
public class SwitchRateLimiter {

    private final int permitsPerMinute;
    private final Cache<String, Long> lastOpenedCache;

    public SwitchRateLimiter(int permitsPerMinute) {
        this.permitsPerMinute = permitsPerMinute;
        this.lastOpenedCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(2))
                .maximumSize(50_000)
                .build();
    }

    /**
     * 尝试开启开关（同一维度 {@code permitsPerMinute} 次/分钟）。
     *
     * @return true=允许开启；false=超频
     */
    public synchronized boolean tryAcquire(String dimensionKey) {
        if (permitsPerMinute <= 0) return true;
        long now = System.currentTimeMillis();
        Long last = lastOpenedCache.getIfPresent(dimensionKey);
        if (last != null && (now - last) < 60_000L) {
            return false;
        }
        lastOpenedCache.put(dimensionKey, now);
        return true;
    }
}