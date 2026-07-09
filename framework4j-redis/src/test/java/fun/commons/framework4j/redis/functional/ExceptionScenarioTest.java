package fun.commons.framework4j.redis.functional;

import fun.commons.framework4j.redis.exception.RedisDataSourceException;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import fun.commons.framework4j.redis.properties.RedisDataSourceProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 异常场景测试
 * <p>
 * 测试覆盖:
 * 1. 获取不存在的数据源
 * 2. 空参数处理
 * 3. 无效配置处理
 * 4. 异常信息验证
 *
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = {ExceptionScenarioTest.TestConfiguration.class})
@ActiveProfiles("test")
@DisplayName("异常场景测试")
@TestPropertySource(locations = "classpath:application-test.yml")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExceptionScenarioTest {

    @Autowired
    private MultiRedisManager multiRedisManager;

    @Test
    @Order(1)
    @DisplayName("获取不存在的数据源 - 抛出RedisDataSourceException")
    void testGetNonExistentDatasource() {
        log.info("========== 测试获取不存在的数据源 ==========");

        String nonExistentName = "non-existent-datasource";

        // 获取不存在的RedisTemplate应该抛出异常
        RedisDataSourceException exception = assertThrows(
            RedisDataSourceException.class,
            () -> multiRedisManager.getStringRedisTemplate(nonExistentName),
            "获取不存在的数据源应该抛出RedisDataSourceException"
        );

        // 验证异常信息包含数据源名称
        assertTrue(exception.getMessage().contains(nonExistentName),
            "异常信息应该包含数据源名称");

        log.info("✅ 异常信息: {}", exception.getMessage());
        log.info("✅ 获取不存在的数据源异常测试通过");
    }

    @Test
    @Order(2)
    @DisplayName("获取不存在的RedissonClient - 返回null")
    void testGetNonExistentRedissonClient() {
        log.info("========== 测试获取不存在的RedissonClient ==========");

        String nonExistentName = "non-existent-redisson";

        // 获取不存在的RedissonClient应该返回null(不抛异常)
        var client = multiRedisManager.getRedissonClient(nonExistentName);
        assertNull(client, "获取不存在的RedissonClient应该返回null");

        log.info("✅ 获取不存在的RedissonClient返回null测试通过");
    }

    @Test
    @Order(3)
    @DisplayName("空字符串数据源名称处理")
    void testEmptyDatasourceName() {
        log.info("========== 测试空字符串数据源名称 ==========");

        // 空字符串
        assertThrows(Exception.class,
            () -> multiRedisManager.getStringRedisTemplate(""),
            "空字符串应该抛出异常");

        // 验证containsDatasource
        assertFalse(multiRedisManager.containsDatasource(""),
            "空字符串不应匹配任何数据源");

        log.info("✅ 空字符串数据源名称处理测试通过");
    }

    @Test
    @Order(4)
    @DisplayName("null数据源名称处理")
    void testNullDatasourceName() {
        log.info("========== 测试null数据源名称 ==========");

        // null参数 - getRedisTemplate
        assertThrows(Exception.class,
            () -> multiRedisManager.getStringRedisTemplate(null),
            "null参数应该抛出异常");

        // null参数 - containsDatasource (HashMap对null调用hashCode会NPE)
        assertThrows(NullPointerException.class,
            () -> multiRedisManager.containsDatasource(null),
            "null参数containsDatasource应抛出NPE");

        // null参数 - getRedissonClient (同样会NPE)
        assertThrows(NullPointerException.class,
            () -> multiRedisManager.getRedissonClient(null),
            "null参数getRedissonClient应抛出NPE");

        log.info("✅ null数据源名称处理测试通过");
    }

    @Test
    @Order(5)
    @DisplayName("健康检查 - 不存在的数据源")
    void testHealthCheckNonExistent() {
        log.info("========== 测试不存在数据源的健康检查 ==========");

        String nonExistentName = "health-check-non-existent";

        // 健康检查不存在的数据源应该返回false或抛出异常
        try {
            boolean result = multiRedisManager.checkHealth(nonExistentName);
            assertFalse(result, "不存在的数据源健康检查应返回false");
            log.info("✅ 不存在的数据源健康检查返回false");
        } catch (RedisDataSourceException e) {
            log.info("✅ 不存在的数据源健康检查抛出异常: {}", e.getMessage());
            assertTrue(e.getMessage().contains(nonExistentName),
                "异常信息应包含数据源名称");
        } catch (Exception e) {
            log.info("✅ 健康检查抛出其他异常: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }

        log.info("✅ 不存在数据源健康检查测试通过");
    }

    @Test
    @Order(6)
    @DisplayName("删除不存在的数据源 - 静默处理")
    void testRemoveNonExistentDatasource() {
        log.info("========== 测试删除不存在的数据源 ==========");

        String nonExistentName = "remove-non-existent";
        int beforeCount = multiRedisManager.getAllDatasourceNames().size();

        // 删除不存在的数据源不应抛出异常
        assertDoesNotThrow(
            () -> multiRedisManager.removeDatasource(nonExistentName),
            "删除不存在的数据源不应抛出异常"
        );

        int afterCount = multiRedisManager.getAllDatasourceNames().size();
        assertEquals(beforeCount, afterCount, "数据源数量不应变化");

        log.info("✅ 删除不存在的数据源静默处理测试通过");
    }

    @Test
    @Order(7)
    @DisplayName("特殊字符数据源名称")
    void testSpecialCharacterDatasourceName() {
        log.info("========== 测试特殊字符数据源名称 ==========");

        String[] specialNames = {
            "datasource with space",
            "datasource@special",
            "数据源中文",
            "datasource\ttab",
            "datasource\nnewline"
        };

        for (String name : specialNames) {
            // 不存在的特殊名称应该抛出异常
            assertThrows(Exception.class,
                () -> multiRedisManager.getStringRedisTemplate(name),
                "特殊字符数据源名称 [" + name.replace("\n", "\\n").replace("\t", "\\t") + "] 应该抛出异常"
            );
        }

        log.info("✅ 特殊字符数据源名称测试通过");
    }

    @Test
    @Order(8)
    @DisplayName("数据源名称大小写敏感性")
    void testDatasourceNameCaseSensitivity() {
        log.info("========== 测试数据源名称大小写敏感性 ==========");

        // 验证配置的数据源存在
        assertTrue(multiRedisManager.containsDatasource("default"),
            "小写default应该存在");

        // 测试大小写变体
        assertFalse(multiRedisManager.containsDatasource("DEFAULT"),
            "大写DEFAULT不应存在(大小写敏感)");

        assertFalse(multiRedisManager.containsDatasource("Default"),
            "首字母大写Default不应存在(大小写敏感)");

        // 如果系统支持大小写不敏感,这个测试会失败,需要根据实际行为调整
        log.info("✅ 数据源名称大小写敏感性测试通过");
    }

    @Test
    @Order(9)
    @DisplayName("获取已删除数据源的异常")
    void testGetDeletedDatasource() {
        log.info("========== 测试获取已删除的数据源 ==========");

        // 先添加一个临时数据源
        String tempName = "temp-to-delete";
        var config = new RedisDataSourceProperties();
        config.setName(tempName);
        config.setHost("localhost");
        config.setPort(6379);
        config.setDatabase(15);
        config.setTimeout(java.time.Duration.ofMillis(3000));

        var lettuce = new RedisDataSourceProperties.LettuceConfig();
        var pool = new RedisDataSourceProperties.PoolConfig();
        pool.setMaxActive(8);
        pool.setMaxIdle(8);
        pool.setMinIdle(2);
        lettuce.setPool(pool);
        config.setLettuce(lettuce);

        var redisson = new RedisDataSourceProperties.RedissonConfig();
        redisson.setEnabled(false);
        config.setRedisson(redisson);

        multiRedisManager.addDataSource(config);

        // 确认已添加
        assertTrue(multiRedisManager.containsDatasource(tempName));
        StringRedisTemplate template = multiRedisManager.getStringRedisTemplate(tempName);
        assertNotNull(template);

        // 删除数据源
        multiRedisManager.removeDatasource(tempName);

        // 验证已删除
        assertFalse(multiRedisManager.containsDatasource(tempName));

        // 再次获取应该抛出异常
        assertThrows(Exception.class,
            () -> multiRedisManager.getStringRedisTemplate(tempName),
            "获取已删除的数据源应该抛出异常"
        );

        log.info("✅ 获取已删除数据源异常测试通过");
    }

    @Test
    @Order(10)
    @DisplayName("并发访问异常处理")
    void testConcurrentAccessException() throws InterruptedException {
        log.info("========== 测试并发访问异常处理 ==========");

        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        int[] exceptionCount = {0};

        // 并发访问不存在的数据源
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    multiRedisManager.getStringRedisTemplate("concurrent-non-existent-" + index);
                } catch (Exception e) {
                    synchronized (exceptionCount) {
                        exceptionCount[0]++;
                    }
                }
            });
        }

        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }

        // 等待完成
        for (Thread thread : threads) {
            thread.join(5000);
        }

        // 所有线程都应该捕获到异常
        assertEquals(threadCount, exceptionCount[0],
            "所有并发请求不存在的数据源都应该抛出异常");

        log.info("✅ 并发访问异常处理测试通过");
    }

    @Test
    @Order(11)
    @DisplayName("异常信息完整性验证")
    void testExceptionMessageCompleteness() {
        log.info("========== 测试异常信息完整性 ==========");

        String nonExistentName = "exception-message-test";

        try {
            multiRedisManager.getStringRedisTemplate(nonExistentName);
            fail("应该抛出异常");
        } catch (RedisDataSourceException e) {
            String message = e.getMessage();
            log.info(">>> 完整异常信息: {}", message);

            // 验证异常信息包含关键信息
            assertNotNull(message, "异常信息不应为空");
            assertTrue(message.length() > 0, "异常信息应有内容");
            assertTrue(message.contains(nonExistentName),
                "异常信息应包含请求的数据源名称");

            log.info("✅ 异常信息完整性验证通过");
        }
    }

    @Test
    @Order(12)
    @DisplayName("连续操作异常恢复")
    void testExceptionRecovery() {
        log.info("========== 测试异常恢复 ==========");

        // 1. 触发异常
        assertThrows(Exception.class,
            () -> multiRedisManager.getStringRedisTemplate("recovery-test-non-existent"));

        // 2. 正常操作应该不受影响
        StringRedisTemplate defaultTemplate = multiRedisManager.getStringRedisTemplate("default");
        assertNotNull(defaultTemplate, "异常后正常操作应该不受影响");

        // 3. 验证可以正常使用
        String testKey = "exception:recovery:test";
        defaultTemplate.opsForValue().set(testKey, "value", 60, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals("value", defaultTemplate.opsForValue().get(testKey));
        defaultTemplate.delete(testKey);

        log.info("✅ 异常恢复测试通过");
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class TestConfiguration {
    }
}
