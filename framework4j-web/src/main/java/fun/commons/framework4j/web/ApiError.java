package fun.commons.framework4j.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 字段级错误详情（用于参数校验失败时返回 error 数组）
 * <p>
 * 对齐 mc-api-spec v1.6 §4 信封规范：失败响应的 error 字段为 {@code List<ApiError>}，
 * 每项含 field / code / message / rejectedValue。
 *
 * <p>{@code rejectedValue} 序列化时自动截断到 100 字符，防大对象 / 敏感信息泄露。
 *
 * @since 2.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    String field,
    String code,
    String message,
    @JsonProperty("rejectedValue") Object rejectedValue
) {

    /** rejectedValue 序列化时的最大长度（超出截断 + "..."） */
    private static final int REJECTED_VALUE_MAX_LEN = 100;

    public static ApiError of(String field, String message) {
        return new ApiError(field, null, message, null);
    }

    public static ApiError of(String field, String code, String message) {
        return new ApiError(field, code, message, null);
    }

    public static ApiError of(String field, String code, String message, Object rejectedValue) {
        return new ApiError(field, code, message, sanitizeRejectedValue(rejectedValue));
    }

    /**
     * 截断 rejectedValue 到 100 字符。null / 短字符串原样返回。
     */
    private static Object sanitizeRejectedValue(Object value) {
        if (value == null) return null;
        if (value instanceof CharSequence cs) {
            String s = cs.toString();
            return s.length() > REJECTED_VALUE_MAX_LEN ? s.substring(0, REJECTED_VALUE_MAX_LEN) + "..." : s;
        }
        // 非 String 类型（Number / Boolean / 集合）原样返回，Jackson 序列化时处理
        return value;
    }
}
