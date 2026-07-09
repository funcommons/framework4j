package fun.commons.framework4j.accesstoken;

import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.redis.annotation.RedisOn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import jakarta.annotation.Resource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AccessToken 核心功能单元测试
 * 验证 @RedisOn 注解集成和核心 Token 功能
 */
@SpringBootTest(classes = AccessTokenCoreFunctionalityTest.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.application.name=test-app",
    "framework4j.access-token.enabled=true",
    "framework4j.access-token.secret-key=test-secret-key-must-be-32-chars-long-for-security",
    "framework4j.access-token.hash-salt=test-hash-salt-for-security",
    "framework4j.access-token.expire-time=3600",
    "framework4j.access-token.redis-name=default",
    "framework4j.redis.enabled=true",
    "framework4j.redis.datasources.default.host=localhost",
    "framework4j.redis.datasources.default.port=6379",
    "framework4j.redis.datasources.default.database=0",
    "framework4j.redis.datasources.default.timeout=3000",
    "framework4j.redis.datasources.default.redisson.enabled=true",
    "framework4j.redis.datasources.default.redisson.config.singleServerConfig.address=redis://localhost:6379",
    "framework4j.redis.datasources.default.redisson.config.singleServerConfig.database=0",
    // 测试策略配置
    "framework4j.access-token.policies.TEST.key=uid",
    "framework4j.access-token.policies.TEST.expire-time=3600",
    "framework4j.access-token.policies.STORAGE_TEST.key=uid",
    "framework4j.access-token.policies.STORAGE_TEST.expire-time=3600",
    "framework4j.access-token.policies.USER.key=uid",
    "framework4j.access-token.policies.USER.expire-time=3600",
    "framework4j.access-token.policies.ADMIN.key=uid",
    "framework4j.access-token.policies.ADMIN.expire-time=3600",
    "framework4j.access-token.policies.REVOKE_TEST.key=uid",
    "framework4j.access-token.policies.REVOKE_TEST.expire-time=3600",
    "framework4j.access-token.policies.CONFIG_TEST.key=uid",
    "framework4j.access-token.policies.CONFIG_TEST.expire-time=3600",
    "framework4j.access-token.policies.BATCH_TEST.key=uid",
    "framework4j.access-token.policies.BATCH_TEST.expire-time=3600"
})
@RedisOn("default")
@DisplayName("AccessToken 核心功能测试")
class AccessTokenCoreFunctionalityTest {

    @Autowired
    private AccessTokenGenerator generator;

