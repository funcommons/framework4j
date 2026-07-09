package fun.commons.framework4j.accesstoken.performance;

import fun.commons.framework4j.accesstoken.config.AccessTokenProperties;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.accesstoken.util.TokenUtils;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 性能与边界测试 (Performance & Boundary)
 * 测试用例: TC-13 to TC-16
 *
 * 使用 framework4j-redis 模块注入 Redis
 *
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = PerformanceBoundaryTest.TestConfiguration.class)
@ActiveProfiles("test")
@Tag("performance")
@DisplayName("性能与边界测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PerformanceBoundaryTest {

    @Autowired
    private AccessTokenGenerator generator;

    @Autowired
    private MultiRedisManager redisManager;

    @Autowired
    private AccessTokenProperties properties;

    private StringRedisTemplate redisTemplate;

    private static final String SECRET_KEY = "test-secret-key-for-jwt-signing-must-be-at-least-32-characters-long-xx";

    @BeforeEach
    void setUp() {
        // 从 MultiRedisManager 获取默认 RedisTemplate
        redisTemplate = redisManager.getStringRedisTemplate("default");

        // 清理测试数据 (使用 generator 的方法构建正确的 key 前缀)
        String keyPattern = generator.buildRedisKey("*", "*").replace(":*:*", ":*");
        var keys = redisTemplate.keys(keyPattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 计算 Redis Key Hash (与 AccessTokenGenerator 逻辑一致)
     */
    private String calculateHash(String type, Map<String, Object> claims) {
        AccessTokenProperties.Policy policy = properties.getPolicies().get(type);
        List<String> keys = policy.getKey();
        StringBuilder keyValue = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) keyValue.append("_");
            keyValue.append(claims.get(keys.get(i)));
        }
        return TokenUtils.calculateKeyHash(keyValue.toString(), properties.getHashSalt());
    }

    @Test
    @Order(1)
    @Tag("performance")
@DisplayName("TC-13: 大数据 Claims")
    void testLargeDataClaims() {
        log.info("========== TC-13: 大数据 Claims ==========");

        // Arrange - 构造 10KB 的描述字段
        StringBuilder largeDesc = new StringBuilder();
        for (int i = 0; i < 1024; i++) {
            largeDesc.append("ABCDEFGHIJ"); // 10 chars * 1024 = 10KB
        }

        String type = "ADMIN";
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", "1001");
        claims.put("role", "admin");
        claims.put("desc", largeDesc.toString());

        log.info(">>> Claims 描述字段大小: {} bytes", largeDesc.length());

        // Act
        String token = generator.generateToken(type, claims);
        log.info(">>> 生成 Token 长度: {} chars", token.length());

        // Assert
        assertNotNull(token, "Token 不应为空");
        // Token 长度应保持合理 (JWT Header + Payload + Signature)
        // 由于 claims 不直接编码到 JWT 中(存 Redis)，Token 长度应较短
        assertTrue(token.length() < 500, "Token 长度应 < 500 (claims 存储在 Redis)");

        // 验证 Redis Value 包含大数据
        String hash = calculateHash(type, claims);
        String redisKey = generator.buildRedisKey(type, hash);
        String redisValue = redisTemplate.opsForValue().get(redisKey);

        assertNotNull(redisValue, "Redis Value 不应为空");
        assertTrue(redisValue.contains(largeDesc.substring(0, 100)),
            "Redis Value 应包含大数据描述");
        log.info(">>> Redis Value 长度: {} bytes", redisValue.length());

        log.info("✅ TC-13 通过: 大数据 Claims 处理正常");
    }

    @Test
    @Order(2)
    @Tag("performance")
@DisplayName("TC-14: 高并发原子计数")
    void testConcurrentMaxUsage() throws InterruptedException {
        log.info("========== TC-14: 高并发原子计数 ==========");

        // Arrange
        int threadCount = 100;
        String type = "RESET"; // max-usage=1

        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", "1001");

        String token = generator.generateToken(type, claims);
        log.info(">>> 生成限次 Token: {}", token);

        String hash = calculateHash(type, claims);
        String usageKey = generator.buildRedisKey(type, hash) + ":usage";

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // Act - 100 个线程并发抢夺 1 次使用机会
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await(); // 等待发令枪
                    // 模拟拦截器调用 (原子 INCR)
                    Long currentUsage = redisTemplate.opsForValue().increment(usageKey);
                    if (currentUsage != null && currentUsage <= 1) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown(); // 发令
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);

        // Assert
        assertTrue(completed, "所有线程应在 30 秒内完成");
        assertEquals(1, successCount.get(), "一次性 Token 在高并发下只能被消费一次");
        assertEquals(99, failCount.get(), "其余 99 个请求应被拦截");

        log.info(">>> 成功请求数: {}, 失败请求数: {}", successCount.get(), failCount.get());
        log.info("✅ TC-14 通过: 高并发限次原子性验证成功");
    }

    @Test
    @Order(3)
    @Tag("performance")
