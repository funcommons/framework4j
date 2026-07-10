package fun.commons.framework4j.api;

import lombok.Getter;

/**
 * API 业务异常
 * <p>
 * 继承自 RuntimeException，用于 Service 层中断流程并透传错误码。
 * 会被 GlobalExceptionHandler 自动捕获并转换为 ApiResponse。
 *
 * @author LDX2T
 * @since 1.0.0
 */
@Getter
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * 错误详情（可选）
     */
    private final Object errorDetail;

    /**
     * 使用 ApiCode 构造
     *
     * @param apiCode 错误码枚举
     */
    public ApiException(ApiCode apiCode) {
        super(apiCode.getMessage());
        this.code = apiCode.getCode();
        this.message = apiCode.getMessage();
        this.errorDetail = null;
    }

    /**
     * 使用 ApiCode + 自定义消息构造
     *
     * @param apiCode 错误码枚举
     * @param message 自定义消息
     */
    public ApiException(ApiCode apiCode, String message) {
        super(message);
        this.code = apiCode.getCode();
        this.message = message;
        this.errorDetail = null;
    }

    /**
     * 使用 ApiCode + 错误详情构造
     *
     * @param apiCode     错误码枚举
     * @param errorDetail 错误详情
     */
    public ApiException(ApiCode apiCode, Object errorDetail) {
        super(apiCode.getMessage());
        this.code = apiCode.getCode();
        this.message = apiCode.getMessage();
        this.errorDetail = errorDetail;
    }

    /**
     * 使用 ApiCode + 自定义消息 + 错误详情构造
     *
     * @param apiCode     错误码枚举
     * @param message     自定义消息
     * @param errorDetail 错误详情
     */
    public ApiException(ApiCode apiCode, String message, Object errorDetail) {
        super(message);
        this.code = apiCode.getCode();
        this.message = message;
        this.errorDetail = errorDetail;
    }

    /**
     * 使用自定义错误码和消息构造
     *
     * @param code    错误码
     * @param message 错误消息
     */
    public ApiException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
        this.errorDetail = null;
    }
}
