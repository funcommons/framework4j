package fun.commons.framework4j.accesstoken;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.accesstoken.context.TokenContext;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.accesstoken.exception.AuthException;
import fun.commons.framework4j.accesstoken.interceptor.TokenInterceptor;
import fun.commons.framework4j.redis.annotation.RedisOn;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AccessToken SDK 集成测试 (真实 Redis 环境)
 * * 前置条件: 本地 localhost:6379 必须有可用的 Redis 服务
 */
@SpringBootTest(
        properties = {
                "spring.application.name=test-app",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379",

                // --- SDK 配置 ---
                "framework4j.access-token.enabled=true",
                "framework4j.access-token.secret-key=test-secret-key-must-be-very-long-1234567890",
                "framework4j.access-token.expire-time=3600",
                "framework4j.access-token.redis-name=default",

                // --- Redis 配置 ---
                "framework4j.redis.enabled=true",
                "framework4j.redis.datasources.default.host=localhost",
                "framework4j.redis.datasources.default.port=6379",
                "framework4j.redis.datasources.default.database=0",
                "framework4j.redis.datasources.default.timeout=3000",
                "framework4j.redis.datasources.default.redisson.enabled=true",
                "framework4j.redis.datasources.default.redisson.config.singleServerConfig.address=redis://localhost:6379",
                "framework4j.redis.datasources.default.redisson.config.singleServerConfig.database=0",

                // 策略1: 管理员 (单点登录 key=uid)
                "framework4j.access-token.policies.ADMIN.key=uid",
                "framework4j.access-token.policies.ADMIN.expire-time=7200",

                // 策略2: 一次性 (max-usage=1)
                "framework4j.access-token.policies.RESET.key=uid",
                "framework4j.access-token.policies.RESET.max-usage=1",

                // 策略3: 自动续期 (3秒续一次，方便测试)
                "framework4j.access-token.policies.WEB.key=uid",
                "framework4j.access-token.policies.WEB.auto-renew=true",
                "framework4j.access-token.policies.WEB.renew-increment=3"
        }
)
@ActiveProfiles("test")
@RedisOn("default")
class AccessTokenSdkIntegrationTest {

    @Autowired
    private AccessTokenGenerator generator;

    @Autowired
    private TokenInterceptor interceptor;

    @Resource
    @RedisOn("default")
    private StringRedisTemplate stringRedisTemplate;

    // 模拟 Controller 方法
    @Mock private HandlerMethod handlerMethod;

    // 为了测试，我们需要一个最小化的 Spring Boot Application
    @SpringBootApplication
    static class TestApplication {}

    @AfterEach
    void tearDown() {
        // v2.1 P0 修复：补清 access:revoked + refresh:family + refresh:revoked 残留
        String[] patterns = {
                "test-app:*",
                "access:revoked:test-app",
                "refresh:family:test-app:*",
                "refresh:revoked:test-app:*"
        };
        for (String pattern : patterns) {
            Set<String> keys = stringRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        }
        TokenContext.clear();
    }

    // ==========================================
    // 1. 核心功能测试 (Redis 数据验证)
    // ==========================================

    @Test
    @DisplayName("TC-01: 生成 Token 并验证 Redis 数据结构")
    void testGenerateAndVerifyRedis() throws Exception {
        String type = "ADMIN";
        Map<String, Object> claims = Map.of("uid", "10086", "role", "master");

        String token = generator.generateToken(type, claims);
        assertNotNull(token);

        // 验证 Redis
        // Key 规则: test-app:accesstoken:ADMIN:{hash}
        // 我们直接模糊查询来验证，因为 Hash 是动态算的
        Set<String> keys = stringRedisTemplate.keys("test-app:accesstoken:ADMIN:*");
        assertEquals(1, keys.size());
        String redisKey = keys.iterator().next();

        String value = stringRedisTemplate.opsForValue().get(redisKey);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> json = mapper.readValue(value, new TypeReference<Map<String, Object>>() {});
        Object claimsRaw = json.get("claims");

        assertEquals("ADMIN", json.get("type"));
        assertTrue(claimsRaw instanceof Map, "claims must be a Map");
        @SuppressWarnings("unchecked")
        Map<String, Object> claimsMap = (Map<String, Object>) claimsRaw;
        assertEquals("10086", claimsMap.get("uid"));
        assertNotNull(json.get("nonce")); // 必须有版本号
    }

    // ==========================================
    // 2. 策略测试 (真实逻辑)
    // ==========================================

    @Test
    @DisplayName("TC-05: 互斥登录 (SSO) - 后登录踢掉先登录")
    void testKickOut() throws Exception {
        String type = "ADMIN";
        Map<String, Object> claims = Map.of("uid", "user-1");

        // 1. 设备A 登录
        String tokenA = generator.generateToken(type, claims);

        // 2. 设备B 登录 (相同 uid，触发互斥覆盖)
        // v2.1 P1: 删除 Thread.sleep(10)，nonce 用 UUID 无需等待
        String tokenB = generator.generateToken(type, claims);

        // 3. 验证 Token A (应该失败)
        AuthException e = assertThrows(AuthException.class, () -> mockInterceptorCall(tokenA, type));
        assertEquals(10205, e.getCode()); // 账号已在别处登录

        // 4. 验证 Token B (应该成功)
        boolean result = mockInterceptorCall(tokenB, type);
        assertTrue(result);
    }

