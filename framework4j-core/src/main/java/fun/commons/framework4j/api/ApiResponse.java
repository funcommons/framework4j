package fun.commons.framework4j.api;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一 API 响应结构
 * <p>
 * 标准响应格式：
 * <pre>
 * {
 *   "code": 0,
 *   "message": "操作成功",
 *   "data": { ... },
 *   "error": null,
 *   "trace_id": "uuid"
 * }
 * </pre>
 *
 * @param <T> 响应数据类型
 * @author LDX2T
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class ApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 业务状态码：0 成功，非 0 失败
     */
    private Integer code;

    /**
     * 提示信息
     */
    private String message;

    /**
     * 业务数据（失败时为 null）
     */
    private T data;

    /**
     * 错误详情（仅失败时可选返回）
     */
    private Object error;

    /**
     * 全链路追踪 ID
     */
    @JSONField(name = "trace_id")
    private String traceId;

    /**
     * 私有构造函数
     */
    private ApiResponse(int code, String message, T data, Object error) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.error = error;
        // 自动从 Micrometer Tracer 获取当前 Trace ID
        this.traceId = TraceContext.getTraceId();
    }

    // ==================== 成功响应 ====================

    /**
     * 成功响应（无数据）
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(ApiCode.SUCCESS.getCode(), ApiCode.SUCCESS.getMessage(), null, null);
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ApiCode.SUCCESS.getCode(), ApiCode.SUCCESS.getMessage(), data, null);
    }

    /**
     * 成功响应（自定义消息 + 数据）
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(ApiCode.SUCCESS.getCode(), message, data, null);
    }

    // ==================== 失败响应 ====================

    /**
     * 失败响应（使用 ApiCode）
     */
    public static <T> ApiResponse<T> fail(ApiCode apiCode) {
        return new ApiResponse<>(apiCode.getCode(), apiCode.getMessage(), null, null);
    }

    /**
     * 失败响应（使用 ApiCode + 自定义消息）
     */
    public static <T> ApiResponse<T> fail(ApiCode apiCode, String message) {
        return new ApiResponse<>(apiCode.getCode(), message, null, null);
    }

    /**
     * 失败响应（使用 ApiCode + 错误详情）
     */
    public static <T> ApiResponse<T> fail(ApiCode apiCode, Object errorDetail) {
        return new ApiResponse<>(apiCode.getCode(), apiCode.getMessage(), null, errorDetail);
    }

    /**
     * 失败响应（自定义错误码和消息）
     */
    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, null);
    }

    /**
     * 失败响应（自定义错误码、消息和错误详情）
     */
    public static <T> ApiResponse<T> fail(int code, String message, Object errorDetail) {
        return new ApiResponse<>(code, message, null, errorDetail);
    }

    // ==================== 判断方法 ====================

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return this.code != null && this.code == 0;
    }

    /**
     * 判断是否失败
     */
    public boolean isFail() {
        return !isSuccess();
    }

    // ==================== Builder 模式 ====================

    /**
     * 设置 TraceId 并返回自身（链式调用）
     */
    public ApiResponse<T> withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
}
