package fun.commons.framework4j.web;

import fun.commons.framework4j.api.ApiCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

/**
 * ApiResponse 边界测试（补充）
 */
@DisplayName("ApiResponse 边界测试")
class ApiResponseBoundaryTest {

    @Test
    @DisplayName("success()：code=0 + message=操作成功 + data=null")
    void successNoData() {
        ApiResponse<Object> r = ApiResponse.success();
        assertThat(r.getCode()).isZero();
        assertThat(r.getMessage()).isEqualTo("操作成功");
        assertThat(r.getData()).isNull();
        assertThat(r.getError()).isNull();
    }

    @Test
    @DisplayName("success(data)：data 非 null")
    void successWithData() {
        ApiResponse<String> r = ApiResponse.success("hello");
        assertThat(r.getData()).isEqualTo("hello");
    }

    @Test
    @DisplayName("success(data, message)：自定义 message")
    void successWithMessage() {
        ApiResponse<String> r = ApiResponse.success("ok", "自定义成功");
        assertThat(r.getMessage()).isEqualTo("自定义成功");
    }

    @Test
    @DisplayName("partialSuccess：code=10700 + data 非 null（唯一例外）")
    void partialSuccess() {
        ApiResponse<String> r = ApiResponse.partialSuccess("partial", java.util.List.of());
        assertThat(r.getCode()).isEqualTo(10700);
        assertThat(r.getData()).isEqualTo("partial");
    }

    @Test
    @DisplayName("fail(ApiCode)：data 必须 null")
    void failApiCode() {
        ApiResponse<Object> r = ApiResponse.fail(ApiCode.NOT_FOUND);
        assertThat(r.getCode()).isEqualTo(10400);
        assertThat(r.getData()).isNull();
    }

    @Test
    @DisplayName("fail(ApiCode, message)：自定义 message")
    void failApiCodeWithMessage() {
        ApiResponse<Object> r = ApiResponse.fail(ApiCode.NOT_FOUND, "订单不存在");
        assertThat(r.getMessage()).isEqualTo("订单不存在");
    }

    @Test
    @DisplayName("fail(int, String)：原始 code")
    void failRawCode() {
        ApiResponse<Object> r = ApiResponse.fail(10601, "库存不足");
        assertThat(r.getCode()).isEqualTo(10601);
    }

    @Test
    @DisplayName("isSuccess / isFail")
    void isSuccessAndIsFail() {
        assertThat(ApiResponse.success().isSuccess()).isTrue();
        assertThat(ApiResponse.success().isFail()).isFalse();
        assertThat(ApiResponse.fail(ApiCode.SYSTEM_BUSY).isSuccess()).isFalse();
        assertThat(ApiResponse.fail(ApiCode.SYSTEM_BUSY).isFail()).isTrue();
    }

    @Test
    @DisplayName("withTraceId：覆盖 traceId")
    void withTraceId() {
        ApiResponse<String> r = ApiResponse.success("data").withTraceId("custom-trace");
        assertThat(r.getTraceId()).isEqualTo("custom-trace");
    }

    @Test
    @DisplayName("timestamp > 0")
    void timestampPositive() {
        ApiResponse<Object> r = ApiResponse.success();
        assertThat(r.getTimestamp()).isPositive();
    }

    @ParameterizedTest
    @EnumSource(ApiCode.class)
    @DisplayName("所有 ApiCode 都有非空 message")
    void allApiCodesHaveMessage(ApiCode code) {
        assertThat(code.getMessage()).isNotNull().isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(ApiCode.class)
    @DisplayName("fromCode 与 getCode 双向一致")
    void fromCodeRoundTrip(ApiCode code) {
        ApiCode found = ApiCode.fromCode(code.getCode());
        assertThat(found).isEqualTo(code);
    }

    @Test
    @DisplayName("fromCode：不存在的 code → 返回 null")
    void fromCodeMissing() {
        assertThat(ApiCode.fromCode(99999)).isNull();
    }

    @Test
    @DisplayName("fail with errors list")
    void failWithErrors() {
        java.util.List<ApiError> errors = java.util.List.of(
                ApiError.of("email", "FORMAT_INVALID", "格式错误"),
                ApiError.of("name", "REQUIRED_MISSING", "必填"));
        ApiResponse<Object> r = ApiResponse.fail(ApiCode.PARAM_ERROR, errors);
        assertThat(r.getError()).hasSize(2);
        assertThat(r.getError().get(0).field()).isEqualTo("email");
    }

    @Test
    @DisplayName("partialSuccess with custom message")
    void partialSuccessWithMessage() {
        ApiResponse<String> r = ApiResponse.partialSuccess("data", "部分成功", java.util.List.of());
        assertThat(r.getMessage()).isEqualTo("部分成功");
        assertThat(r.getData()).isEqualTo("data");
    }

    @Test
    @DisplayName("fail with null data always")
    void failDataAlwaysNull() {
        for (ApiCode code : ApiCode.values()) {
            if (code == ApiCode.SUCCESS) continue;
            ApiResponse<Object> r = ApiResponse.fail(code);
            assertThat(r.getData())
                    .as("fail(%s) data 应为 null", code.name())
                    .isNull();
        }
    }
}
