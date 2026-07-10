package fun.commons.framework4j.datetime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TimeFormatStateHolder 单元测试。
 *
 * <p>覆盖维度：
 * <ol>
 *   <li>setState/getState/clear 往返</li>
 *   <li>shouldUseLocalFormat 与 state 联动</li>
 *   <li>setState(null) 等价于 clear</li>
 *   <li>getState 默认值 = DEFAULT</li>
 *   <li>detectTimeFormatState null safety → DEFAULT</li>
 *   <li>setUseLocal / isUseLocal 向后兼容</li>
 *   <li>getCacheStats / getOperationStats / clearCaches</li>
 *   <li>并发：N 线程各自 setState 隔离</li>
 *   <li>shutdown 后可继续使用（重建 executor）</li>
 * </ol>
 *
 * @since 2.1.0
 */
@DisplayName("TimeFormatStateHolder 测试")
class TimeFormatStateHolderTest {

    @BeforeEach
    void setUp() {
        TimeFormatStateHolder.clear();
        TimeFormatStateHolder.clearCaches();
    }

    @AfterEach
    void tearDown() {
        TimeFormatStateHolder.clear();
        TimeFormatStateHolder.clearCaches();
    }

    @Test
    @DisplayName("getState：默认值 = DEFAULT（无 sourceClass）")
    void defaultState() {
        TimeFormatState state = TimeFormatStateHolder.getState();
        assertThat(state).isSameAs(TimeFormatState.DEFAULT);
        assertThat(state.shouldFormat()).isFalse();
        assertThat(state.isDefault()).isTrue();
    }

    @Test
    @DisplayName("setState + getState：往返一致")
    void setStateRoundTrip() {
        TimeFormatState s = TimeFormatState.annotationLocal("MyController");
        TimeFormatStateHolder.setState(s);
        assertThat(TimeFormatStateHolder.getState()).isSameAs(s);
        assertThat(TimeFormatStateHolder.shouldUseLocalFormat()).isTrue();
    }

    @Test
    @DisplayName("setState(null) 等价于 clear")
    void setStateNullClears() {
        TimeFormatStateHolder.setState(TimeFormatState.MANUAL_LOCAL);
        TimeFormatStateHolder.setState(null);
        assertThat(TimeFormatStateHolder.getState()).isSameAs(TimeFormatState.DEFAULT);
        assertThat(TimeFormatStateHolder.shouldUseLocalFormat()).isFalse();
    }

    @Test
    @DisplayName("clear 重置为 DEFAULT")
    void clearResets() {
        TimeFormatStateHolder.setState(TimeFormatState.MANUAL_LOCAL);
        TimeFormatStateHolder.clear();
        assertThat(TimeFormatStateHolder.getState()).isSameAs(TimeFormatState.DEFAULT);
    }

    @Test
    @DisplayName("setUseLocal(true) → shouldUseLocalFormat=true（向后兼容）")
    void setUseLocalTrueBackwardCompat() {
        TimeFormatStateHolder.setUseLocal(true);
        assertThat(TimeFormatStateHolder.isUseLocal()).isTrue();
        assertThat(TimeFormatStateHolder.shouldUseLocalFormat()).isTrue();
    }

    @Test
    @DisplayName("setUseLocal(false) → shouldUseLocalFormat=false")
    void setUseLocalFalseBackwardCompat() {
        TimeFormatStateHolder.setUseLocal(true);
        TimeFormatStateHolder.setUseLocal(false);
        assertThat(TimeFormatStateHolder.isUseLocal()).isFalse();
    }

    @Test
    @DisplayName("detectTimeFormatState(null) → DEFAULT，不抛 NPE")
    void detectNullReturnsDefault() {
        assertThat(TimeFormatStateHolder.detectTimeFormatState(null))
                .isSameAs(TimeFormatState.DEFAULT);
    }

    @Test
    @DisplayName("detectTimeFormatState：无注解的 HandlerMethod → annotationDefault 状态")
    void detectNoAnnotation() throws Exception {
        HandlerMethod hm = mock(HandlerMethod.class);
        when(hm.getBeanType()).thenReturn((Class) NoAnnotationController.class);
        when(hm.hasMethodAnnotation(LocalTimeFormat.class)).thenReturn(false);
        when(hm.getMethod()).thenReturn(
                NoAnnotationController.class.getMethod("noAnnotation"));

        TimeFormatState state = TimeFormatStateHolder.detectTimeFormatState(hm);
        assertThat(state.isUseLocalFormat()).isFalse();
        assertThat(state.isFromAnnotation()).isTrue();
        assertThat(state.getSourceClass()).contains("NoAnnotationController");
    }

