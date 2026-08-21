package fun.commons.framework4j.openid.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import fun.commons.framework4j.openid.util.OpenIdLongCodec;

import java.io.IOException;

/**
 * {@code @OpenId} 标量字段反序列化器(Long / Integer / String,由 {@code OpenIdTypeSupport} 开关决定受理范围)。
 * <p>
 * 以 Long 为枢轴:Jackson token → {@link OpenIdLongCodec#decodeTokenToLong} →
 * {@link OpenIdLongCodec#convertLongToTarget} 转成目标标量。
 * <ul>
 *   <li>Long/long → 直接返回 Long</li>
 *   <li>Integer/int → {@code Math.toIntExact}(溢出抛错,防恶意大值静默截断)</li>
 *   <li>String → {@code Long.toString}(字段持数字串,业务 {@code Long.parseLong} 取值)</li>
 * </ul>
 * 仅作用于标注 {@code @OpenId} 的字段(由 modifier 按属性挂载),不全局接管。
 *
 * @since 1.3.0
 */
public class OpenIdScalarDeserializer extends JsonDeserializer<Object> {

    private final Class<?> target;
    private final boolean acceptNumericFallback;

    public OpenIdScalarDeserializer(Class<?> target, boolean acceptNumericFallback) {
        this.target = target;
        this.acceptNumericFallback = acceptNumericFallback;
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        Long pivoted = OpenIdLongCodec.decodeTokenToLong(p, ctxt, acceptNumericFallback);
        if (pivoted == null) {
            return null;
        }
        try {
            return OpenIdLongCodec.convertLongToTarget(pivoted, target);
        } catch (ArithmeticException | IllegalArgumentException e) {
            // Integer 溢出等 → 包成 MismatchedInputException(→ GlobalExceptionHandler → BODY_FORMAT_ERROR)
            throw MismatchedInputException.from(p, target, e.getMessage());
        }
    }
}
