package fun.commons.framework4j.openid.annotation;

import java.lang.annotation.*;

/**
 * OpenID 核心注解
 * <p>
 * 作用:
 * 1. 标记在 DTO 字段上: 指示 Jackson 在序列化时自动混淆 (Long -> 12 字符 OpenID)。
 * 2. 标记在 Controller 参数上: 指示 Spring MVC 在绑定时自动还原 (String -> Long)。
 * 3. 标记在字段上: 指示 Swagger 将文档类型修正为 String。
 * <p>
 * v2.2: 移除 @JsonSerialize，改为 OpenIdAutoConfiguration 通过 BeanSerializerModifier
 * 动态应用序列化器 —— 这样 framework4j.openid.enabled=false 时，序列化也真正关闭。
 * 旧的 @JsonSerialize 是字段级静态注解，Jackson 反射读取时完全绕过 Spring 容器，
 * 导致开关失效。
 *
 * @since 1.0.0
 */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OpenId {
}
