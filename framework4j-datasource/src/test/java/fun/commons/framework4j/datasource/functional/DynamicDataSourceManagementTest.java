package fun.commons.framework4j.datasource.functional;

import fun.commons.framework4j.datasource.health.HealthCheckResult;
import fun.commons.framework4j.datasource.config.MultiDataSourceAutoConfiguration;
import fun.commons.framework4j.datasource.manager.MultiDataSourceManager;
import fun.commons.framework4j.datasource.properties.DataSourceProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 动态数据源管理功能测试
 * <p>
 * 测试场景:
 * 1. 动态添加数据源
 * 2. 动态移除数据源
 * 3. 数据源健康检查
 * 4. 批量健康检查
 * 5. 数据源存在性验证
 * 6. 数据源列表获取
 * 7. 异常情况处理
 */
@Slf4j
@SpringBootTest(
        classes = {DynamicDataSourceManagementTest.TestConfiguration.class},
        properties = {
                "spring.autoconfigure.exclude=com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        }
)
@ActiveProfiles("test")
@DisplayName("动态数据源管理功能测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DynamicDataSourceManagementTest {

    @Autowired
    private MultiDataSourceManager manager;

    private static final String DYNAMIC_DS_1 = "dynamic-ds-1";
    private static final String DYNAMIC_DS_2 = "dynamic-ds-2";
    private static final String DYNAMIC_DS_3 = "dynamic-ds-3";

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
    @DisplayName("测试1: 动态添加数据源")
    void test01_AddDynamicDataSource() {
        // 创建第一个动态数据源配置
        DataSourceProperties config1 = createTestDataSourceConfig(
                "jdbc:h2:mem:dynamic-test1;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
                "sa",
                ""
        );

        // 动态添加数据源
        manager.addDataSource(DYNAMIC_DS_1, config1);

        // 验证数据源存在
        assertTrue(manager.containsDatasource(DYNAMIC_DS_1), "动态数据源1应该存在");

        // 验证可以获取数据源
        DataSource ds1 = manager.getDataSource(DYNAMIC_DS_1);
        assertNotNull(ds1, "应该能获取动态数据源1");

        // 验证数据源连接可用
        assertTrue(manager.checkHealth(DYNAMIC_DS_1), "动态数据源1应该是健康的");

        log.info("✅ 动态添加数据源测试通过");
    }

    @Test
    @Order(2)
    @DisplayName("测试2: 添加多个动态数据源")
    void test02_AddMultipleDynamicDataSources() {
        // 添加第二个动态数据源
        DataSourceProperties config2 = createTestDataSourceConfig(
                "jdbc:h2:mem:dynamic-test2;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
                "sa",
                ""
        );

        manager.addDataSource(DYNAMIC_DS_2, config2);

        // 添加第三个动态数据源
        DataSourceProperties config3 = createTestDataSourceConfig(
                "jdbc:h2:mem:dynamic-test3;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
                "sa",
                ""
        );

        manager.addDataSource(DYNAMIC_DS_3, config3);

        // 验证所有动态数据源都存在
        assertTrue(manager.containsDatasource(DYNAMIC_DS_1), "动态数据源1应该存在");
        assertTrue(manager.containsDatasource(DYNAMIC_DS_2), "动态数据源2应该存在");
        assertTrue(manager.containsDatasource(DYNAMIC_DS_3), "动态数据源3应该存在");

        // 验证所有数据源都是健康的
        assertTrue(manager.checkHealth(DYNAMIC_DS_1), "动态数据源1应该是健康的");
        assertTrue(manager.checkHealth(DYNAMIC_DS_2), "动态数据源2应该是健康的");
        assertTrue(manager.checkHealth(DYNAMIC_DS_3), "动态数据源3应该是健康的");

        log.info("✅ 添加多个动态数据源测试通过");
    }

    @Test
    @Order(3)
    @DisplayName("测试3: 动态移除数据源")
    void test03_RemoveDynamicDataSource() {
        // 移除动态数据源2
        manager.removeDataSource(DYNAMIC_DS_2);

        // 验证数据源不存在
        assertFalse(manager.containsDatasource(DYNAMIC_DS_2), "动态数据源2应该不存在");

        // 验证获取数据源抛出异常
        assertThrows(Exception.class, () -> {
            manager.getDataSource(DYNAMIC_DS_2);
        }, "获取已移除的数据源应该抛出异常");

        // 验证其他数据源仍然存在
        assertTrue(manager.containsDatasource(DYNAMIC_DS_1), "动态数据源1应该仍然存在");
        assertTrue(manager.containsDatasource(DYNAMIC_DS_3), "动态数据源3应该仍然存在");

        log.info("✅ 动态移除数据源测试通过");
    }

    @Test
    @Order(4)
    @DisplayName("测试4: 详细健康检查")
    void test04_DetailedHealthCheck() {
        // 对动态数据源1进行详细健康检查
        HealthCheckResult result1 = manager.checkHealthDetailed(DYNAMIC_DS_1);

        assertNotNull(result1, "健康检查结果不应该为null");
        assertEquals(DYNAMIC_DS_1, result1.getDatasourceName(), "数据源名称应该正确");
        assertTrue(result1.isHealthy(), "动态数据源1应该是健康的");
        assertTrue(result1.getResponseTime() >= 0, "响应时间应该大于等于0");
        assertNotNull(result1.getLastCheckTime(), "检查时间不应该为null");
        assertNotNull(result1.getDatasourceType(), "数据源类型不应该为null");

        log.info("数据源 {} 详细健康检查结果: 健康={}, 响应时间={}ms, 类型={}",
                result1.getDatasourceName(), result1.isHealthy(), result1.getResponseTime(), result1.getDatasourceType());

        // 对不存在的数据源进行详细健康检查
        HealthCheckResult resultNonExistent = manager.checkHealthDetailed("non-existent-ds");
        assertNotNull(resultNonExistent, "不存在的数据源健康检查结果也不应该为null");
        assertEquals("non-existent-ds", resultNonExistent.getDatasourceName());
        assertFalse(resultNonExistent.isHealthy(), "不存在的数据源应该是不健康的");
        assertNotNull(resultNonExistent.getErrorMessage(), "应该有错误信息");

        log.info("✅ 详细健康检查测试通过");
    }

    @Test
    @Order(5)
    @DisplayName("测试5: 批量详细健康检查")
    void test05_BatchDetailedHealthCheck() {
        // 批量检查所有数据源的健康状态
        Map<String, HealthCheckResult> results = manager.checkHealthBatchDetailed(null);

        assertNotNull(results, "批量健康检查结果不应该为null");
        assertTrue(results.size() >= 2, "至少应该有2个数据源的检查结果"); // DYNAMIC_DS_1, DYNAMIC_DS_3

        // 验证动态数据源1的健康检查结果
        HealthCheckResult result1 = results.get(DYNAMIC_DS_1);
        assertNotNull(result1, "动态数据源1的检查结果不应该为null");
        assertTrue(result1.isHealthy(), "动态数据源1应该是健康的");
        assertEquals(DYNAMIC_DS_1, result1.getDatasourceName());

        // 验证动态数据源3的健康检查结果
        HealthCheckResult result3 = results.get(DYNAMIC_DS_3);
        assertNotNull(result3, "动态数据源3的检查结果不应该为null");
        assertTrue(result3.isHealthy(), "动态数据源3应该是健康的");
        assertEquals(DYNAMIC_DS_3, result3.getDatasourceName());

        // 统计健康数据源数量
        long healthyCount = results.values().stream()
                .mapToLong(r -> r.isHealthy() ? 1 : 0)
                .sum();

        log.info("批量健康检查完成，总共 {} 个数据源，健康 {} 个", results.size(), healthyCount);
        assertTrue(healthyCount >= 2, "至少应该有2个健康的数据源");

        log.info("✅ 批量详细健康检查测试通过");
    }

    @Test
    @Order(6)
    @DisplayName("测试6: 获取所有数据源名称")
    void test06_GetAllDataSourceNames() {
        // 获取所有数据源名称
        List<String> allNames = manager.getAllDatasourceNames();

        assertNotNull(allNames, "数据源名称列表不应该为null");
        assertTrue(allNames.size() >= 5, "至少应该有5个数据源"); // 3个配置的 + 2个动态的

        // 验证包含预期的数据源名称
        assertTrue(allNames.contains("business"), "应该包含business数据源");
        assertTrue(allNames.contains("log"), "应该包含log数据源");
        assertTrue(allNames.contains("report"), "应该包含report数据源");
        assertTrue(allNames.contains(DYNAMIC_DS_1), "应该包含动态数据源1");
        assertTrue(allNames.contains(DYNAMIC_DS_3), "应该包含动态数据源3");

        // 不应该包含已移除的数据源
        assertFalse(allNames.contains(DYNAMIC_DS_2), "不应该包含已移除的动态数据源2");

        log.info("所有数据源名称: {}", allNames);
        log.info("✅ 获取所有数据源名称测试通过");
    }

    @Test
    @Order(7)
    @DisplayName("测试7: 异常情况处理")
    void test07_ExceptionHandling() {
        // 测试添加null名称的数据源
        manager.addDataSource(null, createTestDataSourceConfig(
                "jdbc:h2:mem:test-null;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
                "sa",
                ""
        ));
        // 应该没有异常抛出，只是忽略

        // 测试添加null配置的数据源
        manager.addDataSource("test-null-config", null);
        // 应该没有异常抛出，只是忽略

        // 测试移除不存在的数据源
        manager.removeDataSource("non-existent-ds");
        // 应该没有异常抛出

        // 测试获取不存在的数据源
        assertThrows(Exception.class, () -> {
            manager.getDataSource("non-existent-ds-after-remove");
        }, "获取不存在的数据源应该抛出异常");

        log.info("✅ 异常情况处理测试通过");
    }

    @Test
    @Order(8)
    @DisplayName("测试8: 重复添加和移除数据源")
    void test08_RepeatedAddAndRemove() {
        // 重复添加相同名称的数据源（应该覆盖）
        DataSourceProperties newConfig = createTestDataSourceConfig(
                "jdbc:h2:mem:dynamic-test1-new;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
                "sa",
                ""
        );

        manager.addDataSource(DYNAMIC_DS_1, newConfig);

        // 验证数据源仍然存在且健康
        assertTrue(manager.containsDatasource(DYNAMIC_DS_1), "重复添加的数据源应该存在");
        assertTrue(manager.checkHealth(DYNAMIC_DS_1), "重复添加的数据源应该是健康的");

        // 重复移除相同的数据源
        manager.removeDataSource(DYNAMIC_DS_1);
        manager.removeDataSource(DYNAMIC_DS_1); // 第二次移除

        // 验证数据源不存在
        assertFalse(manager.containsDatasource(DYNAMIC_DS_1), "重复移除的数据源应该不存在");

        log.info("✅ 重复添加和移除数据源测试通过");
    }

    @Test
    @Order(9)
    @DisplayName("测试9: 清理所有动态数据源")
    void test09_CleanupAllDynamicDataSources() {
        // 移除剩余的动态数据源
        manager.removeDataSource(DYNAMIC_DS_3);

        // 验证所有动态数据源都被移除
        assertFalse(manager.containsDatasource(DYNAMIC_DS_1), "动态数据源1应该被清理");
        assertFalse(manager.containsDatasource(DYNAMIC_DS_2), "动态数据源2应该被清理");
        assertFalse(manager.containsDatasource(DYNAMIC_DS_3), "动态数据源3应该被清理");

        // 验证原始配置的数据源仍然存在
        assertTrue(manager.containsDatasource("business"), "business数据源应该仍然存在");
        assertTrue(manager.containsDatasource("log"), "log数据源应该仍然存在");
        assertTrue(manager.containsDatasource("report"), "report数据源应该仍然存在");

        log.info("✅ 清理所有动态数据源测试通过");
    }

    /**
     * 创建测试用的数据源配置
     */
    private DataSourceProperties createTestDataSourceConfig(String url, String username, String password) {
        DataSourceProperties config = new DataSourceProperties();
        config.setUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.h2.Driver");

        // 配置Druid连接池（较小的值用于测试）
        DataSourceProperties.DruidConfig druidConfig = new DataSourceProperties.DruidConfig();
        druidConfig.setInitialSize(1);
        druidConfig.setMinIdle(1);
        druidConfig.setMaxActive(3);
        druidConfig.setMaxWait(3000);
        druidConfig.setTestWhileIdle(true);
        druidConfig.setTestOnBorrow(false);
        druidConfig.setTestOnReturn(false);
        druidConfig.setValidationQuery("SELECT 1");
        druidConfig.setTimeBetweenEvictionRunsMillis(60000);
        druidConfig.setMinEvictableIdleTimeMillis(300000);
        druidConfig.setFilters("stat"); // 只使用统计过滤器，避免wall过滤器的SQL语法问题

        config.setDruid(druidConfig);

        return config;
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.context.annotation.Import({
            MultiDataSourceAutoConfiguration.class
    })
    static class TestConfiguration {
    }
}