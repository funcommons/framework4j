package fun.commons.framework4j.openid.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import fun.commons.framework4j.openid.util.OpenIdLongCodec;

import java.io.IOException;

/**
 * {@code @OpenId} 标量字段序列化器(Integer / String,由 {@code OpenIdTypeSupport} 开关决定受理)。
 * <p>
 * 通过 {@link OpenIdLongCodec#encodeToOpenId} 以 Long 为枢轴:Integer → {@code longValue} → 混淆串;
 * String → {@code Long.parseLong}(strict,非数字串抛错)→ 混淆串。
 * <p>
 * Long 标量仍走 {@link OpenIdAutoConfiguration.OpenIdJsonSerializer}(既有、文档化),本类只管 Integer/String。
 *
 * @since 1.3.0
 */
public class OpenIdValueJsonSerializer extends JsonSerializer<Object> {

    public static final OpenIdValueJsonSerializer INSTANCE = new OpenIdValueJsonSerializer();

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(OpenIdLongCodec.encodeToOpenId(value));
    }
}
