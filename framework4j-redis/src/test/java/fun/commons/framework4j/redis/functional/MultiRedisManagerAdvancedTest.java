package fun.commons.framework4j.redis.functional;

import fun.commons.framework4j.redis.manager.MultiRedisManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MultiRedisManager 高级API测试
 * <p>
 * 测试覆盖:
 * 1. getDefaultRedisTemplate() - 默认RedisTemplate获取
 * 2. getDefaultRedissonClient() - 默认RedissonClient获取(含三级降级)
 * 3. checkHealth() - 健康检查
 * 4. getAllDatasourceNames() - 获取所有数据源名称
 * 5. containsDatasource() - 检查数据源是否存在
 *
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = {MultiRedisManagerAdvancedTest.TestConfiguration.class})
@ActiveProfiles("test")
@DisplayName("MultiRedisManager 高级API测试")
@TestPropertySource(locations = "classpath:application-test.yml")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MultiRedisManagerAdvancedTest {

    @Autowired
    private MultiRedisManager multiRedisManager;

    private static final String TEST_PREFIX = "advanced:test:";

    @Test
    @Order(1)
    @DisplayName("测试 getDefaultRedisTemplate() - 获取默认RedisTemplate")
    void testGetDefaultRedisTemplate() {
        log.info("========== 测试 getDefaultRedisTemplate() ==========");

        // 获取默认RedisTemplate
        StringRedisTemplate defaultTemplate = (StringRedisTemplate) multiRedisManager.getDefaultRedisTemplate();
        assertNotNull(defaultTemplate, "默认RedisTemplate不应为空");

        // 验证与getRedisTemplate("default")返回相同实例
        StringRedisTemplate explicitDefault = multiRedisManager.getStringRedisTemplate("default");
        assertSame(defaultTemplate, explicitDefault,
            "getDefaultRedisTemplate()应返回与getRedisTemplate(\"default\")相同的实例");

        // 验证可以正常使用
        String testKey = TEST_PREFIX + "default:template";
        String testValue = "default-value-" + System.currentTimeMillis();

        defaultTemplate.opsForValue().set(testKey, testValue, 60, TimeUnit.SECONDS);
        String retrieved = defaultTemplate.opsForValue().get(testKey);
        assertEquals(testValue, retrieved, "默认RedisTemplate应能正常读写数据");

        // 清理
        defaultTemplate.delete(testKey);

        log.info("✅ getDefaultRedisTemplate() 测试通过");
    }

    @Test
    @Order(2)
    @DisplayName("测试 getDefaultRedissonClient() - 获取默认RedissonClient")
    void testGetDefaultRedissonClient() {
        log.info("========== 测试 getDefaultRedissonClient() ==========");

        // 获取默认RedissonClient
        RedissonClient defaultClient = multiRedisManager.getDefaultRedissonClient();

        // 注意: 根据配置,default数据源的redisson.enabled=false
        // 所以应该触发降级逻辑,获取到第一个可用的RedissonClient
        assertNotNull(defaultClient, "应该通过降级逻辑获取到RedissonClient");

        // 验证RedissonClient可用
        String lockKey = TEST_PREFIX + "default:lock";
        var lock = defaultClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(5, 30, TimeUnit.SECONDS);
            assertTrue(locked, "应该能够成功获取锁");
            log.info("✅ 成功获取分布式锁: {}", lockKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("获取锁被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("✅ 成功释放分布式锁: {}", lockKey);
            }
        }

        log.info("✅ getDefaultRedissonClient() 测试通过");
    }

    @Test
    @Order(3)
    @DisplayName("测试 getDefaultRedissonClient() 降级逻辑")
    void testGetDefaultRedissonClientFallback() {
        log.info("========== 测试 getDefaultRedissonClient() 降级逻辑 ==========");

        // 根据application-test.yml配置:
        // - default: redisson.enabled=false (无RedissonClient)
        // - cache: redisson.enabled=true (有RedissonClient)
        // - business: redisson.enabled=true (有RedissonClient)
        // - lock: redisson.enabled=true (有RedissonClient)

        // getDefaultRedissonClient()应该降级到cache或其他可用数据源
        RedissonClient fallbackClient = multiRedisManager.getDefaultRedissonClient();
        assertNotNull(fallbackClient, "降级后应获取到可用的RedissonClient");

        // 验证不是从default获取的(因为default没有启用Redisson)
        RedissonClient defaultClient = multiRedisManager.getRedissonClient("default");
        assertNull(defaultClient, "default数据源不应有RedissonClient(配置中disabled)");

        // 验证fallback是从其他数据源获取的
        RedissonClient cacheClient = multiRedisManager.getRedissonClient("cache");
        assertNotNull(cacheClient, "cache数据源应有RedissonClient");

        log.info("✅ 降级逻辑测试通过: 从default降级到其他可用数据源");
    }

    @Test
    @Order(4)
    @DisplayName("测试 checkHealth() - 健康检查成功场景")
    void testCheckHealthSuccess() {
        log.info("========== 测试 checkHealth() 成功场景 ==========");

        // 测试所有已配置的数据源健康检查
        String[] datasourceNames = {"default", "cache", "business", "lock"};

        for (String name : datasourceNames) {
            if (multiRedisManager.containsDatasource(name)) {
                boolean isHealthy = multiRedisManager.checkHealth(name);
                assertTrue(isHealthy, "数据源 [" + name + "] 应该是健康的");
                log.info("✅ 数据源 [{}] 健康检查通过", name);
            }
        }

        log.info("✅ checkHealth() 成功场景测试通过");
    }

    @Test
    @Order(5)
    @DisplayName("测试 checkHealth() - 不存在的数据源")
    void testCheckHealthNonExistent() {
        log.info("========== 测试 checkHealth() 不存在的数据源 ==========");

        // 检查不存在的数据源应该返回false或抛出异常
        String nonExistentName = "non-existent-datasource";

        // 根据实际实现,可能返回false或抛出异常
        try {
            boolean result = multiRedisManager.checkHealth(nonExistentName);
            assertFalse(result, "不存在的数据源健康检查应返回false");
            log.info("✅ 不存在的数据源健康检查返回false");
        } catch (Exception e) {
            log.info("✅ 不存在的数据源健康检查抛出异常: {}", e.getMessage());
            // 预期行为,测试通过
        }

        log.info("✅ checkHealth() 不存在数据源场景测试通过");
    }

    @Test
    @Order(6)
    @DisplayName("测试 getAllDatasourceNames() - 获取所有数据源名称")
    void testGetAllDatasourceNames() {
        log.info("========== 测试 getAllDatasourceNames() ==========");

        List<String> allNames = multiRedisManager.getAllDatasourceNames();
        assertNotNull(allNames, "数据源名称列表不应为空");
        assertFalse(allNames.isEmpty(), "应该至少有一个数据源");

        log.info(">>> 已注册的数据源: {}", allNames);

        // 验证包含预期的数据源
        assertTrue(allNames.contains("default") || multiRedisManager.containsDatasource("default"),
            "应包含default数据源");

        log.info("✅ getAllDatasourceNames() 测试通过, 共 {} 个数据源", allNames.size());
    }

    @Test
    @Order(7)
    @DisplayName("测试 containsDatasource() - 检查数据源存在性")
    void testContainsDatasource() {
        log.info("========== 测试 containsDatasource() ==========");

        // 测试存在的数据源
        assertTrue(multiRedisManager.containsDatasource("default"), "应包含default数据源");
        assertTrue(multiRedisManager.containsDatasource("cache"), "应包含cache数据源");
        assertTrue(multiRedisManager.containsDatasource("business"), "应包含business数据源");
        assertTrue(multiRedisManager.containsDatasource("lock"), "应包含lock数据源");

        // 测试不存在的数据源
        assertFalse(multiRedisManager.containsDatasource("non-existent"), "不应包含不存在的数据源");
        assertFalse(multiRedisManager.containsDatasource(""), "空字符串不应匹配任何数据源");
        // null参数会导致NPE(HashMap内部调用hashCode)
        assertThrows(NullPointerException.class,
            () -> multiRedisManager.containsDatasource(null),
            "null参数应抛出NPE");

        log.info("✅ containsDatasource() 测试通过");
    }

    @Test
    @Order(8)
    @DisplayName("测试 getRedisTemplate() - 获取指定数据源的RedisTemplate")
    void testGetRedisTemplate() {
        log.info("========== 测试 getRedisTemplate() ==========");

        // 测试获取各个数据源的RedisTemplate
        StringRedisTemplate defaultTemplate = multiRedisManager.getStringRedisTemplate("default");
        StringRedisTemplate cacheTemplate = multiRedisManager.getStringRedisTemplate("cache");
        StringRedisTemplate businessTemplate = multiRedisManager.getStringRedisTemplate("business");
        StringRedisTemplate lockTemplate = multiRedisManager.getStringRedisTemplate("lock");

        assertNotNull(defaultTemplate, "default RedisTemplate不应为空");
        assertNotNull(cacheTemplate, "cache RedisTemplate不应为空");
        assertNotNull(businessTemplate, "business RedisTemplate不应为空");
        assertNotNull(lockTemplate, "lock RedisTemplate不应为空");

        // 验证不同数据源返回不同实例(除非是别名)
        assertNotSame(defaultTemplate, cacheTemplate, "不同数据源应返回不同实例");
        assertNotSame(defaultTemplate, businessTemplate, "不同数据源应返回不同实例");

        // 验证数据隔离(不同database)
        String testKey = TEST_PREFIX + "isolation";
        String defaultValue = "default-" + System.currentTimeMillis();
        String cacheValue = "cache-" + System.currentTimeMillis();

        defaultTemplate.opsForValue().set(testKey, defaultValue, 60, TimeUnit.SECONDS);
        cacheTemplate.opsForValue().set(testKey, cacheValue, 60, TimeUnit.SECONDS);

        assertEquals(defaultValue, defaultTemplate.opsForValue().get(testKey), "default数据应独立");
        assertEquals(cacheValue, cacheTemplate.opsForValue().get(testKey), "cache数据应独立");

        // 清理
        defaultTemplate.delete(testKey);
        cacheTemplate.delete(testKey);

        log.info("✅ getRedisTemplate() 测试通过");
    }

    @Test
    @Order(9)
    @DisplayName("测试 getRedissonClient() - 获取指定数据源的RedissonClient")
    void testGetRedissonClient() {
        log.info("========== 测试 getRedissonClient() ==========");

        // 根据配置,default的redisson.enabled=false
        RedissonClient defaultClient = multiRedisManager.getRedissonClient("default");
        assertNull(defaultClient, "default数据源未启用Redisson,应返回null");

        // 其他数据源启用了Redisson
        RedissonClient cacheClient = multiRedisManager.getRedissonClient("cache");
        RedissonClient businessClient = multiRedisManager.getRedissonClient("business");
        RedissonClient lockClient = multiRedisManager.getRedissonClient("lock");

        assertNotNull(cacheClient, "cache RedissonClient不应为空");
        assertNotNull(businessClient, "business RedissonClient不应为空");
        assertNotNull(lockClient, "lock RedissonClient不应为空");

        // 验证不同数据源返回不同实例
        assertNotSame(cacheClient, businessClient, "不同数据源应返回不同RedissonClient实例");
        assertNotSame(cacheClient, lockClient, "不同数据源应返回不同RedissonClient实例");

        log.info("✅ getRedissonClient() 测试通过");
    }

    @Test
    @Order(10)
    @DisplayName("综合测试 - 多数据源协同工作")
    void testMultiDatasourceCollaboration() {
        log.info("========== 综合测试: 多数据源协同工作 ==========");

        // 模拟实际业务场景:
        // 1. default: 存储配置数据
        // 2. cache: 存储缓存数据
        // 3. business: 存储业务数据
        // 4. lock: 分布式锁

        StringRedisTemplate defaultTemplate = multiRedisManager.getStringRedisTemplate("default");
        StringRedisTemplate cacheTemplate = multiRedisManager.getStringRedisTemplate("cache");
        StringRedisTemplate businessTemplate = multiRedisManager.getStringRedisTemplate("business");
        RedissonClient lockClient = multiRedisManager.getRedissonClient("lock");

        String orderId = "ORDER-" + System.currentTimeMillis();
        String lockKey = TEST_PREFIX + "order:lock:" + orderId;

        // 1. 获取分布式锁
        var lock = lockClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(5, 30, TimeUnit.SECONDS);
            assertTrue(locked, "应该能够获取订单锁");

            // 2. 在锁保护下执行业务操作
            // 2.1 写入业务数据
            businessTemplate.opsForValue().set(
                TEST_PREFIX + "order:" + orderId,
                "{\"orderId\":\"" + orderId + "\",\"status\":\"CREATED\"}",
                300, TimeUnit.SECONDS
            );

            // 2.2 更新缓存
            cacheTemplate.opsForValue().set(
                TEST_PREFIX + "cache:order:" + orderId,
                "cached-" + orderId,
                60, TimeUnit.SECONDS
            );

            // 2.3 记录配置/日志
            defaultTemplate.opsForValue().set(
                TEST_PREFIX + "config:lastOrder",
                orderId,
                3600, TimeUnit.SECONDS
            );

            // 3. 验证数据写入成功
            assertNotNull(businessTemplate.opsForValue().get(TEST_PREFIX + "order:" + orderId));
            assertNotNull(cacheTemplate.opsForValue().get(TEST_PREFIX + "cache:order:" + orderId));
            assertEquals(orderId, defaultTemplate.opsForValue().get(TEST_PREFIX + "config:lastOrder"));

            log.info("✅ 多数据源协同业务操作成功: orderId={}", orderId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("获取锁被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }

            // 清理测试数据
            businessTemplate.delete(TEST_PREFIX + "order:" + orderId);
            cacheTemplate.delete(TEST_PREFIX + "cache:order:" + orderId);
            defaultTemplate.delete(TEST_PREFIX + "config:lastOrder");
        }

        log.info("✅ 综合测试通过: 多数据源协同工作正常");
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class TestConfiguration {
    }
}
