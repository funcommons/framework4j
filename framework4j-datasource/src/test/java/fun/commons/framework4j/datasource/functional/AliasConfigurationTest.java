package fun.commons.framework4j.datasource.functional;

import fun.commons.framework4j.datasource.annotation.DataSourceOn;
import fun.commons.framework4j.datasource.config.MultiDataSourceAutoConfiguration;
import fun.commons.framework4j.datasource.manager.MultiDataSourceManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 别名配置功能测试
 * <p>
 * 测试场景:
 * 1. 别名指向正确的数据源
 * 2. 多个别名指向同一数据源
 * 3. @DataSourceOn 使用别名注入
 * 4. @Qualifier 使用别名注入
 * 5. 别名的所有组件(DataSource, SqlSessionFactory, SqlSessionTemplate, TransactionManager)
 */
@Slf4j
@SpringBootTest(
        classes = {AliasConfigurationTest.TestConfiguration.class},
        properties = {
                "spring.autoconfigure.exclude=com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        }
)
@ActiveProfiles("pgsql-test")
@DisplayName("别名配置功能测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AliasConfigurationTest {

    @Autowired
    private MultiDataSourceManager manager;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private QualifierService qualifierService;

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
    @DisplayName("测试1: 验证别名存在")
    void test01_VerifyAliasExists() {
        assertTrue(manager.containsDatasource("business"), "business 主数据源应该存在");
        assertTrue(manager.containsDatasource("order"), "order 别名应该存在");
        assertTrue(manager.containsDatasource("product"), "product 别名应该存在");
        log.info("✅ 所有别名验证通过");
    }

    @Test
    @Order(2)
    @DisplayName("测试2: 别名指向同一数据源实例")
    void test02_AliasPointsToSameInstance() {
        DataSource businessDs = manager.getDataSource("business");
        DataSource orderDs = manager.getDataSource("order");
        DataSource productDs = manager.getDataSource("product");

        assertSame(businessDs, orderDs, "order 别名应该指向 business 数据源");
        assertSame(businessDs, productDs, "product 别名应该指向 business 数据源");
        assertSame(orderDs, productDs, "order 和 product 应该指向同一数据源");

        log.info("✅ 别名指向同一数据源实例验证通过");
    }

    @Test
    @Order(3)
    @DisplayName("测试3: 别名的所有组件指向同一实例")
    void test03_AliasComponentsPointToSameInstance() {
        // DataSource
        DataSource businessDs = manager.getDataSource("business");
        DataSource orderDs = manager.getDataSource("order");
        assertSame(businessDs, orderDs);

        // SqlSessionFactory
        SqlSessionFactory businessFactory = manager.getSqlSessionFactory("business");
        SqlSessionFactory orderFactory = manager.getSqlSessionFactory("order");
        assertSame(businessFactory, orderFactory);

        // SqlSessionTemplate
        SqlSessionTemplate businessTemplate = manager.getSqlSessionTemplate("business");
        SqlSessionTemplate orderTemplate = manager.getSqlSessionTemplate("order");
        assertSame(businessTemplate, orderTemplate);

        // TransactionManager
        PlatformTransactionManager businessTm = manager.getTransactionManager("business");
        PlatformTransactionManager orderTm = manager.getTransactionManager("order");
        assertSame(businessTm, orderTm);

        log.info("✅ 别名的所有组件指向同一实例验证通过");
    }

    @Test
    @Order(4)
    @DisplayName("测试4: @DataSourceOn 使用别名注入")
    void test04_DataSourceOnWithAlias() {
        assertNotNull(orderService, "OrderService 应该被成功创建");
        assertNotNull(orderService.getDataSource(), "DataSource 应该通过别名 'order' 注入");
        assertNotNull(orderService.getSqlSessionTemplate(), "SqlSessionTemplate 应该通过别名 'order' 注入");

        assertNotNull(productService, "ProductService 应该被成功创建");
        assertNotNull(productService.getDataSource(), "DataSource 应该通过别名 'product' 注入");
        assertNotNull(productService.getSqlSessionTemplate(), "SqlSessionTemplate 应该通过别名 'product' 注入");

        // 验证注入的是同一实例
        assertSame(orderService.getDataSource(), productService.getDataSource(),
                "通过不同别名注入的应该是同一数据源实例");

        log.info("✅ @DataSourceOn 使用别名注入验证通过");
    }

    @Test
    @Order(5)
    @DisplayName("测试5: @Qualifier 使用别名注入")
    void test05_QualifierWithAlias() {
        assertNotNull(qualifierService, "QualifierService 应该被成功创建");
        assertNotNull(qualifierService.getOrderDataSource(), "@Qualifier('order') 应该注入成功");
        assertNotNull(qualifierService.getProductDataSource(), "@Qualifier('product') 应该注入成功");
        assertNotNull(qualifierService.getOrderTemplate(), "@Qualifier('orderSqlSessionTemplate') 应该注入成功");

        // 验证指向同一实例
        assertSame(qualifierService.getOrderDataSource(), qualifierService.getProductDataSource(),
                "通过 @Qualifier 使用不同别名注入的应该是同一数据源");

        log.info("✅ @Qualifier 使用别名注入验证通过");
    }

    @Test
    @Order(6)
    @DisplayName("测试6: 别名与主名称混合使用")
    void test06_MixedAliasAndPrimaryName() {
        DataSource businessByName = manager.getDataSource("business");
        DataSource businessByAlias1 = manager.getDataSource("order");
        DataSource businessByAlias2 = manager.getDataSource("product");

        assertSame(businessByName, businessByAlias1);
        assertSame(businessByName, businessByAlias2);

        log.info("✅ 别名与主名称混合使用验证通过");
    }

    @Test
    @Order(7)
    @DisplayName("测试7: 验证别名不会创建重复的连接池")
    void test07_NoD重复ConnectionPools() throws Exception {
        // 通过不同名称获取 DataSource,验证底层连接池配置一致
        DataSource businessDs = manager.getDataSource("business");
        DataSource orderDs = manager.getDataSource("order");

        // 验证是同一个对象引用
        assertSame(businessDs, orderDs, "应该返回同一个 DataSource 对象");

        // 验证连接URL相同(testdb2)
        try (var conn1 = businessDs.getConnection();
             var conn2 = orderDs.getConnection()) {
            String url1 = conn1.getMetaData().getURL();
            String url2 = conn2.getMetaData().getURL();
            assertEquals(url1, url2, "连接URL应该相同");
            assertTrue(url1.contains("testdb2"), "应该连接到 testdb2 数据库");
        }

        log.info("✅ 验证别名不会创建重复的连接池");
    }

    @Test
    @Order(8)
    @DisplayName("测试8: 通过别名进行健康检查")
    void test08_HealthCheckThroughAlias() {
        assertTrue(manager.checkHealth("business"), "business 数据源应该健康");
        assertTrue(manager.checkHealth("order"), "通过 order 别名检查应该健康");
        assertTrue(manager.checkHealth("product"), "通过 product 别名检查应该健康");

        log.info("✅ 通过别名进行健康检查验证通过");
    }

    // ==================== 测试用服务类 ====================

    @Service
    @DataSourceOn("order")  // 使用别名
    @DependsOn("multiDataSourceManager")
    @Getter
    static class OrderService {
        private DataSource dataSource;
        private SqlSessionTemplate sqlSessionTemplate;
        private PlatformTransactionManager transactionManager;
    }

    @Service
    @DataSourceOn("product")  // 使用别名
    @DependsOn("multiDataSourceManager")
    @Getter
    static class ProductService {
        private DataSource dataSource;
        private SqlSessionTemplate sqlSessionTemplate;
        private PlatformTransactionManager transactionManager;
    }

    @Service
    @DependsOn("multiDataSourceManager")
    @Getter
    static class QualifierService {
        @Autowired
        @Qualifier("order")  // 使用短别名
        private DataSource orderDataSource;

        @Autowired
        @Qualifier("product")  // 使用短别名
        private DataSource productDataSource;

        @Autowired
        @Qualifier("orderSqlSessionTemplate")  // 使用完整别名
        private SqlSessionTemplate orderTemplate;
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.context.annotation.Import({
            MultiDataSourceAutoConfiguration.class
    })
    @org.springframework.context.annotation.ComponentScan(basePackages = "fun.commons.framework4j.datasource.functional")
    static class TestConfiguration {
    }
}
