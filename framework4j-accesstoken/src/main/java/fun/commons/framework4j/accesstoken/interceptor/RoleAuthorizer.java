package fun.commons.framework4j.accesstoken.interceptor;

import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.accesstoken.context.TokenContext;
import fun.commons.framework4j.accesstoken.exception.AuthExceptionFactory;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 角色鉴权器（v1.4.1 / Issue #16 方案 A）
 * <p>
 * token 校验通过后的 post-auth 扩展点：从 {@link TokenContext} 的 Redis claims
 * 读取 {@value #CLAIM_KEY_ROLES} 并与 {@link RequiresToken#roles()}（全匹配）/
 * {@link RequiresToken#anyRole()}（任一匹配）比对，失败抛 10300 FORBIDDEN（与 10200 未认证区分）。
 * <p>
 * 读取的是 Redis 侧 claims（{@code AccessTokenValidationStrategy} 每次请求从用户级
 * metadata key 加载，而非 JWT payload），因此角色变更后调用
 * {@code AccessTokenGenerator#updateClaims} 即可全端实时生效，无需重签 token / 重登。
 * <p>
 * fail-closed：声明了角色要求但令牌未携带 {@value #CLAIM_KEY_ROLES} 时拒绝
 * （存量老 token 升级后访问新增角色校验的端点会 403，重登或 updateClaims 后恢复）。
 *
 * @since 1.4.1
 */
public class RoleAuthorizer {

    /** claims 中角色列表的约定 key（generateToken 写入 / updateClaims 更新） */
    public static final String CLAIM_KEY_ROLES = "roles";

    /**
     * 角色校验入口。roles/anyRole 均为空时直接放行（兼���存量注解）。
     *
     * @param annotation @RequiresToken 注解
     * @throws Exception 10300 FORBIDDEN（或注解指定的自定义异常）
     */
    public void check(RequiresToken annotation) throws Exception {
        String[] requiredAll = annotation.roles();
        String[] requiredAny = annotation.anyRole();
        boolean hasAll = requiredAll != null && requiredAll.length > 0;
        boolean hasAny = requiredAny != null && requiredAny.length > 0;
        if (!hasAll && !hasAny) {
            return;
        }

        Set<String> tokenRoles = readTokenRoles();
        if (tokenRoles.isEmpty()) {
            AuthExceptionFactory.throwCustom(annotation, 10300,
                    "访问被拒绝：令牌未携带角色信息（claims 缺少 [" + CLAIM_KEY_ROLES + "]）");
            return;
        }

        if (hasAll) {
            for (String role : requiredAll) {
                if (!tokenRoles.contains(role)) {
                    AuthExceptionFactory.throwCustom(annotation, 10300,
                            "访问被拒绝：缺少所需角色 [" + role + "]");
                    return;
                }
            }
        }

        if (hasAny) {
            boolean matched = Arrays.stream(requiredAny).anyMatch(tokenRoles::contains);
            if (!matched) {
                AuthExceptionFactory.throwCustom(annotation, 10300,
                        "访问被拒绝：令牌角色不在允许范围 " + Arrays.toString(requiredAny));
            }
        }
    }

    /**
     * 从 TokenContext claims 读取 roles 并归一化为 Set&lt;String&gt;。
     * 兼容 List / 数组 / 单个字符串三种写入形态（Jackson 反序列化后通常为 List）。
     */
    private Set<String> readTokenRoles() {
        Object raw = TokenContext.getClaim(CLAIM_KEY_ROLES);
        if (raw == null) {
            return Set.of();
        }
        Set<String> roles = new HashSet<>();
        if (raw instanceof Collection<?> col) {
            col.forEach(item -> addRole(roles, item));
        } else if (raw.getClass().isArray()) {
            int length = Array.getLength(raw);
            for (int i = 0; i < length; i++) {
                addRole(roles, Array.get(raw, i));
            }
        } else {
            addRole(roles, raw);
        }
        return roles;
    }

    private static void addRole(Set<String> roles, Object item) {
        if (item != null) {
            String role = String.valueOf(item).trim();
            if (!role.isEmpty()) {
                roles.add(role);
            }
        }
    }
}
