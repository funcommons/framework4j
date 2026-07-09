package fun.commons.framework4j.redis.integration;

import fun.commons.framework4j.redis.annotation.RedisOn;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多 Redis 数据源集成测试
 *
 * @since 1.0.0
 */
@SpringBootTest(classes = {MultiRedisIntegrationTest.TestConfiguration.class})
@ActiveProfiles("test")
@DisplayName("多 Redis 数据源集成测试")
@TestPropertySource(locations = "classpath:application-test.yml")
class MultiRedisIntegrationTest {

    @Autowired
    private MultiRedisManager multiRedisManager;

    @Autowired
    @Qualifier("defaultRedisTemplate")
    private StringRedisTemplate defaultRedis;

    @Autowired
    @Qualifier("cacheRedisTemplate")
    private StringRedisTemplate cacheRedis;

    @Autowired
    @Qualifier("businessRedisTemplate")
    private StringRedisTemplate businessRedis;

    @Autowired(required = false)
    @Qualifier("businessRedissonClient")
    private RedissonClient businessRedisson;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private BusinessService businessService;

    @Test
    @DisplayName("测试多数据源注册")
    void testMultipleDatasources() {
        // 验证数据源已注册
        assertTrue(multiRedisManager.containsDatasource("default"));
        assertTrue(multiRedisManager.containsDatasource("cache"));
        assertTrue(multiRedisManager.containsDatasource("business"));
        assertTrue(multiRedisManager.containsDatasource("lock"));

        // 验证别名
        assertTrue(multiRedisManager.containsDatasource("temp"));

        // 验证数据源数量
        List<String> names = multiRedisManager.getAllDatasourceNames();
        assertTrue(names.size() >= 5); // default, cache, temp, business, lock
    }

    @Test
    @DisplayName("测试通过 @Qualifier 注入")
    void testQualifierInjection() {
        assertNotNull(defaultRedis);
        assertNotNull(cacheRedis);
        assertNotNull(businessRedis);
    }

    @Test
    @DisplayName("测试 RedisTemplate 基本操作")
    void testRedisTemplateOperations() {
        String key = "test:key";
        String value = "test-value";

        // 写入
        defaultRedis.opsForValue().set(key, value, 60, TimeUnit.SECONDS);

        // 读取
        String retrieved = defaultRedis.opsForValue().get(key);
        assertEquals(value, retrieved);

        // 删除
        Boolean deleted = defaultRedis.delete(key);
        assertTrue(deleted);

        // 验证已删除
        String afterDelete = defaultRedis.opsForValue().get(key);
        assertNull(afterDelete);
    }

    @Test
    @DisplayName("测试 RedissonClient 基本操作")
    void testRedissonClientOperations() {
        // v2.1 P1: 改 assumeTrue 显式跳过，避免静默假通过
        org.junit.jupiter.api.Assumptions.assumeTrue(businessRedisson != null, "需 Redisson 环境");

        String key = "test:bucket";
        String value = "bucket-value";

        // 使用 RBucket
        RBucket<String> bucket = businessRedisson.getBucket(key);
        bucket.set(value, 60, TimeUnit.SECONDS);

        // 读取
        String retrieved = bucket.get();
        assertEquals(value, retrieved);

        // 删除
        bucket.delete();

        // 验证已删除
        assertNull(bucket.get());
    }

    @Test
    @DisplayName("测试 @RedisOn 注解注入 - CacheService")
    void testRedisOnAnnotation_CacheService() {
        assertNotNull(cacheService);
        assertNotNull(cacheService.getRedisTemplate());

        // 测试数据操作
        cacheService.set("cache:test", "value");
        String value = cacheService.get("cache:test");
        assertEquals("value", value);
    }

    @Test
    @DisplayName("测试 @RedisOn 注解注入 - BusinessService")
    void testRedisOnAnnotation_BusinessService() {
        assertNotNull(businessService);
        assertNotNull(businessService.getRedisTemplate());

        // 测试数据操作
        businessService.saveData("business:test", "business-value");
        String value = businessService.getData("business:test");
        assertEquals("business-value", value);
    }

    @Test
    @DisplayName("测试别名功能")
    void testAliasFeature() {
        // cache 和 temp 是同一个数据源的别名
        StringRedisTemplate cacheTemplate = multiRedisManager.getStringRedisTemplate("cache");
        StringRedisTemplate tempTemplate = multiRedisManager.getStringRedisTemplate("temp");

        // 验证是同一个实例
        assertEquals(cacheTemplate, tempTemplate);

        // 使用 cache 写入
        String key = "alias:test";
        cacheTemplate.opsForValue().set(key, "alias-value");

        // 使用 temp 读取
        String value = tempTemplate.opsForValue().get(key);
        assertEquals("alias-value", value);
    }

    @Test
    @DisplayName("测试 MultiRedisManager 动态获取")
    void testMultiRedisManagerDynamicAccess() {
        String[] datasources = {"default", "cache", "business", "lock"};

        for (String name : datasources) {
            StringRedisTemplate template = multiRedisManager.getStringRedisTemplate(name);
            assertNotNull(template, "数据源 " + name + " 应该存在");
        }
    }

    /**
     * 测试配置类
     */
    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @org.springframework.context.annotation.ComponentScan(basePackages = {
            "fun.commons.framework4j.redis.integration"  // 只扫描本测试类的内部Service
    })
    static class TestConfiguration {
    }

    /**
     * 使用 @RedisOn 注解的缓存服务
     */
    @Service
    @RedisOn("cache")
    @org.springframework.context.annotation.DependsOn("multiRedisManager")
    static class CacheService {
        private StringRedisTemplate redisTemplate;

        public StringRedisTemplate getRedisTemplate() {
            return redisTemplate;
        }

        public void set(String key, String value) {
            redisTemplate.opsForValue().set(key, value, 300, TimeUnit.SECONDS);
        }

        public String get(String key) {
            return redisTemplate.opsForValue().get(key);
        }
    }

    /**
     * 使用 @RedisOn 注解的业务服务
     */
    @Service
    @RedisOn("business")
    @org.springframework.context.annotation.DependsOn("multiRedisManager")
    static class BusinessService {
        private StringRedisTemplate redisTemplate;

        private RedissonClient redissonClient;

        public StringRedisTemplate getRedisTemplate() {
            return redisTemplate;
        }

        public void saveData(String key, String value) {
            redisTemplate.opsForValue().set(key, value);
        }

        public String getData(String key) {
            return redisTemplate.opsForValue().get(key);
        }
    }
}
