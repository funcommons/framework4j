package fun.commons.framework4j.web;

import fun.commons.framework4j.api.ApiCode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/**
 * 统一 API 响应结构（v2.0：不可变 + 强类型 error）
 * <p>
 * 标准响应格式：
 * <pre>
 * {
 *   "code": 0,
 *   "message": "操作成功",
 *   "data": { ... },
 *   "error": null,
 *   "trace_id": "uuid",
 *   "timestamp": 1718660400000
 * }
 * </pre>
 *
 * <p>对齐 mc-api-spec v1.6 §4：
 * <ul>
 *   <li>6 字段必返（code / message / data / error / trace_id / timestamp）</li>
 *   <li>失败时 data 必须 null（10700 部分成功例外）</li>
 *   <li>error 为 List&lt;ApiError&gt;（非 Object）</li>
 *   <li>trace_id 双通道（body + X-Trace-Id Header）</li>
 * </ul>
 *
 * <p>支持 Jackson 反序列化（消费者 Feign 客户端等场景）。
 *
 * @param <T> 响应数据类型
 * @since 1.0.0
 * @see ApiError
 */
@Getter
public class ApiResponse<T> {

    private final int code;
    private final String message;
    private final T data;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<ApiError> error;

    @JsonProperty("trace_id")
    private final String traceId;

    @JsonProperty("timestamp")
    private final long timestamp;

    /**
     * Jackson 反序列化用构造器（消费者 Feign 场景）。
     * <p>反序列化时无 TraceContext，traceId 直接用 JSON 中的值；timestamp 缺省时填 0。
     */
    @JsonCreator
    public ApiResponse(
            @JsonProperty("code") int code,
            @JsonProperty("message") String message,
            @JsonProperty("data") T data,
            @JsonProperty("error") List<ApiError> error,
            @JsonProperty("trace_id") String traceId,
            @JsonProperty("timestamp") Long timestamp) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.error = error;
        this.traceId = traceId;
        this.timestamp = timestamp != null ? timestamp : 0L;
    }

    /** 内部构造：traceId / timestamp 自动填充。 */
    private ApiResponse(int code, String message, T data, List<ApiError> error, boolean autoTrace) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.error = error;
        this.traceId = autoTrace ? fallbackTraceId() : null;
        this.timestamp = autoTrace ? System.currentTimeMillis() : 0L;
    }

    /**
     * 优先用 TraceContext 的 traceId；未配置 Micrometer Tracing 时用 UUID 兜底。
     * <p>对齐 mc-api-spec §4 铁律 6：trace_id 必返。
     */
    private static String fallbackTraceId() {
        String tid = TraceContext.getTraceId();
        return tid != null && !tid.isBlank() ? tid : UUID.randomUUID().toString();
    }

    // ==================== 成功响应 ====================

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(ApiCode.SUCCESS.getCode(), ApiCode.SUCCESS.getMessage(), null, null, true);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ApiCode.SUCCESS.getCode(), ApiCode.SUCCESS.getMessage(), data, null, true);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(ApiCode.SUCCESS.getCode(), message, data, null, true);
    }

    // ==================== 部分成功（10700）====================

    /**
     * 部分成功响应（批量操作中部分失败，data 非 null）。
     * <p>对齐 mc-api-spec §4：10700 是失败时 data 可非 null 的唯一例外。
     */
    public static <T> ApiResponse<T> partialSuccess(T data, List<ApiError> errors) {
        return new ApiResponse<>(ApiCode.PARTIAL_SUCCESS.getCode(), ApiCode.PARTIAL_SUCCESS.getMessage(), data, errors, true);
    }

    public static <T> ApiResponse<T> partialSuccess(T data, String message, List<ApiError> errors) {
        return new ApiResponse<>(ApiCode.PARTIAL_SUCCESS.getCode(), message, data, errors, true);
    }

    // ==================== 失败响应 ====================

    public static <T> ApiResponse<T> fail(ApiCode apiCode) {
        return new ApiResponse<>(apiCode.getCode(), apiCode.getMessage(), null, null, true);
    }

    public static <T> ApiResponse<T> fail(ApiCode apiCode, String message) {
        return new ApiResponse<>(apiCode.getCode(), message, null, null, true);
    }

    public static <T> ApiResponse<T> fail(ApiCode apiCode, List<ApiError> errors) {
        return new ApiResponse<>(apiCode.getCode(), apiCode.getMessage(), null, errors, true);
    }

    public static <T> ApiResponse<T> fail(ApiCode apiCode, String message, List<ApiError> errors) {
        return new ApiResponse<>(apiCode.getCode(), message, null, errors, true);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, null, true);
    }

    public static <T> ApiResponse<T> fail(int code, String message, List<ApiError> errors) {
        return new ApiResponse<>(code, message, null, errors, true);
    }

    /**
     * @deprecated 用 {@link #fail(ApiCode, List)} 替代，error 字段已强类型化为 List&lt;ApiError&gt;。
     *             v2.2 将删除此方法。
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    public static <T> ApiResponse<T> fail(ApiCode apiCode, Object errorDetail) {
        return new ApiResponse<>(apiCode.getCode(), apiCode.getMessage(), null,
                List.of(ApiError.of(null, null, String.valueOf(errorDetail))), true);
    }

    /**
     * @deprecated 用 {@link #fail(int, String, List)} 替代。v2.2 将删除此方法。
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    public static <T> ApiResponse<T> fail(int code, String message, Object errorDetail) {
        return new ApiResponse<>(code, message, null,
                List.of(ApiError.of(null, null, String.valueOf(errorDetail))), true);
    }

    // ==================== 判断方法 ====================

    public boolean isSuccess() {
        return this.code == 0;
    }

    public boolean isFail() {
        return this.code != 0;
    }

    // ==================== 链式 ====================

    /**
     * 覆盖 TraceId（默认从 TraceContext 自动取）。网关透传场景用。
     */
    public ApiResponse<T> withTraceId(String traceId) {
        return new ApiResponse<>(this.code, this.message, this.data, this.error, traceId, this.timestamp);
    }
}

