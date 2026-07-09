package fun.commons.framework4j.accesstoken.exception;

import lombok.Getter;

/**
 * 认证异常基类
 * <p>
 * 继承 RuntimeException，配合全局异常处理器使用。
 * <p>
 * 错误码段位对齐 mc-api-spec v1.6 §7.6（102xx 认证类），默认 10200 UNAUTHORIZED。
 *
 * @since 1.0.0
 */
@Getter
public class AuthException extends RuntimeException {

    /** 默认错误码：10200 UNAUTHORIZED */
    public static final int DEFAULT_CODE = 10200;

    private final int code;

    public AuthException(String message) {
        super(message);
        this.code = DEFAULT_CODE;
    }

    public AuthException(int code, String message) {
        super(message);
        this.code = code;
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
        this.code = DEFAULT_CODE;
    }

    public AuthException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
