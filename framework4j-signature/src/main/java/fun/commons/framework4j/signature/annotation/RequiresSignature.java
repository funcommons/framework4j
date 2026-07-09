package fun.commons.framework4j.signature.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注需要签名校验的接口（Controller 方法/类）
 * <p>
 * 配合 {@code framework4j.signature.path-patterns} 双重路由：
 * <ul>
 *   <li>路径匹配：满足 path-patterns（且不匹配 exclude-path-patterns）</li>
 *   <li>注解：方法或类上有 {@code @RequiresSignature}</li>
 * </ul>
 * 任一满足即触发校验（取决于 {@code SignatureProperties.requireAnnotation}）。
 * <p>
 * 默认实现：仅在 path-patterns 内的请求才校验，注解为可选附加。
 *
 * @since 2.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresSignature {

    /**
     * 是否启用（覆盖全局 enabled，方法级优先）
     */
    boolean enabled() default true;
}
