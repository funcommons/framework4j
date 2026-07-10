package fun.commons.framework4j.datasource.tracing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DefaultTraceIdProvider 补充测试")
class DefaultTraceIdProviderAdditionalTest {

    private final DefaultTraceIdProvider provider = new DefaultTraceIdProvider();

    @AfterEach
    void cleanup() {
        org.slf4j.MDC.clear();
    }

    @Test
    @DisplayName("MDC 有 traceId → 返回 MDC 值")
    void mdcHasTraceId() {
        org.slf4j.MDC.put("traceId", "mdc-trace-123");
        assertThat(provider.getTraceId()).isEqualTo("mdc-trace-123");
    }

    @Test
    @DisplayName("MDC 无 traceId → 返回 null 或非空")
    void mdcNoTraceId() {
        org.slf4j.MDC.clear();
        String result = provider.getTraceId();
        // 可能是 null（无 Tracer）或自动生成
        assertThat(result).satisfiesAnyOf(
                r -> assertThat(r).isNull(),
                r -> assertThat(r).isNotBlank());
    }

    @Test
    @DisplayName("MDC traceId 为空字符串 → 按空处理")
    void mdcEmptyTraceId() {
        org.slf4j.MDC.put("traceId", "");
        String result = provider.getTraceId();
        // 空字符串应被过滤
        assertThat(result).satisfiesAnyOf(
                r -> assertThat(r).isNull(),
                r -> assertThat(r).isNotBlank());
    }

    @Test
    @DisplayName("MDC traceId 为空白 → 按空处理")
    void mdcBlankTraceId() {
        org.slf4j.MDC.put("traceId", "   ");
        String result = provider.getTraceId();
        assertThat(result).satisfiesAnyOf(
                r -> assertThat(r).isNull(),
                r -> assertThat(r).isNotBlank());
    }
}
