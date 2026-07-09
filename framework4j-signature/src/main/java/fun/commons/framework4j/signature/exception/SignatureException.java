package fun.commons.framework4j.signature.exception;

import fun.commons.framework4j.api.ApiCode;
import fun.commons.framework4j.web.ApiException;

/**
 * 签名校验异常
 *
 * @since 2.1.0
 */
public class SignatureException extends ApiException {

    public SignatureException(ApiCode apiCode) {
        super(apiCode);
    }

    public SignatureException(ApiCode apiCode, String message) {
        super(apiCode, message);
    }
}
