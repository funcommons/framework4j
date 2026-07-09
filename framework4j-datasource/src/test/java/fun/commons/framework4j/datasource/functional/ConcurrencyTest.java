package fun.commons.framework4j.datasource.functional;

import fun.commons.framework4j.datasource.annotation.DataSourceOn;
import fun.commons.framework4j.datasource.entity.User;
import fun.commons.framework4j.datasource.config.MultiDataSourceAutoConfiguration;
import fun.commons.framework4j.datasource.manager.MultiDataSourceManager;
import fun.commons.framework4j.datasource.mapper.business.UserMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发安全测试
 * <p>
 * 测试场景:
 * 1. 多线程并发获取数据源
 * 2. 多线程并发写入不同数据源
 * 3. 连接池并发访问
 * 4. 数据隔离性
 * 5. 线程安全验证
 */
@Slf4j
@SpringBootTest(
        classes = {ConcurrencyTest.TestConfiguration.class},
        properties = {
                "spring.autoconfigure.exclude=com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        }
)
@ActiveProfiles("test")
@DisplayName("并发安全测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrencyTest {

    @Autowired
    private MultiDataSourceManager manager;

    @Autowired
    private BusinessService businessService;

    private ExecutorService executorService;
    private static int testCounter = 0;

    /**
     * 执行SQL脚本文件
     */
    private void executeSqlScript(DataSource dataSource, String scriptPath) throws Exception {
        ClassPathResource resource = new ClassPathResource(scriptPath);
        try (InputStream inputStream = resource.getInputStream();
             Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            String sqlScript = FileCopyUtils.copyToString(new InputStreamReader(inputStream));

            // 使用更智能的分割方法，处理多行SQL语句
            String[] lines = sqlScript.split("\n");
            StringBuilder currentSql = new StringBuilder();

            for (String line : lines) {
                line = line.trim();

                // 跳过注释行
                if (line.startsWith("--") || line.isEmpty()) {
                    continue;
                }

                currentSql.append(line).append(" ");

                // 如果行以分号结尾，说明语句结束
                if (line.endsWith(";")) {
                    String sql = currentSql.toString().trim();
                    if (!sql.isEmpty()) {
                        stmt.execute(sql);
                    }
                    currentSql.setLength(0); // 重置StringBuilder
                }
            }

            // 处理最后一个没有分号的语句
            if (currentSql.length() > 0) {
                String sql = currentSql.toString().trim();
                if (!sql.isEmpty()) {
                    stmt.execute(sql);
                }
            }
        }
    }

    @BeforeAll
    void initTestData() throws Exception {
        log.info("========== 初始化测试数据 ==========");

        // 创建表结构（如果需要）
        executeSqlScript(manager.getDataSource("business"), "sql/schema-business-h2.sql");
        log.info("✅ 表结构创建完成");

        log.info("========== 测试数据初始化完成 ==========\n");
    }

    @BeforeEach
    void setUp() {
        testCounter++;
        executorService = Executors.newFixedThreadPool(20);
        log.info("========== 开始测试 #{} ==========", testCounter);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdown();
        log.info("========== 测试 #{} 完成 ==========\n", testCounter);
    }

    @Test
    @Order(1)
    @DisplayName("测试1: 多线程并发获取数据源")
    void test01_ConcurrentGetDataSource() throws InterruptedException {
        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<DataSource> dataSources = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    DataSource ds = manager.getDataSource("business");
                    dataSources.add(ds);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "所有线程应该在10秒内完成");
        assertEquals(threadCount, successCount.get(), "所有线程都应该成功获取数据源");

        // 验证所有线程获取的是同一个实例
        DataSource first = dataSources.get(0);
        for (DataSource ds : dataSources) {
            assertSame(first, ds, "所有线程应该获取到同一个数据源实例");
        }

        log.info("✅ {} 个线程并发获取数据源,全部成功", threadCount);
    }

    @Test
    @Order(2)
    @DisplayName("测试2: 多线程并发写入同一数据源")
    void test02_ConcurrentWriteSameDataSource() throws InterruptedException {
        int threadCount = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long initialCount = businessService.countUsers();
        log.info("初始用户数量: {}", initialCount);

        for (int i = 0; i < threadCount; i++) {
            int finalI = i;
            executorService.submit(() -> {
                try {
                    User user = new User();
                    user.setUsername("concurrent_user_" + finalI);
                    user.setEmail("user" + finalI + "@test.com");
                    user.setStatus(1);
                    user.setCreateTime(LocalDateTime.now());
                    businessService.saveUser(user);
                    successCount.incrementAndGet();
                    log.debug("线程 {} 写入成功: concurrent_user_{}", finalI, finalI);
                } catch (Exception e) {
                    log.error("线程 {} 写入失败: {}, 原因: {}", finalI, e.getMessage(), e.getClass().getSimpleName());
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS), "所有线程应该在60秒内完成");

        // v2.1 P1: 改轮询替代 sleep(2000)，等事务提交完成
        long initialFinalCount = businessService.countUsers();
        long deadline = System.currentTimeMillis() + 5000;
        long finalCount = initialFinalCount;
        long lastCount = initialFinalCount;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
            finalCount = businessService.countUsers();
            if (finalCount == lastCount) break;  // 稳定后退出
            lastCount = finalCount;
        }
        log.info("最终用户数量: {}, 成功数: {}, 失败数: {}", finalCount, successCount.get(), failCount.get());

        // 先验证实际写入成功数
        long actualInserted = finalCount - initialCount;
        assertEquals(successCount.get(), actualInserted,
                "成功计数应该等于实际插入记录数: 成功=" + successCount.get() + ", 实际=" + actualInserted);

        // v2.1 P0 修复：原 if (failCount>0) 仅告警不失败，掩盖并发写入丢失。改为强断言。
        assertEquals(0, failCount.get(),
                "不应有失败线程，实际失败: " + failCount.get());

        // 如果有失败，调整期望值
        long expectedCount = initialCount + successCount.get();
        assertEquals(expectedCount, finalCount,
                "数据库中应该有 " + successCount.get() + " 条新记录 (初始: " + initialCount + ", 最终: " + finalCount + ")");

        log.info("✅ {} 个线程并发写入完成,成功数: {}, 失败数: {}, 数据库新增: {}",
                threadCount, successCount.get(), failCount.get(), actualInserted);
    }

    @Test
    @Order(3)
    @DisplayName("测试3: 多线程并发写入不同数据源")
    void test03_ConcurrentWriteDifferentDataSources() throws InterruptedException, ExecutionException {
        int threadCount = 20;
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            int finalI = i;
            Future<Boolean> future = executorService.submit(() -> {
                try {
                    // 交替写入 business 和 log 数据源
                    if (finalI % 2 == 0) {
                        User user = new User();
                        user.setUsername("multi_ds_user_" + finalI);
                        user.setEmail("user" + finalI + "@test.com");
                        user.setStatus(1);
                        user.setCreateTime(LocalDateTime.now());
                        businessService.saveUser(user);
                    }
                    return true;
                } catch (Exception e) {
                    log.error("线程 {} 失败: {}", finalI, e.getMessage());
                    return false;
                }
            });
            futures.add(future);
        }

        int successCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successCount++;
            }
        }

        assertEquals(threadCount, successCount, "所有线程都应该成功");
        log.info("✅ {} 个线程并发写入不同数据源,全部成功", threadCount);
    }

    @Test
    @Order(4)
    @DisplayName("测试4: 连接池并发获取连接")
    void test04_ConcurrentGetConnection() throws InterruptedException {
        int threadCount = 30;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        DataSource ds = manager.getDataSource("business");

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try (Connection conn = ds.getConnection()) {
                    assertNotNull(conn);
                    assertTrue(conn.isValid(3));
                    successCount.incrementAndGet();

                    // 模拟一些数据库操作
                    Thread.sleep(10);
                } catch (Exception e) {
                    log.error("获取连接失败: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // v2.1 P1: 断言 latch.await 返回值
        assertTrue(latch.await(30, TimeUnit.SECONDS), "所有线程应在 30s 内完成");
        assertEquals(threadCount, successCount.get());

        log.info("✅ {} 个线程并发获取连接,全部成功", threadCount);
    }

    @Test
    @Order(5)
    @DisplayName("测试5: 并发场景下的数据隔离")
    void test05_DataIsolationUnderConcurrency() throws InterruptedException {
        int threadCount = 20;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger businessCount = new AtomicInteger(0);

        long initialBusinessCount = businessService.countUsers();

        for (int i = 0; i < threadCount; i++) {
            int finalI = i;
            executorService.submit(() -> {
                try {
                    // 所有线程都写入 business 数据源
                    User user = new User();
                    user.setUsername("isolation_test_" + finalI);
                    user.setEmail("user" + finalI + "@test.com");
                    user.setStatus(1);
                    user.setCreateTime(LocalDateTime.now());
                    businessService.saveUser(user);
                    businessCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));

        long finalBusinessCount = businessService.countUsers();

        assertEquals(initialBusinessCount + threadCount, finalBusinessCount,
                "business 数据源应该有 " + threadCount + " 条新记录");
        assertEquals(threadCount, businessCount.get());

        log.info("✅ 并发场景下数据隔离验证通过");
    }

    @Test
    @Order(6)
    @DisplayName("测试6: 并发获取不同组件")
    void test06_ConcurrentGetDifferentComponents() throws InterruptedException {
        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        // v2.1 P0: 子线程异常应计入 failCount 而非仅 log
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            int componentType = i % 4;  // 0-3 对应4种组件
            executorService.submit(() -> {
                try {
                    switch (componentType) {
                        case 0 -> manager.getDataSource("business");
                        case 1 -> manager.getSqlSessionFactory("business");
                        case 2 -> manager.getSqlSessionTemplate("business");
                        case 3 -> manager.getTransactionManager("business");
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // v2.1 P0: 异常计入 failCount 而非仅 log
                    failCount.incrementAndGet();
                    log.error("获取组件失败: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // v2.1 P0: 断言 latch.await 返回值 + failCount 为 0
        assertTrue(latch.await(10, TimeUnit.SECONDS), "所有线程应在 10s 内完成");
        assertEquals(0, failCount.get(), "不应有失败线程，实际: " + failCount.get());
        assertEquals(threadCount, successCount.get());

        log.info("✅ {} 个线程并发获取不同组件,全部成功", threadCount);
    }

    @Test
    @Order(7)
    @DisplayName("测试7: 高并发场景下的健康检查")
    void test07_HealthCheckUnderHighConcurrency() throws InterruptedException {
        int threadCount = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger healthyCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    if (manager.checkHealth("business")) {
                        healthyCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // v2.1 P0: 断言 latch.await 返回值
        assertTrue(latch.await(30, TimeUnit.SECONDS), "所有线程应在 30s 内完成");
        assertEquals(threadCount, healthyCount.get(), "所有健康检查都应该成功");

        log.info("✅ 高并发场景下健康检查全部通过");
    }

    @Test
    @Order(8)
    @DisplayName("测试8: 并发场景下的别名访问")
    void test08_AliasAccessUnderConcurrency() throws InterruptedException {
        int threadCount = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<DataSource> businessList = Collections.synchronizedList(new ArrayList<>());
        List<DataSource> orderList = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            int finalI = i;
            executorService.submit(() -> {
                try {
                    if (finalI % 2 == 0) {
                        businessList.add(manager.getDataSource("business"));
                    } else {
                        orderList.add(manager.getDataSource("order"));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // v2.1 P0: 断言 latch.await 返回值
        assertTrue(latch.await(10, TimeUnit.SECONDS), "所有线程应在 10s 内完成");

        // v2.1 P0: 原条件断言若全失败则跳过假通过。改为强断言。
        assertFalse(businessList.isEmpty(), "businessList 不应为空");
        assertFalse(orderList.isEmpty(), "orderList 不应为空");
        assertEquals(threadCount / 2, businessList.size(),
                "business 列表应含 threadCount/2 个元素，实际: " + businessList.size());
        assertEquals(threadCount / 2, orderList.size(),
                "order 列表应含 threadCount/2 个元素，实际: " + orderList.size());

        // 验证所有获取的都是同一个实例
        DataSource business = businessList.get(0);
        DataSource order = orderList.get(0);
        assertSame(business, order, "通过不同别名获取的应该是同一实例");

        log.info("✅ 并发场景下别名访问验证通过");
    }

    // ==================== 测试用服务类 ====================

    @Service
    @DataSourceOn("business")
    @DependsOn("multiDataSourceManager")
    @Getter
    static class BusinessService {
        @Autowired
        private UserMapper userMapper;

        @Transactional(rollbackFor = Exception.class, timeout = 30)
        public void saveUser(User user) {
            userMapper.insert(user);
        }

        public long countUsers() {
            return userMapper.selectCount(null);
        }
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.context.annotation.Import({
            MultiDataSourceAutoConfiguration.class
    })
    @MapperScan(basePackages = "fun.commons.framework4j.datasource.mapper.business",
            sqlSessionFactoryRef = "businessSqlSessionFactory")
    @org.springframework.context.annotation.ComponentScan(basePackages = "fun.commons.framework4j.datasource.functional")
    static class TestConfiguration {
    }
}
