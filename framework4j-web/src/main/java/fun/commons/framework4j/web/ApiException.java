package fun.commons.framework4j.web;

import fun.commons.framework4j.api.ApiCode;

import lombok.Getter;

/**
 * API 业务异常
 * <p>
 * 继承自 RuntimeException，用于 Service 层中断流程并透传错误码。
 * 会被 GlobalExceptionHandler 自动捕获并转换为 ApiResponse。
 *
 * @since 1.0.0
 */
@Getter
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;
    private final Object errorDetail;

    public ApiException(ApiCode apiCode) {
        super(apiCode.getMessage());
        this.code = apiCode.getCode();
        this.errorDetail = null;
    }

    public ApiException(ApiCode apiCode, String message) {
        super(message);
        this.code = apiCode.getCode();
        this.errorDetail = null;
    }

    /**
     * @deprecated 用 {@link #ApiException(ApiCode, java.util.List)} 替代，errorDetail 字段已强类型化为 List&lt;ApiError&gt;。v2.2 将删除。
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    public ApiException(ApiCode apiCode, Object errorDetail) {
        super(apiCode.getMessage());
        this.code = apiCode.getCode();
        this.errorDetail = errorDetail;
    }

    /**
     * @deprecated 用 {@link #ApiException(ApiCode, String, java.util.List)} 替代。v2.2 将删除。
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    public ApiException(ApiCode apiCode, String message, Object errorDetail) {
        super(message);
        this.code = apiCode.getCode();
        this.errorDetail = errorDetail;
    }

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
        this.errorDetail = null;
    }
}
