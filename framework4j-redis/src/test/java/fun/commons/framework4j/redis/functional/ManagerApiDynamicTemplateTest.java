package fun.commons.framework4j.redis.functional;

import fun.commons.framework4j.redis.config.MultiRedisAutoConfiguration;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Manager API 动态模板创建测试
 * <p>
 * 测试 MultiRedisManager.getOrCreateObjectTemplate() 方法
 * 即使数据源配置为 STRING 类型，也能动态创建 OBJECT 类型模板
 *
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = ManagerApiDynamicTemplateTest.TestConfiguration.class)
@ActiveProfiles("test")
@DisplayName("Manager API 动态模板创建测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ManagerApiDynamicTemplateTest {

    @Autowired
    private MultiRedisManager multiRedisManager;

    @BeforeAll
    void setup() {
        log.info("========== 初始化 Manager API 测试环境 ==========");
        try {
            multiRedisManager.getRedisTemplate("default").getConnectionFactory().getConnection().flushDb();
            multiRedisManager.getRedisTemplate("cache").getConnectionFactory().getConnection().flushDb();
            log.info("✅ 测试数据库已清空");
        } catch (Exception e) {
            log.warn("清空数据库失败: {}", e.getMessage());
        }
        log.info("========== 测试环境初始化完成 ==========\n");
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: 验证 getOrCreateObjectTemplate() 从 STRING 数据源动态创建 OBJECT 模板")
    void test01_DynamicCreateObjectTemplateFromStringDatasource() {
        log.info("========== Test 1: 动态创建 OBJECT 模板 ==========");

        // cache 数据源配置为 STRING 类型
        RedisTemplate<?, ?> originalTemplate = multiRedisManager.getRedisTemplate("cache");
        assertThat(originalTemplate).isInstanceOf(StringRedisTemplate.class);
        log.info("原始模板类型: StringRedisTemplate");

        // 动态创建 OBJECT 类型模板
        RedisTemplate<String, Object> objectTemplate = multiRedisManager.getOrCreateObjectTemplate("cache");
        assertThat(objectTemplate).isNotNull();
        assertThat(objectTemplate).isNotInstanceOf(StringRedisTemplate.class);
        log.info("✅ 动态创建的模板类型: RedisTemplate<String, Object>");

        log.info("========== Test 1 通过 ==========\n");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: 动态创建的模板可存储复杂对象")
    void test02_DynamicTemplateCanStoreComplexObjects() {
        log.info("========== Test 2: 动态模板存储对象 ==========");

        RedisTemplate<String, Object> template = multiRedisManager.getOrCreateObjectTemplate("cache");

        String key = "test:dynamic:product:1001";
        ProductDTO product = new ProductDTO(1001L, "商品A", 99.99, 100);

        template.opsForValue().set(key, product, 60, TimeUnit.SECONDS);
        ProductDTO retrieved = (ProductDTO) template.opsForValue().get(key);

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getId()).isEqualTo(1001L);
        assertThat(retrieved.getName()).isEqualTo("商品A");
        assertThat(retrieved.getPrice()).isEqualTo(99.99);
        assertThat(retrieved.getStock()).isEqualTo(100);

        log.info("✅ 动态模板成功存取复杂对象: {}", retrieved);
        log.info("========== Test 2 通过 ==========\n");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: 动态模板缓存机制验证")
    void test03_DynamicTemplateCachingMechanism() {
        log.info("========== Test 3: 动态模板缓存验证 ==========");

        // 第一次调用
        RedisTemplate<String, Object> template1 = multiRedisManager.getOrCreateObjectTemplate("cache");

        // 第二次调用应该返回缓存的实例
        RedisTemplate<String, Object> template2 = multiRedisManager.getOrCreateObjectTemplate("cache");

        assertThat(template1).isSameAs(template2);
        log.info("✅ 动态模板被正确缓存，多次调用返回同一实例");

        log.info("========== Test 3 通过 ==========\n");
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: 同一数据源可同时使用 STRING 和 OBJECT 模板")
    void test04_SameDatasourceSupportsMultipleTemplateTypes() {
        log.info("========== Test 4: 同一数据源多模板类型 ==========");

        // 使用 StringRedisTemplate 存储字符串
        StringRedisTemplate stringTemplate = multiRedisManager.getStringRedisTemplate("cache");
        String stringKey = "test:string:key1";
        stringTemplate.opsForValue().set(stringKey, "string-value", 60, TimeUnit.SECONDS);

        // 使用 RedisTemplate<String, Object> 存储对象
        RedisTemplate<String, Object> objectTemplate = multiRedisManager.getOrCreateObjectTemplate("cache");
        String objectKey = "test:object:key1";
        ProductDTO product = new ProductDTO(2001L, "商品B", 199.99, 50);
        objectTemplate.opsForValue().set(objectKey, product, 60, TimeUnit.SECONDS);

        // 验证两种类型数据都能正确存取
        String stringValue = stringTemplate.opsForValue().get(stringKey);
        assertThat(stringValue).isEqualTo("string-value");
        log.info("✅ STRING 模板数据存取正常");

        ProductDTO retrievedProduct = (ProductDTO) objectTemplate.opsForValue().get(objectKey);
        assertThat(retrievedProduct).isNotNull();
        assertThat(retrievedProduct.getName()).isEqualTo("商品B");
        log.info("✅ OBJECT 模板数据存取正常");

        log.info("========== Test 4 通过 ==========\n");
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: 从已配置为 OBJECT 的数据源调用 getOrCreateObjectTemplate()")
    void test05_GetOrCreateFromConfiguredObjectDatasource() {
        log.info("========== Test 5: 从 OBJECT 数据源获取模板 ==========");

        // business 数据源已配置为 OBJECT 类型
        RedisTemplate<String, Object> template = multiRedisManager.getOrCreateObjectTemplate("business");

        assertThat(template).isNotNull();
        assertThat(template).isNotInstanceOf(StringRedisTemplate.class);

        // 验证可以正常使用
        String key = "test:configured:order:1001";
        OrderDTO order = new OrderDTO(1001L, "ORD-2024-001", 299.99);
        template.opsForValue().set(key, order, 60, TimeUnit.SECONDS);

        OrderDTO retrieved = (OrderDTO) template.opsForValue().get(key);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getOrderNo()).isEqualTo("ORD-2024-001");

        log.info("✅ 从配置化 OBJECT 数据源获取模板成功");
        log.info("========== Test 5 通过 ==========\n");
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: 动态模板支持 Hash 操作")
    void test06_DynamicTemplateSupportsHashOperations() {
        log.info("========== Test 6: 动态模板 Hash 操作 ==========");

        RedisTemplate<String, Object> template = multiRedisManager.getOrCreateObjectTemplate("cache");

        String hashKey = "test:hash:user:session";
        SessionDTO session1 = new SessionDTO("session-001", "user-A", System.currentTimeMillis());
        SessionDTO session2 = new SessionDTO("session-002", "user-B", System.currentTimeMillis());

        template.opsForHash().put(hashKey, "session-001", session1);
        template.opsForHash().put(hashKey, "session-002", session2);

        SessionDTO retrievedSession1 = (SessionDTO) template.opsForHash().get(hashKey, "session-001");
        SessionDTO retrievedSession2 = (SessionDTO) template.opsForHash().get(hashKey, "session-002");

        assertThat(retrievedSession1).isNotNull();
        assertThat(retrievedSession1.getSessionId()).isEqualTo("session-001");
        assertThat(retrievedSession2).isNotNull();
        assertThat(retrievedSession2.getUserId()).isEqualTo("user-B");

        log.info("✅ 动态模板 Hash 操作正常");
        log.info("========== Test 6 通过 ==========\n");
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: 并发场景下动态模板创建安全性")
    void test07_ConcurrentDynamicTemplateCreationSafety() throws InterruptedException {
        log.info("========== Test 7: 并发创建安全性 ==========");

        int threadCount = 5;
        Thread[] threads = new Thread[threadCount];
        RedisTemplate<String, Object>[] templates = new RedisTemplate[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                templates[index] = multiRedisManager.getOrCreateObjectTemplate("default");
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // 验证所有线程获取的是同一个实例
        for (int i = 1; i < threadCount; i++) {
            assertThat(templates[i]).isSameAs(templates[0]);
        }

        log.info("✅ 并发场景下动态模板创建安全，所有线程获取同一实例");
        log.info("========== Test 7 通过 ==========\n");
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: 动态模板序列化一致性验证")
    void test08_DynamicTemplateSerializationConsistency() {
        log.info("========== Test 8: 序列化一致性验证 ==========");

        RedisTemplate<String, Object> template = multiRedisManager.getOrCreateObjectTemplate("cache");

        String key = "test:serialization:complex";
        NestedObject nested = new NestedObject();
        nested.setLevel1("一级数据");
        nested.setLevel2Data(new ProductDTO(3001L, "嵌套商品", 399.99, 20));

        template.opsForValue().set(key, nested, 60, TimeUnit.SECONDS);
        NestedObject retrieved = (NestedObject) template.opsForValue().get(key);

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getLevel1()).isEqualTo("一级数据");
        assertThat(retrieved.getLevel2Data()).isNotNull();
        assertThat(retrieved.getLevel2Data().getName()).isEqualTo("嵌套商品");

        log.info("✅ 嵌套对象序列化/反序列化一致");
        log.info("========== Test 8 通过 ==========\n");
    }

    // ========== 测试数据类 ==========

    @Data
    public static class ProductDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;
        private String name;
        private Double price;
        private Integer stock;

        public ProductDTO() {}

        public ProductDTO(Long id, String name, Double price, Integer stock) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.stock = stock;
        }
    }

    @Data
    public static class OrderDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long orderId;
        private String orderNo;
        private Double amount;

        public OrderDTO() {}

        public OrderDTO(Long orderId, String orderNo, Double amount) {
            this.orderId = orderId;
            this.orderNo = orderNo;
            this.amount = amount;
        }
    }

    @Data
    public static class SessionDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String sessionId;
        private String userId;
        private Long loginTime;

        public SessionDTO() {}

        public SessionDTO(String sessionId, String userId, Long loginTime) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.loginTime = loginTime;
        }
    }

    @Data
    public static class NestedObject implements Serializable {
        private static final long serialVersionUID = 1L;

        private String level1;
        private ProductDTO level2Data;
    }

    // ========== 测试配置 ==========

    @org.springframework.boot.SpringBootConfiguration
    @Import({
        MultiRedisAutoConfiguration.class
    })
    static class TestConfiguration {
    }
}
