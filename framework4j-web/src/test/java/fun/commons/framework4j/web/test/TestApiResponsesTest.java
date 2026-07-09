package fun.commons.framework4j.web.test;

import fun.commons.framework4j.web.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BaseUnitTest 示例 + TestApiResponses 验证
 *
 * @since 2.1.0
 */
@DisplayName("TestApiResponses fixtures 验证")
class TestApiResponsesTest {

    @BeforeEach
    void setUp() {
        // TraceContext 初始化（实际项目自动装配，这里手动设置）
        org.slf4j.MDC.put("trace_id", "test-trace-1");
    }

    @Test
    @DisplayName("ok() 返回 code=0 + 自动 trace_id")
    void ok_withTraceId() {
        ApiResponse<Object> r = TestApiResponses.ok();
        TestApiResponses.assertEnvelope(r);
        assertThat(r.getCode()).isZero();
        assertThat(r.getMessage()).isEqualTo("操作成功");
        // trace_id 应自动生成（Tracer 或 UUID 兜底）
        assertThat(r.getTraceId()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("ok(data) 带 data")
    void okWithData() {
        ApiResponse<String> r = TestApiResponses.ok("hello");
        assertThat(r.getData()).isEqualTo("hello");
    }

    @Test
    @DisplayName("fail(code) 时 data 必须 null")
    void fail_dataNull() {
        ApiResponse<Object> r = TestApiResponses.fail(fun.commons.framework4j.api.ApiCode.NOT_FOUND);
        TestApiResponses.assertFailDataNull(r);
    }

    @Test
    @DisplayName("failWithErrors 包含 error 字段")
    void failWithErrors() {
        ApiResponse<Object> r = TestApiResponses.failWithErrors(java.util.List.of(
                fun.commons.framework4j.web.ApiError.of("email", "FORMAT_INVALID", "格式错误")));
        assertThat(r.getError()).hasSize(1);
        assertThat(r.getError().get(0).field()).isEqualTo("email");
    }

    @Test
    @DisplayName("MockHttpServletRequest fixtures 示例")
    void mockRequestFixture() {
        MockHttpServletRequest req = TestRedisRequests.post("/v1/orders");
        req.addHeader("X-User-Id", "u-1");
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getRequestURI()).isEqualTo("/v1/orders");
        assertThat(req.getHeader("X-User-Id")).isEqualTo("u-1");
    }
}