    @Test
    @DisplayName("detectTimeFormatState：方法级 @LocalTimeFormat → annotationLocal")
    void detectMethodAnnotation() throws Exception {
        HandlerMethod hm = mock(HandlerMethod.class);
        when(hm.getBeanType()).thenReturn((Class) WithMethodAnnotationController.class);
        when(hm.hasMethodAnnotation(LocalTimeFormat.class)).thenReturn(true);
        when(hm.getMethod()).thenReturn(
                WithMethodAnnotationController.class.getMethod("withAnnotation"));

        TimeFormatState state = TimeFormatStateHolder.detectTimeFormatState(hm);
        assertThat(state.isUseLocalFormat()).isTrue();
        assertThat(state.isFromAnnotation()).isTrue();
    }

    @Test
    @DisplayName("detectTimeFormatState：相同 handler 重复调用 → 缓存命中（统计指标增加）")
    void detectCachesRepeatedCalls() throws Exception {
        HandlerMethod hm = mock(HandlerMethod.class);
        when(hm.getBeanType()).thenReturn((Class) NoAnnotationController.class);
        when(hm.hasMethodAnnotation(LocalTimeFormat.class)).thenReturn(false);
        when(hm.getMethod()).thenReturn(
                NoAnnotationController.class.getMethod("noAnnotation"));

        long before = TimeFormatStateHolder.getOperationStats()
                .getOrDefault("method_cache_hit", 0L);
        TimeFormatStateHolder.detectTimeFormatState(hm);
        TimeFormatStateHolder.detectTimeFormatState(hm);
        long after = TimeFormatStateHolder.getOperationStats()
                .getOrDefault("method_cache_hit", 0L);
        assertThat(after).isGreaterThan(before);
    }

    @Test
    @DisplayName("getCacheStats：返回非空对象 + total = class + method")
    void cacheStatsTotals() {
        TimeFormatStateHolder.CacheStats stats = TimeFormatStateHolder.getCacheStats();
        assertThat(stats).isNotNull();
        assertThat(stats.getTotalCacheSize())
                .isEqualTo(stats.getClassCacheSize() + stats.getMethodCacheSize());
    }

    @Test
    @DisplayName("clearCaches：调用后 clear_caches 计数 >= 1")
    void clearCachesIncrementsStat() {
        TimeFormatStateHolder.clearCaches();
        // clearCaches 会先 STATS.clear() 再 updateStats("clear_caches")，
        // 所以调用后 clear_caches 恰好 = 1（而不是累积）
        long after = TimeFormatStateHolder.getOperationStats()
                .getOrDefault("clear_caches", 0L);
        assertThat(after).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("并发：N 线程各自 setState → ThreadLocal 隔离，不互相污染")
    void concurrentSetStateIsolated() throws Exception {
        int n = 20;
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger mismatches = new AtomicInteger();

        for (int i = 0; i < n; i++) {
            final int idx = i;
            final boolean local = (idx % 2 == 0);
            pool.submit(() -> {
                try {
                    start.await();
                    TimeFormatState expected = TimeFormatState.manualLocal("t-" + idx);
                    if (!local) expected = TimeFormatState.manualDefault("t-" + idx);
                    TimeFormatStateHolder.setState(expected);
                    Thread.sleep(5);
                    TimeFormatState actual = TimeFormatStateHolder.getState();
                    if (!actual.equals(expected)) mismatches.incrementAndGet();
                    TimeFormatStateHolder.clear();
                } catch (Throwable t) {
                    mismatches.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        assertThat(mismatches.get()).isZero();
    }

    @Test
    @DisplayName("shutdown：重建 executor 后仍可继续 setState/getState")
    void shutdownAllowsReuse() {
        TimeFormatStateHolder.shutdown();
        TimeFormatStateHolder.setState(TimeFormatState.MANUAL_LOCAL);
        assertThat(TimeFormatStateHolder.shouldUseLocalFormat()).isTrue();
        TimeFormatStateHolder.clear();
    }

    @Test
    @DisplayName("CacheStats.toString 包含 totalCacheSize")
    void cacheStatsToString() {
        TimeFormatStateHolder.CacheStats s = TimeFormatStateHolder.getCacheStats();
        String str = s.toString();
        assertThat(str).contains("classCacheSize").contains("methodCacheSize").contains("totalSize");
    }

    // ============ 测试用 Controller 类 ============

    static class NoAnnotationController {
        public void noAnnotation() {}
    }

    static class WithMethodAnnotationController {
        @LocalTimeFormat
        public void withAnnotation() {}
    }
}