    @Test
    @DisplayName("TC-06 & TC-14: 高并发限次测试 (原子性验证)")
    void testConcurrentMaxUsage() throws InterruptedException {
        String type = "RESET";
        Map<String, Object> claims = Map.of("uid", "10086");
        // 生成一个只能用 1 次的 Token
        String token = generator.generateToken(type, claims);

        int threadCount = 50; // 50 个并发请求
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        // v2.1 P0: 非 AuthException 计数器，断言为 0
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // 模拟拦截器调用
                    mockInterceptorCall(token, type);
                    successCount.incrementAndGet();
                } catch (AuthException e) {
                    if (e.getCode() == 10201) { // 次数超限
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // v2.1 P0 修复：非 AuthException 不应发生，计入 errorCount 而非吞掉
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // v2.1 P0 修复：断言 latch.await 返回值，超时则测试失败而非假通过
        assertTrue(latch.await(10, TimeUnit.SECONDS),
                "所有线程应在 10s 内完成，可能超时");
        executor.shutdown();
        if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
        // v2.1 P0 修复：非 AuthException 不应发生
        assertEquals(0, errorCount.get(), "不应有非 AuthException 异常");
        // 预期：只有 1 个成功，49 个失败
        // 如果没有原子性保护，这里 successCount 可能会 > 1
        assertEquals(1, successCount.get(), "并发下只能有一次成功");
        assertEquals(49, failCount.get(), "其余请求应被拦截");
    }

    @Test
    @DisplayName("TC-08: 自动续期测试")
    void testAutoRenew() throws Exception {
        String type = "WEB";
        Map<String, Object> claims = Map.of("uid", "user-web");
        String token = generator.generateToken(type, claims);

        // 获取 Redis Key
        Set<String> keys = stringRedisTemplate.keys("test-app:accesstoken:WEB:*");
        String redisKey = keys.iterator().next();

        // 初始 TTL (配置了 auto-renew=true, renew-increment=3)
        // 注意：generateToken 会按 properties.expire-time 设置初始 TTL (这里是 3600)
        // 第一次拦截器调用后，才会重置为 renew-increment (3s)

        // 1. 第一次调用，触发续期 -> TTL 变为 3s
        mockInterceptorCall(token, type);
        Long ttl1 = stringRedisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        assertTrue(ttl1 <= 3 && ttl1 > 0, "第一次调用后 TTL 应重置为 3s");

        // 2. 等待 1.5 秒 (未过期) — v2.1 P1: 缩短为 1500ms（renew-increment=3s，1.5s 安全）
        Thread.sleep(1500);

        // 3. 再次调用，再次续期 -> TTL 变回 3s
        mockInterceptorCall(token, type);
        Long ttl2 = stringRedisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        assertTrue(ttl2 > 1.5, "再次调用后 TTL 应被重置");

        // 4. 等待过期（renew-increment=3s，等 4s 确保 key 过期）
        // v2.1 P1: 保留 sleep，因 renew-increment=3s 是测试核心配置，Awaitility 也需等 3s+
        Thread.sleep(4000);

        // 5. 再次调用 -> 失败
        AuthException e = assertThrows(AuthException.class, () -> mockInterceptorCall(token, type));
        assertEquals(10201, e.getCode()); // 过期
    }

    @Test
    @DisplayName("TC-04: 令牌注销")
    void testRevoke() throws Exception {
        String token = generator.generateToken("ADMIN", Map.of("uid", "revoke-test"));

        // 确保 Redis 存在
        Set<String> keys = stringRedisTemplate.keys("test-app:accesstoken:ADMIN:*");
        assertEquals(1, keys.size());

        // 注销
        generator.revokeToken(token);

        // 验证 Redis 删除
        keys = stringRedisTemplate.keys("test-app:accesstoken:ADMIN:*");
        assertTrue(keys.isEmpty());

        // 再次验证抛异常
        assertThrows(AuthException.class, () -> mockInterceptorCall(token, "ADMIN"));
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    /**
     * 模拟完整的拦截器调用流程
     */
    private boolean mockInterceptorCall(String token, String requiredType) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        // Mock 注解
        RequiresToken annotation = Mockito.mock(RequiresToken.class);
        Mockito.when(annotation.value()).thenReturn(requiredType);
        Mockito.when(annotation.exception()).thenReturn((Class) AuthException.class);
        Mockito.when(handlerMethod.getMethodAnnotation(RequiresToken.class)).thenReturn(annotation);

        return interceptor.preHandle(req, resp, handlerMethod);
    }
}