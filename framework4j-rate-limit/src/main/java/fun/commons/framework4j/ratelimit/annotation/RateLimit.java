package fun.commons.framework4j.ratelimit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注限流接口（Controller 方法/类）
 * <p>
 * 配合 {@code framework4j.rate-limit.path-patterns} 双重路由。
 *
 * @since 2.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RateLimit {

    /**
     * 限流 key（支持 SpEL：{@code #userId} / {@code #request.ip} 等）。
     * 空表示用 scope 默认解析（ip/user/app）。
     */
    String key() default "";

    /** window 内最大请求数 */
    int limit() default 100;

    /** 窗口大小（Duration 格式：1s/1m/1h） */
    String window() default "1m";

    /**
     * scope：ip / user / app / global
     * <ul>
     *   <li>ip - 客户端 IP（X-Forwarded-For 优先）</li>
     *   <li>user - 从 TokenContext 取 uid</li>
     *   <li>app - 从 X-Access-Key Header 取</li>
     *   <li>global - 全局共享 key</li>
     * </ul>
     */
    String scope() default "ip";

    /**
     * 算法：sliding_window / token_bucket
     */
    String algorithm() default "sliding_window";

    /**
     * 令牌桶突发容量（仅 algorithm=token_bucket 时生效，0 表示 = limit）
     */
    int burst() default 0;
}
