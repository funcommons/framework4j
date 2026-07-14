package fun.commons.framework4j.openid.config;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import fun.commons.framework4j.openid.annotation.OpenId;

import java.util.List;

/**
 * 扫描 @OpenId 字段并应用 OpenIdJsonSerializer
 * <p>
 * v2.2: 替代 @JsonSerialize 字段级注解方案，使开关受 Spring 容器控制。
 * 仅在 OpenIdAutoConfiguration 启用时（framework4j.openid.enabled=true，默认 true）
 * 通过 Jackson Module 注册；关闭时本类不被加载，@OpenId 字段按普通 Long 序列化。
 *
 * @since 2.2.0
 */
public class OpenIdBeanSerializerModifier extends BeanSerializerModifier {

    private final OpenIdAutoConfiguration.OpenIdJsonSerializer serializer =
            new OpenIdAutoConfiguration.OpenIdJsonSerializer();

    @Override
    @SuppressWarnings("unchecked")
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                     BeanDescription beanDesc,
                                                     List<BeanPropertyWriter> beanProperties) {
        for (BeanPropertyWriter writer : beanProperties) {
            if (writer.getAnnotation(OpenId.class) != null) {
                // BeanPropertyWriter.assignSerializer 签名为 JsonSerializer<Object>，
                // 这里强制原始类型转换；OpenIdJsonSerializer 实际只处理 Long。
                writer.assignSerializer(
                        (com.fasterxml.jackson.databind.JsonSerializer<Object>)
                                (com.fasterxml.jackson.databind.JsonSerializer<?>) serializer);
            }
        }
        return beanProperties;
    }
}
