package fun.commons.framework4j.accesstoken.interceptor;

import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.accesstoken.context.TokenContext;
import fun.commons.framework4j.accesstoken.exception.AuthException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RoleAuthorizer 单元测试（v1.4.1 / Issue #16 方案 A）
 * <p>
 * 覆盖：roles 全匹配 / anyRole 任一匹配 / 两者组合 / fail-closed（无 roles claim）/
 * claims 形态归一化（List / 单字符串 / 空列表）/ 自定义异常透传。
 */
@DisplayName("RoleAuthorizer 角色校验（v1.4.1）")
class RoleAuthorizerTest {

    private final RoleAuthorizer authorizer = new RoleAuthorizer();

    /** 注解夹具：通过反射拿真实 @RequiresToken 实例 */
    static class Holders {
        @RequiresToken("WEB")
        void plain() {}

        @RequiresToken(value = "WEB", roles = {"PLATFORM_ADMIN", "ACCOUNT_ADMIN"})
        void allRoles() {}

        @RequiresToken(value = "WEB", anyRole = {"ACCOUNT_ADMIN", "PLATFORM_ADMIN"})
        void anyRole() {}

        @RequiresToken(value = "WEB", roles = {"PLATFORM_ADMIN"}, anyRole = {"AUDITOR", "ACCOUNT_ADMIN"})
        void combined() {}

        @RequiresToken(value = "WEB", roles = {"PLATFORM_ADMIN"}, exception = CustomAuthException.class)
        void customException() {}
    }

    public static class CustomAuthException extends RuntimeException {
        public CustomAuthException(String message) {
            super(message);
        }
    }

    @AfterEach
    void tearDown() {
        TokenContext.clear();
    }

    private static RequiresToken ann(String method) throws NoSuchMethodException {
        return Holders.class.getDeclaredMethod(method).getAnnotation(RequiresToken.class);
    }

    private static void contextWith(Object roles) {
        TokenContext.set("WEB", roles == null ? Map.of() : Map.of("roles", roles));
    }

    @Test
    @DisplayName("未声明角色要求时直接放行（兼容存量注解）")
    void plainAnnotationPasses() throws Exception {
        contextWith(null);
        assertDoesNotThrow(() -> authorizer.check(ann("plain")));
        contextWith(List.of("MEMBER"));
        assertDoesNotThrow(() -> authorizer.check(ann("plain")));
    }

    @Test
    @DisplayName("roles 全匹配：令牌包含全部所需角色才放行")
    void allRolesMatch() throws Exception {
        contextWith(List.of("PLATFORM_ADMIN", "ACCOUNT_ADMIN", "MEMBER"));
        assertDoesNotThrow(() -> authorizer.check(ann("allRoles")));

        contextWith(List.of("PLATFORM_ADMIN"));
        AuthException e = assertThrows(AuthException.class, () -> authorizer.check(ann("allRoles")));
        assertEquals(10300, e.getCode());
        assertTrue(e.getMessage().contains("ACCOUNT_ADMIN"));
    }

    @Test
    @DisplayName("anyRole 任一匹配：命中其一即放行")
    void anyRoleMatch() throws Exception {
        contextWith(List.of("MEMBER", "ACCOUNT_ADMIN"));
        assertDoesNotThrow(() -> authorizer.check(ann("anyRole")));

        contextWith(List.of("MEMBER"));
        AuthException e = assertThrows(AuthException.class, () -> authorizer.check(ann("anyRole")));
        assertEquals(10300, e.getCode());
    }

    @Test
    @DisplayName("roles + anyRole 同时声明：两个条件必须同时满足")
    void combinedRoles() throws Exception {
        contextWith(List.of("PLATFORM_ADMIN", "ACCOUNT_ADMIN"));
        assertDoesNotThrow(() -> authorizer.check(ann("combined")));

        // 缺 roles 要求的 PLATFORM_ADMIN → anyRole 命中也不放行
        contextWith(List.of("ACCOUNT_ADMIN", "AUDITOR"));
        AuthException e1 = assertThrows(AuthException.class, () -> authorizer.check(ann("combined")));
        assertEquals(10300, e1.getCode());

        // roles 满足但 anyRole 未命中 → 拒绝
        contextWith(List.of("PLATFORM_ADMIN"));
        AuthException e2 = assertThrows(AuthException.class, () -> authorizer.check(ann("combined")));
        assertEquals(10300, e2.getCode());
    }

    @Test
    @DisplayName("fail-closed：令牌未携带 roles claim 时拒绝（10300，存量老 token 场景）")
    void missingRolesClaimDenied() throws Exception {
        contextWith(null);
        AuthException e = assertThrows(AuthException.class, () -> authorizer.check(ann("allRoles")));
        assertEquals(10300, e.getCode());
        assertTrue(e.getMessage().contains("roles"));

        // 空列表同样视为无角色
        contextWith(List.of());
        AuthException e2 = assertThrows(AuthException.class, () -> authorizer.check(ann("allRoles")));
        assertEquals(10300, e2.getCode());
    }

    @Test
    @DisplayName("claims 形态归一化：单字符串角色与 List 等价")
    void singleStringRoleNormalized() throws Exception {
        contextWith("PLATFORM_ADMIN");
        assertDoesNotThrow(() -> authorizer.check(Holders.class.getDeclaredMethod("plain").getAnnotation(RequiresToken.class)));
        // 单字符串 "PLATFORM_ADMIN" + 要求 [PLATFORM_ADMIN, ACCOUNT_ADMIN] → 缺 ACCOUNT_ADMIN
        AuthException e = assertThrows(AuthException.class, () -> authorizer.check(ann("allRoles")));
        assertEquals(10300, e.getCode());
    }

    @Test
    @DisplayName("自定义异常：exception() 指定的类型透传（含 10300 码构造）")
    void customExceptionPropagated() throws Exception {
        contextWith(List.of("MEMBER"));
        assertThrows(CustomAuthException.class, () -> authorizer.check(ann("customException")));
    }
}
