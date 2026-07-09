package fun.commons.framework4j.datasource.unit;

import fun.commons.framework4j.datasource.config.MultiDataSourceAutoConfiguration;
import fun.commons.framework4j.datasource.exception.DataSourceException;
import fun.commons.framework4j.datasource.manager.MultiDataSourceManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MultiDataSourceManager 单元测试
 * <p>
 * 测试场景:
 * 1. 数据源注册与获取
 * 2. 别名管理
 * 3. 健康检查
 * 4. 动态添加/删除数据源
 * 5. 异常处理
 * 6. 边界条件
 */
@Slf4j
@SpringBootTest(
        classes = {MultiDataSourceManagerTest.TestConfiguration.class},
        properties = {
                "spring.autoconfigure.exclude=com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        }
)
@ActiveProfiles("pgsql-test")
@DisplayName("MultiDataSourceManager 单元测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MultiDataSourceManagerTest {

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
    @DisplayName("测试1: Manager 初始化验证")
    void test01_ManagerInitialization() {
        assertNotNull(manager, "MultiDataSourceManager 应该被成功注入");
        List<String> allNames = manager.getAllDatasourceNames();
        assertNotNull(allNames, "数据源名称列表不应该为空");
        assertTrue(allNames.size() >= 3, "至少应该有3个数据源(default/business, log, report)");
        log.info("✅ Manager 包含 {} 个数据源: {}", allNames.size(), allNames);
    }

    @Test
    @Order(2)
    @DisplayName("测试2: 获取 DataSource")
    void test02_GetDataSource() {
        DataSource businessDs = manager.getDataSource("business");
        assertNotNull(businessDs, "business 数据源应该存在");

        DataSource logDs = manager.getDataSource("log");
        assertNotNull(logDs, "log 数据源应该存在");

        assertNotSame(businessDs, logDs, "不同的数据源应该是不同的实例");
        log.info("✅ 获取 DataSource 成功");
    }

    @Test
    @Order(3)
    @DisplayName("测试3: 获取 SqlSessionFactory")
    void test03_GetSqlSessionFactory() {
        SqlSessionFactory businessFactory = manager.getSqlSessionFactory("business");
        assertNotNull(businessFactory, "business SqlSessionFactory 应该存在");

        SqlSessionFactory logFactory = manager.getSqlSessionFactory("log");
        assertNotNull(logFactory, "log SqlSessionFactory 应该存在");

        assertNotSame(businessFactory, logFactory, "不同数据源的 Factory 应该是不同的实例");
        log.info("✅ 获取 SqlSessionFactory 成功");
    }

    @Test
    @Order(4)
    @DisplayName("测试4: 获取 SqlSessionTemplate")
    void test04_GetSqlSessionTemplate() {
        SqlSessionTemplate businessTemplate = manager.getSqlSessionTemplate("business");
        assertNotNull(businessTemplate, "business SqlSessionTemplate 应该存在");

        SqlSessionTemplate logTemplate = manager.getSqlSessionTemplate("log");
        assertNotNull(logTemplate, "log SqlSessionTemplate 应该存在");

        assertNotSame(businessTemplate, logTemplate, "不同数据源的 Template 应该是不同的实例");
        log.info("✅ 获取 SqlSessionTemplate 成功");
    }

    @Test
    @Order(5)
    @DisplayName("测试5: 获取 TransactionManager")
    void test05_GetTransactionManager() {
        PlatformTransactionManager businessTm = manager.getTransactionManager("business");
        assertNotNull(businessTm, "business TransactionManager 应该存在");

        PlatformTransactionManager logTm = manager.getTransactionManager("log");
        assertNotNull(logTm, "log TransactionManager 应该存在");

        assertNotSame(businessTm, logTm, "不同数据源的 TransactionManager 应该是不同的实例");
        log.info("✅ 获取 TransactionManager 成功");
    }

    @Test
    @Order(6)
    @DisplayName("测试6: containsDatasource 检查")
    void test06_ContainsDatasource() {
        assertTrue(manager.containsDatasource("business"), "应该包含 business 数据源");
        assertTrue(manager.containsDatasource("log"), "应该包含 log 数据源");
        assertTrue(manager.containsDatasource("report"), "应该包含 report 数据源");

        // 测试别名
        assertTrue(manager.containsDatasource("order"), "应该包含 order 别名");
        assertTrue(manager.containsDatasource("product"), "应该包含 product 别名");

        // 测试不存在的数据源
        assertFalse(manager.containsDatasource("nonexistent"), "不应该包含不存在的数据源");

        log.info("✅ containsDatasource 检查正常");
    }

    @Test
    @Order(7)
    @DisplayName("测试7: 健康检查")
    void test07_HealthCheck() {
        assertTrue(manager.checkHealth("business"), "business 数据源应该健康");
        assertTrue(manager.checkHealth("log"), "log 数据源应该健康");
        assertTrue(manager.checkHealth("report"), "report 数据源应该健康");

        // 通过别名检查
        assertTrue(manager.checkHealth("order"), "通过 order 别名检查应该健康");

        log.info("✅ 所有数据源健康检查通过");
    }

    @Test
    @Order(8)
    @DisplayName("测试8: 获取所有数据源名称")
    void test08_GetAllDatasourceNames() {
        List<String> allNames = manager.getAllDatasourceNames();
        assertNotNull(allNames, "数据源名称列表不应该为空");

        assertTrue(allNames.contains("business") || allNames.contains("order") || allNames.contains("product"),
                "应该包含 business 或其别名");
        assertTrue(allNames.contains("log"), "应该包含 log");
        assertTrue(allNames.contains("report"), "应该包含 report");

        log.info("✅ 所有数据源名称: {}", allNames);
    }

    @Test
    @Order(9)
    @DisplayName("测试9: 获取不存在的数据源抛出异常")
    void test09_GetNonexistentDataSourceThrowsException() {
        assertThrows(DataSourceException.class, () -> {
            manager.getDataSource("nonexistent");
        }, "获取不存在的数据源应该抛出 DataSourceException");

        assertThrows(DataSourceException.class, () -> {
            manager.getSqlSessionFactory("nonexistent");
        }, "获取不存在的 SqlSessionFactory 应该抛出 DataSourceException");

        assertThrows(DataSourceException.class, () -> {
            manager.getSqlSessionTemplate("nonexistent");
        }, "获取不存在的 SqlSessionTemplate 应该抛出 DataSourceException");

        assertThrows(DataSourceException.class, () -> {
            manager.getTransactionManager("nonexistent");
        }, "获取不存在的 TransactionManager 应该抛出 DataSourceException");

        log.info("✅ 获取不存在的数据源正确抛出异常");
    }

    @Test
    @Order(10)
    @DisplayName("测试10: 数据源连接验证")
    void test10_DataSourceConnectionVerification() throws Exception {
        DataSource businessDs = manager.getDataSource("business");

        try (Connection conn = businessDs.getConnection()) {
            assertNotNull(conn, "连接不应该为空");
            assertFalse(conn.isClosed(), "连接应该是打开的");

            // 验证连接到正确的数据库
            String url = conn.getMetaData().getURL();
            assertTrue(url.contains("testdb2"), "business 应该连接到 testdb2");

            // 执行简单查询
            boolean valid = conn.isValid(3);
            assertTrue(valid, "连接应该有效");
        }

        log.info("✅ 数据源连接验证通过");
    }

    @Test
    @Order(11)
    @DisplayName("测试11: 验证不同数据源连接到不同数据库")
    void test11_DifferentDataSourcesDifferentDatabases() throws Exception {
        try (Connection businessConn = manager.getDataSource("business").getConnection();
             Connection logConn = manager.getDataSource("log").getConnection();
             Connection reportConn = manager.getDataSource("report").getConnection()) {

            String businessUrl = businessConn.getMetaData().getURL();
            String logUrl = logConn.getMetaData().getURL();
            String reportUrl = reportConn.getMetaData().getURL();

            assertTrue(businessUrl.contains("testdb2"), "business 应该连接到 testdb2");
            assertTrue(logUrl.contains("testdb3"), "log 应该连接到 testdb3");
            assertTrue(reportUrl.contains("testdb4"), "report 应该连接到 testdb4");

            assertNotEquals(businessUrl, logUrl, "business 和 log 应该连接到不同的数据库");
            assertNotEquals(businessUrl, reportUrl, "business 和 report 应该连接到不同的数据库");
            assertNotEquals(logUrl, reportUrl, "log 和 report 应该连接到不同的数据库");
        }

        log.info("✅ 不同数据源连接到不同数据库验证通过");
    }

    @Test
    @Order(12)
    @DisplayName("测试12: 边界条件 - null 和空字符串")
    void test12_BoundaryConditions() {
        assertFalse(manager.containsDatasource(null), "null 不应该被识别为存在的数据源");
        assertFalse(manager.containsDatasource(""), "空字符串不应该被识别为存在的数据源");
        assertFalse(manager.containsDatasource("   "), "空白字符串不应该被识别为存在的数据源");

        log.info("✅ 边界条件测试通过");
    }

    @Test
    @Order(13)
    @DisplayName("测试13: 通过别名获取组件验证")
    void test13_GetComponentsThroughAlias() {
        // 通过主名称获取
        DataSource businessDs = manager.getDataSource("business");
        SqlSessionFactory businessFactory = manager.getSqlSessionFactory("business");

        // 通过别名获取
        DataSource orderDs = manager.getDataSource("order");
        SqlSessionFactory orderFactory = manager.getSqlSessionFactory("order");

        // 验证是同一实例
        assertSame(businessDs, orderDs, "通过别名获取的 DataSource 应该是同一实例");
        assertSame(businessFactory, orderFactory, "通过别名获取的 SqlSessionFactory 应该是同一实例");

        log.info("✅ 通过别名获取组件验证通过");
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.context.annotation.Import({
            MultiDataSourceAutoConfiguration.class
    })
    static class TestConfiguration {
    }
}
