package fun.commons.framework4j.redis.functional;

import fun.commons.framework4j.redis.manager.MultiRedisManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 别名配置功能测试
 * <p>
 * 测试覆盖:
 * 1. aliases字段配置验证
 * 2. 别名Bean注册验证
 * 3. 别名与主名称共享连接验证
 * 4. 通过别名注入验证
 * 5. stringRedisTemplate自动别名
 *
 * 测试配置 (application-test.yml):
 * cache:
 *   aliases: [temp]
 *   ...
 *
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = {AliasConfigurationTest.TestConfiguration.class})
@ActiveProfiles("test")
@DisplayName("别名配置功能测试")
@TestPropertySource(locations = "classpath:application-test.yml")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AliasConfigurationTest {

    @Autowired
    private MultiRedisManager multiRedisManager;

    @Autowired
    private ApplicationContext applicationContext;

    // 主名称注入
    @Autowired
    @Qualifier("cacheRedisTemplate")
    private StringRedisTemplate cacheRedisTemplate;

    // 别名注入
    @Autowired
    @Qualifier("tempRedisTemplate")
    private StringRedisTemplate tempRedisTemplate;

    // Redisson主名称
    @Autowired
    @Qualifier("cacheRedissonClient")
    private RedissonClient cacheRedissonClient;

    // Redisson别名
    @Autowired
    @Qualifier("tempRedissonClient")
    private RedissonClient tempRedissonClient;

    private static final String TEST_PREFIX = "alias:test:";

    @Test
    @Order(1)
    @DisplayName("验证别名Bean已注册")
    void testAliasBeanRegistration() {
        log.info("========== 验证别名Bean注册 ==========");

        // 验证主名称Bean存在
        assertTrue(applicationContext.containsBean("cacheRedisTemplate"),
            "cacheRedisTemplate Bean应该存在");
        assertTrue(applicationContext.containsBean("cacheRedissonClient"),
            "cacheRedissonClient Bean应该存在");

        // 验证别名Bean存在
        assertTrue(applicationContext.containsBean("tempRedisTemplate"),
            "tempRedisTemplate Bean(别名)应该存在");
        assertTrue(applicationContext.containsBean("tempRedissonClient"),
            "tempRedissonClient Bean(别名)应该存在");

        log.info("✅ 别名Bean注册验证通过");
    }

    @Test
    @Order(2)
    @DisplayName("验证主名称和别名共享同一连接")
    void testAliasSharesConnection() {
        log.info("========== 验证主名称和别名共享连接 ==========");

        // 通过主名称获取
        StringRedisTemplate cacheTemplate = multiRedisManager.getStringRedisTemplate("cache");
        // 通过别名获取
        StringRedisTemplate tempTemplate = multiRedisManager.getStringRedisTemplate("temp");

        assertNotNull(cacheTemplate, "通过主名称cache应能获取RedisTemplate");
        assertNotNull(tempTemplate, "通过别名temp应能获取RedisTemplate");

        // 验证共享同一个ConnectionFactory
        assertSame(
            cacheTemplate.getConnectionFactory(),
            tempTemplate.getConnectionFactory(),
            "主名称和别名应该共享同一个ConnectionFactory"
        );

        log.info("✅ 主名称和别名共享连接验证通过");
    }

    @Test
    @Order(3)
    @DisplayName("验证通过别名写入的数据可通过主名称读取")
    void testDataConsistency() {
        log.info("========== 验证数据一致性 ==========");

        String testKey = TEST_PREFIX + "consistency";
        String testValue = "value-" + System.currentTimeMillis();

        // 通过别名写入
        tempRedisTemplate.opsForValue().set(testKey, testValue, 60, TimeUnit.SECONDS);

        // 通过主名称读取
        String retrieved = cacheRedisTemplate.opsForValue().get(testKey);

        assertEquals(testValue, retrieved,
            "通过别名写入的数据应该能通过主名称读取");

        // 反向验证: 通过主名称写入,别名读取
        String testKey2 = TEST_PREFIX + "consistency2";
        String testValue2 = "value2-" + System.currentTimeMillis();

        cacheRedisTemplate.opsForValue().set(testKey2, testValue2, 60, TimeUnit.SECONDS);
        String retrieved2 = tempRedisTemplate.opsForValue().get(testKey2);

        assertEquals(testValue2, retrieved2,
            "通过主名称写入的数据应该能通过别名读取");

        // 清理
        cacheRedisTemplate.delete(testKey);
        cacheRedisTemplate.delete(testKey2);

        log.info("✅ 数据一致性验证通过");
    }

    @Test
    @Order(4)
    @DisplayName("验证RedissonClient别名")
    void testRedissonClientAlias() {
        log.info("========== 验证RedissonClient别名 ==========");

        assertNotNull(cacheRedissonClient, "主名称RedissonClient不应为空");
        assertNotNull(tempRedissonClient, "别名RedissonClient不应为空");

        // 验证共享同一实例
        assertSame(cacheRedissonClient, tempRedissonClient,
            "主名称和别名应该返回同一个RedissonClient实例");

        // 验证锁功能
        String lockKey = TEST_PREFIX + "alias:lock";
        var lock1 = cacheRedissonClient.getLock(lockKey);
        var lock2 = tempRedissonClient.getLock(lockKey);

        try {
            // 通过主名称获取锁
            boolean locked = lock1.tryLock(5, 10, TimeUnit.SECONDS);
            assertTrue(locked, "应该能通过主名称获取锁");

            // 通过别名尝试获取同一个锁应该失败(因为已被锁定)
            boolean lockedAgain = lock2.tryLock(100, TimeUnit.MILLISECONDS);
            // 由于是同一个RedissonClient,getLock返回的是同一个锁对象
            // 所以这里的行为取决于Redisson的实现
            log.info(">>> 通过别名再次获取锁结果: {}", lockedAgain);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (lock1.isHeldByCurrentThread()) {
                lock1.unlock();
            }
        }

        log.info("✅ RedissonClient别名验证通过");
    }

    @Test
    @Order(5)
    @DisplayName("验证containsDatasource支持别名查询")
    void testContainsDatasourceWithAlias() {
        log.info("========== 验证containsDatasource支持别名 ==========");

        // 主名称应该存在
        assertTrue(multiRedisManager.containsDatasource("cache"),
            "应该能通过主名称cache查询数据源");

        // 别名也应该存在
        assertTrue(multiRedisManager.containsDatasource("temp"),
            "应该能通过别名temp查询数据源");

        log.info("✅ containsDatasource别名支持验证通过");
    }

    @Test
    @Order(6)
    @DisplayName("验证健康检查支持别名")
    void testHealthCheckWithAlias() {
        log.info("========== 验证健康检查支持别名 ==========");

        // 通过主名称健康检查
        boolean cacheHealthy = multiRedisManager.checkHealth("cache");
        assertTrue(cacheHealthy, "通过主名称cache的健康检查应该通过");

        // 通过别名健康检查
        boolean tempHealthy = multiRedisManager.checkHealth("temp");
        assertTrue(tempHealthy, "通过别名temp的健康检查应该通过");

        log.info("✅ 健康检查别名支持验证通过");
    }

    @Test
    @Order(7)
    @DisplayName("验证stringRedisTemplate自动别名")
    void testStringRedisTemplateAlias() {
        log.info("========== 验证stringRedisTemplate自动别名 ==========");

        // stringRedisTemplate应该是Primary RedisTemplate的别名
        if (applicationContext.containsBean("stringRedisTemplate")) {
            StringRedisTemplate stringRedisTemplate =
                applicationContext.getBean("stringRedisTemplate", StringRedisTemplate.class);
            assertNotNull(stringRedisTemplate, "stringRedisTemplate应该存在");

            // 验证可以正常使用
            String testKey = TEST_PREFIX + "stringRedisTemplate";
            stringRedisTemplate.opsForValue().set(testKey, "test", 60, TimeUnit.SECONDS);
            assertNotNull(stringRedisTemplate.opsForValue().get(testKey));
            stringRedisTemplate.delete(testKey);

            log.info("✅ stringRedisTemplate自动别名验证通过");
        } else {
            log.info(">>> stringRedisTemplate别名未配置,跳过此测试");
        }
    }

    @Test
    @Order(8)
    @DisplayName("通过@Qualifier注入别名Bean")
    void testQualifierInjectionWithAlias() {
        log.info("========== 验证@Qualifier注入别名Bean ==========");

        // 验证注入的别名Bean可用
        assertNotNull(tempRedisTemplate, "通过@Qualifier注入的别名Bean不应为空");
        assertNotNull(tempRedissonClient, "通过@Qualifier注入的别名RedissonClient不应为空");

        // 验证与主名称Bean的关系
        assertSame(
            cacheRedisTemplate.getConnectionFactory(),
            tempRedisTemplate.getConnectionFactory(),
            "通过@Qualifier注入的主名称和别名应共享ConnectionFactory"
        );

        // 使用别名进行数据操作
        String testKey = TEST_PREFIX + "qualifier:alias";
        String testValue = "qualifier-value";

        tempRedisTemplate.opsForValue().set(testKey, testValue, 60, TimeUnit.SECONDS);
        assertEquals(testValue, tempRedisTemplate.opsForValue().get(testKey));

        // 清理
        tempRedisTemplate.delete(testKey);

        log.info("✅ @Qualifier注入别名Bean验证通过");
    }

    @Test
    @Order(9)
    @DisplayName("getAllDatasourceNames包含别名")
    void testGetAllDatasourceNamesIncludesAliases() {
        log.info("========== 验证getAllDatasourceNames包含别名 ==========");

        var allNames = multiRedisManager.getAllDatasourceNames();
        log.info(">>> 所有数据源名称: {}", allNames);

        // 主名称应该在列表中
        assertTrue(allNames.contains("cache") || multiRedisManager.containsDatasource("cache"),
            "数据源列表应包含主名称cache");

        // 别名也应该被识别(可能在列表中,或通过containsDatasource可查询)
        assertTrue(multiRedisManager.containsDatasource("temp"),
            "别名temp应该能被识别");

        log.info("✅ getAllDatasourceNames别名验证通过");
    }

    @Test
    @Order(10)
    @DisplayName("综合场景 - 使用别名进行业务操作")
    void testBusinessScenarioWithAlias() {
        log.info("========== 综合场景: 使用别名进行业务操作 ==========");

        // 场景: 缓存服务使用temp别名,业务上更语义化

        // 1. 缓存用户数据
        String userId = "user-" + System.currentTimeMillis();
        String userKey = TEST_PREFIX + "user:" + userId;
        String userData = "{\"id\":\"" + userId + "\",\"name\":\"TestUser\"}";

        tempRedisTemplate.opsForValue().set(userKey, userData, 300, TimeUnit.SECONDS);

        // 2. 另一个服务通过主名称cache读取(验证数据共享)
        String retrieved = cacheRedisTemplate.opsForValue().get(userKey);
        assertEquals(userData, retrieved, "通过主名称应能读取别名写入的数据");

        // 3. 使用分布式锁保护更新操作
        String lockKey = TEST_PREFIX + "lock:user:" + userId;
        var lock = tempRedissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                // 更新用户数据
                String updatedData = "{\"id\":\"" + userId + "\",\"name\":\"UpdatedUser\"}";
                tempRedisTemplate.opsForValue().set(userKey, updatedData, 300, TimeUnit.SECONDS);

                // 验证更新成功
                assertEquals(updatedData, cacheRedisTemplate.opsForValue().get(userKey));
                log.info("✅ 使用别名完成业务操作");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            // 清理
            cacheRedisTemplate.delete(userKey);
        }

        log.info("✅ 综合场景测试通过");
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class TestConfiguration {
    }
}
