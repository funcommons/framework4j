package fun.commons.framework4j.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解
 * <p>
 * 标注需要审计的方法（AOP 自动记录）
 *
 * @since 2.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditable {

    /** 操作类型（业务语义，如 "DELETE_ORDER"） */
    String action();

    /** 目标资源类型（如 "order"） */
    String targetType() default "";

    /**
     * 目标资源 ID 的 SpEL 表达式（如 "#orderId"）
     */
    String targetIdSpel() default "";

    /**
     * 是否记录入参（默认 true）
     */
    boolean logArgs() default true;

    /**
     * 是否记录返回值（默认 false，敏感数据避免泄漏）
     */
    boolean logResult() default false;

    /**
     * 异常时是否也审计（默认 true）
     */
    boolean logOnError() default true;
}
