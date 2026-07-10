package fun.commons.framework4j.cache;

import fun.commons.framework4j.cache.config.CacheProperties;
import fun.commons.framework4j.cache.service.CacheService;
import fun.commons.framework4j.cache.service.SingleFlightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CacheService 单元测试（mock Redis）
 * <p>
 * 覆盖 L1 命中、L2 命中、全未命中、防穿透空值、evict、put、warmup 等
 */
@DisplayName("CacheService 单元测试（mock Redis）")
class CacheServiceUnitTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private CacheService cacheService;
    private CacheProperties properties;
    private SingleFlightService singleFlightService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        properties = new CacheProperties();
        properties.setKeyPrefix("test");
        properties.setDefaultTtlSeconds(60);
        properties.setNullTtlSeconds(10);
        // 关闭单飞（单元测试不走真实 Redis 锁）
        properties.getSingleFlight().setEnabled(false);

        singleFlightService = mock(SingleFlightService.class);
        cacheService = new CacheService(redisTemplate, properties, singleFlightService);
    }

    @Test
    @DisplayName("L1 命中：不触 Redis、不触 loader")
    void l1HitSkipsRedisAndLoader() {
        AtomicInteger loadCount = new AtomicInteger(0);

        // 第一次：全未命中 → 走 loader → 回填 L1 + L2
        when(valueOps.get("user:u-1")).thenReturn(null);
        cacheService.get("user", "u-1", 60, () -> { loadCount.incrementAndGet(); return "v1"; }, String.class);

        // 第二次：L1 命中 → 不触 Redis GET、不触 loader
        loadCount.set(0);
        String result = cacheService.get("user", "u-1", 60,
                () -> { loadCount.incrementAndGet(); return "should-not-load"; }, String.class);

        assertThat(result).isEqualTo("v1");
        assertThat(loadCount.get()).isZero();
    }

    @Test
    @DisplayName("L2 命中：回填 L1、不触 loader")
    void l2HitFillsL1() {
        AtomicInteger loadCount = new AtomicInteger(0);

        // L1 空 + L2 有值
        when(valueOps.get("user:u-2")).thenReturn("l2-value");

        String result = cacheService.get("user", "u-2", 60,
                () -> { loadCount.incrementAndGet(); return "should-not-load"; }, String.class);

        assertThat(result).isEqualTo("l2-value");
        assertThat(loadCount.get()).isZero();
    }

    @Test
    @DisplayName("全未命中 + loader 返回非 null → 走 loader + 回填 L1+L2")
    void allMissLoadAndFill() {
        AtomicInteger loadCount = new AtomicInteger(0);
        when(valueOps.get("user:u-3")).thenReturn(null);

        String result = cacheService.get("user", "u-3", 60,
                () -> { loadCount.incrementAndGet(); return "loaded"; }, String.class);

        assertThat(result).isEqualTo("loaded");
        assertThat(loadCount.get()).isEqualTo(1);
        // 验证 L2 被写入（set 调用）
        verify(valueOps).set(eq("user:u-3"), eq("loaded"), any(Duration.class));
    }

    @Test
    @DisplayName("防穿透：loader 返回 null → 缓存空值标记 + 短 TTL")
    void nullValueCached() {
        when(valueOps.get("user:u-null")).thenReturn(null);

        String result = cacheService.get("user", "u-null", 60, () -> null, String.class);

        assertThat(result).isNull();
        // 验证空值标记写入 L2（短 TTL = nullTtlSeconds）
        verify(valueOps).set(eq("user:u-null"), eq("__NULL__"), any(Duration.class));
    }

    @Test
    @DisplayName("防穿透二次读：空值标记在 L1 → 直接返回 null（不触 loader）")
    void nullMarkerSecondReadSkipsLoader() {
        AtomicInteger loadCount = new AtomicInteger(0);

        // 第一次：全未命中 → loader 返 null → 缓存空值标记
        when(valueOps.get("user:u-null2")).thenReturn(null);
        cacheService.get("user", "u-null2", 60, () -> { loadCount.incrementAndGet(); return null; }, String.class);

        // 第二次：L1 有空值标记 → 返 null（不触 loader）
        loadCount.set(0);
        String result = cacheService.get("user", "u-null2", 60,
                () -> { loadCount.incrementAndGet(); return "should-not-load"; }, String.class);

        assertThat(result).isNull();
        assertThat(loadCount.get()).isZero();
    }

    @Test
    @DisplayName("put：写入 L1 + L2（含 TTL 抖动）")
    void putWritesL1AndL2() {
        cacheService.put("user", "u-put", 60, "put-value");

        // 验证 L2 set 被调用
        verify(valueOps).set(eq("user:u-put"), eq("put-value"), any(Duration.class));
    }

    @Test
    @DisplayName("put：null 值也写入（空值标记）")
    void putNullValue() {
        cacheService.put("user", "u-null-put", 60, null);
        verify(valueOps).set(eq("user:u-null-put"), eq("__NULL__"), any(Duration.class));
    }

    @Test
    @DisplayName("evict：双删 L1 + L2")
    void evictDeletesL1AndL2() {
        // 先 put
        cacheService.put("user", "u-evict", 60, "val");
        // 再 evict
        cacheService.evict("user", "u-evict");
        // 验证 L2 delete 被调用
        verify(redisTemplate).delete("user:u-evict");
    }

    @Test
    @DisplayName("evict 后再 get → 走 loader（L1 已清）")
    void evictThenGetGoesToLoader() {
        AtomicInteger loadCount = new AtomicInteger(0);

        cacheService.put("user", "u-evict2", 60, "v");
        cacheService.evict("user", "u-evict2");

        when(valueOps.get("user:u-evict2")).thenReturn(null);
        String result = cacheService.get("user", "u-evict2", 60,
                () -> { loadCount.incrementAndGet(); return "reloaded"; }, String.class);

        assertThat(result).isEqualTo("reloaded");
        assertThat(loadCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("L1 disabled → 全走 L2 + loader")
    void l1DisabledAllFromL2() {
        properties.getL1().setEnabled(false);
        when(valueOps.get("user:u-nol1")).thenReturn(null);

        String result = cacheService.get("user", "u-nol1", 60, () -> "from-loader", String.class);

        assertThat(result).isEqualTo("from-loader");
    }

    @Test
    @DisplayName("get 对象类型：Jackson 序列化/反序列化")
    void getObjectType() {
        when(valueOps.get("user:u-obj")).thenReturn("{\"id\":\"obj-1\",\"name\":\"Alice\"}");

        TestUser result = cacheService.get("user", "u-obj", 60, () -> null, TestUser.class);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("obj-1");
        assertThat(result.getName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("put 对象类型 → L2 存 JSON 字符串")
    void putObjectTypeSerializesToJson() {
        TestUser user = new TestUser("u-put-obj", "Bob");
        cacheService.put("user", "u-put-obj", 60, user);

        verify(valueOps).set(eq("user:u-put-obj"), contains("\"id\":\"u-put-obj\""), any(Duration.class));
    }

    @Test
    @DisplayName("Redis 写入异常 → 不拖垮业务（put 不抛）")
    void redisPutFailureSwallowed() {
        doThrow(new RuntimeException("redis down")).when(valueOps)
                .set(anyString(), anyString(), any(Duration.class));

        // 不应抛异常
        assertThatCode(() -> cacheService.put("user", "u-err", 60, "val"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("warmup：批量加载")
    void warmupBatchLoad() {
        cacheService.warmup("user", java.util.List.of("u-w1", "u-w2", "u-w3"), 60,
                id -> "warmed-" + id, String.class);

        // 验证每个 key 都被写入 L2
        verify(valueOps).set(eq("user:u-w1"), eq("warmed-u-w1"), any(Duration.class));
        verify(valueOps).set(eq("user:u-w2"), eq("warmed-u-w2"), any(Duration.class));
        verify(valueOps).set(eq("user:u-w3"), eq("warmed-u-w3"), any(Duration.class));
    }

    @Test
    @DisplayName("warmup：空列表 → 不执行")
    void warmupEmptyListNoop() {
        cacheService.warmup("user", java.util.List.of(), 60, id -> "x", String.class);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("warmup：null 列表 → 不执行")
    void warmupNullListNoop() {
        cacheService.warmup("user", null, 60, id -> "x", String.class);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("warmup：loader 抛异常 → 跳过该 key（不影响其他）")
    void warmupLoaderErrorSkipsKey() {
        cacheService.warmup("user", java.util.List.of("ok", "fail"), 60,
                id -> id.equals("fail") ? throwRuntime() : "ok-val", String.class);

        verify(valueOps).set(eq("user:ok"), eq("ok-val"), any(Duration.class));
        verify(valueOps, never()).set(eq("user:fail"), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("TTL 抖动：put 时 L2 TTL 不等于原始值（±10%）")
    void ttlJitterApplied() {
        org.mockito.ArgumentCaptor<Duration> ttlCaptor = org.mockito.ArgumentCaptor.forClass(Duration.class);
        cacheService.put("user", "u-jitter", 1000, "val");
        verify(valueOps).set(anyString(), anyString(), ttlCaptor.capture());
        long ttlSeconds = ttlCaptor.getValue().getSeconds();
        // 1000 ± 10% → [900, 1100]
        assertThat(ttlSeconds).isBetween(900L, 1100L);
    }

    private static String throwRuntime() {
        throw new RuntimeException("intentional");
    }

    public static class TestUser {
        private String id;
        private String name;
        public TestUser() {}
        public TestUser(String id, String name) { this.id = id; this.name = name; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
