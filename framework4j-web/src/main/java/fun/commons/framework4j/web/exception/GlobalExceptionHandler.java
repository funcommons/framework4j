package fun.commons.framework4j.web.exception;

import fun.commons.framework4j.api.ApiCode;
import fun.commons.framework4j.web.ApiError;
import fun.commons.framework4j.web.ApiException;
import fun.commons.framework4j.web.ApiResponse;
import fun.commons.framework4j.web.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import org.springframework.web.bind.MissingRequestHeaderException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 全局异常处理器
 * <p>
 * 捕获所有 Controller 层抛出的异常，统一转换为标准的 API 响应格式。
 * <p>
 * <b>HTTP 状态码策略</b>（对齐 mc-api-spec §4）：
 * <ul>
 *   <li>业务异常（ApiException / 参数校验 / DB 错误）→ HTTP 200，错误信息在响应体 code 字段</li>
 *   <li>路由层异常（404 / 405 / 415 / 413 / 503）→ 保留原 HTTP 状态码，body 仍带信封</li>
 * </ul>
 *
 * @since 1.0.1
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnProperty(
        prefix = "framework4j.api.exception-handler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(ApiException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleApiException(ApiException e) {
        log.warn("[业务异常] code={}, message={}", e.getCode(), e.getMessage());
        Object detail = e.getErrorDetail();
        if (detail == null) {
            return ApiResponse.fail(e.getCode(), e.getMessage());
        }
        List<ApiError> errors = toApiErrors(detail);
        return ApiResponse.fail(e.getCode(), e.getMessage(), errors);
    }

    /**
     * 把 ApiException.errorDetail（Object）安全转为 List&lt;ApiError&gt;。
     * 支持的输入类型：
     * <ul>
     *   <li>List&lt;ApiError&gt; — 直接用</li>
     *   <li>List&lt;?&gt; — 过滤非 ApiError 元素</li>
     *   <li>其他 — 包装成单元素 List</li>
     * </ul>
     */
    private static List<ApiError> toApiErrors(Object detail) {
        if (detail instanceof List<?> list) {
            if (list.isEmpty()) {
                return List.of();
            }
            // 检查首元素类型：是 ApiError 则直接强转，否则转换
            if (list.get(0) instanceof ApiError) {
                @SuppressWarnings("unchecked")
                List<ApiError> casted = (List<ApiError>) list;
                return casted;
            }
            return list.stream()
                    .filter(o -> o instanceof ApiError)
                    .map(o -> (ApiError) o)
                    .toList();
        }
        return List.of(ApiError.of(null, null, String.valueOf(detail)));
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

        List<ApiError> errors = bindingResult.getFieldErrors().stream()
                .map(fe -> ApiError.of(fe.getField(), determineErrorCode(fe), fe.getDefaultMessage(), fe.getRejectedValue()))
                .toList();

        log.warn("[参数校验异常] {}", errors);
        return ApiResponse.fail(ApiCode.PARAM_ERROR, errors);
    }

    /**
     * 处理 @Validated 约束违反异常 (Path/Query 参数校验)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleConstraintViolationException(ConstraintViolationException e) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();

        List<ApiError> errors = violations.stream()
                .map(v -> ApiError.of(v.getPropertyPath().toString(), determineErrorCode(v), v.getMessage(), v.getInvalidValue()))
                .toList();

        log.warn("[约束违反异常] {}", errors);
        return ApiResponse.fail(ApiCode.PARAM_ERROR, errors);
    }

    /**
     * v2.1: 子错误码常量表（原两个 determineErrorCode 重载 switch 重复，抽 Map 统一）。
     * key = jakarta validation 注解名（FieldError.getCode() 或 ConstraintDescriptor.annotationType().getSimpleName()）
     */
    private static final Map<String, String> VALIDATION_ERROR_CODE_MAP = Map.ofEntries(
            Map.entry("NotBlank", "REQUIRED_MISSING"),
            Map.entry("NotNull", "REQUIRED_MISSING"),
            Map.entry("NotEmpty", "REQUIRED_MISSING"),
            Map.entry("Email", "FORMAT_INVALID"),
            Map.entry("Pattern", "FORMAT_INVALID"),
            Map.entry("Min", "OUT_OF_RANGE"),
            Map.entry("Max", "OUT_OF_RANGE"),
            Map.entry("Size", "OUT_OF_RANGE"),
            Map.entry("DecimalMin", "OUT_OF_RANGE"),
            Map.entry("DecimalMax", "OUT_OF_RANGE"),
            Map.entry("Positive", "OUT_OF_RANGE"),
            Map.entry("PositiveOrZero", "OUT_OF_RANGE"),
            Map.entry("Negative", "OUT_OF_RANGE"),
            Map.entry("NegativeOrZero", "OUT_OF_RANGE")
    );

    private static String resolveValidationErrorCode(String annotationName) {
        if (annotationName == null) return "INVALID";
        return VALIDATION_ERROR_CODE_MAP.getOrDefault(annotationName, "INVALID");
    }

    private static String determineErrorCode(FieldError fe) {
        return resolveValidationErrorCode(fe.getCode());
    }

    /**
     * 根据 ConstraintViolation 映射子错误码（Path/Query 参数校验）。
     */
    private static String determineErrorCode(ConstraintViolation<?> v) {
        jakarta.validation.metadata.ConstraintDescriptor<?> desc = v.getConstraintDescriptor();
        if (desc == null) return "INVALID";
        return resolveValidationErrorCode(desc.getAnnotation().annotationType().getSimpleName());
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
     * 处理缺少请求头异常 (@RequestHeader 必填项缺失)
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleMissingRequestHeaderException(MissingRequestHeaderException e) {
        String message = String.format("缺少必要请求头: [%s]", e.getHeaderName());
        log.warn("[请求头缺失] {}", message);
        return ApiResponse.fail(ApiCode.PARAM_MISSING, message);
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
     * <p>对齐 mc-api-spec §4：路由层异常保留原 HTTP 状态码，不走 200 信封。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<?> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("[请求方法不支持] {}", e.getMessage());
        return ApiResponse.fail(ApiCode.METHOD_NOT_SUPPORTED, "支持的方法: " + e.getSupportedHttpMethods());
    }

    /**
     * 处理媒体类型不支持异常 (415)
     * <p>对齐 mc-api-spec §4：路由层异常保留原 HTTP 状态码。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ApiResponse<?> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        log.warn("[媒体类型不支持] {}", e.getMessage());
        return ApiResponse.fail(ApiCode.MEDIA_TYPE_NOT_SUPPORTED, "不支持的媒体类型: " + e.getContentType());
    }

    /**
     * 处理 Spring Boot 3.2+ 资源不存在异常 (404)
     * <p>对齐 mc-api-spec §4：路由层异常保留原 HTTP 状态码。
     * <p>替换了部分场景下的 NoHandlerFoundException</p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<?> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("[资源不存在] {} {}", e.getHttpMethod(), e.getResourcePath());
        return ApiResponse.fail(ApiCode.NOT_FOUND);
    }

    /**
     * 处理旧版 404 异常 (需要配置 throw-exception-if-no-handler-found=true)
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<?> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.warn("[资源不存在] {} {}", e.getHttpMethod(), e.getRequestURL());
        return ApiResponse.fail(ApiCode.NOT_FOUND);
    }

    /**
     * 处理文件上传超限异常 (413 Payload Too Large)
     */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiResponse<?> handleMaxUploadSizeExceededException(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        log.warn("[文件超限] {}", e.getMessage());
        return ApiResponse.fail(ApiCode.FILE_SIZE_EXCEED, "文件大小超过限制");
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
     * <p>
     * v2.2：按消息特征分流，避免所有 IAE 都包成 10106 BUSINESS_RULE_ERROR（语义模糊）：
     * <ul>
     *   <li>{@link NumberFormatException} 或消息以 {@code "For input string:"} 开头 → 10102 参数格式错误</li>
     *   <li>消息以 {@code "Name for argument of type"} 开头（反射读不到 parameter name）→
     *       10005 中间件错误（编译配置问题，非业务错误），log.error 并提示加 {@code -parameters}</li>
     *   <li>其它 → 10106 业务规则校验失败（保留 v2.1 行为）</li>
     * </ul>
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleIllegalArgumentException(IllegalArgumentException e) {
        String message = e.getMessage();
        String safeMessage = message == null ? "" : message;

        if (e instanceof NumberFormatException
                || safeMessage.startsWith("For input string:")) {
            log.warn("[参数格式错误] {}", message);
            return ApiResponse.fail(ApiCode.PARAM_FORMAT_ERROR, message);
        }

        if (safeMessage.startsWith("Name for argument of type")) {
            log.error("[编译配置错误] 缺 -parameters 编译选项，导致反射读不到 parameter name: {}", message, e);
            return ApiResponse.fail(ApiCode.MIDDLEWARE_ERROR,
                    "服务端编译配置错误（缺少 -parameters 编译选项），请联系管理员");
        }

        log.warn("[业务规则校验失败] {}", message);
        return ApiResponse.fail(ApiCode.BUSINESS_RULE_ERROR, message);
    }

    /**
     * 处理响应序列化失败异常（如循环引用）。
     * <p>此时响应可能已部分写出，无法再返回 JSON 信封，只记日志。
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotWritableException.class)
    public void onNotWritable(org.springframework.http.converter.HttpMessageNotWritableException e) {
        log.error("[响应序列化失败] 响应可能已部分写出", e);
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
     * <p>v2.1 P1 修复：未知系统异常（NPE/OutOfMemoryError 等）返回 HTTP 500，
     * 让监控系统从 HTTP 状态码维度看到 5xx 告警。业务异常仍走 HTTP 200 + 信封 code。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleException(Exception e) {
        log.error("[系统异常] 兜底捕获，实际异常类型: {}", e.getClass().getName());
        log.error("[系统异常] 详细堆栈:", e);
        return ApiResponse.fail(ApiCode.SYSTEM_BUSY, "系统繁忙，请稍后重试");
    }
}