@DisplayName("TC-15: 特殊字符 Key")
    void testSpecialCharacterKey() {
        log.info("========== TC-15: 特殊字符 Key ==========");

        // Arrange - Key 包含冒号、斜杠、Emoji
        String type = "ADMIN";
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", "user:123/emoji_\uD83D\uDD11"); // 包含冒号、斜杠、钥匙 Emoji
        claims.put("role", "admin:super/test");

        log.info(">>> 特殊字符 UID: {}", claims.get("uid"));

        // Act
        String token = generator.generateToken(type, claims);
        log.info(">>> 生成 Token: {}", token);

        // Assert
        assertNotNull(token, "Token 不应为空");

        // 计算 Hash
        String hash = calculateHash(type, claims);

        // Hash 应为标准 Hex 格式
        assertTrue(hash.matches("[a-f0-9]+"), "Hash 应为标准 Hex 格式");
        log.info(">>> Hash: {}", hash);

        // 验证 Redis 读写正常
        String redisKey = generator.buildRedisKey(type, hash);
        Boolean exists = redisTemplate.hasKey(redisKey);
        assertTrue(exists, "Redis Key 应存在");

        String redisValue = redisTemplate.opsForValue().get(redisKey);
        assertNotNull(redisValue, "Redis Value 不应为空");
        assertTrue(redisValue.contains("emoji"), "Redis Value 应包含原始数据");

        log.info("✅ TC-15 通过: 特殊字符处理正常");
    }

    @Test
    @Order(4)
    @Tag("performance")
@DisplayName("TC-16: 极短有效期")
    void testVeryShortTtl() throws InterruptedException {
        log.info("========== TC-16: 极短有效期 ==========");

        // Arrange - 使用 INVITE 类型 (expire=60)，手动设置极短 TTL
        String type = "INVITE";
        Map<String, Object> claims = new HashMap<>();
        claims.put("code", "SHORT-TTL-TEST");

        String token = generator.generateToken(type, claims);
        log.info(">>> 生成 Token: {}", token);

        String hash = calculateHash(type, claims);
        String redisKey = generator.buildRedisKey(type, hash);

        // Act 1 - 立即验证应成功
        Boolean existsImmediately = redisTemplate.hasKey(redisKey);
        assertTrue(existsImmediately, "立即验证: Token 应有效");
        log.info(">>> 立即验证: Token 存在");

        // 手动设置极短 TTL (1 秒)
        redisTemplate.expire(redisKey, 1, TimeUnit.SECONDS);
        Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.MILLISECONDS);
        log.info(">>> 设置 TTL: {} ms", ttl);

        // v2.1 P1: 改轮询替代 sleep(1100)，避免边界紧邻 flaky
        long deadline = System.currentTimeMillis() + 3000;
        Boolean existsAfterExpiry;
        do {
            existsAfterExpiry = redisTemplate.hasKey(redisKey);
            if (!existsAfterExpiry) break;
            Thread.sleep(100);
        } while (System.currentTimeMillis() < deadline);

        // Assert - 过期后应失败
        assertFalse(existsAfterExpiry, "过期后: Token 应无效");
        log.info(">>> 过期后验证: Token 已过期");

        log.info("✅ TC-16 通过: 极短有效期处理正常");
    }

    @Test
    @Order(5)
    @Tag("performance")
@DisplayName("TC-14b: 高并发 Token 生成")
    void testConcurrentTokenGeneration() throws InterruptedException {
        log.info("========== TC-14b: 高并发 Token 生成 ==========");

        // Arrange
        int threadCount = 50;
        String type = "ADMIN";

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Act - 50 个线程并发生成 Token
        for (int i = 0; i < threadCount; i++) {
            final int userId = 1000 + i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    Map<String, Object> claims = new HashMap<>();
                    claims.put("uid", String.valueOf(userId));
                    claims.put("role", "user");

                    String token = generator.generateToken(type, claims);
                    if (token != null && !token.isEmpty()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("生成 Token 异常: {}", e.getMessage());
                    errorCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown(); // 发令
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);

        // Assert
        assertTrue(completed, "所有线程应在 30 秒内完成");
        assertEquals(threadCount, successCount.get(), "所有 Token 生成应成功");
        assertEquals(0, errorCount.get(), "不应有错误");

        log.info(">>> 成功生成 Token 数: {}, 错误数: {}", successCount.get(), errorCount.get());
        log.info("✅ TC-14b 通过: 高并发 Token 生成验证成功");
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class TestConfiguration {
    }
}
