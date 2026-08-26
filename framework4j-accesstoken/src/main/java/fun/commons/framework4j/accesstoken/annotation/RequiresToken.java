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
     * 要求令牌拥有的角色（全匹配，AND）
     * <p>
     * 角色从 Redis claims 的 {@code roles} 键读取（{@link fun.commons.framework4j.accesstoken.interceptor.RoleAuthorizer#CLAIM_KEY_ROLES}），
     * 支持签发时写入（generateToken claims）或角色变更后 {@code AccessTokenGenerator#updateClaims} 实时更新。
     * <p>
     * v1.4.1（Issue #16 方案 A）。空数组（默认）不做角色校验，兼容存量用法。
     * 注意：{@code type=refresh} 的接口不做角色校验。
     */
    String[] roles() default {};

    /**
     * 要求令牌拥有的角色（任一匹配，OR）
     * <p>
     * 与 {@link #roles()} 同时声明时，两个条件必须同时满足（AND）。
     * v1.4.1（Issue #16 方案 A）。
     */
    String[] anyRole() default {};

    /**
     * 鉴权失败时抛出的异常类型
     * 默认为 AuthException
     */
    Class<? extends Exception> exception() default AuthException.class;
}