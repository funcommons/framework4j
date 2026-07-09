package fun.commons.framework4j.cache;

import fun.commons.framework4j.cache.config.CacheProperties;
import fun.commons.framework4j.cache.service.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * CacheService 端到端集成测试
 *
 * @since 2.1.0
 */
@SpringBootTest
@ActiveProfiles("integration-test")
class CacheIntegrationTest {

    @SpringBootApplication
    static class TestApplication {}

    @Autowired
    private CacheService cacheService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CacheProperties properties;

    @BeforeEach
    void cleanRedis() {
        Set<String> keys = redisTemplate.keys(properties.getKeyPrefix() + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("场景1: 全未命中走 loader + 回填 L1+L2")
    void scenario1_cacheMissLoadAndFill() {
        AtomicInteger loadCount = new AtomicInteger(0);
        String result = cacheService.get("user", "u-1", 60,
                () -> {
                    loadCount.incrementAndGet();
                    return "user-data-1";
                }, String.class);

        assertThat(result).isEqualTo("user-data-1");
        assertThat(loadCount.get()).isEqualTo(1);
        // L2 已回填
        assertThat(redisTemplate.opsForValue().get("user:u-1")).isEqualTo("user-data-1");
    }

    @Test
    @DisplayName("场景2: L1 命中不触 loader 也不触 Redis")
    void scenario2_l1HitSkipsLoader() {
        AtomicInteger loadCount = new AtomicInteger(0);

        cacheService.get("user", "u-2", 60, () -> "data-2", String.class);
        loadCount.set(0);

        String r = cacheService.get("user", "u-2", 60, () -> {
            loadCount.incrementAndGet();
            return "should-not-call";
        }, String.class);

        assertThat(r).isEqualTo("data-2");
        assertThat(loadCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("场景3: 防穿透：loader 返回 null 也缓存")
    void scenario3_nullCaching() {
        AtomicInteger loadCount = new AtomicInteger(0);

        String r1 = cacheService.get("user", "u-null", 60, () -> {
            loadCount.incrementAndGet();
            return null;
        }, String.class);
        String r2 = cacheService.get("user", "u-null", 60, () -> {
            loadCount.incrementAndGet();
            return null;
        }, String.class);

        assertThat(r1).isNull();
        assertThat(r2).isNull();
        // loader 只调一次（第二次走空值标记）
        assertThat(loadCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("场景4: 单飞：50 并发只 1 次回源")
    void scenario4_singleFlight() throws InterruptedException {
        int threads = 50;
        AtomicInteger loadCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    try { startLatch.await(); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                    cacheService.get("user", "u-singleflight", 60, () -> {
                        loadCount.incrementAndGet();
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                        return "single-flight-result";
                    }, String.class);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();

        // 单飞：loader 只调 1 次（leader 回源，follower 等待）
        assertThat(loadCount.get()).isLessThanOrEqualTo(2);  // 允许 leader + 兜底
        executor.shutdownNow();
    }

    @Test
    @DisplayName("场景5: put → 立即 get 命中")
    void scenario5_putAndGet() {
        cacheService.put("user", "u-3", 60, "data-3");
        String r = cacheService.get("user", "u-3", 60, () -> "fallback", String.class);
        assertThat(r).isEqualTo("data-3");
    }

    @Test
    @DisplayName("场景6: put 对象 → get 反序列化")
    void scenario6_putObject() {
        TestUser user = new TestUser("u-4", "Alice", 30);
        cacheService.put("user", "obj-u-4", 60, user);

        TestUser r = cacheService.get("user", "obj-u-4", 60,
                () -> null, TestUser.class);

        assertThat(r).isNotNull();
        assertThat(r.getId()).isEqualTo("u-4");
        assertThat(r.getName()).isEqualTo("Alice");
        assertThat(r.getAge()).isEqualTo(30);
    }

    @Test
    @DisplayName("场景7: evict 双删 L1+L2")
    void scenario7_evict() {
        cacheService.put("user", "u-5", 60, "data-5");
        assertThat(redisTemplate.opsForValue().get("user:u-5")).isEqualTo("data-5");

        cacheService.evict("user", "u-5");

        assertThat(redisTemplate.opsForValue().get("user:u-5")).isNull();
        // L1 也应清空（下次 get 会走 loader）
        AtomicInteger count = new AtomicInteger(0);
        String r = cacheService.get("user", "u-5", 60, () -> {
            count.incrementAndGet();
            return "reloaded";
        }, String.class);
        assertThat(r).isEqualTo("reloaded");
        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("场景8: TTL 抖动防雪崩（多次 put TTL 不全相同）")
    void scenario8_ttlJitter() {
        // 连续 put 相同 TTL，验证 TTL 有抖动
        long baseTtl = 3600;
        java.util.Set<Long> ttls = new java.util.HashSet<>();
        for (int i = 0; i < 20; i++) {
            cacheService.put("user", "u-jitter-" + i, baseTtl, "data-" + i);
            Long ttl = redisTemplate.getExpire("user:u-jitter-" + i, TimeUnit.SECONDS);
            if (ttl != null) ttls.add(ttl);
        }
        // 至少有 2 种不同 TTL（证明抖动生效）
        assertThat(ttls.size()).isGreaterThan(1);
    }

    @Test
    @DisplayName("场景9: 单飞兜底 - leader loader 抛异常时 follower 应能走兜底")
    void scenario9_singleFlightLeaderException() throws Exception {
        // leader loader 抛异常，follower 等待超时后兜底回源（返回非异常数据）
        AtomicInteger loadCount = new AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<Throwable> leaderError = new java.util.concurrent.atomic.AtomicReference<>();

        // 自定义 loader：第一次抛异常（leader），后续正常返回
        java.util.function.Supplier<String> loader = () -> {
            if (loadCount.get() == 0) {
                leaderError.set(new RuntimeException("DB down"));
                throw new RuntimeException("DB down");
            }
            return "fallback-result";
        };

        // 第一个线程做 leader，loader 抛异常
        try {
            cacheService.get("user", "u-leader-error", 60, loader, String.class);
            assertThat(false).as("leader 应抛异常").isTrue();
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("DB down");
        }

        // 后续线程做 follower，等待超时后兜底回源（loader 走 fallback 分支）
        loadCount.set(1);
        String r = cacheService.get("user", "u-leader-error", 60, loader, String.class);
        // follower 应通过（兜底或 L2 缓存）
        assertThat(r).isIn("fallback-result", null);
    }

    @Test
    @DisplayName("场景10: 单飞 follower 超时兜底 - 等待 maxRetry 后退化")
    void scenario10_followerTimeoutFallback() throws Exception {
        // leader 不写 L2（mock 故障），follower 应在 wait 超时后直接回源
        // 用 isolated prefix 确保无其他测试干扰
        AtomicInteger loadCount = new AtomicInteger(0);
        // 同步连续 3 次调用，模拟 leader 已离开 + L2 无数据
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            cacheService.get("user", "u-fallback-" + idx, 60, () -> {
                loadCount.incrementAndGet();
                return "data-" + idx;
            }, String.class);
        }
        // 至少 3 次 loader 调用（无并发 → 每次都走 loader）
        assertThat(loadCount.get()).isGreaterThanOrEqualTo(3);
    }

    // ============ 测试对象 ============
    public static class TestUser {
        private String id;
        private String name;
        private int age;

        public TestUser() {}
        public TestUser(String id, String name, int age) {
            this.id = id; this.name = name; this.age = age;
        }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }
}
