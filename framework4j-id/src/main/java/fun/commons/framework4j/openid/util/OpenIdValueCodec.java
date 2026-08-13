package fun.commons.framework4j.openid.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import fun.commons.framework4j.id.util.IdObfuscator;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * OpenId 值解码/编码的统一枢轴(v1.3 三开关后的核心)。
 * <p>
 * 一切 {@code @OpenId} 处理都以 <b>Long 为枢轴</b>双向转换:
 * <ul>
 *   <li>解码(入参):文本/Jackson token → Long(枢轴)→ 目标类型(Long/Integer/String)</li>
 *   <li>编码(出参):Java 值(Long/Integer/String)→ Long(枢轴)→ 混淆串</li>
 * </ul>
 * 类型判定(哪些类型受理)与开关读法见 {@link OpenIdTypeSupport};本类只管与类型无关的纯转换逻辑。
 * 放在 util ���供 config(Jackson modifier)/formatter/web 三层共用,避免包循环依赖。
 * <p>
 * 解码接受规则({@code acceptNumericFallback}):
 * <ul>
 *   <li>合法 OpenId 混淆串(含 {@code PREFIX_xxx}) → 永远接受,反混淆</li>
 *   <li>纯数字 / 数字串 → 仅当 {@code acceptNumericFallback=true}(默认)时透传;否则拒绝(strict 收口)</li>
 *   <li>其它 → 拒绝(抛异常)</li>
 * </ul>
 *
 * @since 1.3.0
 */
public final class OpenIdValueCodec {

    private OpenIdValueCodec() {
    }

    private static final Pattern NUMERIC = Pattern.compile("^-?\\d+$");

    // ==================== 解码:文本 → Long(枢轴) ====================

    /**
     * 文本 → Long。Formatter(path/query)与 Jackson 字符串分支共用。
     * <p>返回 {@code null} 表示空;抛 {@link IllegalArgumentException} 表示不可解码(由调用方包成各自异常)。
     */
    public static Long decodeTextToLong(String text, boolean acceptNumericFallback) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (IdObfuscator.isValid(text)) {
            return IdObfuscator.fromOpenId(text);
        }
        if (NUMERIC.matcher(text).matches()) {
            if (!acceptNumericFallback) {
                throw new IllegalArgumentException("strict 模式(accept-numeric-fallback=false)拒绝纯数字: " + text);
            }
            long value = Long.parseLong(text);
            if (value < 0) {
                throw new IllegalArgumentException("ID cannot be negative: " + text);
            }
            return value;
        }
        throw new IllegalArgumentException("既非合法 OpenID 混淆串也非数字: " + text);
    }

    /**
     * Jackson token → Long。空/null → null;numeric 受 fallback 控制;字符串走 {@link #decodeTextToLong}。
     * <p>不可解码时抛 {@link MismatchedInputException}(→ GlobalExceptionHandler → BODY_FORMAT_ERROR)。
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
                        "strict 模式(accept-numeric-fallback=false)拒绝纯数字");
            }
            long value = p.getValueAsLong();
            if (value < 0) {
                throw MismatchedInputException.from(p, Long.class, "ID cannot be negative: " + value);
            }
            return value;
        }
        if (tok == JsonToken.VALUE_STRING) {
            String s = p.getValueAsString();
            try {
                return decodeTextToLong(s, acceptNumericFallback);
            } catch (IllegalArgumentException e) {
                throw ctxt.weirdStringException(s, Long.class, e.getMessage());
            }
        }
        throw MismatchedInputException.from(p, Long.class, "期望 OpenID 串或数字,实际 token: " + tok);
    }

    // ==================== Long(枢轴) → 目标标量 ====================

    /**
     * 枢轴 Long 转成目标标量类型。Integer 溢出抛 {@link ArithmeticException}(调用方按需包异常)。
     */
    public static Object convertLongToTarget(long value, Class<?> target) {
        if (target == Long.class || target == long.class) {
            return value;
        }
        if (target == Integer.class || target == int.class) {
            return Math.toIntExact(value);
        }
        if (target == String.class) {
            return Long.toString(value);
        }
        throw new IllegalArgumentException("不支持的 @OpenId 目标类型: " + target);
    }

    // ==================== 编码:Java 值 → 混淆串 ====================

    /**
     * 任意受支持值(Long/Integer/String)→ 混淆串。String 必须是数字串(strict),否则抛 {@link IllegalArgumentException}。
     */
    public static String encodeToOpenId(Object value) {
        if (value == null) {
            return null;
        }
        long pivoted;
        if (value instanceof Number) {
            pivoted = ((Number) value).longValue();
        } else if (value instanceof String) {
            try {
                pivoted = Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("@OpenId String 字段持非数字串(无法转 Long): " + value, e);
            }
        } else {
            throw new IllegalArgumentException("@OpenId 序列化遇到非 Number/String 类型: " + value.getClass());
        }
        if (pivoted < 0) {
            throw new IllegalArgumentException("ID cannot be negative: " + pivoted);
        }
        return IdObfuscator.toOpenId(pivoted);
    }
}
