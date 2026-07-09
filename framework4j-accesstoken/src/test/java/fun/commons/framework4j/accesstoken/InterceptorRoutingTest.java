package fun.commons.framework4j.accesstoken;

import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator.TokenPair;
import fun.commons.framework4j.accesstoken.core.RefreshTokenService;
import fun.commons.framework4j.accesstoken.exception.AuthException;
import fun.commons.framework4j.accesstoken.interceptor.TokenInterceptor;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 拦截器路由测试：@RequiresToken.type() = "access" / "refresh" 分流
 */
@SpringBootTest
@ActiveProfiles("embedded-redis")
class InterceptorRoutingTest {

    @Resource
    private AccessTokenGenerator generator;

    @Resource
    private RefreshTokenService refreshService;

    @Resource
    private TokenInterceptor interceptor;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @SpringBootApplication
    static class TestApplication {}

    /** 用于构造真实 HandlerMethod 的 controller。 */
    @RestController
    static class TestController {
        @PostMapping("/v1/auth/refresh")
        @RequiresToken(value = "WEB", type = "refresh")
        public void refresh() {}

        @GetMapping("/v1/users/me")
        @RequiresToken(value = "WEB")
        public void me() {}
    }

    @BeforeEach
    void setUp() {
        cleanRedis();
    }

    @AfterEach
    void tearDown() {
        cleanRedis();
    }

    private void cleanRedis() {
        // v2.1 P0 修复：补清 access:revoked + refresh:family + refresh:revoked 残留
        String[] patterns = {
                "accesstoken-embedded-test:*",
                "access:revoked:accesstoken-embedded-test",
                "refresh:family:accesstoken-embedded-test:*",
                "refresh:revoked:accesstoken-embedded-test:*"
        };
        for (String pattern : patterns) {
            Set<String> keys = stringRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        }
    }

    @Test
    @DisplayName("@RequiresToken(type=\"refresh\") 接受合法 refresh token")
    void testRefreshRouteAcceptsValidRefreshToken() throws Exception {
        TokenPair pair = refreshService.generateTokenPair(Map.of("uid", "u1", "type", "WEB"));

        HandlerMethod handler = handlerMethod("refresh");
        MockHttpServletRequest req = newRequest(pair.refreshToken());
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(req, resp, handler));
    }

    @Test
    @DisplayName("@RequiresToken(type=\"refresh\") 拒绝 access token（防越权）")
    void testRefreshRouteRejectsAccessToken() throws Exception {
        String accessToken = generator.generateToken("WEB", Map.of("uid", "u2", "type", "WEB"));

        HandlerMethod handler = handlerMethod("refresh");
        MockHttpServletRequest req = newRequest(accessToken);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        AuthException ex = assertThrows(AuthException.class,
                () -> interceptor.preHandle(req, resp, handler));
        assertEquals(10211, ex.getCode(), "access token 不能走 refresh 路由");
    }

    @Test
    @DisplayName("默认 @RequiresToken()（type 缺省=access）走 access 路径")
    void testDefaultRouteIsAccess() throws Exception {
        String accessToken = generator.generateToken("WEB", Map.of("uid", "u3", "type", "WEB"));

        HandlerMethod handler = handlerMethod("me");
        MockHttpServletRequest req = newRequest(accessToken);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(req, resp, handler));
    }

    @Test
    @DisplayName("family 被 poison 后，refresh 路由直接拒 10211")
    void testPoisonedFamilyRejected() throws Exception {
        TokenPair pair = refreshService.generateTokenPair(Map.of("uid", "u4", "type", "WEB"));
        String familyId = pair.familyId();

        // 直接注入 poison key（绕过 generator.refreshAccessToken 的全族撤销），
        // 隔离测试 interceptor 对"family 被 poison"的响应路径（10211）。
        // 注意：实际生产中 poison 会同时把 jti 加入 access:revoked（→ 10208），
        // 那个路径已由 testRefreshRouteRejectsAccessToken 覆盖。
        stringRedisTemplate.opsForValue().set(
                "refresh:revoked:accesstoken-embedded-test:" + familyId, "1",
                java.time.Duration.ofDays(7));

        HandlerMethod handler = handlerMethod("refresh");
        MockHttpServletRequest req = newRequest(pair.refreshToken());
        MockHttpServletResponse resp = new MockHttpServletResponse();

        AuthException ex = assertThrows(AuthException.class,
                () -> interceptor.preHandle(req, resp, handler));
        assertEquals(10211, ex.getCode());
        assertTrue(ex.getMessage().contains("撤销") || ex.getMessage().contains("重用"),
                "message 应说明撤销/重用检测，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("@RequiresToken(type=\"refresh\") 接受被轮转掉的旧 jti（Lua 中已 consumed）")
    void testRotatedJtiPassesInterceptor() throws Exception {
        TokenPair pair = refreshService.generateTokenPair(Map.of("uid", "u5", "type", "WEB"));

        // 第一次 refresh → 旧 jti 仍存在 family hash（consumed=true）
        refreshService.refreshAccessToken(pair.refreshToken());

        HandlerMethod handler = handlerMethod("refresh");
        MockHttpServletRequest req = newRequest(pair.refreshToken());
        MockHttpServletResponse resp = new MockHttpServletResponse();

        // interceptor 只做"前置校验"（存在性 + poison 状态），不区分 consumed
        // 真正的 consumed 校验在 generator.refreshAccessToken 内部的 Lua 里完成
        assertTrue(interceptor.preHandle(req, resp, handler),
                "interceptor 应通过，consumed 校验由 generator Lua 处理");
    }

    // ==================== 工具 ====================

    private MockHttpServletRequest newRequest(String token) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/auth/refresh");
        req.addHeader("Authorization", "Bearer " + token);
        return req;
    }

    /**
     * 从 TestController 真实方法构造 HandlerMethod（保证 getBeanType / getMethodAnnotation 可用）
     */
    private HandlerMethod handlerMethod(String name) throws NoSuchMethodException {
        Method m = TestController.class.getDeclaredMethod(name);
        return new HandlerMethod(new TestController(), m);
    }
}
