package fun.commons.framework4j.accesstoken.core;

import fun.commons.framework4j.accesstoken.config.AccessTokenProperties;
import fun.commons.framework4j.accesstoken.exception.AuthException;
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
 * 核心功能测试 (Core Functionality)
 * 测试用例: TC-01 to TC-04
 *
 * 使用 framework4j-redis 模块注入 Redis
 *
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = CoreFunctionalityTest.TestConfiguration.class)
@ActiveProfiles("test")
@DisplayName("核心功能测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CoreFunctionalityTest {

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
    @DisplayName("TC-01: 单主键 Token 生成与校验")
    void testSingleKeyTokenGeneration() {
        log.info("========== TC-01: 单主键 Token 生成与校验 ==========");

        // Arrange
        String type = "ADMIN";
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", "1001");
        claims.put("role", "admin");

        // Act
        String token = generator.generateToken(type, claims);
        log.info(">>> 生成 Token: {}", token);

        // Assert
        assertNotNull(token, "Token 不应为空");
        assertTrue(token.length() > 0, "Token 应有内容");

        // 解析 Token
        Map<String, Object> parsedClaims = TokenUtils.parseToken(token, SECRET_KEY);
        assertNotNull(parsedClaims, "解析后的 Claims 不应为空");
        assertEquals(type, parsedClaims.get("type"), "Token 类型应匹配");
        assertTrue(parsedClaims.containsKey("nonce"), "Token 应包含 nonce");

        // 验证 Redis Key (使用 calculateHash 计算)
        String hash = calculateHash(type, claims);
        String redisKey = generator.buildRedisKey(type, hash);
        Boolean exists = redisTemplate.hasKey(redisKey);
        assertTrue(exists, "Redis 中应存在对应的 Key");

        // 验证 TTL
        Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        assertNotNull(ttl, "TTL 不应为空");
        assertTrue(ttl > 3500 && ttl <= 3600, "TTL 应约为 3600 秒");

        log.info("✅ TC-01 通过: Token 生成与校验正常");
    }

    @Test
    @Order(2)
    @DisplayName("TC-02: 联合主键 Token 生成与校验")
    void testCompositeKeyTokenGeneration() {
        log.info("========== TC-02: 联合主键 Token 生成与校验 ==========");

        // Arrange
        String type = "APP";
        Map<String, Object> claims1 = new HashMap<>();
        claims1.put("uid", "1001");
        claims1.put("dev", "ip15");

        Map<String, Object> claims2 = new HashMap<>();
        claims2.put("uid", "1001");
        claims2.put("dev", "android");

        // Act
        String token1 = generator.generateToken(type, claims1);
        String token2 = generator.generateToken(type, claims2);

        log.info(">>> Token1 (ip15): {}", token1);
        log.info(">>> Token2 (android): {}", token2);

        // Assert
        assertNotEquals(token1, token2, "不同设备应生成不同 Token");

        // 计算 Hash
        String hash1 = calculateHash(type, claims1);
        String hash2 = calculateHash(type, claims2);

        assertNotEquals(hash1, hash2, "联合主键应生成不同的 Hash");

        // 验证 Redis 中存在两个不同的 Key
        String redisKey1 = generator.buildRedisKey(type, hash1);
        String redisKey2 = generator.buildRedisKey(type, hash2);

        assertTrue(redisTemplate.hasKey(redisKey1), "Redis 中应存在 Key1");
        assertTrue(redisTemplate.hasKey(redisKey2), "Redis 中应存在 Key2");

        log.info("✅ TC-02 通过: 联合主键生成不同 Token");
    }

    @Test
    @Order(3)
    @DisplayName("TC-03: 缺少必要 Key 字段拦截")
    void testMissingRequiredKeyField() {
        log.info("========== TC-03: 缺少必要 Key 字段拦截 ==========");

        // Arrange
        String type = "ADMIN";
        Map<String, Object> claims = new HashMap<>();
        claims.put("name", "admin"); // 缺少 uid 字段

        // Act & Assert
        Exception exception = assertThrows(AuthException.class, () -> {
            generator.generateToken(type, claims);
        });

        log.info(">>> 捕获异常: {}", exception.getMessage());
        assertTrue(exception.getMessage().contains("uid") ||
                   exception.getMessage().contains("必需") ||
                   exception.getMessage().contains("缺少"),
            "异常信息应提示缺少 uid 字段");

        log.info("✅ TC-03 通过: 缺少必要字段被正确拦截");
    }

    @Test
    @Order(4)
    @DisplayName("TC-04: 令牌注销 (Revoke)")
    void testTokenRevocation() {
        log.info("========== TC-04: 令牌注销 (Revoke) ==========");

        // Arrange
        String type = "ADMIN";
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", "1001");
        claims.put("role", "admin");

        String token = generator.generateToken(type, claims);
        log.info(">>> 生成 Token: {}", token);

        // 验证 Token 有效
        String hash = calculateHash(type, claims);
        String redisKey = generator.buildRedisKey(type, hash);
        assertTrue(redisTemplate.hasKey(redisKey), "注销前 Redis Key 应存在");

        // Act - 注销 Token
        generator.revokeToken(token);
        log.info(">>> Token 已注销");

        // Assert
        Boolean exists = redisTemplate.hasKey(redisKey);
        assertFalse(exists, "注销后 Redis Key 应被删除");

        // v2.1 P0 修复：原 try 块空，无任何断言验证 revoke 后校验失败。
        // 改为验证 jti 已进入撤销 Set，isRevoked 返回 true。
        String jti = (String) TokenUtils.parseToken(token, SECRET_KEY).get("jti");
        assertTrue(generator.isRevoked(jti), "注销后 jti 应在撤销 Set 中，isRevoked 返回 true");
        log.info("✅ TC-04 通过: Token 注销成功 + jti 已加入撤销 Set");
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class TestConfiguration {
    }
}