    @Resource
    @RedisOn("default")
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        // v2.1 P1: 删除未使用的 @Mock StringRedisTemplate + openMocks 死代码
        // 清理测试数据
        var keys = stringRedisTemplate.keys("test-app:accesstoken:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("验证 @RedisOn 注解注入成功")
    void testRedisOnInjection() {
        assertNotNull(generator, "AccessTokenGenerator 应该成功注入");
        assertNotNull(stringRedisTemplate, "StringRedisTemplate 应该通过 @RedisOn 注解成功注入");
    }

    @Test
    @DisplayName("验证基本 Token 生成功能")
    void testBasicTokenGeneration() {
        String type = "TEST";
        Map<String, Object> claims = Map.of("uid", "test-user-123", "role", "user");

        String token = generator.generateToken(type, claims);

        assertNotNull(token, "Token 应该成功生成");
        assertFalse(token.isEmpty(), "Token 不应该为空");
        assertTrue(token.length() > 20, "Token 应该有合理的长度"); // JWT 通常较长
    }

    @Test
    @DisplayName("验证 Token 生成后的 Redis 存储")
    void testTokenRedisStorage() {
        String type = "STORAGE_TEST";
        Map<String, Object> claims = Map.of("uid", "test-user-456", "department", "IT");

        String token = generator.generateToken(type, claims);

        // 验证 Redis 中存储了数据
        var keys = stringRedisTemplate.keys("test-app:accesstoken:" + type + ":*");
        assertNotNull(keys, "应该有相关的 Redis key");
        assertFalse(keys.isEmpty(), "Redis 中应该存储了 Token 数据");

        // 验证存储的数据格式
        String redisKey = keys.iterator().next();
        String redisValue = stringRedisTemplate.opsForValue().get(redisKey);
        assertNotNull(redisValue, "Redis 值不应该为空");
        assertTrue(redisValue.contains("\"type\":\"" + type + "\""), "Redis 值应该包含正确的类型");
        assertTrue(redisValue.contains("\"uid\":\"test-user-456\""), "Redis 值应该包含用户数据");
    }

    @Test
    @DisplayName("验证不同类型 Token 的隔离")
    void testTokenTypeIsolation() {
        String type1 = "USER";
        String type2 = "ADMIN";
        Map<String, Object> claims = Map.of("uid", "user-789");

        String token1 = generator.generateToken(type1, claims);
        String token2 = generator.generateToken(type2, claims);

        // 验证不同类型的 Token 存储在不同的 Redis key 中
        var type1Keys = stringRedisTemplate.keys("test-app:accesstoken:" + type1 + ":*");
        var type2Keys = stringRedisTemplate.keys("test-app:accesstoken:" + type2 + ":*");

        assertEquals(1, type1Keys.size(), "USER 类型应该有一个 key");
        assertEquals(1, type2Keys.size(), "ADMIN 类型应该有一个 key");
        assertNotEquals(type1Keys.iterator().next(), type2Keys.iterator().next(),
                      "不同类型应该使用不同的 Redis key");
    }

    @Test
    @DisplayName("验证 Token 注销功能")
    void testTokenRevocation() {
        String type = "REVOKE_TEST";
        Map<String, Object> claims = Map.of("uid", "revoke-user");

        String token = generator.generateToken(type, claims);

        // 验证 Token 已存储
        var keys = stringRedisTemplate.keys("test-app:accesstoken:" + type + ":*");
        assertEquals(1, keys.size(), "Token 应该已存储");

        // 注销 Token
        generator.revokeToken(token);

        // 验证 Token 已被删除
        keys = stringRedisTemplate.keys("test-app:accesstoken:" + type + ":*");
        assertTrue(keys.isEmpty(), "Token 应该已被删除");
    }

    @Test
    @DisplayName("验证配置参数传递")
    void testConfigurationParameters() {
        // 验证配置已正确传递到 generator
        assertDoesNotThrow(() -> {
            // CONFIG_TEST 策略要求 key 字段是 uid，所以需要提供 uid
            String token = generator.generateToken("CONFIG_TEST", Map.of("uid", "config-test-user"));
            assertNotNull(token, "配置传递应该正常，Token 可以生成");
        }, "配置参数传递应该不会导致异常");
    }

    @Test
    @DisplayName("验证批量 Token 生成")
    void testBatchTokenGeneration() {
        String type = "BATCH_TEST";
        int count = 5;

        for (int i = 0; i < count; i++) {
            Map<String, Object> claims = Map.of("uid", "batch-user-" + i, "index", i);
            String token = generator.generateToken(type, claims);

            assertNotNull(token, "第 " + (i + 1) + " 个 Token 应该成功生成");
            assertFalse(token.isEmpty(), "第 " + (i + 1) + " 个 Token 不应该为空");
        }

        // 验证所有 Token 都已存储
        var keys = stringRedisTemplate.keys("test-app:accesstoken:" + type + ":*");
        assertEquals(count, keys.size(), "应该存储了 " + count + " 个 Token");
    }

    @Test
    @DisplayName("验证 Redis 连接和操作")
    void testRedisOperations() {
        // 直接测试 StringRedisTemplate 的 Redis 操作
        String testKey = "test:redis:operations";
        String testValue = "test-value-123";

        // 写入
        stringRedisTemplate.opsForValue().set(testKey, testValue);

        // 读取
        String retrievedValue = stringRedisTemplate.opsForValue().get(testKey);
        assertEquals(testValue, retrievedValue, "Redis 读写操作应该正常");

        // 删除
        assertTrue(stringRedisTemplate.delete(testKey), "Redis 删除操作应该成功");

        // 验证已删除
        assertNull(stringRedisTemplate.opsForValue().get(testKey), "数据应该已被删除");
    }

    @SpringBootApplication
    static class TestApplication {}
}