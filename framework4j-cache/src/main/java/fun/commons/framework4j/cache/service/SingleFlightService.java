package fun.commons.framework4j.cache.service;

import fun.commons.framework4j.cache.config.CacheProperties;
import fun.commons.framework4j.cache.lua.CacheLuaScripts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * 单飞服务（分布式锁防击穿）
 * <p>
 * v2.1 P0 修复（第 7 轮）：leader 与 follower 共用同一 per-key future。
 * <ul>
 *   <li>leader 用 {@code computeIfAbsent} 拿 future（如果 follower 先建了就复用），
 *       leader 完成后 {@code complete()} + CAS 删除</li>
 *   <li>follower 用 {@code computeIfAbsent} 拿同一 future，
 *       被 leader {@code complete} 唤醒（不再永远超时）</li>
 *   <li>releaseLeader 只 complete 自己拿到的 future 引用（不误删他人的）</li>
 * </ul>
 *
 * @since 2.1.0
 */
@Slf4j
public class SingleFlightService {

    private final StringRedisTemplate redisTemplate;
    private final CacheProperties properties;

    /** per-key 共享 future（leader 或 follower 首个进来的人创建，后续复用） */
    private final ConcurrentHashMap<String, CompletableFuture<Void>> inFlight = new ConcurrentHashMap<>();

    public SingleFlightService(StringRedisTemplate redisTemplate, CacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 尝试成为 leader（获取分布式锁）。
     * <p>同时获取 per-key future（如果 follower 先建了就复用）。
     *
     * @param key 业务缓存 key
     * @return LeaderContext（token + future）；加锁失败返回 null
     */
    public LeaderContext tryAcquireLeader(String key) {
        String lockKey = properties.getKeyPrefix() + ":lock:" + key;
        String token = UUID.randomUUID().toString();
        CacheProperties.SingleFlightConfig cfg = properties.getSingleFlight();
        try {
            Long result = redisTemplate.execute(
                    CacheLuaScripts.LOCK,
                    List.of(lockKey),
                    token,
                    String.valueOf(cfg.getLockTtlSeconds()));
            if (result != null && result == 1L) {
                // v2.1 P0: computeIfAbsent 统一拿 future（follower 先建了就复用）
                CompletableFuture<Void> future = inFlight.computeIfAbsent(key, k -> new CompletableFuture<>());
                return new LeaderContext(token, future);
            }
            return null;
        } catch (Exception e) {
            log.warn("[Cache] SingleFlight lock failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 释放锁 + 唤醒等待该 key 的所有 follower（仅 complete 自己拿到的 future）
     */
    public void releaseLeader(String key, LeaderContext ctx) {
        if (ctx == null || ctx.token == null) return;
        String lockKey = properties.getKeyPrefix() + ":lock:" + key;
        try {
            redisTemplate.execute(CacheLuaScripts.UNLOCK, List.of(lockKey), ctx.token);
        } catch (Exception e) {
            log.warn("[Cache] SingleFlight unlock failed: {}", e.getMessage());
        } finally {
            // v2.1 P0: 只 complete 自己拿到的 future，CAS 删自己的
            if (ctx.future != null && !ctx.future.isDone()) {
                ctx.future.complete(null);
            }
            inFlight.remove(key, ctx.future);
        }
    }

    /**
     * 等待 leader 回填（复用 leader 注册的同一 future，被 complete 后立即唤醒）
     *
     * @param key 与 leader 对应的同一个 key
     * @param cacheReadSupplier 读取缓存（leader 已回填后返回非 null）
     */
    public <T> T waitForLeader(String key, java.util.function.Supplier<T> cacheReadSupplier) {
        // 先尝试直接读（leader 可能已完成）
        T value = cacheReadSupplier.get();
        if (value != null) return value;

        // v2.1 P0: computeIfAbsent 拿 per-key future（leader 先注册则复用，否则自建）
        CompletableFuture<Void> future = inFlight.computeIfAbsent(key, k -> new CompletableFuture<>());

        CacheProperties.SingleFlightConfig cfg = properties.getSingleFlight();
        long totalTimeoutMs = cfg.getWaitMillis() * cfg.getMaxRetry();
        try {
            future.get(totalTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            // 超时正常
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException ee) {
            log.debug("[Cache] waitForLeader leader failed: {}", ee.getMessage());
        }

        // CAS 删自己的 future（防误删新 leader 的）
        inFlight.remove(key, future);
        return cacheReadSupplier.get();
    }

    /** Leader 上下文（token + future 引用，用于精准 release） */
    public static class LeaderContext {
        public final String token;
        public final CompletableFuture<Void> future;

        public LeaderContext(String token, CompletableFuture<Void> future) {
            this.token = token;
            this.future = future;
        }
    }
}
