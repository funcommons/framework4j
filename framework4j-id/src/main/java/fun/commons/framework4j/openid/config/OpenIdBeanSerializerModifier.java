package fun.commons.framework4j.openid.config;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.openid.util.OpenIdTypeSupport;

import java.util.List;

/**
 * 扫描 {@code @OpenId} 字段并按 {@link OpenIdTypeSupport} 应用对应序列化器(v1.3 三开关)。
 * <p>
 * 受理由 {@link OpenIdTypeSupport#supportedScalarOf(JavaType)} 统一判定:
 * <ul>
 *   <li>Long/long 标量(恒受理)→ {@link OpenIdAutoConfiguration.OpenIdJsonSerializer}</li>
 *   <li>Integer/String 标量(开关开启)→ {@link OpenIdValueJsonSerializer}</li>
 *   <li>受支持标量的集合/数组 → {@link OpenIdCollectionJsonSerializer}(输出混淆串数组)</li>
 *   <li>不受理 → 跳过(按普通字段序列化;误用由 fail-fast 兜底)</li>
 * </ul>
 * 类型判定复用 {@link OpenIdTypeSupport},与反序列化侧对称。
 *
 * @since 2.2.0(v1.3 类型感知 + 三开关)
 */
public class OpenIdBeanSerializerModifier extends BeanSerializerModifier {

    private final OpenIdAutoConfiguration.OpenIdJsonSerializer longSerializer =
            new OpenIdAutoConfiguration.OpenIdJsonSerializer();

    private final OpenIdTypeSupport typeSupport;

    public OpenIdBeanSerializerModifier(OpenIdTypeSupport typeSupport) {
        this.typeSupport = typeSupport;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                     BeanDescription beanDesc,
                                                     List<BeanPropertyWriter> beanProperties) {
        for (BeanPropertyWriter writer : beanProperties) {
            if (writer.getAnnotation(OpenId.class) == null) {
                continue;
            }
            JavaType type = writer.getType();
            Class<?> elementTarget = typeSupport.supportedScalarOf(type);
            if (elementTarget == null) {
                continue;
            }
            JsonSerializer<?> chosen;
            if (type.getRawClass() == elementTarget) {
                chosen = scalarSerializerFor(elementTarget);
            } else {
                chosen = OpenIdCollectionJsonSerializer.INSTANCE;
            }
            if (chosen == null) {
                continue;
            }
            writer.assignSerializer((JsonSerializer<Object>) (JsonSerializer<?>) chosen);
        }
        return beanProperties;
    }

    private JsonSerializer<?> scalarSerializerFor(Class<?> elementTarget) {
        if (elementTarget == Long.class || elementTarget == long.class) {
            return longSerializer;
        }
        if (elementTarget == Integer.class || elementTarget == int.class
                || elementTarget == String.class) {
            return OpenIdValueJsonSerializer.INSTANCE;
        }
        return null;
    }
}
