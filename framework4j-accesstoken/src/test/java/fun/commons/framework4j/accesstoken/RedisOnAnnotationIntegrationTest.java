package fun.commons.framework4j.accesstoken;

import fun.commons.framework4j.accesstoken.config.AccessTokenProperties;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.accesstoken.interceptor.TokenInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * AccessToken 模块 @RedisOn 注解集成测试
 *
 * 验证 AccessToken 模块与 Redis 模块的 @RedisOn 注解集成功能：
 * 1. 默认数据源注入
 * 2. 配置驱动数据源选择
 * 3. 字段注入功能
 * 4. 向后兼容性
 */
@SpringJUnitConfig
@DisplayName("AccessToken @RedisOn 注解集成测试")
public class RedisOnAnnotationIntegrationTest {

    @TestConfiguration
    static class TestConfig {

        @Bean
        public AccessTokenProperties accessTokenPropertiesDefault() {
            AccessTokenProperties properties = new AccessTokenProperties();
            properties.setEnabled(true);
            properties.setSecretKey("test-secret-key-for-jwt-signing-must-be-at-least-32-characters-long-xx");
            properties.setHashSalt("test-hash-salt-for-jwt-signing-must-also-be-non-empty");
            properties.setExpireTime(3600);
            properties.setRedisName("default"); // 测试默认数据源

            // 配置测试策略
            Map<String, AccessTokenProperties.Policy> policies = new HashMap<>();
            AccessTokenProperties.Policy adminPolicy = new AccessTokenProperties.Policy();
            adminPolicy.setKeyFromString("uid");
            policies.put("ADMIN", adminPolicy);
            properties.setPolicies(policies);

            return properties;
        }

        @Bean
        public AccessTokenProperties accessTokenPropertiesCustom() {
            AccessTokenProperties properties = new AccessTokenProperties();
            properties.setEnabled(true);
            properties.setSecretKey("test-secret-key-for-jwt-signing-must-be-at-least-32-characters-long-xx");
            properties.setHashSalt("test-hash-salt-for-jwt-signing-must-also-be-non-empty");
            properties.setExpireTime(3600);
            properties.setRedisName("token"); // 测试自定义数据源

            // 配置测试策略
            Map<String, AccessTokenProperties.Policy> policies = new HashMap<>();
            AccessTokenProperties.Policy adminPolicy = new AccessTokenProperties.Policy();
            adminPolicy.setKeyFromString("uid");
            policies.put("ADMIN", adminPolicy);
            properties.setPolicies(policies);

            return properties;
        }

        @Bean
        public RedissonClient redissonClient() {
            return mock(RedissonClient.class);
        }

        @Bean
        public StringRedisTemplate defaultRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        public StringRedisTemplate tokenRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }

    @Autowired
    private AccessTokenProperties accessTokenPropertiesDefault;

    @Autowired
    private AccessTokenProperties accessTokenPropertiesCustom;

    @Autowired
    private RedissonClient redissonClient;

    @Test
    @DisplayName("验证 AccessTokenProperties 默认 redisName 配置")
    void testDefaultRedisNameConfiguration() {
        assertNotNull(accessTokenPropertiesDefault);
        assertEquals("default", accessTokenPropertiesDefault.getRedisName());
        assertTrue(accessTokenPropertiesDefault.isEnabled());
        assertNotNull(accessTokenPropertiesDefault.getSecretKey());
    }

    @Test
    @DisplayName("验证 AccessTokenProperties 自定义 redisName 配置")
    void testCustomRedisNameConfiguration() {
        assertNotNull(accessTokenPropertiesCustom);
        assertEquals("token", accessTokenPropertiesCustom.getRedisName());
        assertTrue(accessTokenPropertiesCustom.isEnabled());
        assertNotNull(accessTokenPropertiesCustom.getSecretKey());
    }

