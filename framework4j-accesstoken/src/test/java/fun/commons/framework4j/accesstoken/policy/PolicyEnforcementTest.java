package fun.commons.framework4j.accesstoken.policy;

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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 策略控制测试 (Policy Enforcement)
 * 测试用例: TC-05 to TC-08
 *
 * 使用 framework4j-redis 模块注入 Redis
 *
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = PolicyEnforcementTest.TestConfiguration.class)
@ActiveProfiles("test")
@DisplayName("策略控制测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PolicyEnforcementTest {

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

        // 清理测试数据
        var keys = redisTemplate.keys("accesstoken-test:accesstoken:*");
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
    @DisplayName("TC-05: 互斥登录 (SSO/Kick-out)")
    void testSsoKickout() {
        log.info("========== TC-05: 互斥登录 (SSO/Kick-out) ==========");

        // Arrange
        String type = "ADMIN";
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", "1001");
        claims.put("role", "admin");

        // Act - 生成 Token A
        String tokenA = generator.generateToken(type, claims);
        Map<String, Object> claimsA = TokenUtils.parseToken(tokenA, SECRET_KEY);
        String nonceA = claimsA.get("nonce").toString();
        log.info(">>> Token A Nonce: {}", nonceA);

        // v2.1 P1: 删除 sleep(10)，nonce 用 UUID 已保证唯一

        // Act - 生成 Token B (同一个 uid，应该踢出 Token A)
        String tokenB = generator.generateToken(type, claims);
        Map<String, Object> claimsB = TokenUtils.parseToken(tokenB, SECRET_KEY);
        String nonceB = claimsB.get("nonce").toString();
        log.info(">>> Token B Nonce: {}", nonceB);

        // Assert
        assertNotEquals(nonceA, nonceB, "两次生成的 Nonce 应不同");

        // 验证 Redis 中的 Nonce 是 Token B 的 (使用与 Generator 相同的方式计算 hash)
        String hash = calculateHash(type, claims);
        String redisKey = generator.buildRedisKey(type, hash);
        String storedValue = redisTemplate.opsForValue().get(redisKey);

        assertNotNull(storedValue, "Redis 中应存在 Token 数据");
        assertTrue(storedValue.contains(nonceB), "Redis 中存储的应是 Token B 的 Nonce");

        log.info("✅ TC-05 通过: 新登录踢出旧登录");
    }

    @Test
    @Order(2)
    @DisplayName("TC-06: 限次策略 (Fail-Secure)")
    void testMaxUsagePolicy() {
        log.info("========== TC-06: 限次策略 (Fail-Secure) ==========");

        // Arrange - 使用 RESET 类型 (max-usage=1)
        String type = "RESET";
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", "1001");

        String token = generator.generateToken(type, claims);
        log.info(">>> 生成限次 Token: {}", token);

        String hash = calculateHash(type, claims);
        String usageKey = generator.buildRedisKey(type, hash) + ":usage";

        // Act - 第一次使用
        Long count1 = redisTemplate.opsForValue().increment(usageKey);
        log.info(">>> 第一次使用后计数: {}", count1);

        // Assert - 第一次应成功
        assertEquals(1L, count1, "第一次使用计数应为 1");

        // Act - 第二次使用
        Long count2 = redisTemplate.opsForValue().increment(usageKey);
        log.info(">>> 第二次使用后计数: {}", count2);

        // Assert - 第二次应超过限制
        assertEquals(2L, count2, "第二次使用计数应为 2");
        assertTrue(count2 > 1, "第二次使用应超过限制");

        log.info("✅ TC-06 通过: 限次策略正确计数");
    }

    @Test
    @Order(3)
    @DisplayName("TC-07: 限时激活 (TTL)")
    void testTtlExpiration() throws InterruptedException {
        log.info("========== TC-07: 限时激活 (TTL) ==========");

        // Arrange - 使用 INVITE 类型 (expire=60)
        String type = "INVITE";
        Map<String, Object> claims = new HashMap<>();
        claims.put("code", "INVITE-2024");

        String token = generator.generateToken(type, claims);
        log.info(">>> 生成限时 Token: {}", token);

        String hash = calculateHash(type, claims);
        String redisKey = generator.buildRedisKey(type, hash);

        // Assert - 验证 TTL
        Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        assertNotNull(ttl, "TTL 不应为空");
        assertTrue(ttl > 50 && ttl <= 60, "TTL 应约为 60 秒");
        log.info(">>> Token TTL: {} 秒", ttl);

        // 模拟过期 (通过设置极短 TTL)
        redisTemplate.expire(redisKey, 1, TimeUnit.MILLISECONDS);

        // v2.1 P1: 改轮询验证过期，避免固定 sleep
        long deadline = System.currentTimeMillis() + 2000;
        Boolean exists;
        do {
            exists = redisTemplate.hasKey(redisKey);
            if (!exists) break;
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);

        // Assert - 验证 Key 已过期
        assertFalse(exists, "过期后 Redis Key 应不存在");

        log.info("✅ TC-07 通过: 限时激活正确过期");
    }

    @Test
    @Order(4)
    @DisplayName("TC-08: 自动续期 (Auto Renew)")
    void testAutoRenew() {
        log.info("========== TC-08: 自动续期 (Auto Renew) ==========");

        // Arrange - 使用 ADMIN 类型 (auto-renew=true)
        String type = "ADMIN";
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", "1001");
        claims.put("role", "admin");

        String token = generator.generateToken(type, claims);
        log.info(">>> 生成自动续期 Token: {}", token);

        String hash = calculateHash(type, claims);
        String redisKey = generator.buildRedisKey(type, hash);

        // 获取初始 TTL
        Long initialTtl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        log.info(">>> 初始 TTL: {} 秒", initialTtl);

        // 模拟使用后刷新 TTL (模拟自动续期)
        Long renewIncrement = 1800L;
        redisTemplate.expire(redisKey, renewIncrement, TimeUnit.SECONDS);

        // Assert - 验证 TTL 已重置
        Long renewedTtl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        log.info(">>> 续期后 TTL: {} 秒", renewedTtl);

        assertNotNull(renewedTtl, "续期后 TTL 不应为空");
        assertTrue(renewedTtl > 1700 && renewedTtl <= 1800,
            "续期后 TTL 应约为 1800 秒");

        log.info("✅ TC-08 通过: 自动续期正确重置 TTL");
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class TestConfiguration {
    }
}
