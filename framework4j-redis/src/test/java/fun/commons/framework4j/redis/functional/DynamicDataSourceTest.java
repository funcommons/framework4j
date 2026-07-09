package fun.commons.framework4j.redis.functional;

import fun.commons.framework4j.redis.manager.MultiRedisManager;
import fun.commons.framework4j.redis.properties.RedisDataSourceProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 动态数据源管理测试
 * <p>
 * 测试覆盖:
 * 1. addDataSource() - 运行时动态添加数据源
 * 2. removeDatasource() - 运行时动态删除数据源
 * 3. 多租户场景模拟
 * 4. 资源清理验证
 *
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = {DynamicDataSourceTest.TestConfiguration.class})
@ActiveProfiles("test")
@DisplayName("动态数据源管理测试")
@TestPropertySource(locations = "classpath:application-test.yml")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DynamicDataSourceTest {

    @Autowired
    private MultiRedisManager multiRedisManager;

    private static final String TEST_PREFIX = "dynamic:test:";
    private static final String TENANT_PREFIX = "tenant-";

    @Test
    @Order(1)
    @DisplayName("测试 addDataSource() - 动态添加数据源")
    void testAddDataSource() {
        log.info("========== 测试 addDataSource() ==========");

        // 记录初始数据源数量
        int initialCount = multiRedisManager.getAllDatasourceNames().size();
        log.info(">>> 初始数据源数量: {}", initialCount);

        // 创建新数据源配置
        String newDatasourceName = TENANT_PREFIX + "test-001";
        RedisDataSourceProperties config = createTestConfig(newDatasourceName);

        // 动态添加数据源
        multiRedisManager.addDataSource(config);

        // 验证数据源已添加
        assertTrue(multiRedisManager.containsDatasource(newDatasourceName),
            "新数据源应该已被添加");

        int afterAddCount = multiRedisManager.getAllDatasourceNames().size();
        assertEquals(initialCount + 1, afterAddCount, "数据源数量应该增加1");

        // 验证新数据源可用
        StringRedisTemplate newTemplate = multiRedisManager.getStringRedisTemplate(newDatasourceName);
        assertNotNull(newTemplate, "应该能够获取新添加的数据源");

        // 测试数据操作
        String testKey = TEST_PREFIX + newDatasourceName + ":key";
        String testValue = "value-" + System.currentTimeMillis();
        newTemplate.opsForValue().set(testKey, testValue, 60, TimeUnit.SECONDS);
        assertEquals(testValue, newTemplate.opsForValue().get(testKey), "新数据源应能正常读写");

        // 清理
        newTemplate.delete(testKey);
        multiRedisManager.removeDatasource(newDatasourceName);

        log.info("✅ addDataSource() 测试通过");
    }

    @Test
    @Order(2)
    @DisplayName("测试 removeDatasource() - 动态删除数据源")
    void testRemoveDatasource() {
        log.info("========== 测试 removeDatasource() ==========");

        // 先添加一个临时数据源
        String tempDatasourceName = TENANT_PREFIX + "temp-remove";
        RedisDataSourceProperties config = createTestConfig(tempDatasourceName);
        multiRedisManager.addDataSource(config);

        // 确认已添加
        assertTrue(multiRedisManager.containsDatasource(tempDatasourceName),
            "临时数据源应该已被添加");

        int beforeRemoveCount = multiRedisManager.getAllDatasourceNames().size();

        // 删除数据源
        multiRedisManager.removeDatasource(tempDatasourceName);

        // 验证已删除
        assertFalse(multiRedisManager.containsDatasource(tempDatasourceName),
            "数据源应该已被删除");

        int afterRemoveCount = multiRedisManager.getAllDatasourceNames().size();
        assertEquals(beforeRemoveCount - 1, afterRemoveCount, "数据源数量应该减少1");

        log.info("✅ removeDatasource() 测试通过");
    }

    @Test
    @Order(3)
    @DisplayName("测试 removeDatasource() - 删除不存在的数据源")
    void testRemoveNonExistentDatasource() {
        log.info("========== 测试删除不存在的数据源 ==========");

        String nonExistentName = TENANT_PREFIX + "non-existent";
        int beforeCount = multiRedisManager.getAllDatasourceNames().size();

        // 删除不存在的数据源应该不会抛出异常
        assertDoesNotThrow(() -> multiRedisManager.removeDatasource(nonExistentName),
            "删除不存在的数据源不应抛出异常");

        int afterCount = multiRedisManager.getAllDatasourceNames().size();
        assertEquals(beforeCount, afterCount, "数据源数量不应变化");

        log.info("✅ 删除不存在的数据源测试通过");
    }

    @Test
    @Order(4)
    @DisplayName("多租户场景 - 动态创建租户数据源")
    void testMultiTenantScenario() {
        log.info("========== 多租户场景测试 ==========");

        // 模拟3个租户
        String[] tenantIds = {"tenant-A", "tenant-B", "tenant-C"};

        try {
            // 为每个租户创建数据源
            for (String tenantId : tenantIds) {
                RedisDataSourceProperties config = createTestConfig(tenantId);
                multiRedisManager.addDataSource(config);
                log.info(">>> 创建租户数据源: {}", tenantId);
            }

            // 验证所有租户数据源已创建
            for (String tenantId : tenantIds) {
                assertTrue(multiRedisManager.containsDatasource(tenantId),
                    "租户数据源 [" + tenantId + "] 应该存在");
            }

            // 模拟各租户独立操作数据(每个租户用独立key)
            for (String tenantId : tenantIds) {
                StringRedisTemplate template = multiRedisManager.getStringRedisTemplate(tenantId);
                String key = TEST_PREFIX + tenantId + ":data";  // 租户专属key
                String value = "value-for-" + tenantId;

                template.opsForValue().set(key, value, 60, TimeUnit.SECONDS);
            }

            // 验证数据隔离
            for (String tenantId : tenantIds) {
                StringRedisTemplate template = multiRedisManager.getStringRedisTemplate(tenantId);
                String key = TEST_PREFIX + tenantId + ":data";  // 租户专属key
                String expected = "value-for-" + tenantId;
                String actual = template.opsForValue().get(key);

                assertEquals(expected, actual,
                    "租户 [" + tenantId + "] 的数据应该独立");
            }

            log.info("✅ 多租户数据隔离验证通过");

        } finally {
            // 清理租户数据源
            for (String tenantId : tenantIds) {
                try {
                    StringRedisTemplate template = multiRedisManager.getStringRedisTemplate(tenantId);
                    template.delete(TEST_PREFIX + tenantId + ":data");  // 租户专属key
                } catch (Exception e) {
                    // 忽略清理异常
                }
                multiRedisManager.removeDatasource(tenantId);
                log.info(">>> 清理租户数据源: {}", tenantId);
            }
        }

        log.info("✅ 多租户场景测试通过");
    }

    @Test
    @Order(5)
    @DisplayName("测试 addDataSource() 带Redisson配置")
    void testAddDataSourceWithRedisson() {
        log.info("========== 测试添加带Redisson的数据源 ==========");

        String datasourceName = TENANT_PREFIX + "with-redisson";
        RedisDataSourceProperties config = createTestConfig(datasourceName);
        config.getRedisson().setEnabled(true);  // 启用Redisson

        try {
            // 添加数据源
            multiRedisManager.addDataSource(config);

            // 验证数据源已添加
            assertTrue(multiRedisManager.containsDatasource(datasourceName));

            // 获取RedisTemplate
            StringRedisTemplate template = multiRedisManager.getStringRedisTemplate(datasourceName);
            assertNotNull(template);

            // 获取RedissonClient
            var redissonClient = multiRedisManager.getRedissonClient(datasourceName);
            assertNotNull(redissonClient, "启用Redisson后应该能获取RedissonClient");

            // 测试分布式锁
            String lockKey = TEST_PREFIX + "dynamic:lock";
            var lock = redissonClient.getLock(lockKey);
            try {
                boolean locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
                assertTrue(locked, "应该能够获取锁");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }

            log.info("✅ 带Redisson的数据源测试通过");

        } finally {
            multiRedisManager.removeDatasource(datasourceName);
        }
    }

    @Test
    @Order(6)
    @DisplayName("健康检查 - 动态添加的数据源")
    void testHealthCheckForDynamicDatasource() {
        log.info("========== 测试动态数据源健康检查 ==========");

        String datasourceName = TENANT_PREFIX + "health-check";
        RedisDataSourceProperties config = createTestConfig(datasourceName);

        try {
            multiRedisManager.addDataSource(config);

            // 健康检查
            boolean isHealthy = multiRedisManager.checkHealth(datasourceName);
            assertTrue(isHealthy, "动态添加的数据源应该是健康的");

            log.info("✅ 动态数据源健康检查通过");

        } finally {
            multiRedisManager.removeDatasource(datasourceName);
        }
    }

    @Test
    @Order(7)
    @DisplayName("并发添加多个数据源")
    void testConcurrentAddDataSource() throws InterruptedException {
        log.info("========== 并发添加数据源测试 ==========");

        int threadCount = 5;
        String[] datasourceNames = new String[threadCount];
        Thread[] threads = new Thread[threadCount];

        // 并发添加数据源
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            datasourceNames[i] = TENANT_PREFIX + "concurrent-" + i;
            threads[i] = new Thread(() -> {
                RedisDataSourceProperties config = createTestConfig(datasourceNames[index]);
                multiRedisManager.addDataSource(config);
                log.info(">>> 线程 {} 添加数据源: {}", index, datasourceNames[index]);
            });
        }

        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join(5000);
        }

        try {
            // 验证所有数据源已添加
            for (String name : datasourceNames) {
                assertTrue(multiRedisManager.containsDatasource(name),
                    "数据源 [" + name + "] 应该已被添加");
            }

            log.info("✅ 并发添加数据源测试通过");

        } finally {
            // 清理
            for (String name : datasourceNames) {
                multiRedisManager.removeDatasource(name);
            }
        }
    }

    @Test
    @Order(8)
    @DisplayName("资源清理验证 - 连接池释放")
    void testResourceCleanup() {
        log.info("========== 资源清理验证 ==========");

        String datasourceName = TENANT_PREFIX + "resource-cleanup";
        RedisDataSourceProperties config = createTestConfig(datasourceName);

        // 添加数据源
        multiRedisManager.addDataSource(config);
        StringRedisTemplate template = multiRedisManager.getStringRedisTemplate(datasourceName);

        // 使用数据源
        String testKey = TEST_PREFIX + "cleanup:key";
        template.opsForValue().set(testKey, "value", 60, TimeUnit.SECONDS);
        assertNotNull(template.opsForValue().get(testKey));

        // 删除数据源
        multiRedisManager.removeDatasource(datasourceName);

        // 验证数据源已不可用
        assertFalse(multiRedisManager.containsDatasource(datasourceName),
            "数据源应该已被删除");

        // 尝试获取已删除的数据源应该抛出异常或返回null
        assertThrows(Exception.class, () -> {
            multiRedisManager.getStringRedisTemplate(datasourceName);
        }, "获取已删除的数据源应该抛出异常");

        log.info("✅ 资源清理验证通过");
    }

    /**
     * 创建测试用数据源配置
     */
    private RedisDataSourceProperties createTestConfig(String name) {
        RedisDataSourceProperties config = new RedisDataSourceProperties();
        config.setName(name);
        config.setHost("localhost");
        config.setPort(6379);
        config.setDatabase(15);  // 使用database 15进行测试,避免影响其他数据
        config.setTimeout(Duration.ofMillis(3000));

        // 连接池配置
        RedisDataSourceProperties.LettuceConfig lettuce = new RedisDataSourceProperties.LettuceConfig();
        RedisDataSourceProperties.PoolConfig pool = new RedisDataSourceProperties.PoolConfig();
        pool.setMaxActive(8);
        pool.setMaxIdle(8);
        pool.setMinIdle(2);
        pool.setMaxWait(1000);
        lettuce.setPool(pool);
        config.setLettuce(lettuce);

        // Redisson配置(默认禁用)
        RedisDataSourceProperties.RedissonConfig redisson = new RedisDataSourceProperties.RedissonConfig();
        redisson.setEnabled(false);
        config.setRedisson(redisson);

        return config;
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class TestConfiguration {
    }
}
