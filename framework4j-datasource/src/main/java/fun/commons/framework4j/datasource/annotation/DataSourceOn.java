package fun.commons.framework4j.datasource.annotation;

import java.lang.annotation.*;

/**
 * 多数据源注入注解
 * 可用于字段或类级别,指定使用的数据源名称
 *
 * @since 1.0.0
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataSourceOn {
    /**
     * 数据源名称 (如 "business", "log", "report")
     */
    String value();

    /**
     * 严格模式
     * true: 如果指定的数据源不存在,抛出异常 (默认)
     * false: 如果指定的数据源不存在,降级使用默认数据源 (default)
     */
    boolean strict() default true;
}
