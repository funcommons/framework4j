package fun.commons.framework4j.openid.annotation;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fun.commons.framework4j.openid.config.OpenIdAutoConfiguration;
import java.lang.annotation.*;

/**
 * OpenID 核心注解
 * <p>
 * 作用:
 * 1. 标记在 DTO 字段上: 指示 Jackson 在序列化时自动混淆 (Long -> 12 字符 OpenID)。
 * 2. 标记在 Controller 参数上: 指示 Spring MVC 在绑定时自动还原 (String -> Long)。
 * 3. 标记在字段上: 指示 Swagger 将文档类型修正为 String。
 * <p>
 * v2.1: 加 @JsonSerialize(using=...) 字段级注册，不再全局覆盖 Long.class。
 *
 * @since 1.0.0
 */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JsonSerialize(using = OpenIdAutoConfiguration.OpenIdJsonSerializer.class)
public @interface OpenId {
}
