package fun.commons.framework4j.web;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TraceContext 配置 / 异常路径 / 并发安全补充测试。
 *
 * <p>原 TraceContextTest 覆盖了 MDC 基础路径。本测试补充：
 * <ol>
 *   <li>注入 mock Tracer：当 MDC 空时从 Tracer.currentSpan() 读</li>
 *   <li>Tracer 兜底：currentSpan==null → null</li>
 *   <li>setTraceId 仅在非空白时写 MDC（blank 时 remove）</li>
 *   <li>isTracerAvailable 与 setTracer 联动</li>
 *   <li>getTraceId 优先级：MDC > Tracer</li>
 *   <li>并发：N 线程 setTracer/clear 不互相破坏</li>
 * </ol>
 *
 * <p>注意：与 io.micrometer.tracing.TraceContext 同名，使用 TC 别名避免歧义。
 *
 * @since 2.1.0
 */
@DisplayName("TraceContext Tracer 注入与并发测试")
class TraceContextAdditionalTest {

    /** 我的 framework4j.web.TraceContext 别名，避免与 io.micrometer.tracing.TraceContext 同名 */
    private static final class TC {
        static void setTracer(Tracer t) { fun.commons.framework4j.web.TraceContext.setTracer(t); }
        static void clear() { fun.commons.framework4j.web.TraceContext.clear(); }
        static String getTraceId() { return fun.commons.framework4j.web.TraceContext.getTraceId(); }
        static boolean isTracerAvailable() { return fun.commons.framework4j.web.TraceContext.isTracerAvailable(); }
        static void setTraceId(String s) { fun.commons.framework4j.web.TraceContext.setTraceId(s); }
    }

    @BeforeEach
    @AfterEach
    void reset() {
        TC.clear();
        MDC.clear();
    }

    @Test
    @DisplayName("setTracer(mock) → isTracerAvailable=true")
    void setMockTracerEnablesAvailability() {
        Tracer t = mock(Tracer.class);
        TC.setTracer(t);
        assertThat(TC.isTracerAvailable()).isTrue();
    }

    @Test
    @DisplayName("getTraceId：MDC 空时走 Tracer.currentSpan().context().traceId()")
    void getTraceIdFromTracerWhenMdcEmpty() {
        Tracer t = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext ctx = mock(TraceContext.class);
        when(t.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(ctx);
        when(ctx.traceId()).thenReturn("tracer-trace-id");

        TC.setTracer(t);
        assertThat(MDC.get("traceId")).isNull();
        assertThat(TC.getTraceId()).isEqualTo("tracer-trace-id");
    }

    @Test
    @DisplayName("getTraceId：Tracer 存在但 currentSpan==null → null")
    void getTraceIdWhenNoCurrentSpan() {
        Tracer t = mock(Tracer.class);
        when(t.currentSpan()).thenReturn(null);
        TC.setTracer(t);
        assertThat(TC.getTraceId()).isNull();
    }

    @Test
    @DisplayName("getTraceId 优先级：MDC 值胜过 Tracer 值")
    void mdcWinsOverTracer() {
        Tracer t = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext ctx = mock(TraceContext.class);
        when(t.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(ctx);
        when(ctx.traceId()).thenReturn("tracer-id");

        TC.setTracer(t);
        MDC.put("traceId", "mdc-id");
        assertThat(TC.getTraceId()).isEqualTo("mdc-id");
    }

    @Test
    @DisplayName("getTraceId：MDC 是空白字符串 → 走 Tracer")
    void blankMdcFallsThroughToTracer() {
        Tracer t = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext ctx = mock(TraceContext.class);
        when(t.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(ctx);
        when(ctx.traceId()).thenReturn("from-tracer");

        TC.setTracer(t);
        MDC.put("traceId", "   ");
        assertThat(TC.getTraceId()).isEqualTo("from-tracer");
    }

    @Test
    @DisplayName("setTraceId 空白字符串 → 清除 MDC（不写）")
    void blankTraceIdClearsMdc() {
        MDC.put("traceId", "previous");
        TC.setTraceId("   ");
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    @DisplayName("setTraceId(null) → 清除 MDC")
    void nullTraceIdClearsMdc() {
        MDC.put("traceId", "previous");
        TC.setTraceId(null);
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    @DisplayName("setTraceId(空白字符) → 清除 MDC（tab/newline）")
    void whitespaceOnlyTraceIdClearsMdc() {
        MDC.put("traceId", "v1");
        TC.setTraceId("\t\n");
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    @DisplayName("clear：同时清 Tracer 和 MDC")
    void clearResetsBothTracerAndMdc() {
        Tracer t = mock(Tracer.class);
        TC.setTracer(t);
        TC.setTraceId("temp");
        TC.clear();
        assertThat(TC.isTracerAvailable()).isFalse();
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    @DisplayName("setTracer 多次调用：每次替换生效")
    void setTracerMultipleTimes() {
        Tracer t1 = mock(Tracer.class);
        Tracer t2 = mock(Tracer.class);
        when(t2.currentSpan()).thenReturn(null);
        TC.setTracer(t1);
        assertThat(TC.isTracerAvailable()).isTrue();
        TC.setTracer(t2);
        assertThat(TC.isTracerAvailable()).isTrue();
        TC.setTracer(null);
        assertThat(TC.isTracerAvailable()).isFalse();
    }

    @Test
    @DisplayName("并发：N 线程同时 setTracer / clear / getTraceId → 无异常")
    void concurrentTracerAccess() throws Exception {
        int n = 20;
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n * 3);
        AtomicInteger errors = new AtomicInteger();

        Runnable r1 = () -> {
            try {
                start.await();
                TC.setTracer(mock(Tracer.class));
            } catch (Throwable t) { errors.incrementAndGet(); }
            finally { done.countDown(); }
        };
        Runnable r2 = () -> {
            try {
                start.await();
                TC.clear();
            } catch (Throwable t) { errors.incrementAndGet(); }
            finally { done.countDown(); }
        };
        Runnable r3 = () -> {
            try {
                start.await();
                TC.getTraceId();
                TC.isTracerAvailable();
            } catch (Throwable t) { errors.incrementAndGet(); }
            finally { done.countDown(); }
        };

        for (int i = 0; i < n; i++) {
            pool.submit(r1);
            pool.submit(r2);
            pool.submit(r3);
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        assertThat(errors.get()).isZero();
    }

    @Test
    @DisplayName("getTraceId 在并发 setTracer 时不会 NPE（volatile 读保护）")
    void getTraceIdConcurrentWithSetTracerNoNpe() throws Exception {
        int n = 50;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger npeCount = new AtomicInteger();
        AtomicInteger resultCount = new AtomicInteger();

        for (int i = 0; i < n; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    if (idx % 2 == 0) {
                        TC.setTracer(mock(Tracer.class));
                    } else {
                        TC.setTracer(null);
                    }
                    String tid = TC.getTraceId();
                    if (tid != null) resultCount.incrementAndGet();
                } catch (NullPointerException npe) {
                    npeCount.incrementAndGet();
                } catch (Throwable t) {
                    // 忽略其他异常
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        // 任何线程都不应抛 NPE
        assertThat(npeCount.get()).isZero();
        // 至少跑完一次，不关心 resultCount 是否 > 0
        assertThat(resultCount.get()).isGreaterThanOrEqualTo(0);
    }
}
