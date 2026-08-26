package fun.commons.framework4j.accesstoken.web;

import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.accesstoken.config.AccessTokenProperties;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.accesstoken.exception.AuthException;
import fun.commons.framework4j.accesstoken.util.TokenUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 角色鉴权集成测试（v1.4.1 / Issue #16 方案 A）
 * <p>
 * 验证链路：generateToken 携带 roles → TokenInterceptor 角色校验（10300）→
 * updateClaims 实时改角色（同一 token 无需重签）→ 旧注解端点不受影响。
 * 同时验证 updateClaims 保留 metadata TTL（SET KEEPTTL）。
 */
@Slf4j
@SpringBootTest(classes = RoleAuthIntegrationTest.TestConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "framework4j.access-token.path-patterns=/role/**")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("角色鉴权集成测试（v1.4.1）")
class RoleAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenGenerator generator;

    @Autowired
    private AccessTokenProperties properties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        // profile=test → spring.application.name=accesstoken-test
        String[] patterns = {
                "accesstoken-test:accesstoken:*",
                "access:revoked:accesstoken-test"
        };
        for (String pattern : patterns) {
            var keys = stringRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        }
    }

    private String token(String uid, List<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", uid);
        if (roles != null) {
            claims.put("roles", roles);
        }
        return generator.generateToken("WEB", claims);
    }

    @Test
    @DisplayName("TC-R1: roles 全匹配 + anyRole 任一匹配，校验失败返回 10300 而非 10200")
    void roleChecksEnforced() throws Exception {
        String memberToken = token("3001", List.of("MEMBER"));

        // 无角色注解端点不受影响
        mockMvc.perform(get("/role/plain").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(content().string("plain-ok"));

        // MEMBER 不满足 anyRole={ACCOUNT_ADMIN, PLATFORM_ADMIN}
        mockMvc.perform(get("/role/any").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(result -> {
                    Exception resolved = result.getResolvedException();
                    assertInstanceOf(AuthException.class, resolved);
                    assertEquals(10300, ((AuthException) resolved).getCode(), "应为 10300 FORBIDDEN 而非 10200");
                });

        // MEMBER 不满足 roles={PLATFORM_ADMIN}
        mockMvc.perform(get("/role/all").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(result -> assertEquals(10300, ((AuthException) result.getResolvedException()).getCode()));

        // 满足角色则放行
        String adminToken = token("3002", List.of("PLATFORM_ADMIN", "ACCOUNT_ADMIN"));
        mockMvc.perform(get("/role/all").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().string("platform-ok"));
        mockMvc.perform(get("/role/any").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-R2: fail-closed — 存量 token 无 roles claim 访问新加角色端点 → 10300 提示重登")
    void legacyTokenWithoutRolesDenied() throws Exception {
        String legacyToken = token("3003", null);

        mockMvc.perform(get("/role/all").header("Authorization", "Bearer " + legacyToken))
                .andExpect(status().isForbidden())
                .andExpect(result -> {
                    AuthException e = (AuthException) result.getResolvedException();
                    assertEquals(10300, e.getCode());
                    assertTrue(e.getMessage().contains("roles"), "应提示缺少 roles claim: " + e.getMessage());
                });

        // 同一 token 访问无角色要求端点仍正常
        mockMvc.perform(get("/role/plain").header("Authorization", "Bearer " + legacyToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-R3: updateClaims 实时改角色 — 同一 token 不重签即获得新角色")
    void updateClaimsTakesEffectImmediately() throws Exception {
        String memberToken = token("3004", List.of("MEMBER"));

        // 先被拒
        mockMvc.perform(get("/role/any").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        // 业务侧角色变更 → updateClaims
        Map<String, Object> newClaims = new HashMap<>();
        newClaims.put("uid", "3004");
        newClaims.put("roles", List.of("PLATFORM_ADMIN", "ACCOUNT_ADMIN"));
        assertTrue(generator.updateClaims("WEB", "3004", newClaims), "在线用户应更新成功");

        // 同一 token（JWT 未变）下一请求即通过
        mockMvc.perform(get("/role/all").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(content().string("platform-ok"));

        // 未登录用户返回 false
        Map<String, Object> nobody = new HashMap<>();
        nobody.put("uid", "3999");
        nobody.put("roles", List.of("PLATFORM_ADMIN"));
        assertFalse(generator.updateClaims("WEB", "3999", nobody), "无 token 用户应返回 false");
    }

    @Test
    @DisplayName("TC-R4: updateClaims 保留 metadata TTL（SET KEEPTTL，不得重置为永不过期）")
    void updateClaimsPreservesTtl() throws Exception {
        token("3005", List.of("MEMBER"));
        String redisKey = generator.buildRedisKey("WEB",
                TokenUtils.calculateKeyHash("3005", properties.getHashSalt()));

        Long ttlBefore = stringRedisTemplate.getExpire(redisKey, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(ttlBefore);
        assertTrue(ttlBefore > 0, "WEB policy 86400s，TTL 应为正: " + ttlBefore);

        Thread.sleep(1500);

        Map<String, Object> newClaims = new HashMap<>();
        newClaims.put("uid", "3005");
        newClaims.put("roles", List.of("PLATFORM_ADMIN"));
        assertTrue(generator.updateClaims("WEB", "3005", newClaims));

        Long ttlAfter = stringRedisTemplate.getExpire(redisKey, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(ttlAfter);
        // KEEPTTL：TTL 只随时间自然衰减（间隔 1.5s），不得清零/重置为 -1（永不过期）
        assertTrue(ttlAfter > 0, "TTL 不得被清除（KEEPTTL 失效会变 -1）: " + ttlAfter);
        assertTrue(ttlAfter <= ttlBefore, "TTL 不得被重置变长: before=" + ttlBefore + " after=" + ttlAfter);
        assertTrue(ttlBefore - ttlAfter < 10, "TTL 衰减应只有自然流逝的 1.5s 左右: before=" + ttlBefore + " after=" + ttlAfter);
    }

    // ==================== 测试配置类 ====================

    @RestController
    static class TestController {

        @GetMapping("/role/plain")
        @RequiresToken("WEB")
        public String plain() {
            return "plain-ok";
        }

        @GetMapping("/role/all")
        @RequiresToken(value = "WEB", roles = {"PLATFORM_ADMIN"})
        public String platformOnly() {
            return "platform-ok";
        }

        @GetMapping("/role/any")
        @RequiresToken(value = "WEB", anyRole = {"ACCOUNT_ADMIN", "PLATFORM_ADMIN"})
        public String managerAny() {
            return "manager-ok";
        }
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class TestConfiguration {

        @Bean
        public TestController testController() {
            return new TestController();
        }

        /** 10300 → 403，10200 → 401，便于断言认证与授权的区分 */
        @Bean
        public org.springframework.web.servlet.HandlerExceptionResolver roleAuthExceptionResolver() {
            return (request, response, handler, ex) -> {
                if (ex instanceof AuthException authEx) {
                    response.setStatus(authEx.getCode() == 10300 ? 403 : 401);
                    return new org.springframework.web.servlet.ModelAndView();
                }
                return null;
            };
        }
    }
}
