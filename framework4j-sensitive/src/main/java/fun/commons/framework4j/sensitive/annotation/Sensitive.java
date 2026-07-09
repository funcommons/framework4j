package fun.commons.framework4j.sensitive.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fun.commons.framework4j.sensitive.serializer.SensitiveJsonSerializer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段脱敏注解（Jackson 序列化时自动脱敏）
 *
 * @since 2.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveJsonSerializer.class)
public @interface Sensitive {

    /** 脱敏规则 */
    SensitiveRule value();

    /** v2.1 功能增强：自定义脱敏 pattern（仅 value=CUSTOM 生效）
     * <p>格式 "前保留,后保留,星号数"，如 "2,2,4" → Ab********cd
     */
    String pattern() default "";
}
