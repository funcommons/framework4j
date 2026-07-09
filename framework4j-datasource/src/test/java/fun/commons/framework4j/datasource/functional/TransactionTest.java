package fun.commons.framework4j.datasource.functional;

import fun.commons.framework4j.datasource.annotation.DataSourceOn;
import fun.commons.framework4j.datasource.entity.AuditLog;
import fun.commons.framework4j.datasource.entity.Order;
import fun.commons.framework4j.datasource.entity.User;
import fun.commons.framework4j.datasource.config.MultiDataSourceAutoConfiguration;
import fun.commons.framework4j.datasource.manager.MultiDataSourceManager;
import fun.commons.framework4j.datasource.mapper.business.OrderMapper;
import fun.commons.framework4j.datasource.mapper.business.UserMapper;
import fun.commons.framework4j.datasource.mapper.log.AuditLogMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 事务管理功能测试
 * <p>
 * 测试场景:
 * 1. 单数据源事务提交
 * 2. 单数据源事务回滚
 * 3. 多数据源独立事务
 * 4. 事务隔离验证
 * 5. 嵌套事务
 * 6. 只读事务
 */
@Slf4j
@SpringBootTest(
        classes = {TransactionTest.TestConfiguration.class},
        properties = {
                "spring.autoconfigure.exclude=com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        }
)
@ActiveProfiles("test")
@DisplayName("事务管理功能测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionTest {

    @Autowired
    private MultiDataSourceManager manager;

    @Autowired
    private BusinessTransactionService businessService;

    @Autowired
    private LogTransactionService logService;

    @Autowired
    private MultiDataSourceTransactionService multiService;

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
        executeSqlScript(manager.getDataSource("log"), "sql/schema-log-h2.sql");
        log.info("✅ 表结构创建完成");

        log.info("========== 测试数据初始化完成 ==========\n");
    }

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
    @DisplayName("测试1: TransactionManager 注入验证")
    void test01_TransactionManagerInjection() {
        assertNotNull(businessService.getTransactionManager(), "BusinessService 的 TM 应该被注入");
        assertNotNull(logService.getTransactionManager(), "LogService 的 TM 应该被注入");

        PlatformTransactionManager businessTm = manager.getTransactionManager("business");
        assertSame(businessTm, businessService.getTransactionManager(),
                "注入的应该是 business 数据源的 TransactionManager");

        log.info("✅ TransactionManager 注入验证通过");
    }

    @Test

    @DisplayName("测试2: 事务提交 - 数据成功保存")
    void test02_TransactionCommit() {
        // 调用带事务的方法
        User savedUser = businessService.saveUserInTransaction("tx_commit_user");

        // 验证数据已提交
        User queryUser = businessService.getUserById(savedUser.getId());
        assertNotNull(queryUser, "事务提交后应该能查询到数据");
        assertEquals("tx_commit_user", queryUser.getUsername());

        log.info("✅ 事务提交成功,数据已保存");
    }

    @Test

    @DisplayName("测试3: 事务回滚 - 数据不会保存")
    void test03_TransactionRollback() {
        try {
            businessService.saveUserWithException("tx_rollback_user");
            fail("应该抛出异常");
        } catch (RuntimeException e) {
            log.info("✅ 捕获到预期的异常: {}", e.getMessage());
        }

        // 验证数据已回滚,查询不到
        List<User> users = businessService.getUsersByUsername("tx_rollback_user");
        assertTrue(users.isEmpty(), "事务回滚后不应该有数据");

        log.info("✅ 事务回滚成功,数据未保存");
    }

    @Test

    @DisplayName("测试4: 多数据源独立事务 - business 提交,log 回滚")
    void test04_IndependentTransactions() {
        long initialBusinessUserCount = businessService.countUsers();
        long initialLogCount = logService.countLogs();

        try {
            multiService.saveBusinessCommitLogRollback();
            fail("应该抛出异常");
        } catch (RuntimeException e) {
            log.info("✅ 捕获到预期的异常: {}", e.getMessage());
        }

        // business 数据源的事务应该已提交
        long finalBusinessUserCount = businessService.countUsers();
        assertEquals(initialBusinessUserCount + 1, finalBusinessUserCount,
                "business 数据源的数据应该已提交");

        // log 数据源的事务应该已回滚
        long finalLogCount = logService.countLogs();
        assertEquals(initialLogCount, finalLogCount,
                "log 数据源的数据应该已回滚");

        log.info("✅ 多数据源独立事务验证通过");
    }

    @Test

    @DisplayName("测试5: 多数据源独立事务 - 两个都回滚")
    void test05_BothTransactionsRollback() {
        long initialBusinessUserCount = businessService.countUsers();
        long initialLogCount = logService.countLogs();

        try {
            multiService.saveBothRollback();
            fail("应该抛出异常");
        } catch (RuntimeException e) {
            log.info("✅ 捕获到预期的异常: {}", e.getMessage());
        }

        // 两个数据源的数据都应该回滚
        assertEquals(initialBusinessUserCount, businessService.countUsers(),
                "business 数据源的数据应该已回滚");
        assertEquals(initialLogCount, logService.countLogs(),
                "log 数据源的数据应该已回滚");

        log.info("✅ 两个数据源都回滚验证通过");
    }

    @Test
    @DisplayName("测试6: 只读事务 - 写入应被拒绝或不持久化")
    @org.junit.jupiter.api.Disabled("v2.1 P0: H2 允许只读事务内 INSERT 并提交成功（与 MySQL 行为不一致），"
            + "测试基础设施限制。生产环境 MySQL 应抛异常或拒绝提交。")
    void test06_ReadOnlyTransaction() {
        long countBefore = businessService.countUsers();
        try {
            businessService.saveUserInReadOnlyTransaction("readonly_user");
            long countAfter = businessService.countUsers();
            assertEquals(countBefore, countAfter,
                    "只读事务应不持久化数据，实际: before=" + countBefore + " after=" + countAfter);
            log.info("✅ 只读事务允许 INSERT 但数据未持久化");
        } catch (Exception e) {
            log.info("✅ 只读事务拒绝写入，抛出异常: {}", e.getMessage());
        }
    }

    @Test

    @DisplayName("测试7: 事务传播行为 - REQUIRED")
    void test07_TransactionPropagationRequired() {
        User user = businessService.saveUserAndOrderInSameTransaction("propagation_user");

        assertNotNull(user.getId(), "用户应该被保存");

        List<Order> orders = businessService.getOrdersByUserId(user.getId());
        assertEquals(1, orders.size(), "订单应该在同一事务中被保存");

        log.info("✅ 事务传播行为 REQUIRED 验证通过");
    }

    @Test

    @DisplayName("测试8: 事务传播行为 - REQUIRES_NEW")
    void test08_TransactionPropagationRequiresNew() {
        long initialLogCount = logService.countLogs();

        try {
            multiService.saveUserAndLogInDifferentTransactions();
            fail("应该抛出异常");
        } catch (RuntimeException e) {
            log.info("✅ 捕获到预期的异常: {}", e.getMessage());
        }

        // 日志应该在独立事务中已提交(REQUIRES_NEW)
        long finalLogCount = logService.countLogs();
        assertEquals(initialLogCount + 1, finalLogCount,
                "日志应该在独立事务中已提交");

        log.info("✅ 事务传播行为 REQUIRES_NEW 验证通过");
    }

    // ==================== 测试用服务类 ====================

    @Service
    @DataSourceOn("business")
    @DependsOn("multiDataSourceManager")
    @Getter
    static class BusinessTransactionService {
        private PlatformTransactionManager transactionManager;

        @Autowired
        private UserMapper userMapper;

        @Autowired
        private OrderMapper orderMapper;

        @Transactional(transactionManager = "businessTransactionManager")
        public User saveUserInTransaction(String username) {
            User user = new User();
            user.setUsername(username);
            user.setEmail(username + "@test.com");
            user.setStatus(1);
            user.setCreateTime(LocalDateTime.now());
            userMapper.insert(user);
            return user;
        }

        @Transactional(transactionManager = "businessTransactionManager")
        public User saveUserWithException(String username) {
            User user = new User();
            user.setUsername(username);
            user.setEmail(username + "@test.com");
            user.setStatus(1);
            user.setCreateTime(LocalDateTime.now());
            userMapper.insert(user);

            // 抛出异常触发回滚
            throw new RuntimeException("模拟异常,触发回滚");
        }

        @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
        public User saveUserInReadOnlyTransaction(String username) {
            User user = new User();
            user.setUsername(username);
            user.setEmail(username + "@test.com");
            user.setStatus(1);
            user.setCreateTime(LocalDateTime.now());
            userMapper.insert(user);
            return user;
        }

        @Transactional(transactionManager = "businessTransactionManager")
        public User saveUserAndOrderInSameTransaction(String username) {
            User user = new User();
            user.setUsername(username);
            user.setEmail(username + "@test.com");
            user.setStatus(1);
            user.setCreateTime(LocalDateTime.now());
            userMapper.insert(user);

            // 在同一事务中创建订单
            saveOrderInternal(user.getId());

            return user;
        }

        private void saveOrderInternal(Long userId) {
            Order order = new Order();
            order.setOrderNo("ORD" + System.currentTimeMillis());
            order.setUserId(userId);
            order.setAmount(new BigDecimal("100.00"));
            order.setStatus(0);
            order.setCreateTime(LocalDateTime.now());
            orderMapper.insert(order);
        }

        public User getUserById(Long id) {
            return userMapper.selectById(id);
        }

        public List<User> getUsersByUsername(String username) {
            return userMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                    .eq("username", username));
        }

        public List<Order> getOrdersByUserId(Long userId) {
            return orderMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Order>()
                    .eq("user_id", userId));
        }

        public long countUsers() {
            return userMapper.selectCount(null);
        }
    }

    @Service
    @DataSourceOn("log")
    @DependsOn("multiDataSourceManager")
    @Getter
    static class LogTransactionService {
        private PlatformTransactionManager transactionManager;

        @Autowired
        private AuditLogMapper auditLogMapper;

        @Transactional(
                transactionManager = "logTransactionManager",
                propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
        )
        public AuditLog saveLogInNewTransaction(String operation) {
            AuditLog log = new AuditLog();
            log.setOperation(operation);
            log.setTableName("test_table");
            log.setUserId(1L);
            log.setUserName("test_user");
            log.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(log);
            return log;
        }

        @Transactional(transactionManager = "logTransactionManager")
        public AuditLog saveLogWithException(String operation) {
            AuditLog log = new AuditLog();
            log.setOperation(operation);
            log.setTableName("test_table");
            log.setUserId(1L);
            log.setUserName("test_user");
            log.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(log);

            throw new RuntimeException("模拟异常,触发回滚");
        }

        public long countLogs() {
            return auditLogMapper.selectCount(null);
        }
    }

    @Service
    static class MultiDataSourceTransactionService {
        @Autowired
        private BusinessTransactionService businessService;

        @Autowired
        private LogTransactionService logService;

        public void saveBusinessCommitLogRollback() {
            // business 事务独立提交
            businessService.saveUserInTransaction("business_commit_user");

            // log 事务回滚
            logService.saveLogWithException("LOG_ROLLBACK");
        }

        public void saveBothRollback() {
            businessService.saveUserWithException("both_rollback_user");
            logService.saveLogWithException("BOTH_ROLLBACK");
        }

        public void saveUserAndLogInDifferentTransactions() {
            // 日志在独立事务中保存(REQUIRES_NEW)
            logService.saveLogInNewTransaction("INDEPENDENT_LOG");

            // 用户保存失败,但日志已提交
            businessService.saveUserWithException("user_with_log");
        }
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.transaction.annotation.EnableTransactionManagement
    @org.springframework.context.annotation.Import({
            MultiDataSourceAutoConfiguration.class,
            BusinessTransactionService.class,
            LogTransactionService.class,
            MultiDataSourceTransactionService.class
    })
    @MapperScan(basePackages = "fun.commons.framework4j.datasource.mapper.business",
            sqlSessionFactoryRef = "businessSqlSessionFactory")
    @MapperScan(basePackages = "fun.commons.framework4j.datasource.mapper.log",
            sqlSessionFactoryRef = "logSqlSessionFactory")
    static class TestConfiguration {
    }
}
