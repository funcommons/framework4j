package fun.commons.framework4j.openid.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import fun.commons.framework4j.openid.util.OpenIdValueCodec;

import java.io.IOException;
import java.lang.reflect.Array;

/**
 * {@code @OpenId} 集合/数组字段序列化器。
 * <p>
 * 元素类型由 {@code OpenIdTypeSupport} 开关决定(Long / Integer / String);逐元素经
 * {@link OpenIdValueCodec#encodeToOpenId} 以 Long 为枢轴混淆(Number → longValue;String → parseLong),
 * {@code null} 元素输出 JSON null。覆盖 {@code Collection} 与数组(含 {@code long[]}/{@code int[]})。
 *
 * @since 1.3.0
 */
public class OpenIdCollectionJsonSerializer extends JsonSerializer<Object> {

    public static final OpenIdCollectionJsonSerializer INSTANCE = new OpenIdCollectionJsonSerializer();

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeStartArray();
        if (value.getClass().isArray()) {
            int n = Array.getLength(value);
            for (int i = 0; i < n; i++) {
                writeElement(Array.get(value, i), gen);
            }
        } else if (value instanceof Iterable<?>) {
            for (Object element : (Iterable<?>) value) {
                writeElement(element, gen);
            }
        } else {
            throw new IllegalArgumentException("@OpenId 集合序列化器遇到非集合/数组类型: " + value.getClass());
        }
        gen.writeEndArray();
    }

    private static void writeElement(Object element, JsonGenerator gen) throws IOException {
        if (element == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(OpenIdValueCodec.encodeToOpenId(element));
    }
}
