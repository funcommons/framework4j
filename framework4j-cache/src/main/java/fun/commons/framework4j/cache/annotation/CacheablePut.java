package fun.commons.framework4j.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 缓存写入（更新 L1+L2）
 *
 * @since 2.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CacheablePut {

    String prefix();
    String key();
    long ttl() default -1;
    boolean l1Enabled() default true;
}
