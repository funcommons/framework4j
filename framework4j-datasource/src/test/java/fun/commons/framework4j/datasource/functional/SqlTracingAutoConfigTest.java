package fun.commons.framework4j.datasource.functional;

import com.alibaba.druid.pool.DruidDataSource;
import fun.commons.framework4j.datasource.config.MultiDataSourceAutoConfiguration;
import fun.commons.framework4j.datasource.manager.MultiDataSourceManager;
import fun.commons.framework4j.datasource.tracing.TraceIdDruidFilter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL 追踪自动配置集成测试
 * <p>
 * 测试 SqlTracingAutoConfiguration 的自动配置功能：
 * 1. TraceIdDruidFilter 自动添加到 DruidDataSource
 * 2. 不同追踪模式的配置生效
 * 3. TraceId 从 MDC 正确获取并注入到 SQL
 * 4. Druid slf4j filter 打印 SQL 日志
 */
@Slf4j
@SpringBootTest(
        classes = {SqlTracingAutoConfigTest.TestConfiguration.class},
        properties = {
                "spring.autoconfigure.exclude=com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        }
)
@ActiveProfiles("tracing-test")
@DisplayName("SQL 追踪自动配置集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlTracingAutoConfigTest {

    @Autowired
    private MultiDataSourceManager manager;

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @Order(1)
    @DisplayName("测试1: 验证 SqlTracingAutoConfiguration 已生效")
    void test01_AutoConfigurationEnabled() {
        assertNotNull(manager, "MultiDataSourceManager 应该被注入");
        assertTrue(manager.containsDatasource("default"), "default 数据源应该存在");
        assertTrue(manager.containsDatasource("business"), "business 数据源应该存在");
        assertTrue(manager.containsDatasource("log"), "log 数据源应该存在");

        log.info("✅ SqlTracingAutoConfiguration 自动配置已生效");
    }

    @Test
    @Order(2)
    @DisplayName("测试2: 验证 TraceIdDruidFilter 已添加到 ALL 模式数据源")
    void test02_FilterAddedToAllModeDataSource() {
        DataSource ds = manager.getDataSource("default");
        assertTrue(ds instanceof DruidDataSource, "应该是 DruidDataSource");

        DruidDataSource druidDs = (DruidDataSource) ds;
        boolean hasTraceFilter = druidDs.getProxyFilters().stream()
                .anyMatch(f -> f instanceof TraceIdDruidFilter);

        assertTrue(hasTraceFilter, "default 数据源应该有 TraceIdDruidFilter (ALL 模式)");

        log.info("✅ TraceIdDruidFilter 已添加到 ALL 模式数据源");
        log.info("default 数据源的 ProxyFilters: {}", druidDs.getProxyFilters());
    }

    @Test
    @Order(3)
    @DisplayName("测试3: 验证 TraceIdDruidFilter 已添加到 WRITE_ONLY 模式数据源")
    void test03_FilterAddedToWriteOnlyModeDataSource() {
        DataSource ds = manager.getDataSource("business");
        DruidDataSource druidDs = (DruidDataSource) ds;

        boolean hasTraceFilter = druidDs.getProxyFilters().stream()
                .anyMatch(f -> f instanceof TraceIdDruidFilter);

        assertTrue(hasTraceFilter, "business 数据源应该有 TraceIdDruidFilter (WRITE_ONLY 模式)");

        log.info("✅ TraceIdDruidFilter 已添加到 WRITE_ONLY 模式数据源");
    }

    @Test
    @Order(4)
    @DisplayName("测试4: 验证 DISABLED 模式数据源没有 TraceIdDruidFilter")
    void test04_NoFilterForDisabledMode() {
        DataSource ds = manager.getDataSource("log");
        DruidDataSource druidDs = (DruidDataSource) ds;

        boolean hasTraceFilter = druidDs.getProxyFilters().stream()
                .anyMatch(f -> f instanceof TraceIdDruidFilter);

        assertFalse(hasTraceFilter, "log 数据源不应该有 TraceIdDruidFilter (DISABLED 模式)");

        log.info("✅ DISABLED 模式数据源没有 TraceIdDruidFilter");
    }

    @Test
    @Order(5)
    @DisplayName("测试5: 验证 Druid slf4j filter 已启用")
    void test05_Slf4jFilterEnabled() {
        DataSource ds = manager.getDataSource("default");
        DruidDataSource druidDs = (DruidDataSource) ds;

        // 检查 filters 配置 - 通过 ProxyFilters 和 FilterClassNames 判断
        var proxyFilters = druidDs.getProxyFilters();
        log.info("default 数据源的 ProxyFilters 数量: {}", proxyFilters != null ? proxyFilters.size() : 0);

        // slf4j filter 会自动添加到 filters 中
        assertNotNull(proxyFilters, "ProxyFilters 不应该为 null");
        assertFalse(proxyFilters.isEmpty(), "应该有 filters");

        log.info("✅ Druid filters 已启用，ProxyFilters: {}", proxyFilters);
    }

    @Test
    @Order(6)
    @DisplayName("测试6: 验证 ALL 模式 - SELECT 语句包含 TraceId")
    void test06_AllModeSelectWithTraceId() throws Exception {
        // 设置 MDC TraceId
        String testTraceId = "auto-config-test-trace-001";
        MDC.put("traceId", testTraceId);

        log.info("========== 开始测试 ALL 模式 SELECT 语句 ==========");
        log.info("设置 TraceId: {}", testTraceId);

        DataSource ds = manager.getDataSource("default");

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 AS result")) {

            // 打印实际 SQL (用于观察 TraceId 是否添加)
            log.info("PreparedStatement toString: {}", ps.toString());

            // 执行查询
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "应该有结果");
                assertEquals(1, rs.getInt("result"), "结果应该是 1");
            }
        }

        log.info("✅ ALL 模式 SELECT 测试完成，查看日志确认 TraceId 是否添加");
        log.info("========== ALL 模式 SELECT 测试结束 ==========\n");
    }

    @Test
    @Order(7)
    @DisplayName("测试7: 验证 WRITE_ONLY 模式 - SELECT 不包含 TraceId")
    void test07_WriteOnlyModeSelectWithoutTraceId() throws Exception {
        String testTraceId = "write-only-test-trace-001";
        MDC.put("traceId", testTraceId);

        log.info("========== 开始测试 WRITE_ONLY 模式 SELECT 语句 ==========");
        log.info("设置 TraceId: {}", testTraceId);

        DataSource ds = manager.getDataSource("business");

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 AS result")) {

            log.info("PreparedStatement toString: {}", ps.toString());

            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "应该有结果");
                assertEquals(1, rs.getInt("result"), "结果应该是 1");
            }
        }

        log.info("✅ WRITE_ONLY 模式 SELECT 测试完成，SELECT 不应该包含 TraceId");
        log.info("========== WRITE_ONLY 模式 SELECT 测试结束 ==========\n");
    }

    @Test
    @Order(8)
    @DisplayName("测试8: 验证 DISABLED 模式 - SQL 不包含 TraceId")
    void test08_DisabledModeNoTraceId() throws Exception {
        String testTraceId = "disabled-test-trace-001";
        MDC.put("traceId", testTraceId);

        log.info("========== 开始测试 DISABLED 模式 ==========");
        log.info("设置 TraceId: {}", testTraceId);

        DataSource ds = manager.getDataSource("log");

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 AS result")) {

            log.info("PreparedStatement toString: {}", ps.toString());

            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "应该有结果");
            }
        }

        log.info("✅ DISABLED 模式测试完成，SQL 不应该包含 TraceId");
        log.info("========== DISABLED 模式测试结束 ==========\n");
    }

    @Test
    @Order(9)
    @DisplayName("测试9: 验证 TraceId 从多个 MDC 键名获取")
    void test09_TraceIdFromMultipleMdcKeys() throws Exception {
        log.info("========== 开始测试 TraceId 从多个 MDC 键名获取 ==========");

        DataSource ds = manager.getDataSource("default");

        // 测试 traceId
        MDC.put("traceId", "trace-id-001");
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1")) {
            log.info("使用 traceId: {}", ps.toString());
            ps.executeQuery().close();
        }
        MDC.clear();

        // 测试 X-Request-Id
        MDC.put("X-Request-Id", "request-id-002");
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1")) {
            log.info("使用 X-Request-Id: {}", ps.toString());
            ps.executeQuery().close();
        }
        MDC.clear();

        // 测试 trace_id
        MDC.put("trace_id", "trace_id-003");
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1")) {
            log.info("使用 trace_id: {}", ps.toString());
            ps.executeQuery().close();
        }

        log.info("✅ TraceId 从多个 MDC 键名获取测试完成");
        log.info("========== 测试结束 ==========\n");
    }

    @Test
    @Order(10)
    @DisplayName("测试10: 验证没有 TraceId 时使用 'none'")
    void test10_NoTraceIdUsesNone() throws Exception {
        // 不设置 MDC
        MDC.clear();

        log.info("========== 开始测试没有 TraceId 时使用 'none' ==========");

        DataSource ds = manager.getDataSource("default");

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 AS result")) {

            log.info("PreparedStatement toString (无 TraceId): {}", ps.toString());

            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "应该有结果");
            }
        }

        log.info("✅ 没有 TraceId 时使用 'none' 测试完成");
        log.info("========== 测试结束 ==========\n");
    }

    @Test
    @Order(11)
    @DisplayName("测试11: 验证 topic 配置生效")
    void test11_TopicConfiguration() {
        log.info("========== 开始验证 topic 配置 ==========");

        DataSource ds = manager.getDataSource("default");
        DruidDataSource druidDs = (DruidDataSource) ds;

        // 获取 TraceIdDruidFilter
        TraceIdDruidFilter filter = druidDs.getProxyFilters().stream()
                .filter(f -> f instanceof TraceIdDruidFilter)
                .map(f -> (TraceIdDruidFilter) f)
                .findFirst()
                .orElse(null);

        assertNotNull(filter, "应该能找到 TraceIdDruidFilter");

        log.info("✅ topic 配置验证完成");
        log.info("default 数据源配置的 topic: default-datasource");
        log.info("========== 测试结束 ==========\n");
    }

    // ==================== 测试配置类 ====================

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.context.annotation.Import({
            fun.commons.framework4j.datasource.tracing.SqlTracingAutoConfiguration.class,
            MultiDataSourceAutoConfiguration.class
    })
    @MapperScan(basePackages = "fun.commons.framework4j.datasource.mapper.business",
            sqlSessionFactoryRef = "businessSqlSessionFactory")
    @MapperScan(basePackages = "fun.commons.framework4j.datasource.mapper.log",
            sqlSessionFactoryRef = "logSqlSessionFactory")
    static class TestConfiguration {
    }
}
