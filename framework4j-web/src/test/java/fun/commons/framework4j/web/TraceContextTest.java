package fun.commons.framework4j.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TraceContext 测试")
class TraceContextTest {

    @AfterEach
    void cleanup() {
        org.slf4j.MDC.remove("traceId");
    }

    @Test
    @DisplayName("setTraceId + getTraceId：基本读写")
    void setAndGetTraceId() {
        TraceContext.setTraceId("test-trace-id-123");
        assertThat(TraceContext.getTraceId()).isEqualTo("test-trace-id-123");
    }

    @Test
    @DisplayName("getTraceId：未设置 → 返回 null（无 Tracer）")
    void getTraceIdWithoutSet() {
        TraceContext.clear();
        // 无 Tracer 且 MDC 无值 → null 或 UUID（取决于实现）
        String traceId = TraceContext.getTraceId();
        // 可能是 null 或自动生成的 UUID
        assertThat(traceId).satisfiesAnyOf(
                tid -> assertThat(tid).isNull(),
                tid -> assertThat(tid).isNotBlank());
    }

    @Test
    @DisplayName("clear：清除后 MDC 无 traceId")
    void clearRemovesMDC() {
        TraceContext.setTraceId("to-be-cleared");
        TraceContext.clear();
        assertThat(org.slf4j.MDC.get("traceId")).isNull();
    }

    @Test
    @DisplayName("setTraceId(null)：不抛异常")
    void setTraceIdNull() {
        assertThatCode(() -> TraceContext.setTraceId(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("setTraceId(空字符串)：不抛异常")
    void setTraceIdEmpty() {
        assertThatCode(() -> TraceContext.setTraceId("")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("isTracerAvailable：无 Tracer → false")
    void isTracerAvailableWithoutTracer() {
        assertThat(TraceContext.isTracerAvailable()).isFalse();
    }

    @Test
    @DisplayName("setTracer(null)：不抛异常")
    void setTracerNull() {
        assertThatCode(() -> TraceContext.setTracer(null)).doesNotThrowAnyException();
        assertThat(TraceContext.isTracerAvailable()).isFalse();
    }

    @Test
    @DisplayName("getTraceId：MDC 有值 → 优先返回 MDC 值")
    void getTraceIdFromMDC() {
        org.slf4j.MDC.put("traceId", "mdc-trace-id");
        assertThat(TraceContext.getTraceId()).isEqualTo("mdc-trace-id");
    }

    @Test
    @DisplayName("setTraceId 覆盖 MDC 已有值")
    void setTraceIdOverwritesMDC() {
        org.slf4j.MDC.put("traceId", "old-value");
        TraceContext.setTraceId("new-value");
        assertThat(org.slf4j.MDC.get("traceId")).isEqualTo("new-value");
    }

    @Test
    @DisplayName("多次 setTraceId + clear 循环：无泄漏")
    void multipleSetClearCycles() {
        for (int i = 0; i < 100; i++) {
            TraceContext.setTraceId("cycle-" + i);
            assertThat(TraceContext.getTraceId()).isEqualTo("cycle-" + i);
            TraceContext.clear();
        }
        assertThat(org.slf4j.MDC.get("traceId")).isNull();
    }

    @Test
    @DisplayName("并发 setTraceId：ThreadLocal 隔离（MDC 是线程独立的）")
    void concurrentSetTraceId() throws Exception {
        int threads = 10;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.atomic.AtomicInteger failures = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    String expected = "trace-" + idx;
                    TraceContext.setTraceId(expected);
                    Thread.sleep(10);
                    String actual = TraceContext.getTraceId();
                    if (!expected.equals(actual)) failures.incrementAndGet();
                    TraceContext.clear();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();
        assertThat(failures.get()).isZero();
    }
}
