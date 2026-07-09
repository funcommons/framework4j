package fun.commons.framework4j.accesstoken;

import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.redis.annotation.RedisOn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 基本集成测试 - 验证 @RedisOn 注解核心功能
 */
@SpringBootTest(classes = BasicIntegrationTest.TestConfiguration.class)
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
    "framework4j.access-token.policies.STORAGE_TEST.expire-time=3600"
})
@RedisOn("default")
@DisplayName("基本 @RedisOn 集成测试")
class BasicIntegrationTest {

    @Autowired
    private AccessTokenGenerator generator;

    @Resource
    @RedisOn("default")
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedissonClient redissonClient;

    @BeforeEach
    void setUp() {
        // 清理测试数据
        var keys = stringRedisTemplate.keys("test-app:accesstoken:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("验证 @RedisOn 注解的 StringRedisTemplate 注入")
    void testRedisOnInjection() {
        assertNotNull(stringRedisTemplate, "StringRedisTemplate 应该通过 @RedisOn 注解成功注入");
        assertNotNull(generator, "AccessTokenGenerator 应该成功注入");
    }

    @Test
    @DisplayName("验证基本 Token 生成功能")
    void testBasicTokenGeneration() {
        String type = "TEST";
        Map<String, Object> claims = Map.of("uid", "test-user-123", "role", "user");

        // 这应该能够正常工作，不抛出异常
        assertDoesNotThrow(() -> {
            String token = generator.generateToken(type, claims);
            assertNotNull(token, "Token 应该成功生成");
            assertFalse(token.isEmpty(), "Token 不应该为空");
        });
    }

    @Test
    @DisplayName("验证 Redis 数据写入")
    void testRedisDataWrite() {
        // 使用注入的 StringRedisTemplate 写入测试数据
        stringRedisTemplate.opsForValue().set("test:key", "test-value");

        String value = stringRedisTemplate.opsForValue().get("test:key");
        assertEquals("test-value", value, "Redis 数据应该正确写入和读取");

        // 清理
        stringRedisTemplate.delete("test:key");
    }

    @Test
    @DisplayName("验证 Token 生成后 Redis 存储")
    void testTokenRedisStorage() {
        String type = "STORAGE_TEST";
        Map<String, Object> claims = Map.of("uid", "test-user-456");

        String token = generator.generateToken(type, claims);

        // 验证 Redis 中有数据（基于已知的 key 格式）
        var keys = stringRedisTemplate.keys("test-app:accesstoken:" + type + ":*");
        assertNotNull(keys, "应该有相关的 Redis key");
        assertTrue(!keys.isEmpty(), "Redis 中应该存储了 Token 数据");

        // 清理
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @SpringBootApplication
    static class TestConfiguration {}
}