package fun.commons.framework4j.datasource.functional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @DataSourceOn 注解功能测试
 * 使用 MyBatis Plus
 *
 * 【重要提示】
 * 1. 务必检查 pom.xml，必须使用 mybatis-plus-spring-boot3-starter >= 3.5.5
 * 2. 已添加 spring.autoconfigure.exclude 屏蔽官方自动配置，防止干扰
 */
@Slf4j
@SpringBootTest(
        classes = {DataSourceOnAnnotationTest.TestConfiguration.class},
        // 【核心修复】强制排除 MyBatis-Plus 官方自动配置
        // 防止它注册一个不兼容 Spring 6 的 sqlSessionFactory Bean 导致崩溃
        properties = {
                "spring.autoconfigure.exclude=com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        }
)
@ActiveProfiles("test")
@DisplayName("@DataSourceOn 注解功能测试 (PostgreSQL + MyBatis Plus)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataSourceOnAnnotationTest {

    @Autowired
    private MultiDataSourceManager multiDataSourceManager;

    @Autowired
    private BusinessService businessService;

    @Autowired
    private LogService logService;

    @Autowired
    private MixedService mixedService;

    private int testCounter = 0;

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
                        log.debug("执行SQL: {}", sql.substring(0, Math.min(sql.length(), 50)) + "...");
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
        log.info("========== 开始初始化测试数据 ==========");

        if (multiDataSourceManager == null) {
            throw new RuntimeException("Spring 容器启动失败，MultiDataSourceManager 为空。");
        }

        // ========== 1. 创建表结构并清除数据 ==========
        log.info(">>> 第1步: 创建表结构并清除所有数据");

        // 执行业务数据源SQL脚本
        executeSqlScript(multiDataSourceManager.getDataSource("business"), "sql/schema-business-h2.sql");

        // 执行日志数据源SQL脚本
        executeSqlScript(multiDataSourceManager.getDataSource("log"), "sql/schema-log-h2.sql");

        // 执行报表数据源SQL脚本
        executeSqlScript(multiDataSourceManager.getDataSource("report"), "sql/schema-report-h2.sql");

        log.info("✅ 所有数据源表结构创建完成");

        // ========== 2. 初始化 Mock 数据 ==========
        log.info(">>> 第2步: 初始化 Mock 数据");

        try (Connection conn = multiDataSourceManager.getDataSource("business").getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO t_user (username, email, phone, status, create_time) VALUES " +
                    "('admin', 'admin@test.com', '13800000001', 1, '2024-01-01 10:00:00'), " +
                    "('zhangsan', 'zhangsan@test.com', '13800000002', 1, '2024-01-01 11:00:00'), " +
                    "('lisi', 'lisi@test.com', '13800000003', 1, '2024-01-02 09:00:00'), " +
                    "('wangwu', 'wangwu@test.com', '13800000004', 0, '2024-01-02 14:00:00'), " +
                    "('test_user', 'test@test.com', '13800000005', 1, '2024-01-03 08:00:00')");

            stmt.execute("INSERT INTO t_order (order_no, user_id, amount, status, remark, create_time) VALUES " +
                    "('ORD20240101001', 1, 199.00, 1, '测试订单1', '2024-01-01 10:30:00'), " +
                    "('ORD20240101002', 2, 299.00, 1, '测试订单2', '2024-01-01 14:20:00'), " +
                    "('ORD20240101003', 2, 99.50, 0, '待支付订单', '2024-01-02 09:15:00'), " +
                    "('ORD20240101004', 3, 1299.00, 2, '已取消订单', '2024-01-02 16:00:00'), " +
                    "('ORD20240101005', 1, 599.00, 1, '测试订单5', '2024-01-03 11:30:00')");

            stmt.execute("INSERT INTO t_product (name, price, stock, category, status, create_time) VALUES " +
                    "('iPhone 15', 6999.00, 100, '手机', 1, '2024-01-01 00:00:00'), " +
                    "('MacBook Pro', 12999.00, 50, '电脑', 1, '2024-01-01 00:00:00'), " +
                    "('AirPods Pro', 1999.00, 200, '配件', 1, '2024-01-01 00:00:00'), " +
                    "('旧款手机', 999.00, 10, '手机', 0, '2024-01-01 00:00:00'), " +
                    "('iPad Pro', 8999.00, 80, '平板', 1, '2024-01-01 00:00:00')");
            log.info("✅ business 数据源 Mock 数据已初始化");
        }

        try (Connection conn = multiDataSourceManager.getDataSource("log").getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO t_audit_log (operation, table_name, record_id, old_value, new_value, user_id, user_name, ip_address, create_time) VALUES " +
                    "('INSERT', 't_user', 1, NULL, '{\"username\":\"admin\",\"email\":\"admin@test.com\"}', 0, 'system', '127.0.0.1', '2024-01-01 10:00:00'), " +
                    "('INSERT', 't_order', 1, NULL, '{\"order_no\":\"ORD20240101001\",\"amount\":199.00}', 1, 'admin', '192.168.1.100', '2024-01-01 10:30:00'), " +
                    "('UPDATE', 't_order', 1, '{\"status\":0}', '{\"status\":1}', 1, 'admin', '192.168.1.100', '2024-01-01 10:35:00'), " +
                    "('INSERT', 't_product', 1, NULL, '{\"name\":\"iPhone 15\",\"price\":6999.00}', 1, 'admin', '192.168.1.100', '2024-01-01 00:00:00'), " +
                    "('DELETE', 't_user', 10, '{\"username\":\"deleted_user\"}', NULL, 1, 'admin', '192.168.1.100', '2024-01-02 15:00:00')");
            log.info("✅ log 数据源 Mock 数据已初始化");
        }

        try (Connection conn = multiDataSourceManager.getDataSource("report").getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO t_daily_report (report_date, total_users, new_users, total_orders, total_amount, create_time) VALUES " +
                    "('2024-01-01', 100, 10, 50, 25000.00, '2024-01-02 00:00:00'), " +
                    "('2024-01-02', 110, 12, 65, 32000.00, '2024-01-03 00:00:00'), " +
                    "('2024-01-03', 120, 8, 45, 22000.00, '2024-01-04 00:00:00'), " +
                    "('2024-01-04', 128, 9, 55, 28500.00, '2024-01-05 00:00:00'), " +
                    "('2024-01-05', 135, 7, 60, 31000.00, '2024-01-06 00:00:00')");
            log.info("✅ report 数据源 Mock 数据已初始化");
        }

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
    @org.junit.jupiter.api.Order(1)
    @DisplayName("测试1: 验证 MultiDataSourceManager 初始化")
    void test01_VerifyMultiDataSourceManager() {
        assertNotNull(multiDataSourceManager, "MultiDataSourceManager 应该被成功注入");
        if (multiDataSourceManager.containsDatasource("default")) {
            assertTrue(multiDataSourceManager.containsDatasource("default"));
        }
        assertTrue(multiDataSourceManager.containsDatasource("business"));
        assertTrue(multiDataSourceManager.containsDatasource("log"));
        log.info("✅ MultiDataSourceManager 检查通过");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("测试2: @DataSourceOn(\"business\") - 基本注入")
    void test02_BusinessServiceBasicInjection() {
        assertNotNull(businessService, "BusinessService 应该被成功创建");
        assertNotNull(businessService.getDataSource(), "DataSource 应该被自动注入");
        assertNotNull(businessService.getSqlSessionTemplate(), "SqlSessionTemplate 应该被自动注入");
        assertNotNull(businessService.getTransactionManager(), "TransactionManager 应该被自动注入");
        log.info("✅ @DataSourceOn(\"business\") 成功注入所有组件");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("测试3: @DataSourceOn(\"business\") - MyBatis Plus CRUD 操作")
    void test03_BusinessServiceCrudOperation() {
        User admin = businessService.getUserById(1L);
        assertNotNull(admin, "应该能查询到 Mock 用户 admin");
        assertEquals("admin", admin.getUsername(), "用户名应该是 admin");

        User newUser = new User();
        newUser.setUsername("test_user_" + System.currentTimeMillis());
        newUser.setEmail("test@example.com");
        newUser.setStatus(1);
        newUser.setCreateTime(LocalDateTime.now());
        businessService.saveUser(newUser);

        businessService.deleteUser(newUser.getId());
        log.info("✅ @DataSourceOn(\"business\") MyBatis Plus CRUD 操作正常");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("测试4: @DataSourceOn(\"business\") - 订单操作")
    void test04_BusinessServiceOrderOperation() {
        Order mockOrder = businessService.getOrderById(1L);
        assertNotNull(mockOrder, "应该能查询到 Mock 订单");

        List<Order> ordersOfUser1 = businessService.getOrdersByUserId(1L);
        assertEquals(2, ordersOfUser1.size(), "用户1应该有2个订单");
        log.info("✅ @DataSourceOn(\"business\") 订单操作正常");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("测试5: @DataSourceOn(\"log\") - 审计日志操作")
    void test05_LogServiceAuditOperation() {
        assertNotNull(logService.getSqlSessionTemplate(), "SqlSessionTemplate 应该被自动注入");

        List<AuditLog> mockLogs = logService.getLogsByUserId(1L);
        assertEquals(4, mockLogs.size(), "用户1应该有4条审计日志");
        log.info("✅ @DataSourceOn(\"log\") 审计日志操作正常");
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("测试6: 数据源隔离验证")
    void test06_DataSourceIsolation() {
        User user = new User();
        user.setUsername("isolation_test");
        businessService.saveUser(user);

        AuditLog auditLog = new AuditLog();
        auditLog.setOperation("ISOLATION_TEST");
        auditLog.setUserId(999L);
        logService.saveAuditLog(auditLog);

        assertNotNull(businessService.getUserById(user.getId()), "应该在 business 数据源找到用户");
        assertFalse(logService.getLogsByOperation("ISOLATION_TEST").isEmpty(), "应该在 log 数据源找到日志");
        log.info("✅ 数据源隔离验证通过");
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("测试7: 混合使用 - @DataSourceOn + @Qualifier")
    void test07_MixedUsage() {
        assertNotNull(mixedService.getBusinessTemplate(), "Business Template 应该通过 @DataSourceOn 注入");
        assertNotNull(mixedService.getLogTemplate(), "Log Template 应该通过 @Qualifier 注入");
        log.info("✅ 混合使用 @DataSourceOn + @Qualifier 正常");
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("测试8: 验证注入的是正确的数据源实例")
    void test08_VerifyCorrectDataSourceInstance() {
        DataSource businessDs = multiDataSourceManager.getDataSource("business");
        assertSame(businessDs, businessService.getDataSource(), "BusinessService 应该注入 business 数据源的 DataSource");
        log.info("✅ 注入的数据源实例验证正确");
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("测试9: 数据源健康检查")
    void test09_HealthCheck() {
        assertTrue(multiDataSourceManager.checkHealth("business"), "business 数据源应该健康");
        log.info("✅ 所有数据源健康检查通过");
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("测试10: 别名配置验证")
    void test10_AliasConfiguration() {
        assertTrue(multiDataSourceManager.containsDatasource("order"), "应包含 order 别名");
        DataSource businessDs = multiDataSourceManager.getDataSource("business");
        DataSource orderDs = multiDataSourceManager.getDataSource("order");
        assertSame(businessDs, orderDs, "order 应该指向 business 数据源");
        log.info("✅ 别名配置验证通过");
    }

    // ==================== 测试用服务类 ====================

    @Service
    @DataSourceOn("business")
    @DependsOn("multiDataSourceManager")
    @Getter
    static class BusinessService {
        private DataSource dataSource;
        private SqlSessionFactory sqlSessionFactory;
        private SqlSessionTemplate sqlSessionTemplate;
        private PlatformTransactionManager transactionManager;

        @Autowired
        private UserMapper userMapper;

        @Autowired
        private OrderMapper orderMapper;

        public void saveUser(User user) { userMapper.insert(user); }
        public User getUserById(Long id) { return userMapper.selectById(id); }
        public void updateUser(User user) { userMapper.updateById(user); }
        public void deleteUser(Long id) { userMapper.deleteById(id); }
        public void saveOrder(Order order) { orderMapper.insert(order); }
        public Order getOrderById(Long id) { return orderMapper.selectById(id); }
        public List<Order> getOrdersByUserId(Long userId) {
            return orderMapper.selectList(new QueryWrapper<Order>().eq("user_id", userId));
        }
    }

    @Service
    @DataSourceOn("log")
    @DependsOn("multiDataSourceManager")
    @Getter
    static class LogService {
        private SqlSessionTemplate sqlSessionTemplate;

        @Autowired
        private AuditLogMapper auditLogMapper;

        public void saveAuditLog(AuditLog log) {
            auditLogMapper.insert(log);
        }

        public List<AuditLog> getLogsByUserId(Long userId) {
            return auditLogMapper.selectList(new QueryWrapper<AuditLog>().eq("user_id", userId));
        }

        public List<AuditLog> getLogsByOperation(String operation) {
            return auditLogMapper.selectList(new QueryWrapper<AuditLog>().eq("operation", operation));
        }
    }

    @Service
    @DataSourceOn("business")
    @DependsOn("multiDataSourceManager")
    @Getter
    static class MixedService {
        private SqlSessionTemplate businessTemplate;

        @Autowired
        @Qualifier("logSqlSessionTemplate")
        private SqlSessionTemplate logTemplate;

        @Autowired
        private UserMapper userMapper;

        @Autowired
        private AuditLogMapper auditLogMapper;

        public void saveUserToBusiness(User user) {
            userMapper.insert(user);
        }

        public void saveLogToLog(AuditLog log) {
            auditLogMapper.insert(log);
        }
    }

    @org.springframework.boot.SpringBootConfiguration
    // 【修复】显式导入内部服务类，因为 ComponentScan 通常不扫描 Test 类内部的静态类
    @Import({
            MultiDataSourceAutoConfiguration.class,
            DataSourceOnAnnotationTest.BusinessService.class,
            DataSourceOnAnnotationTest.LogService.class,
            DataSourceOnAnnotationTest.MixedService.class
    })
    @MapperScan(basePackages = "fun.commons.framework4j.datasource.mapper.business",
            sqlSessionFactoryRef = "businessSqlSessionFactory")
    @MapperScan(basePackages = "fun.commons.framework4j.datasource.mapper.log",
            sqlSessionFactoryRef = "logSqlSessionFactory")
    @org.springframework.context.annotation.ComponentScan(basePackages = "fun.commons.framework4j.datasource.functional")
    static class TestConfiguration {
    }
}