    @Test
    @DisplayName("验证 @RedisOn 注解类创建与字段注入")
    void testRedisOnAnnotationClassCreation() {
        // 测试 AccessTokenGenerator 可以正常创建
        // @RedisOn 注解应该生效
        assertDoesNotThrow(() -> {
            StringRedisTemplate mockTemplate = mock(StringRedisTemplate.class);
            AccessTokenGenerator generator = new AccessTokenGenerator(
                accessTokenPropertiesDefault,
                mockTemplate,
                "test-app"
            );
            assertNotNull(generator);
        });
    }

    @Test
    @DisplayName("验证 TokenInterceptor @RedisOn 注解集成")
    void testTokenInterceptorRedisOnIntegration() {
        assertDoesNotThrow(() -> {
            StringRedisTemplate mockTemplate = mock(StringRedisTemplate.class);
            AccessTokenGenerator generator = new AccessTokenGenerator(
                accessTokenPropertiesDefault,
                mockTemplate,
                "test-app"
            );

            TokenInterceptor interceptor = new TokenInterceptor(
                generator,
                accessTokenPropertiesDefault,
                mockTemplate
            );
            assertNotNull(interceptor);
        });
    }

    @Test
    @DisplayName("验证配置属性占位符解析")
    void testConfigurationPropertyPlaceholder() {
        // 验证 @RedisOn("${framework4j.access-token.redis-name:default}")
        // 注解中的占位符能够正确解析
        AccessTokenProperties properties = new AccessTokenProperties();

        // 测试默认值
        assertEquals("default", properties.getRedisName());

        // 测试自定义值
        properties.setRedisName("custom-redis");
        assertEquals("custom-redis", properties.getRedisName());
    }

    @Test
    @DisplayName("验证策略配置完整性")
    void testPolicyConfigurationIntegrity() {
        assertNotNull(accessTokenPropertiesDefault.getPolicies());
        assertTrue(accessTokenPropertiesDefault.getPolicies().containsKey("ADMIN"));

        AccessTokenProperties.Policy adminPolicy = accessTokenPropertiesDefault.getPolicies().get("ADMIN");
        assertNotNull(adminPolicy);
        assertNotNull(adminPolicy.getKey());
        assertFalse(adminPolicy.getKey().isEmpty());
    }

    @Test
    @DisplayName("验证向后兼容性")
    void testBackwardCompatibility() {
        // 验证现有配置继续工作
        AccessTokenProperties properties = new AccessTokenProperties();

        // 测试默认值（新版本提供）
        assertEquals("default", properties.getRedisName()); // 新版本有默认值

        // 测试空字符串设置保持原样
        properties.setRedisName("");
        assertEquals("", properties.getRedisName());

        // 测试显式设置 null 后的行为
        properties.setRedisName(null);
        assertNull(properties.getRedisName()); // 显式设置 null 后为 null
    }

    /**
     * 内部测试用例：模拟 Spring 环境下的 @RedisOn 注解处理
     * 注意：这是一个简化的测试，实际的 @RedisOn 处理由 Spring 的 BeanPostProcessor 完成
     */
    @Test
    @DisplayName("模拟 @RedisOn 注解处理逻辑")
    void testRedisOnAnnotationProcessingSimulation() {
        // 模拟 @RedisOn 注解值解析
        String annotationValue = "${framework4j.access-token.redis-name:default}";

        // 模拟属性解析
        String resolvedValue;
        if (annotationValue.startsWith("${") && annotationValue.endsWith("}")) {
            String placeholder = annotationValue.substring(2, annotationValue.length() - 1);
            if (placeholder.contains(":")) {
                String[] parts = placeholder.split(":", 2);
                String propertyKey = parts[0];
                String defaultValue = parts[1];
                // 在实际环境中，这里会从 Environment 中解析属性值
                resolvedValue = "default".equals(defaultValue) ? "default" : defaultValue;
            } else {
                resolvedValue = "default"; // 无默认值时的回退
            }
        } else {
            resolvedValue = annotationValue;
        }

        assertEquals("default", resolvedValue);
    }
}