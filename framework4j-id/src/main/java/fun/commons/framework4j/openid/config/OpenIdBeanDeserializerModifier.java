package fun.commons.framework4j.openid.config;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.openid.util.OpenIdTypeSupport;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 扫描 {@code @OpenId} 字段并按 {@link OpenIdTypeSupport} 挂载反序列化器(R1/R2/R3 + v1.3 三开关)。
 * <p>
 * 与序列化侧 {@link OpenIdBeanSerializerModifier} 对称,由 {@link OpenIdAutoConfiguration} 的
 * BeanPostProcessor 注册到同一个 {@code SimpleModule}。
 * <p>
 * {@code updateBuilder} 会被 Jackson 对<b>每一个</b>被反序列化的 bean 类型逐一调用(含嵌套 record/对象),
 * 因此嵌套字段上的 {@code @OpenId} 天然生效(R3),无需 {@code @OpenIdRecursive}。
 * <p>
 * 类型受理由 {@link OpenIdTypeSupport#supportedScalarOf(JavaType)} 统一判定:
 * 受支持标量 → {@link OpenIdScalarDeserializer};受支持标量的集合/数组 → {@link OpenIdCollectionDeserializer};
 * 不受理(开关关闭的 Integer/String、Map 等) → 跳过(误用由 fail-fast 启动期检出)。
 * <p>
 * 采用"先收集替换、迭代结束后再 apply"避免迭代期修改 builder。
 *
 * @since 1.3.0
 */
public class OpenIdBeanDeserializerModifier extends BeanDeserializerModifier {

    private final OpenIdTypeSupport typeSupport;

    public OpenIdBeanDeserializerModifier(OpenIdTypeSupport typeSupport) {
        this.typeSupport = typeSupport;
    }

    @Override
    public BeanDeserializerBuilder updateBuilder(DeserializationConfig config,
                                                 BeanDescription beanDesc,
                                                 BeanDeserializerBuilder builder) {
        boolean fallback = typeSupport.isAcceptNumericFallback();
        List<SettableBeanProperty> replacements = new ArrayList<>();
        for (Iterator<SettableBeanProperty> it = builder.getProperties(); it.hasNext(); ) {
            SettableBeanProperty prop = it.next();
            if (prop.getAnnotation(OpenId.class) == null) {
                continue;
            }
            JavaType type = prop.getType();
            Class<?> elementTarget = typeSupport.supportedScalarOf(type);
            if (elementTarget == null) {
                continue; // @OpenId 标在未受理类型上 —— 不处理(fail-fast 兜底)
            }
            JsonDeserializer<?> deser;
            if (type.getRawClass() == elementTarget) {
                deser = new OpenIdScalarDeserializer(elementTarget, fallback);
            } else {
                deser = new OpenIdCollectionDeserializer(type, elementTarget, fallback);
            }
            replacements.add(prop.withValueDeserializer(deser));
        }
        for (SettableBeanProperty replacement : replacements) {
            builder.addOrReplaceProperty(replacement, false);
        }
        return builder;
    }
}
