package fun.commons.framework4j.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 全局异常处理器
 * <p>
 * 捕获所有 Controller 层抛出的异常，统一转换为标准的 API 响应格式。
 * <p>
 * <b>重要规范</b>: 所有异常都返回 HTTP 200 OK，错误信息在响应体中。
 *
 * @author LDX2T
 * @since 1.0.1
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(ApiException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleApiException(ApiException e) {
        log.warn("[业务异常] code={}, message={}", e.getCode(), e.getMessage());
        if (e.getErrorDetail() != null) {
            return ApiResponse.fail(e.getCode(), e.getMessage(), e.getErrorDetail());
        }
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常 (@Valid / @RequestBody)
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleValidationException(Exception e) {
        BindingResult bindingResult;
        if (e instanceof MethodArgumentNotValidException) {
            bindingResult = ((MethodArgumentNotValidException) e).getBindingResult();
        } else {
            bindingResult = ((BindException) e).getBindingResult();
        }

        List<Map<String, String>> errorDetails = new ArrayList<>();
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            Map<String, String> detail = new HashMap<>();
            detail.put("field", fieldError.getField());
            detail.put("message", fieldError.getDefaultMessage());
            detail.put("rejectedValue", String.valueOf(fieldError.getRejectedValue()));
            errorDetails.add(detail);
        }

        log.warn("[参数校验异常] {}", errorDetails);
        return ApiResponse.fail(ApiCode.PARAM_ERROR, errorDetails);
    }

    /**
     * 处理 @Validated 约束违反异常 (Path/Query 参数校验)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleConstraintViolationException(ConstraintViolationException e) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        List<Map<String, String>> errorDetails = new ArrayList<>();

        for (ConstraintViolation<?> violation : violations) {
            Map<String, String> detail = new HashMap<>();
            detail.put("field", violation.getPropertyPath().toString());
            detail.put("message", violation.getMessage());
            detail.put("rejectedValue", String.valueOf(violation.getInvalidValue()));
            errorDetails.add(detail);
        }

        log.warn("[约束违反异常] {}", errorDetails);
        return ApiResponse.fail(ApiCode.PARAM_ERROR, errorDetails);
    }

    /**
     * 处理缺少请求参数异常 (@RequestParam 必填项缺失)
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        String message = String.format("缺少必要参数: [%s]", e.getParameterName());
        log.warn("[参数缺失] {}", message);
        return ApiResponse.fail(ApiCode.PARAM_ERROR, message);
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String message = String.format("参数 [%s] 类型不匹配，需要类型: %s", e.getName(), e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知");
        log.warn("[参数类型异常] {}", message);
        return ApiResponse.fail(ApiCode.PARAM_FORMAT_ERROR, message);
    }

    /**
     * 处理请求体格式错误 (JSON 解析失败)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.error("[请求体格式错误]", e);
        return ApiResponse.fail(ApiCode.BODY_FORMAT_ERROR, "请求体格式错误或缺失");
    }

    /**
     * 处理请求方法不支持异常 (405)
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("[请求方法不支持] {}", e.getMessage());
        return ApiResponse.fail(ApiCode.METHOD_NOT_SUPPORTED, "支持的方法: " + e.getSupportedHttpMethods());
    }

    /**
     * 处理媒体类型不支持异常 (415)
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        log.warn("[媒体类型不支持] {}", e.getMessage());
        return ApiResponse.fail(ApiCode.MEDIA_TYPE_NOT_SUPPORTED, "不支持的媒体类型: " + e.getContentType());
    }

    /**
     * 处理 Spring Boot 3.2+ 资源不存在异常 (404)
     * <p>替换了部分场景下的 NoHandlerFoundException</p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("[资源不存在] {} {}", e.getHttpMethod(), e.getResourcePath());
        return ApiResponse.fail(ApiCode.RESOURCE_NOT_FOUND);
    }

    /**
     * 处理旧版 404 异常 (需要配置 throw-exception-if-no-handler-found=true)
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.warn("[资源不存在] {} {}", e.getHttpMethod(), e.getRequestURL());
        return ApiResponse.fail(ApiCode.RESOURCE_NOT_FOUND);
    }

    /**
     * 处理数据库唯一键冲突 (Duplicate Key)
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("[数据库唯一键冲突] {}", e.getMessage());
        // 通常意味着插入了重复数据
        return ApiResponse.fail(ApiCode.BUSINESS_RULE_ERROR, "数据已存在，请勿重复提交");
    }

    /**
     * 处理数据库 SQL 语法错误
     */
    @ExceptionHandler(BadSqlGrammarException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleBadSqlGrammarException(BadSqlGrammarException e) {
        log.error("[数据库SQL语法错误]", e);
        return ApiResponse.fail(ApiCode.SYSTEM_BUSY, "数据库操作异常");
    }

    /**
     * 处理数据库数据完整性异常 (如字段过长、非空约束等)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.error("[数据库数据完整性异常]", e);
        return ApiResponse.fail(ApiCode.BUSINESS_RULE_ERROR, "数据操作失败，请检查数据约束");
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("[非法参数异常] {}", e.getMessage());
        return ApiResponse.fail(ApiCode.BUSINESS_RULE_ERROR, e.getMessage());
    }

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleNullPointerException(NullPointerException e) {
        log.error("[空指针异常]", e);
        return ApiResponse.fail(ApiCode.SYSTEM_BUSY, "空指针异常，可能是未初始化的对象");
    }

    /**
     * 处理所有未捕获的异常（兜底）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleException(Exception e) {
        // [调试专用] 打印具体的异常类名，用于排查为什么异常没有被前面的 Handler 捕获
        log.error("[系统异常] 兜底捕获，实际异常类型: {}", e.getClass().getName());
        log.error("[系统异常] 详细堆栈:", e);
        return ApiResponse.fail(ApiCode.SYSTEM_BUSY, "系统繁忙，请稍后重试 " + e.getMessage());
    }
}