package fun.commons.framework4j.datasource.functional;

import fun.commons.framework4j.datasource.annotation.DataSourceOn;
import fun.commons.framework4j.datasource.config.MultiDataSourceAutoConfiguration;
import fun.commons.framework4j.datasource.manager.MultiDataSourceManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JdbcTemplate基础功能测试
 * <p>
 * 测试场景:
 * 1. JdbcTemplate注入验证
 * 2. 基本CRUD操作
 * 3. 多数据源隔离
 */
@Slf4j
@SpringBootTest(
        classes = {JdbcTemplateBasicTest.TestConfiguration.class},
        properties = {
                "spring.autoconfigure.exclude=com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        }
)
@ActiveProfiles("test")
@DisplayName("JdbcTemplate基础功能测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcTemplateBasicTest {

    @Autowired
    private MultiDataSourceManager manager;

    @Autowired
    private BusinessJdbcService businessService;

    @Autowired
    private LogJdbcService logService;

    private static int testCounter = 0;

    @BeforeAll
    void initTestData() throws Exception {
        log.info("========== 初始化测试数据 ==========");

        // 创建表结构
        executeSqlScript(manager.getDataSource("business"), "sql/schema-business-h2.sql");
        executeSqlScript(manager.getDataSource("log"), "sql/schema-log-h2.sql");

        log.info("✅ 表结构创建完成");
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
    @Order(1)
    @DisplayName("测试1: JdbcTemplate注入验证")
    void test01_JdbcTemplateInjection() {
        // 验证Manager中的JdbcTemplate获取
        JdbcTemplate businessTemplate = manager.getJdbcTemplate("business");
        assertNotNull(businessTemplate, "Business JdbcTemplate应该被成功注入");

        JdbcTemplate logTemplate = manager.getJdbcTemplate("log");
        assertNotNull(logTemplate, "Log JdbcTemplate应该被成功注入");

        // 验证Service中的JdbcTemplate注入
        assertNotNull(businessService.getJdbcTemplate(), "BusinessService的JdbcTemplate应该被注入");
        assertNotNull(logService.getJdbcTemplate(), "LogService的JdbcTemplate应该被注入");

        log.info("✅ JdbcTemplate注入验证通过");
    }

    @Test
    @Order(2)
    @DisplayName("测试2: 基本CRUD操作")
    void test02_BasicCrudOperations() {
        // 创建数据
        String insertSql = "INSERT INTO t_user (username, email, phone, status) VALUES (?, ?, ?, ?)";
        int insertResult = businessService.getJdbcTemplate().update(
                insertSql, "jdbcTemplate_test", "jdbc@test.com", "13800000000", 1);
        assertEquals(1, insertResult, "插入操作应该成功");

        // 查询数据
        String selectSql = "SELECT * FROM t_user WHERE username = ?";
        Map<String, Object> user = businessService.getJdbcTemplate().queryForMap(
                selectSql, "jdbcTemplate_test");
        assertNotNull(user, "应该能查询到插入的数据");
        assertEquals("jdbc@test.com", user.get("email"));
        assertEquals("13800000000", user.get("phone"));
        assertEquals(1, user.get("status"));

        // 更新数据
        String updateSql = "UPDATE t_user SET email = ? WHERE username = ?";
        int updateResult = businessService.getJdbcTemplate().update(
                updateSql, "updated@test.com", "jdbcTemplate_test");
        assertEquals(1, updateResult, "更新操作应该成功");

        // 验证更新
        Map<String, Object> updatedUser = businessService.getJdbcTemplate().queryForMap(
                selectSql, "jdbcTemplate_test");
        assertEquals("updated@test.com", updatedUser.get("email"));

        // 删除数据
        String deleteSql = "DELETE FROM t_user WHERE username = ?";
        int deleteResult = businessService.getJdbcTemplate().update(
                deleteSql, "jdbcTemplate_test");
        assertEquals(1, deleteResult, "删除操作应该成功");

        log.info("✅ 基本CRUD操作测试通过");
    }

    @Test
    @Order(3)
    @DisplayName("测试3: 多数据源隔离")
    void test03_DataSourceIsolation() {
        // 在business数据源插入数据
        businessService.getJdbcTemplate().update(
                "INSERT INTO t_user (username, email, phone, status) VALUES (?, ?, ?, ?)",
                "isolation_test", "iso@test.com", "13800000999", 1);

        // 在log数据源插入审计日志
        logService.getJdbcTemplate().update(
                "INSERT INTO t_audit_log (operation, table_name, record_id, user_id, user_name) VALUES (?, ?, ?, ?, ?)",
                "ISOLATION_TEST", "t_user", 999L, 1L, "test_user");

        // 验证数据隔离
        Integer businessCount = businessService.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM t_user WHERE username = ?", Integer.class, "isolation_test");
        assertEquals(1, businessCount, "Business数据源应该有测试用户");

        Integer logCount = logService.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE operation = ?", Integer.class, "ISOLATION_TEST");
        assertEquals(1, logCount, "Log数据源应该有测试日志");

        // 验证跨数据源查询失败（表不存在）
        assertThrows(Exception.class, () -> {
            businessService.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM t_audit_log", Integer.class);
        }, "Business数据源不应该有audit_log表");

        assertThrows(Exception.class, () -> {
            logService.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM t_user", Integer.class);
        }, "Log数据源不应该有user表");

        log.info("✅ 多数据源隔离测试通过");
    }

    /**
     * 执行SQL脚本文件
     */
    private void executeSqlScript(javax.sql.DataSource dataSource, String scriptPath) throws Exception {
        org.springframework.core.io.ClassPathResource resource =
            new org.springframework.core.io.ClassPathResource(scriptPath);
        try (java.io.InputStream inputStream = resource.getInputStream();
             java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {

            String sqlScript = org.springframework.util.FileCopyUtils.copyToString(
                new java.io.InputStreamReader(inputStream));

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

    // ==================== 测试用服务类 ====================

    @Service
    @DataSourceOn("business")
    @DependsOn("multiDataSourceManager")
    @Getter
    static class BusinessJdbcService {
        private JdbcTemplate jdbcTemplate;
    }

    @Service
    @DataSourceOn("log")
    @DependsOn("multiDataSourceManager")
    @Getter
    static class LogJdbcService {
        private JdbcTemplate jdbcTemplate;
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.context.annotation.Import({
            MultiDataSourceAutoConfiguration.class,
            JdbcTemplateBasicTest.BusinessJdbcService.class,
            JdbcTemplateBasicTest.LogJdbcService.class
    })
    static class TestConfiguration {
    }
}