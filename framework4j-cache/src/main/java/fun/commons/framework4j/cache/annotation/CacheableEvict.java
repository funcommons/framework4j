package fun.commons.framework4j.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 缓存删除（双删 L1+L2）
 *
 * @since 2.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CacheableEvict {

    String prefix();
    String key();
    /** 是否同步删 L2（默认 true） */
    boolean evictL2() default true;
}
