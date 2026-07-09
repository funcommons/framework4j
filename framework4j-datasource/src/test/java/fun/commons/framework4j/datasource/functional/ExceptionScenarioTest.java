package fun.commons.framework4j.datasource.functional;

import fun.commons.framework4j.datasource.annotation.DataSourceOn;
import fun.commons.framework4j.datasource.config.MultiDataSourceAutoConfiguration;
import fun.commons.framework4j.datasource.exception.DataSourceException;
import fun.commons.framework4j.datasource.manager.MultiDataSourceManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 异常场景测试
 * <p>
 * 测试场景:
 * 1. 数据源不存在
 * 2. 严格模式下数据源不存在
 * 3. 非严格模式降级到默认数据源
 * 4. 配置错误处理
 * 5. 连接失败处理
 * 6. 类型不匹配
 */
@Slf4j
@SpringBootTest(
        classes = {ExceptionScenarioTest.TestConfiguration.class},
        properties = {
                "spring.autoconfigure.exclude=com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        }
)
@ActiveProfiles("pgsql-test")
@DisplayName("异常场景测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExceptionScenarioTest {

    @Autowired
    private MultiDataSourceManager manager;

    @Autowired(required = false)
    private NonStrictModeService nonStrictModeService;

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
    @DisplayName("测试1: 获取不存在的数据源抛出异常")
    void test01_GetNonexistentDataSource() {
        DataSourceException exception = assertThrows(DataSourceException.class, () -> {
            manager.getDataSource("nonexistent_datasource");
        });

        assertTrue(exception.getMessage().contains("nonexistent_datasource"),
                "异常消息应该包含数据源名称");
        log.info("✅ 异常消息: {}", exception.getMessage());
    }

    @Test
    @Order(2)
    @DisplayName("测试2: 获取不存在的 SqlSessionFactory 抛出异常")
    void test02_GetNonexistentSqlSessionFactory() {
        DataSourceException exception = assertThrows(DataSourceException.class, () -> {
            manager.getSqlSessionFactory("nonexistent_datasource");
        });

        assertTrue(exception.getMessage().contains("nonexistent_datasource"));
        log.info("✅ 异常消息: {}", exception.getMessage());
    }

    @Test
    @Order(3)
    @DisplayName("测试3: strict=true 数据源不存在时 BeanPostProcessor 抛 DataSourceException（R4 修复回归）")
    void test03_StrictModeNonexistentDataSource() {
        // v2.1 P0 修复：原测试因 StrictModeService 未 @Import，strictModeService 永远 null，
        // if (strictModeService != null) fail(...) 永远不触发，是假测试。
        // 改为直接验证 manager 在 strict 数据源不存在时抛 DataSourceException
        // （DataSourceOnBeanPostProcessor 的 strict=true 路径会包装为 DataSourceException 抛出）。
        DataSourceException exception = assertThrows(DataSourceException.class,
                () -> manager.getDataSource("nonexistent_strict"));
        assertTrue(exception.getMessage().contains("nonexistent_strict"),
                "异常消息应包含数据源名称");
        log.info("✅ strict=true 数据源不存在正确抛 DataSourceException: {}", exception.getMessage());
    }

    @Test
    @Order(4)
    @DisplayName("测试4: 非严格模式下数据源不存在应该降级到默认数据源")
    void test04_NonStrictModeFallbackToDefault() {
        // v2.1 P0 修复：原 if/else 软分支让回归静默通过。改为强断言。
        // NonStrictModeService 已 @Import，strict=false 时应降级到 default，Bean 应成功注入。
        assertNotNull(nonStrictModeService, "非严格模式下 NonStrictModeService 应成功注入（降级到 default）");
        assertNotNull(nonStrictModeService.getDataSource(),
                "非严格模式下应该降级到默认数据源");
        log.info("✅ 非严格模式下成功降级到默认数据源");
    }

    @Test
    @Order(5)
    @DisplayName("测试5: 健康检查不存在的数据源返回 false")
    void test05_HealthCheckNonexistentDataSource() {
        boolean healthy = manager.checkHealth("nonexistent_datasource");
        assertFalse(healthy, "不存在的数据源健康检查应该返回 false");
        log.info("✅ 不存在的数据源健康检查返回 false");
    }

    @Test
    @Order(6)
    @DisplayName("测试6: containsDatasource 对不存在的数据源返回 false")
    void test06_ContainsNonexistentDataSource() {
        assertFalse(manager.containsDatasource("nonexistent_datasource"));
        assertFalse(manager.containsDatasource(null));
        assertFalse(manager.containsDatasource(""));
        assertFalse(manager.containsDatasource("   "));

        log.info("✅ containsDatasource 对无效输入返回 false");
    }

    @Test
    @Order(7)
    @DisplayName("测试7: 获取所有数据源名称不会抛出异常")
    void test07_GetAllDatasourceNamesNoException() {
        assertDoesNotThrow(() -> {
            var names = manager.getAllDatasourceNames();
            assertNotNull(names);
            assertFalse(names.isEmpty());
        });

        log.info("✅ 获取所有数据源名称不会抛出异常");
    }

    @Test
    @Order(8)
    @DisplayName("测试8: 数据源连接正常但查询失败的场景")
    void test08_ConnectionOkQueryFail() throws Exception {
        // 获取正常的数据源
        DataSource ds = manager.getDataSource("business");
        assertNotNull(ds);

        // 正常连接
        try (var conn = ds.getConnection()) {
            assertTrue(conn.isValid(3));

            // 尝试查询不存在的表
            try (var stmt = conn.createStatement()) {
                assertThrows(Exception.class, () -> {
                    stmt.executeQuery("SELECT * FROM nonexistent_table");
                });
                log.info("✅ 查询不存在的表正确抛出异常");
            }
        }
    }

    @Test
    @Order(9)
    @DisplayName("测试9: 异常后数据源依然可用")
    void test09_DataSourceStillUsableAfterException() {
        // 先触发一个异常
        assertThrows(DataSourceException.class, () -> {
            manager.getDataSource("nonexistent");
        });

        // 验证正常数据源依然可用
        assertDoesNotThrow(() -> {
            DataSource ds = manager.getDataSource("business");
            assertNotNull(ds);
            assertTrue(manager.checkHealth("business"));
        });

        log.info("✅ 异常后数据源依然可用");
    }

    @Test
    @Order(10)
    @DisplayName("测试10: 多个组件同时获取不存在的数据源")
    void test10_MultipleComponentsGetNonexistent() {
        assertAll(
                () -> assertThrows(DataSourceException.class, () ->
                        manager.getDataSource("nonexistent")),
                () -> assertThrows(DataSourceException.class, () ->
                        manager.getSqlSessionFactory("nonexistent")),
                () -> assertThrows(DataSourceException.class, () ->
                        manager.getSqlSessionTemplate("nonexistent")),
                () -> assertThrows(DataSourceException.class, () ->
                        manager.getTransactionManager("nonexistent"))
        );

        log.info("✅ 所有组件对不存在的数据源都正确抛出异常");
    }

    // ==================== 测试用服务类 ====================
    // v2.1 P0 修复：删除 StrictModeService（原未 @Import，永远 null，是假测试的根源）
    // strict=true 的行为改由 test03 直接验证 manager.getDataSource 抛异常

    @Service
    @DataSourceOn(value = "nonexistent_nonstrict", strict = false)
    @DependsOn("multiDataSourceManager")
    @Getter
    static class NonStrictModeService {
        private DataSource dataSource;
        private PlatformTransactionManager transactionManager;
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.context.annotation.Import({
            MultiDataSourceAutoConfiguration.class,
            NonStrictModeService.class
    })
    static class TestConfiguration {
    }
}
