package fun.commons.framework4j.tracelog.unit;

import fun.commons.framework4j.tracelog.rate.PerTraceRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PerTraceRateLimiter 单 trace 限速")
class PerTraceRateLimiterTest {

    @Test
    @DisplayName("0 速率 → 不限制")
    void unlimited() {
        PerTraceRateLimiter limiter = new PerTraceRateLimiter(0);
        for (int i = 0; i < 1000; i++) {
            assertThat(limiter.tryAcquire("trace1")).isTrue();
        }
    }

    @Test
    @DisplayName("突发容量内全放行，超出后限速")
    void burstThenThrottle() throws InterruptedException {
        PerTraceRateLimiter limiter = new PerTraceRateLimiter(100); // 突发 200

        // 突发 200 条全过
        int allowed = 0;
        for (int i = 0; i < 300; i++) {
            if (limiter.tryAcquire("traceA")) allowed++;
        }
        // 应该接近 200（突发容量）
        assertThat(allowed).isBetween(190, 210);

        // 等待 1s 让令牌补充
        Thread.sleep(1100);
        int secondAllowed = 0;
        for (int i = 0; i < 200; i++) {
            if (limiter.tryAcquire("traceA")) secondAllowed++;
        }
        // 1 秒补充 ~100 个令牌
        assertThat(secondAllowed).isGreaterThan(50);
    }

    @Test
    @DisplayName("不同 trace 互不影响")
    void independentBuckets() {
        PerTraceRateLimiter limiter = new PerTraceRateLimiter(10);
        // trace1 用完
        for (int i = 0; i < 30; i++) limiter.tryAcquire("trace1");
        // trace2 仍能拿到令牌
        assertThat(limiter.tryAcquire("trace2")).isTrue();
    }

    @Test
    @DisplayName("并发安全")
    void concurrentSafe() throws InterruptedException {
        PerTraceRateLimiter limiter = new PerTraceRateLimiter(1000);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger allowed = new AtomicInteger();
        int threads = 8, perThread = 1000;
        CountDownLatch latch = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                for (int i = 0; i < perThread; i++) {
                    if (limiter.tryAcquire("traceX")) allowed.incrementAndGet();
                }
                latch.countDown();
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        // 突发容量 2000 令牌，不会无限制放行
        assertThat(allowed.get()).isLessThan(threads * perThread);
        assertThat(allowed.get()).isGreaterThan(1500); // 至少放行大部分突发
    }

    @Test
    @DisplayName("null traceId → 不限速")
    void nullTraceId() {
        PerTraceRateLimiter limiter = new PerTraceRateLimiter(10);
        for (int i = 0; i < 100; i++) {
            assertThat(limiter.tryAcquire(null)).isTrue();
        }
    }
}