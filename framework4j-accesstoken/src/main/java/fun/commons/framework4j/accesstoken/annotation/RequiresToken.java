package fun.commons.framework4j.accesstoken.annotation;


import fun.commons.framework4j.accesstoken.exception.AuthException;

import java.lang.annotation.*;

/**
 * 鉴权注解
 * 可标记在类或方法上
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresToken {

    /**
     * 要求的 Token 业务类型（access token / refresh token 的 sub 字段）
     */
    String value();

    /**
     * Token 物理类型：access | refresh
     * <p>
     * - "access"（默认）：校验 access token，走完整业务校验链路
     * - "refresh"：校验 refresh token，仅校验 family 状态
     */
    String type() default "access";

    /**
     * 鉴权失败时抛出的异常类型
     * 默认为 AuthException
     */
    Class<? extends Exception> exception() default AuthException.class;
}