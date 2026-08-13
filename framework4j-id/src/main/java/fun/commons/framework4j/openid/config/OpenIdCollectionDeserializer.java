package fun.commons.framework4j.openid.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import fun.commons.framework4j.openid.util.OpenIdValueCodec;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * {@code @OpenId} 集合/数组字段反序列化器。
 * <p>
 * 受理元素类型由 {@code OpenIdTypeSupport} 开关决定(Long / Integer / String),容器覆盖:
 * {@code List}→{@link ArrayList}、{@code Set}→{@link LinkedHashSet}(保序去重)、
 * {@code Queue}→{@link ArrayDeque}、数组→对应 {@code Long[]/long[]/Integer[]/int[]/String[]}。
 * <p>
 * 逐元素以 Long 为枢轴:token → {@link OpenIdValueCodec#decodeTokenToLong} →
 * {@link OpenIdValueCodec#convertLongToTarget} 转成元素目标类型。null 元素保留(List/Set),
 * 基本类型数组({@code long[]}/{@code int[]})的 null 元素以 0 填充。
 *
 * @since 1.3.0
 */
public class OpenIdCollectionDeserializer extends JsonDeserializer<Object> {

    private final JavaType type;
    private final Class<?> elementTarget;
    private final boolean acceptNumericFallback;

    public OpenIdCollectionDeserializer(JavaType type, Class<?> elementTarget, boolean acceptNumericFallback) {
        this.type = type;
        this.elementTarget = elementTarget;
        this.acceptNumericFallback = acceptNumericFallback;
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken tok = p.currentToken();
        if (tok == null || tok == JsonToken.VALUE_NULL) {
            return null;
        }
        if (tok != JsonToken.START_ARRAY) {
            throw MismatchedInputException.from(p, type.getRawClass(), "期望 JSON 数组,实际 token: " + tok);
        }

        List<Object> buffer = new ArrayList<>();
        JsonToken t;
        while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
            if (t == JsonToken.VALUE_NULL) {
                buffer.add(null);
                continue;
            }
            Long pivoted = OpenIdValueCodec.decodeTokenToLong(p, ctxt, acceptNumericFallback);
            buffer.add(pivoted == null ? null : convertElement(pivoted, p));
        }

        if (type.isArrayType()) {
            return toArray(buffer);
        }
        Class<?> raw = type.getRawClass();
        if (Set.class.isAssignableFrom(raw)) {
            return new LinkedHashSet<>(buffer);
        }
        if (Queue.class.isAssignableFrom(raw)) {
            return new ArrayDeque<>(buffer);
        }
        return new ArrayList<>(buffer);
    }

    private Object convertElement(long pivoted, JsonParser p) throws IOException {
        try {
            return OpenIdValueCodec.convertLongToTarget(pivoted, elementTarget);
        } catch (ArithmeticException | IllegalArgumentException e) {
            throw MismatchedInputException.from(p, elementTarget, e.getMessage());
        }
    }

    private Object toArray(List<Object> buffer) {
        Class<?> component = type.getRawClass().getComponentType();
        Object arr = Array.newInstance(component, buffer.size());
        for (int i = 0; i < buffer.size(); i++) {
            Object v = buffer.get(i);
            if (component == long.class) {
                Array.setLong(arr, i, v == null ? 0L : (Long) v);
            } else if (component == int.class) {
                Array.setInt(arr, i, v == null ? 0 : ((Number) v).intValue());
            } else {
                Array.set(arr, i, v);
            }
        }
        return arr;
    }
}
