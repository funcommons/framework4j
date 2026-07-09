package fun.commons.framework4j.datasource.tracing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultTraceIdProvider 单元测试
 */
class DefaultTraceIdProviderTest {

    private final DefaultTraceIdProvider provider = new DefaultTraceIdProvider();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("MDC 无 traceId 时返回 null")
    void returnsNullWhenMdcEmpty() {
        assertNull(provider.getTraceId());
    }

    @Test
    @DisplayName("MDC 含 traceId 键时优先返回")
    void prefersTraceIdKey() {
        MDC.put("traceId", "abc-123");
        MDC.put("trace_id", "should-not-win");
        assertEquals("abc-123", provider.getTraceId());
    }

    @Test
    @DisplayName("MDC 含 snake_case trace_id 键时返回")
    void readsSnakeCaseKey() {
        MDC.put("trace_id", "snake-456");
        assertEquals("snake-456", provider.getTraceId());
    }

    @Test
    @DisplayName("MDC 含 X-B3-TraceId 键时返回")
    void readsB3Key() {
        MDC.put("X-B3-TraceId", "b3-789");
        assertEquals("b3-789", provider.getTraceId());
    }

    @Test
    @DisplayName("MDC 含空白 traceId 时跳过该键继续找下一个")
    void skipsBlankValueAndContinues() {
        MDC.put("traceId", "   ");
        MDC.put("trace_id", "fallback");
        assertEquals("fallback", provider.getTraceId());
    }

    @Test
    @DisplayName("MDC 含 X-Request-Id 键时返回")
    void readsRequestIdKey() {
        MDC.put("X-Request-Id", "req-001");
        assertEquals("req-001", provider.getTraceId());
    }

    @Test
    @DisplayName("MDC 含 requestId / request_id 键时返回")
    void readsRequestIdVariants() {
        MDC.put("requestId", "rid-1");
        assertEquals("rid-1", provider.getTraceId());

        MDC.remove("requestId");
        MDC.put("request_id", "rid-2");
        assertEquals("rid-2", provider.getTraceId());
    }
}
