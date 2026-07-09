package fun.commons.framework4j.signature.interceptor;

import fun.commons.framework4j.api.ApiCode;
import fun.commons.framework4j.signature.config.SignatureProperties;
import fun.commons.framework4j.signature.exception.SignatureException;
import fun.commons.framework4j.signature.service.SignatureService;
import fun.commons.framework4j.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 签名校验拦截器
 *
 * @since 2.1.0
 */
@Slf4j
public class SignatureInterceptor implements HandlerInterceptor {

    private final SignatureService signatureService;
    private final SignatureProperties properties;

    public SignatureInterceptor(SignatureService signatureService, SignatureProperties properties) {
        this.signatureService = signatureService;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        try {
            signatureService.validate(request);
            return true;
        } catch (SignatureException e) {
            writeJson(response, e);
            return false;
        }
    }

    private void writeJson(HttpServletResponse response, SignatureException e) throws IOException {
        String message = e.getMessage() != null ? e.getMessage() : "签名校验失败";
        ApiResponse<?> body = ApiResponse.fail(e.getCode(), message);
        // 签名错误统一返 HTTP 401（鉴权失败），业务码在信封 code 中
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(toJson(body));
    }

    private String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception ex) {
            return "{\"code\":" + ApiCode.SYSTEM_BUSY.getCode() + ",\"message\":\"序列化失败\"}";
        }
    }
}
