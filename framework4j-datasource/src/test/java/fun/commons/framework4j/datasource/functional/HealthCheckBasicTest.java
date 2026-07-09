package fun.commons.framework4j.datasource.functional;

import fun.commons.framework4j.datasource.config.MultiDataSourceAutoConfiguration;
import fun.commons.framework4j.datasource.manager.MultiDataSourceManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据源健康检查基础功能测试
 * <p>
 * 测试场景:
 * 1. 正常数据源健康检查
 * 2. 不存在数据源健康检查
 * 3. 并发健康检查
 * 4. 健康检查异常处理
 * 5. 健康检查性能测试
 */
@Slf4j
@SpringBootTest(
        classes = {HealthCheckBasicTest.TestConfiguration.class},
        properties = {
                "spring.autoconfigure.exclude=com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        }
)
@ActiveProfiles("test")
@DisplayName("数据源健康检查基础功能测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HealthCheckBasicTest {

    @Autowired
    private MultiDataSourceManager manager;

    private static int testCounter = 0;

    @BeforeEach
    void setUp() {
        testCounter++;
        log.info("========== 开始测试 #{} ==========", testCounter);
    }

    @AfterEach
    void tearDown() {
        log.info("========== 测试 #{} 完成 ==========\n", testCounter);
    }

    @Test
    @Order(1)
    @DisplayName("测试1: 正常数据源健康检查")
    void test01_HealthyDataSourceCheck() {
        // 检查现有数据源的健康状态
        boolean businessHealthy = manager.checkHealth("business");
        boolean logHealthy = manager.checkHealth("log");
        boolean reportHealthy = manager.checkHealth("report");

        assertTrue(businessHealthy, "Business数据源应该是健康的");
        assertTrue(logHealthy, "Log数据源应该是健康的");
        assertTrue(reportHealthy, "Report数据源应该是健康的");

        // 检查默认数据源
        boolean defaultHealthy = manager.checkHealth("default");
        assertTrue(defaultHealthy, "默认数据源应该是健康的");

        log.info("✅ 正常数据源健康检查通过");
    }

    @Test
    @Order(2)
    @DisplayName("测试2: 不存在数据源健康检查")
    void test02_NonExistentDataSourceCheck() {
        // 检查不存在的数据源，应该返回false而不是抛出异常
        boolean healthResult = manager.checkHealth("non-existent-ds");
        assertFalse(healthResult, "不存在的数据源健康检查应该返回false");

        log.info("✅ 不存在数据源健康检查通过");
    }

    @Test
    @Order(3)
    @DisplayName("测试3: 批量健康检查")
    void test03_BatchHealthCheck() {
        // 准备多个数据源进行批量检查
        List<String> dataSources = List.of("business", "log", "report");
        Map<String, Boolean> healthResults = new java.util.HashMap<>();

        // 批量健康检查
        for (String dsName : dataSources) {
            try {
                boolean healthy = manager.checkHealth(dsName);
                healthResults.put(dsName, healthy);
            } catch (Exception e) {
                healthResults.put(dsName, false);
                log.warn("数据源 {} 健康检查失败: {}", dsName, e.getMessage());
            }
        }

        // 验证结果
        assertTrue(healthResults.getOrDefault("business", false), "Business数据源应该健康");
        assertTrue(healthResults.getOrDefault("log", false), "Log数据源应该健康");
        assertTrue(healthResults.getOrDefault("report", false), "Report数据源应该健康");

        // 统计健康数据源数量
        long healthyCount = healthResults.values().stream().mapToLong(b -> b ? 1 : 0).sum();
        assertEquals(3, healthyCount, "应该有3个健康的数据源");

        log.info("✅ 批量健康检查通过，健康数据源数量: {}", healthyCount);
    }

    @Test
    @Order(4)
    @DisplayName("测试4: 并发健康检查")
    void test04_ConcurrentHealthCheck() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 并发健康检查
        // v2.1 P0 修复：用 AtomicReference 收集子线程异常，避免 AssertionError 被吞导致假通过
        java.util.concurrent.atomic.AtomicReference<Throwable> firstError = new java.util.concurrent.atomic.AtomicReference<>();
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // 检查现有数据源
                    boolean businessHealthy = manager.checkHealth("business");
                    boolean logHealthy = manager.checkHealth("log");

                    assertTrue(businessHealthy, "并发健康检查：business数据源应该健康");
                    assertTrue(logHealthy, "并发健康检查：log数据源应该健康");

                } catch (Throwable e) {
                    // v2.1 P0: 捕获 Throwable（含 AssertionError），记录第一个异常
                    firstError.compareAndSet(null, e);
                    log.error("并发健康检查失败: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "所有并发健康检查应该在30秒内完成");
        // v2.1 P2: 补 awaitTermination，与同类并发测试风格一致
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }

        // v2.1 P0: 子线程异常应传播到主线程
        if (firstError.get() != null) {
            throw new AssertionError("并发健康检查子线程失败: " + firstError.get().getMessage(), firstError.get());
        }

        log.info("✅ 并发健康检查通过");
    }

    @Test
    @Order(5)
    @DisplayName("测试5: 健康检查异常处理")
    void test05_HealthCheckExceptionHandling() {
        // 测试null数据源名称
        boolean nullResult = manager.checkHealth(null);
        assertFalse(nullResult, "null数据源名称健康检查应该返回false");

        // 测试空数据源名称
        boolean emptyResult = manager.checkHealth("");
        assertFalse(emptyResult, "空数据源名称健康检查应该返回false");

        // 测试空白字符数据源名称
        boolean blankResult = manager.checkHealth("   ");
        assertFalse(blankResult, "空白字符数据源名称健康检查应该返回false");

        log.info("✅ 健康检查异常处理通过");
    }

    @Test
    @Order(6)
    @DisplayName("测试6: 健康检查性能测试")
    void test06_HealthCheckPerformance() {
        int iterations = 50; // 减少迭代次数以避免性能问题
        long startTime = System.currentTimeMillis();

        // 执行多次健康检查
        for (int i = 0; i < iterations; i++) {
            boolean healthy = manager.checkHealth("business");
            assertTrue(healthy, "健康检查应该通过");
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double averageTime = (double) totalTime / iterations;

        log.info("执行 {} 次健康检查，总耗时: {} ms，平均耗时: {:.2f} ms",
                iterations, totalTime, averageTime);

        // 性能断言：平均健康检查时间不应超过200ms
        assertTrue(averageTime < 200, "平均健康检查时间不应超过200ms");

        log.info("✅ 健康检查性能测试通过");
    }

    @Test
    @Order(7)
    @DisplayName("测试7: 数据源连接验证")
    void test07_DataSourceConnectionValidation() {
        // 验证数据源管理器能正确识别包含的数据源
        assertTrue(manager.containsDatasource("business"), "应该包含business数据源");
        assertTrue(manager.containsDatasource("log"), "应该包含log数据源");
        assertTrue(manager.containsDatasource("report"), "应该包含report数据源");
        assertFalse(manager.containsDatasource("non-existent"), "不应该包含不存在的数据源");

        // 验证能获取数据源
        assertDoesNotThrow(() -> {
            javax.sql.DataSource ds = manager.getDataSource("business");
            assertNotNull(ds, "Business数据源不应该为null");
        }, "获取business数据源应该成功");

        assertDoesNotThrow(() -> {
            javax.sql.DataSource ds = manager.getDataSource("log");
            assertNotNull(ds, "Log数据源不应该为null");
        }, "获取log数据源应该成功");

        log.info("✅ 数据源连接验证通过");
    }

    @Test
    @Order(8)
    @DisplayName("测试8: 多次健康检查稳定性")
    void test08_MultipleHealthCheckStability() throws InterruptedException {
        // 在短时间内执行多次健康检查，验证稳定性
        int checkCount = 20;

        for (int i = 0; i < checkCount; i++) {
            boolean businessHealthy = manager.checkHealth("business");
            boolean logHealthy = manager.checkHealth("log");

            assertTrue(businessHealthy, "第" + (i + 1) + "次business健康检查应该通过");
            assertTrue(logHealthy, "第" + (i + 1) + "次log健康检查应该通过");
            // v2.1 P1: 删除 Thread.sleep(50)，20 次循环 × 50ms = 1s 纯 flaky 来源
        }

        log.info("✅ 多次健康检查稳定性通过，共检查{}次", checkCount);
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.context.annotation.Import({
            MultiDataSourceAutoConfiguration.class
    })
    static class TestConfiguration {
    }
}