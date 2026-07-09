package fun.commons.framework4j.accesstoken;

import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.accesstoken.context.TokenContext;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.accesstoken.exception.AuthException;
import fun.commons.framework4j.accesstoken.interceptor.TokenInterceptor;
import jakarta.annotation.Resource;
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
 * AccessToken 内存Redis集成测试
 *
 * 使用内存Redis进行测试，无需外部Redis服务
 * 验证所有核心功能在内存环境下的正确性
 */
@SpringBootTest
@ActiveProfiles("embedded-redis")
class EmbeddedRedisIntegrationTest {

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
        // 清理所有测试数据
        cleanRedisData();
    }

    @AfterEach
    void tearDown() {
        // 清理Redis数据和上下文
        cleanRedisData();
        TokenContext.clear();
    }

    private void cleanRedisData() {
        // v2.1 P0 修复：补清 access:revoked + refresh:family + refresh:revoked + activation 残留
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

    // ==================== 基础功能测试 ====================

    @Test
    @DisplayName("基础Token生成和验证")
    void testBasicTokenGeneration() {
        // 生成Token
        Map<String, Object> claims = Map.of("uid", "10086", "role", "admin");
        String token = generator.generateToken("ADMIN", claims);

        assertNotNull(token);
        assertTrue(token.length() > 50); // JWT应该足够长

        // 验证Redis存储
        Set<String> keys = stringRedisTemplate.keys("accesstoken-embedded-test:accesstoken:ADMIN:*");
        assertEquals(1, keys.size());
    }

    @Test
    @DisplayName("Token验证成功")
    void testTokenValidationSuccess() throws Exception {
        Map<String, Object> claims = Map.of("uid", "10086");
        String token = generator.generateToken("WEB", claims);

        // 模拟拦截器验证
        boolean result = mockInterceptorCall(token, "WEB");

        assertTrue(result);

        // 验证上下文设置
        assertNotNull(TokenContext.getClaim("uid"));
        assertEquals("10086", TokenContext.getClaim("uid"));
    }

    @Test
    @DisplayName("无效Token验证失败")
    void testInvalidTokenValidationFailure() throws Exception {
        String invalidToken = "invalid.token.string";

        AuthException exception = assertThrows(AuthException.class,
            () -> mockInterceptorCall(invalidToken, "WEB"));

        assertEquals(10202, exception.getCode()); // 令牌格式错误
    }

    @Test
    @DisplayName("Token过期验证")
    void testTokenExpired() throws Exception {
        Map<String, Object> claims = Map.of("inviteCode", "INVITE-123456");
        String token = generator.generateToken("INVITE", claims); // 1分钟过期

        // v2.1 P1: 原 Thread.sleep(61000) 等 61 秒，严重拖慢 CI。
        // 改为主动删除 Redis key 模拟过期（interceptor 校验 Redis key 不存在时抛 10201）
        Set<String> keys = stringRedisTemplate.keys("accesstoken-embedded-test:accesstoken:INVITE:*");
        assertNotNull(keys, "Redis keys 不应为 null");
        assertFalse(keys.isEmpty(), "应至少有一个 INVITE token key");
        stringRedisTemplate.delete(keys);

        AuthException exception = assertThrows(AuthException.class,
            () -> mockInterceptorCall(token, "INVITE"));

        assertEquals(10201, exception.getCode()); // Token过期
    }

    // ==================== 策略测试 ====================

    @Test
    @DisplayName("单点登录策略 - 后登录踢掉先登录")
    void testSingleSignOn() throws Exception {
        String uid = "sso-test-user";

        // 第一次登录
        String token1 = generator.generateToken("ADMIN", Map.of("uid", uid));
        assertTrue(mockInterceptorCall(token1, "ADMIN"));

        // 第二次登录（同一用户）— v2.1 P1: 删除 sleep(10)，nonce 用 UUID 无需等待
        String token2 = generator.generateToken("ADMIN", Map.of("uid", uid));

        // 验证第二个Token有效
        assertTrue(mockInterceptorCall(token2, "ADMIN"));

        // 验证第一个Token被踢掉
        AuthException exception = assertThrows(AuthException.class,
            () -> mockInterceptorCall(token1, "ADMIN"));

        assertEquals(10205, exception.getCode()); // 账号已在别处登录
    }

    @Test
    @DisplayName("一次性Token策略")
    void testSingleUseToken() throws Exception {
        Map<String, Object> claims = Map.of("uid", "reset-user", "type", "password");
        String token = generator.generateToken("RESET", claims); // max-usage=1

        // 第一次使用成功
        assertTrue(mockInterceptorCall(token, "RESET"));

        // 第二次使用失败
        AuthException exception = assertThrows(AuthException.class,
            () -> mockInterceptorCall(token, "RESET"));

        assertEquals(10201, exception.getCode()); // 次数超限
    }

    @Test
    @DisplayName("自动续期策略")
    void testAutoRenew() throws Exception {
        Map<String, Object> claims = Map.of("uid", "auto-renew-user");
        String token = generator.generateToken("WEB", claims);

        // 获取Redis Key
        Set<String> keys = stringRedisTemplate.keys("accesstoken-embedded-test:accesstoken:WEB:*");
        if (keys.isEmpty()) {
            fail("应该找到Redis键");
        }
        String redisKey = keys.iterator().next();

        // 第一次调用，应该触发续期
        mockInterceptorCall(token, "WEB");
        Long ttl1 = stringRedisTemplate.getExpire(redisKey, TimeUnit.SECONDS);

        // TTL应该在续期时间范围内 (300秒 ± 10秒误差)
        assertTrue(ttl1 > 290 && ttl1 <= 300,
            "续期后TTL应该约为300秒，实际: " + ttl1);

        // 等待一段时间
        Thread.sleep(2000);

        // 再次调用，应该再次续期
        mockInterceptorCall(token, "WEB");
        Long ttl2 = stringRedisTemplate.getExpire(redisKey, TimeUnit.SECONDS);

        assertTrue(ttl2 > 298 && ttl2 <= 300,
            "第二次续期后TTL应该重置为300秒，实际: " + ttl2);
    }

    @Test
    @DisplayName("多设备登录策略")
    void testMultiDeviceLogin() throws Exception {
        String uid = "multi-device-user";
        String deviceId1 = "device-001";
        String deviceId2 = "device-002";

        // 设备1登录
        String token1 = generator.generateToken("APP", Map.of("uid", uid, "deviceId", deviceId1));

        // 设备2登录（同一用户，不同设备）
        String token2 = generator.generateToken("APP", Map.of("uid", uid, "deviceId", deviceId2));

        // 两个Token都应该有效（不同设备可以同时登录）
        assertTrue(mockInterceptorCall(token1, "APP"));
        assertTrue(mockInterceptorCall(token2, "APP"));

        // 验证上下文信息包含设备ID
        mockInterceptorCall(token1, "APP");
        assertEquals("multi-device-user", TokenContext.getClaim("uid"));
        assertEquals("device-001", TokenContext.getClaim("deviceId"));
    }

    // ==================== Token管理测试 ====================

    @Test
    @DisplayName("Token注销")
    void testTokenRevoke() throws Exception {
        Map<String, Object> claims = Map.of("uid", "revoke-test");
        String token = generator.generateToken("ADMIN", claims);

        // 验证Token有效
        assertTrue(mockInterceptorCall(token, "ADMIN"));

        // 注销Token
        generator.revokeToken(token);

        // 验证Token失效
        AuthException exception = assertThrows(AuthException.class,
            () -> mockInterceptorCall(token, "ADMIN"));

        assertEquals(10208, exception.getCode()); // Token已注销（撤销检查 10208）

        // 验证Redis数据被清除
        Set<String> keys = stringRedisTemplate.keys("accesstoken-embedded-test:accesstoken:ADMIN:*");
        assertTrue(keys.isEmpty());
    }

    @Test
    @DisplayName("Token注销功能验证")
    void testTokenRevocation() throws Exception {
        Map<String, Object> claims = Map.of("uid", "revoke-enhanced-test", "action", "test");
        String token = generator.generateToken("WEB", claims);

        // 验证Token有效
        assertTrue(mockInterceptorCall(token, "WEB"));

        // 验证上下文信息
        assertEquals("revoke-enhanced-test", TokenContext.getClaim("uid"));
        assertEquals("test", TokenContext.getClaim("action"));

        // 注销Token
        generator.revokeToken(token);

        // 验证Token失效
        AuthException exception = assertThrows(AuthException.class,
            () -> mockInterceptorCall(token, "WEB"));

        assertEquals(10208, exception.getCode()); // Token已注销

        // 验证Redis数据被清除
        Set<String> keys = stringRedisTemplate.keys("accesstoken-embedded-test:accesstoken:WEB:*");
        assertTrue(keys.isEmpty());
    }

    // ==================== 并发测试 ====================

    @Test
    @DisplayName("高并发Token生成测试")
    void testConcurrentTokenGeneration() throws InterruptedException {
        int threadCount = 20;
        int tokensPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < tokensPerThread; j++) {
                        Map<String, Object> claims = Map.of(
                            "uid", "concurrent-user-" + threadIndex,
                            "deviceId", "device-" + threadIndex + "-" + j,
                            "threadId", threadIndex,
                            "tokenId", j
                        );
                        String token = generator.generateToken("APP", claims);

                        if (token != null && token.length() > 50) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // v2.1 P1: 断言 latch.await 返回值
        assertTrue(latch.await(15, TimeUnit.SECONDS), "所有线程应在 15s 内完成");
        executor.shutdown();
        if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
        // 验证所有Token都生成成功
        assertEquals(threadCount * tokensPerThread, successCount.get());
        assertEquals(0, errorCount.get());

        // 验证Redis中存储的数据量
        Set<String> keys = stringRedisTemplate.keys("accesstoken-embedded-test:accesstoken:APP:*");
        assertEquals(threadCount * tokensPerThread, keys.size());
    }

    @Test
    @DisplayName("高并发Token验证测试")
    void testConcurrentTokenValidation() throws InterruptedException {
        // 先生成一些Token
        Map<String, Object> claims = Map.of("uid", "concurrent-validate-user");
        String token = generator.generateToken("WEB", claims);

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    if (mockInterceptorCall(token, "WEB")) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // 记录但不失败
                } finally {
                    latch.countDown();
                }
            });
        }

        // v2.1 P1: 断言 latch.await 返回值
        assertTrue(latch.await(15, TimeUnit.SECONDS), "所有线程应在 15s 内完成");
        executor.shutdown();
        if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
        // 大部分应该成功（可能有小部分因网络或其他原因失败）
        assertTrue(successCount.get() > threadCount * 0.9,
            "并发验证成功率应该超过90%");
    }

    // ==================== 边界测试 ====================

    @Test
    @DisplayName("超大Claims测试")
    void testLargeClaims() throws Exception {
        // 创建包含大量数据的claims
        StringBuilder largeString = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeString.append("data").append(i).append(",");
        }

        Map<String, Object> claims = Map.of(
            "uid", "large-claims-user",
            "largeData", largeString.toString()
        );

        // 应该能正常生成Token
        String token = generator.generateToken("ADMIN", claims);
        assertNotNull(token);

        // 应该能正常验证
        assertTrue(mockInterceptorCall(token, "ADMIN"));
    }

    @Test
    @DisplayName("特殊字符Claims测试")
    void testSpecialCharactersClaims() throws Exception {
        Map<String, Object> claims = Map.of(
            "uid", "special-chars-user",
            "chinese", "测试中文字符",
            "emoji", "😀🚀💻",
            "symbols", "!@#$%^&*()_+-=[]{}|;':\",./<>?"
        );

        String token = generator.generateToken("WEB", claims);
        assertNotNull(token);

        assertTrue(mockInterceptorCall(token, "WEB"));

        // 验证特殊字符在上下文中正确
        assertEquals("测试中文字符", TokenContext.getClaim("chinese"));
        assertEquals("😀🚀💻", TokenContext.getClaim("emoji"));
    }

    // ==================== 辅助方法 ====================

    /**
     * 模拟拦截器调用
     */
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