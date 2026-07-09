package fun.commons.framework4j.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 缓存读取（同时回填 L1+L2）
 * <p>
 * v2.1 P1: 删除 l1Enabled / singleFlight / bloomGuard / nullTtl 死字段
 * （切面未实现透传，用户配置无效，违反 §7.1）
 * 这些行为由 CacheProperties 全局配置控制。
 *
 * @since 2.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CacheableGet {

    /** key 前缀（业务域，如 "user"） */
    String prefix();

    /** SpEL key 表达式（如 "#id" / "#user.id"） */
    String key();

    /** TTL（秒），-1 表示用全局默认 */
    long ttl() default -1;

    /** v2.1 功能增强：空值缓存 TTL（秒），-1 表示用全局默认 nullTtlSeconds */
    long nullTtl() default -1;
}
