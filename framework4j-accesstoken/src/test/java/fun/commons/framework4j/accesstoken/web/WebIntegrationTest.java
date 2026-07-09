package fun.commons.framework4j.accesstoken.web;

import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.accesstoken.context.TokenContext;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.accesstoken.exception.AuthException;
import fun.commons.framework4j.accesstoken.interceptor.TokenInterceptor;
import fun.commons.framework4j.redis.annotation.RedisOn;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web 集成测试 (Integration)
 * 测试用例: TC-09 to TC-12
 *
 * 使用 framework4j-redis 模块注入 Redis
 *
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = WebIntegrationTest.TestConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RedisOn("default")
@DisplayName("Web 集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenGenerator generator;

    @Autowired(required = false)
    private MultiRedisManager redisManager;

    @Resource
    @RedisOn("default")
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        // 如果有 MultiRedisManager，使用它获取 RedisTemplate
        if (redisManager != null) {
            stringRedisTemplate = redisManager.getStringRedisTemplate("default");
        }

        // v2.1 P0 修复：补清 access:revoked + refresh:family + refresh:revoked 残留
        String[] patterns = {
                "test-app:*",
                "access:revoked:test-app",
                "refresh:family:test-app:*",
                "refresh:revoked:test-app:*"
        };
        for (String pattern : patterns) {
            var keys = stringRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        }
        TokenContext.clear();
    }

    @Test
    @Order(1)
    @DisplayName("TC-09: 鉴权注解类型匹配")
    void testTokenTypeMismatch() throws Exception {
        log.info("========== TC-09: 鉴权注解类型匹配 ==========");

        // Arrange - 生成 WEB 类型的 Token
        String type = "WEB";
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", "1001");

        String token = generator.generateToken(type, claims);
        log.info(">>> 生成 WEB 类型 Token: {}", token);

        // Act - 请求需要 ADMIN 类型的接口
        mockMvc.perform(get("/test/admin-only")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                // Assert - 应该被拦截 (类型不匹配)
                .andExpect(status().isUnauthorized())
                .andExpect(result -> {
                    Exception resolvedException = result.getResolvedException();
                    assertNotNull(resolvedException, "应抛出异常");
                    assertTrue(resolvedException instanceof AuthException, "应为 AuthException");
                    AuthException authEx = (AuthException) resolvedException;
                    assertEquals(10300, authEx.getCode(), "错误码应为 10300");
                    log.info(">>> 捕获异常: code={}, msg={}", authEx.getCode(), authEx.getMessage());
                });

        log.info("✅ TC-09 通过: Token 类型不匹配被正确拦截");
    }

    @Test
    @Order(2)
    @DisplayName("TC-10: 自定义异常抛出")
    void testCustomExceptionThrow() throws Exception {
        log.info("========== TC-10: 自定义异常抛出 ==========");

        // Act - 请求使用自定义异常的接口，不带 Token
        mockMvc.perform(get("/test/custom-exception")
                .contentType(MediaType.APPLICATION_JSON))
                // Assert - 应该抛出自定义异常
                .andExpect(status().isUnauthorized())
                .andExpect(result -> {
                    Exception resolvedException = result.getResolvedException();
                    assertNotNull(resolvedException, "应抛出异常");
                    assertTrue(resolvedException instanceof CustomAuthException,
                        "应为 CustomAuthException, 实际为: " + resolvedException.getClass().getName());
                    log.info(">>> 捕获自定义异常: {}", resolvedException.getClass().getSimpleName());
                });

        log.info("✅ TC-10 通过: 自定义异常被正确抛出");
    }

    @Test
    @Order(3)
    @DisplayName("TC-11: 上下文注入")
    void testContextInjection() throws Exception {
        log.info("========== TC-11: 上下文注入 ==========");

        // Arrange - 生成 ADMIN 类型的 Token
        String type = "ADMIN";
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", "1001");
        claims.put("role", "super_admin");

        String token = generator.generateToken(type, claims);
        log.info(">>> 生成 Token: {}", token);

        // Act - 请求接口
        mockMvc.perform(get("/test/context-check")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                // Assert - Controller 应能正确获取上下文
                .andExpect(status().isOk())
                .andExpect(content().string("1001"));

        log.info("✅ TC-11 通过: 上下文注入正确");
    }

    @Test
    @Order(4)
    @DisplayName("TC-12: 异步线程丢失验证")
    void testAsyncThreadContextLoss() throws Exception {
        log.info("========== TC-12: 异步线程丢失验证 ==========");

        // Arrange - 模拟主线程设置上下文
        String type = "ADMIN";
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", "1001");

        TokenContext.set(type, claims);

        // 主线程获取
        String mainThreadUid = TokenContext.getClaim("uid");
        log.info(">>> 主线程 UID: {}", mainThreadUid);
        assertEquals("1001", mainThreadUid, "主线程应能获取 UID");

        // Act - 在子线程中获取
        AtomicReference<String> asyncUid = new AtomicReference<>();
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            asyncUid.set(TokenContext.getClaim("uid"));
            log.info(">>> 子线程 UID: {}", asyncUid.get());
        });

        future.join();

        // Assert - 子线程应获取不到 (ThreadLocal 隔离)
        assertNull(asyncUid.get(), "子线程不应获取到 UID (ThreadLocal 隔离)");

        // 验证手动传递场景
        TokenContext.ContextData contextData = TokenContext.getContext();
        AtomicReference<String> passedUid = new AtomicReference<>();

        CompletableFuture<Void> futureWithPass = CompletableFuture.runAsync(() -> {
            TokenContext.setContext(contextData);
            passedUid.set(TokenContext.getClaim("uid"));
            log.info(">>> 手动传递后子线程 UID: {}", passedUid.get());
            TokenContext.clear();
        });

        futureWithPass.join();

        assertEquals("1001", passedUid.get(), "手动传递上下文后应能获取 UID");

        TokenContext.clear();
        log.info("✅ TC-12 通过: ThreadLocal 隔离性验证通过");
    }

    // ==================== 测试配置类 ====================

    /**
     * 自定义认证异常 (用于 TC-10)
     */
    public static class CustomAuthException extends RuntimeException {
        public CustomAuthException(String message) {
            super(message);
        }
    }

    /**
     * 测试用 Controller
     */
    @RestController
    static class TestController {

        @GetMapping("/test/admin-only")
        @RequiresToken("ADMIN")
        public String adminOnly() {
            return "admin access granted";
        }

        @GetMapping("/test/custom-exception")
        @RequiresToken(value = "ADMIN", exception = CustomAuthException.class)
        public String customException() {
            return "should not reach here";
        }

        @GetMapping("/test/context-check")
        @RequiresToken("ADMIN")
        public String contextCheck() {
            String uid = TokenContext.getClaim("uid");
            return uid != null ? uid : "null";
        }

        @GetMapping("/test/public")
        public String publicEndpoint() {
            return "public access";
        }
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class TestConfiguration implements WebMvcConfigurer {

        @Autowired
        private TokenInterceptor tokenInterceptor;

        @Bean
        public TestController testController() {
            return new TestController();
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(tokenInterceptor)
                    .addPathPatterns("/test/**")
                    .excludePathPatterns("/test/public");
        }

        @Bean
        public org.springframework.web.servlet.HandlerExceptionResolver customExceptionResolver() {
            return (request, response, handler, ex) -> {
                if (ex instanceof AuthException || ex instanceof CustomAuthException) {
                    response.setStatus(401);
                    return new org.springframework.web.servlet.ModelAndView();
                }
                return null;
            };
        }
    }
}
