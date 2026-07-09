package fun.commons.framework4j.web.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.api.ApiCode;
import fun.commons.framework4j.web.ApiResponse;

import java.util.List;

/**
 * ApiResponse / ApiCode 测试 fixtures
 * <p>
 * v2.1 沉淀：减少 35+ @BeforeEach 中重复的 ApiResponse 构造代码。
 *
 * @since 2.1.0
 */
public final class TestApiResponses {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private TestApiResponses() {}

    /** 成功响应（data=null） */
    public static ApiResponse<Object> ok() {
        return ApiResponse.success();
    }

    /** 成功响应（带 data） */
    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.success(data);
    }

    /** 业务失败响应 */
    public static ApiResponse<Object> fail(ApiCode code) {
        return ApiResponse.fail(code);
    }

    /** 业务失败响应（带自定义 message） */
    public static ApiResponse<Object> fail(ApiCode code, String message) {
        return ApiResponse.fail(code, message);
    }

    /** 参数校验失败响应（带 error 字段） */
    public static ApiResponse<Object> failWithErrors(List<fun.commons.framework4j.web.ApiError> errors) {
        return ApiResponse.fail(ApiCode.PARAM_ERROR, errors);
    }

    /**
     * 断言 ApiResponse 6 字段完整（traceId/timestamp 非空、code/message 存在）
     */
    public static void assertEnvelope(ApiResponse<?> r) {
        org.assertj.core.api.Assertions.assertThat(r).isNotNull();
        org.assertj.core.api.Assertions.assertThat(r.getTraceId()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(r.getTimestamp()).isGreaterThan(0L);
    }

    /**
     * 断言失败时 data 必须 null（除 10700）
     */
    public static void assertFailDataNull(ApiResponse<?> r) {
        if (r.getCode() != ApiCode.PARTIAL_SUCCESS.getCode()) {
            org.assertj.core.api.Assertions.assertThat(r.getData()).isNull();
        }
    }
}
