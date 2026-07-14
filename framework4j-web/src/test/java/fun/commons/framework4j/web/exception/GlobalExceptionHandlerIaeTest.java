package fun.commons.framework4j.web.exception;

import fun.commons.framework4j.api.ApiCode;
import fun.commons.framework4j.web.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link GlobalExceptionHandler#handleIllegalArgumentException(IllegalArgumentException)}
 * 的分流行为（v2.2 修复 GitHub Issue #1 Issue B）。
 * <p>
 * 分流规则：
 * <ul>
 *   <li>{@link NumberFormatException} 或 message 以 {@code "For input string:"} 开头 → 10102</li>
 *   <li>message 以 {@code "Name for argument of type"} 开头 → 10005（编译配置错误）</li>
 *   <li>其它 → 10106（保留 v2.1 行为）</li>
 * </ul>
 */
@DisplayName("IAE 分流处理器测试")
class GlobalExceptionHandlerIaeTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("NumberFormatException → 10102 PARAM_FORMAT_ERROR")
    void numberFormatException_mappedTo10102() {
        ApiResponse<?> resp = handler.handleIllegalArgumentException(
                new NumberFormatException("For input string: \"abc\""));

        assertThat(resp.getCode()).isEqualTo(ApiCode.PARAM_FORMAT_ERROR.getCode());
        assertThat(resp.getMessage()).contains("For input string");
    }

    @Test
    @DisplayName("纯 NumberFormatException 实例 → 10102")
    void numberFormatExceptionInstance_mappedTo10102() {
        ApiResponse<?> resp = handler.handleIllegalArgumentException(
                new NumberFormatException("not a number"));

        assertThat(resp.getCode()).isEqualTo(ApiCode.PARAM_FORMAT_ERROR.getCode());
    }

    @Test
    @DisplayName("message 以 'For input string:' 开头但非 NFE 类型 → 仍 10102")
    void forInputStringPrefix_evenNonNfe_mappedTo10102() {
        // 注：Spring 转换 Long 失败时抛的不是 NFE 而是带 NFE cause 的 MethodArgumentTypeMismatchException
        // 但有时简单 case 直接 IAE with this message
        ApiResponse<?> resp = handler.handleIllegalArgumentException(
                new IllegalArgumentException("For input string: \"xyz\""));

        assertThat(resp.getCode()).isEqualTo(ApiCode.PARAM_FORMAT_ERROR.getCode());
    }

    @Test
    @DisplayName("缺 -parameters 导致的反射失败 message → 10005 MIDDLEWARE_ERROR")
    void missingParameterName_mappedTo10005() {
        ApiResponse<?> resp = handler.handleIllegalArgumentException(
                new IllegalArgumentException(
                        "Name for argument of type [java.lang.Long] not specified, " +
                                "and parameter name information not found in class file either."));

        assertThat(resp.getCode()).isEqualTo(ApiCode.MIDDLEWARE_ERROR.getCode());
        assertThat(resp.getMessage()).contains("服务端编译配置错误");
    }

    @Test
    @DisplayName("真业务校验 IAE → 10106 BUSINESS_RULE_ERROR（保留 v2.1 行为）")
    void businessIAE_mappedTo10106() {
        ApiResponse<?> resp = handler.handleIllegalArgumentException(
                new IllegalArgumentException("订单状态不允许此操作"));

        assertThat(resp.getCode()).isEqualTo(ApiCode.BUSINESS_RULE_ERROR.getCode());
        assertThat(resp.getMessage()).isEqualTo("订单状态不允许此操作");
    }

    @Test
    @DisplayName("null message 的 IAE → 10106（兜底）")
    void nullMessageIAE_fallsBackTo10106() {
        ApiResponse<?> resp = handler.handleIllegalArgumentException(
                new IllegalArgumentException());

        assertThat(resp.getCode()).isEqualTo(ApiCode.BUSINESS_RULE_ERROR.getCode());
    }

    @Test
    @DisplayName("空 message 的 IAE → 10106（兜底）")
    void emptyMessageIAE_fallsBackTo10106() {
        ApiResponse<?> resp = handler.handleIllegalArgumentException(
                new IllegalArgumentException(""));

        assertThat(resp.getCode()).isEqualTo(ApiCode.BUSINESS_RULE_ERROR.getCode());
    }

    @Test
    @DisplayName("HTTP 状态码统一 200（业务异常，符合 mc-api-spec）")
    void httpStatusAlways200() throws NoSuchMethodException {
        HttpStatus status = GlobalExceptionHandler.class
                .getDeclaredMethod("handleIllegalArgumentException", IllegalArgumentException.class)
                .getAnnotation(org.springframework.web.bind.annotation.ResponseStatus.class)
                .value();
        assertThat(status).isEqualTo(HttpStatus.OK);
    }
}
