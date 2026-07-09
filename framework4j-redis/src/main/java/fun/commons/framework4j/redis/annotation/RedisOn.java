package fun.commons.framework4j.redis.annotation;

import java.lang.annotation.*;

/**
 * 多数据源注入注解
 * 可用于字段或类级别
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedisOn {
    /**
     * 数据源名称 (如 "cache", "business")
     */
    String value();

    /**
     * 严格模式
     * true: 如果指定的数据源不存在，抛出异常 (默认)
     * false: 如果指定的数据源不存在，降级使用默认数据源 (default)
     */
    boolean strict() default true;
}