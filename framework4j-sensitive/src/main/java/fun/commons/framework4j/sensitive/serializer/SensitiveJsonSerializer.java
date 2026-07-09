package fun.commons.framework4j.sensitive.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import fun.commons.framework4j.sensitive.annotation.Sensitive;
import fun.commons.framework4j.sensitive.annotation.SensitiveRule;
import fun.commons.framework4j.sensitive.util.SensitiveUtils;

import java.io.IOException;

/**
 * Jackson 脱敏序列化器
 * <p>
 * 配合 {@link Sensitive} 注解，序列化时自动按规则脱敏。
 *
 * @since 2.1.0
 */
public class SensitiveJsonSerializer extends JsonSerializer<String> implements ContextualSerializer {

    private SensitiveRule rule;
    private String pattern;

    public SensitiveJsonSerializer() {}

    public SensitiveJsonSerializer(SensitiveRule rule) {
        this.rule = rule;
    }

    public SensitiveJsonSerializer(SensitiveRule rule, String pattern) {
        this.rule = rule;
        this.pattern = pattern;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (rule == null) {
            gen.writeString(value);
            return;
        }
        gen.writeString(SensitiveUtils.desensitize(value, rule, pattern));
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) {
        // v2.1 P1: property 可能为 null（root value 序列化场景），判空防 NPE
        if (property == null) {
            return new JsonSerializer<String>() {
                @Override
                public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                    gen.writeString(value);  // 无规则 → 原样输出
                }
            };
        }
        Sensitive annotation = property.getAnnotation(Sensitive.class);
        if (annotation != null) {
            return new SensitiveJsonSerializer(annotation.value(), annotation.pattern());
        }
        return this;
    }
}
