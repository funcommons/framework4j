package fun.commons.framework4j.openid.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import fun.commons.framework4j.id.util.IdObfuscator;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * OpenId 值的 Long 枢轴编解码（v1.3 三开关后的唯一值转换层）。
 * <p>
 * 所有 {@code @OpenId} 通道（Jackson 序列化/反序列化、path/query 入参）的值转换
 * 都以 <b>Long 为枢轴</b>：
 * <ul>
 *   <li>解码：文本/Jackson token → Long（枢轴）→ 由调用方经 {@link #convertLongToTarget} 转目标标量</li>
 *   <li>编码：Java 值（Number/String）→ Long（枢轴）→ 混淆串</li>
 * </ul>
 * 受理哪些标量类型、是否接受纯数字，由 {@link OpenIdTypeSupport} 决定，本类不含开关逻辑。
 *
 * @since 1.3.0
 */
public final class OpenIdLongCodec {

    private OpenIdLongCodec() {
    }

    // v2.1 沿袭：正则预编译（允许负号进入解析，负数由 validate 拒绝并给出明确消息）
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+$");

    // ==================== 解码：文本 → Long（枢轴） ====================

    /**
     * 文本解码为 Long 枢轴。path/query 入参与 Jackson 字符串分支共用。
     *
     * @param text                  原始文本
     * @param acceptNumericFallback 是否接受纯数字（兼容期开关）
     * @return 解码出的 Long；空文本返回 {@code null}
     * @throws IllegalArgumentException 非法格式 / strict 模式收到纯数字 / 负数
     */
    public static Long decodeTextToLong(String text, boolean acceptNumericFallback) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        // 1) 合法 OpenId 混淆串（含 PREFIX_ 前缀）→ 反混淆
        if (IdObfuscator.isValid(text)) {
            return IdObfuscator.fromOpenId(text);
        }
        // 2) 纯数字 → 兼容期透传
        if (NUMERIC_PATTERN.matcher(text).matches()) {
            if (!acceptNumericFallback) {
                throw new IllegalArgumentException(
                        "strict 模式（accept-numeric-fallback=false）拒绝纯数字: " + text);
            }
            long value = Long.parseLong(text);
            validateNonNegative(value);
            return value;
        }
        throw new IllegalArgumentException("Invalid OpenID format: " + text);
    }

    /**
     * Jackson token 解码为 Long 枢轴。反序列化标量与集合元素共用。
     * <p>不可解码时抛 {@link MismatchedInputException}（→ GlobalExceptionHandler → BODY_FORMAT_ERROR）。
     */
    public static Long decodeTokenToLong(JsonParser p, DeserializationContext ctxt,
                                   boolean acceptNumericFallback) throws IOException {
        JsonToken tok = p.currentToken();
        if (tok == null || tok == JsonToken.VALUE_NULL) {
            return null;
        }
        if (tok.isNumeric()) {
            if (!acceptNumericFallback) {
                throw MismatchedInputException.from(p, Long.class,
                        "strict 模式（accept-numeric-fallback=false）拒绝纯数字");
            }
            long value = p.getValueAsLong();
            if (value < 0) {
                throw MismatchedInputException.from(p, Long.class, "ID cannot be negative: " + value);
            }
            return value;
        }
        if (tok == JsonToken.VALUE_STRING) {
            String text = p.getValueAsString();
            try {
                return decodeTextToLong(text, acceptNumericFallback);
            } catch (IllegalArgumentException e) {
                throw ctxt.weirdStringException(text, Long.class, e.getMessage());
            }
        }
        // 标量位置出现 object/array 等非预期 token → 交给 Jackson 标准路径
        @SuppressWarnings("unchecked")
        Long fallback = (Long) ctxt.handleUnexpectedToken(Long.class, p);
        return fallback;
    }

    // ==================== Long（枢轴）→ 目标标量 ====================

    /**
     * 枢轴 Long 转目标标量。
     * <ul>
     *   <li>{@code Long/long} → 原值</li>
     *   <li>{@code Integer/int} → {@link Math#toIntExact}（溢出抛 {@link ArithmeticException}，防恶意大值静默截断）</li>
     *   <li>{@code String} → 数字串（字段持解码后的明文，线上是混淆串）</li>
     * </ul>
     */
    public static Object convertLongToTarget(long value, Class<?> target) {
        if (target == Integer.class || target == int.class) {
            return Math.toIntExact(value);
        }
        if (target == String.class) {
            return Long.toString(value);
        }
        return value; // Long / long
    }

    // ==================== 编码：Java 值 → 混淆串 ====================

    /**
     * 受支持值（Long/Integer/String 等 Number 或数字串）→ 混淆串。
     * String 必须持数字串（strict），否则抛 {@link IllegalArgumentException}。
     */
    public static String encodeToOpenId(Object value) {
        if (value == null) {
            return null;
        }
        long pivoted;
        if (value instanceof Number number) {
            pivoted = number.longValue();
        } else if (value instanceof String s) {
            try {
                pivoted = Long.parseLong(s);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("@OpenId String 字段必须持数字串（无法转 Long）: " + value, e);
            }
        } else {
            throw new IllegalArgumentException("@OpenId 编码遇到非 Number/String 类型: " + value.getClass());
        }
        validateNonNegative(pivoted); // 与解码路径一致的错误消息（"ID cannot be negative"）
        return IdObfuscator.toOpenId(pivoted);
    }

    private static void validateNonNegative(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("ID cannot be negative: " + value);
        }
    }
}
