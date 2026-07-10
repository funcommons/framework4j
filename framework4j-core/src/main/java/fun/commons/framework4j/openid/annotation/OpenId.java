package fun.commons.framework4j.openid.annotation;

import java.lang.annotation.*;

/**
 * OpenID 核心注解
 * <p>
 * 作用:
 * 1. 标记在 DTO 字段上: 指示 FastJson2 在序列化时自动混淆 (Long -> String)。
 * 2. 标记在 Controller 参数上: 指示 Spring MVC 在绑定时自动还原 (String -> Long)。
 * 3. 标记在字段上: 指示 Swagger 将文档类型修正为 String。
 * <p>
 * 使用场景:
 * <pre>
 * // DTO 输出混淆
 * &#64;OpenId
 * private Long id;
 *
 * // Controller 入参还原
 * public Result get(@OpenId @PathVariable Long id);
 * </pre>
 *
 * @author LDX2T
 * @since 1.0.0
 */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OpenId {
}