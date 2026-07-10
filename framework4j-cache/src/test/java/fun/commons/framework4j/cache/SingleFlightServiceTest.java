package fun.commons.framework4j.cache;

import fun.commons.framework4j.cache.config.CacheProperties;
import fun.commons.framework4j.cache.service.SingleFlightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SingleFlightService 单元测试
 */
@DisplayName("SingleFlightService 单飞测试")
class SingleFlightServiceTest {

    private StringRedisTemplate redisTemplate;
    private SingleFlightService service;
    private CacheProperties properties;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        properties = new CacheProperties();
        properties.setKeyPrefix("cache");
        CacheProperties.SingleFlightConfig cfg = properties.getSingleFlight();
        cfg.setLockTtlSeconds(3);
        cfg.setWaitMillis(50);
        cfg.setMaxRetry(3);
        service = new SingleFlightService(redisTemplate, properties);
    }

    @Test
    @DisplayName("tryAcquireLeader：Lua 返回 1 → 加锁成功 + 返回 LeaderContext")
    void tryAcquireLeaderSuccess() {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(1L);
        SingleFlightService.LeaderContext ctx = service.tryAcquireLeader("test-key");
        assertThat(ctx).isNotNull();
        assertThat(ctx.token).isNotBlank();
        assertThat(ctx.future).isNotNull();
    }

    @Test
    @DisplayName("tryAcquireLeader：Lua 返回 0 → 加锁失败返回 null")
    void tryAcquireLeaderFailed() {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(0L);
        SingleFlightService.LeaderContext ctx = service.tryAcquireLeader("test-key");
        assertThat(ctx).isNull();
    }

    @Test
    @DisplayName("tryAcquireLeader：Redis 异常 → 返回 null（不拖垮业务）")
    void tryAcquireLeaderRedisError() {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("connection refused"));
        assertThat(service.tryAcquireLeader("key")).isNull();
    }

    @Test
    @DisplayName("tryAcquireLeader：Lua 返回 null → 加锁失败")
    void tryAcquireLeaderNullReturn() {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(null);
        assertThat(service.tryAcquireLeader("key")).isNull();
    }

    @Test
    @DisplayName("releaseLeader：正常释放（complete future + CAS remove）")
    void releaseLeaderNormal() {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(1L);
        SingleFlightService.LeaderContext ctx = service.tryAcquireLeader("release-key");
        assertThat(ctx).isNotNull();

        // release 不应抛异常
        assertThatCode(() -> service.releaseLeader("release-key", ctx)).doesNotThrowAnyException();
        // future 应 completed
        assertThat(ctx.future.isDone()).isTrue();
    }

    @Test
    @DisplayName("releaseLeader：Redis 异常 → 不抛（finally 仍 complete future）")
    void releaseLeaderRedisError() {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(1L)  // 第一次 LOCK 成功
                .thenThrow(new RuntimeException("unlock failed")); // UNLOCK 异常

        SingleFlightService.LeaderContext ctx = service.tryAcquireLeader("err-key");
        service.releaseLeader("err-key", ctx);
        // 即使 Redis 异常，future 仍应 completed
        assertThat(ctx.future.isDone()).isTrue();
    }

    @Test
    @DisplayName("releaseLeader：null ctx → 不抛异常")
    void releaseLeaderNullCtx() {
        assertThatCode(() -> service.releaseLeader("key", null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("waitForLeader：缓存已有值 → 立即返回（不等 future）")
    void waitForLeaderCacheHit() {
        String result = service.waitForLeader("hit-key", () -> "cached-value");
        assertThat(result).isEqualTo("cached-value");
    }

    @Test
    @DisplayName("waitForLeader：缓存为空 → 超时后返回 null")
    void waitForLeaderTimeout() {
        // 不注册 leader，follower 创建自己的 future → 超时
        java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);
        String result = service.waitForLeader("timeout-key", () -> {
            count.incrementAndGet();
            return null;
        });
        // 超时后调 cacheReadSupplier → 返回 null
        assertThat(result).isNull();
        assertThat(count.get()).isGreaterThan(0); // 至少调了一次
    }

    @Test
    @DisplayName("waitForLeader：被 leader complete 唤醒")
    void waitForLeaderWokenByLeader() throws Exception {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(1L);
        SingleFlightService.LeaderContext ctx = service.tryAcquireLeader("wake-key");
        assertThat(ctx).isNotNull();

        // 异步 release（模拟 leader 完成回填）
        new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            service.releaseLeader("wake-key", ctx);
        }).start();

        // follower 等待
        String result = service.waitForLeader("wake-key", () -> "leader-filled-value");
        assertThat(result).isEqualTo("leader-filled-value");
    }

    @Test
    @DisplayName("waitForLeader：中断异常 → 返回 null + 恢复中断标志")
    void waitForLeaderInterrupted() throws Exception {
        Thread.currentThread().interrupt();
        // supplier 返回 null → 进入 future.get → 抛 InterruptedException → 返回 null
        String result = service.waitForLeader("interrupt-key", () -> null);
        assertThat(result).isNull();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
}
