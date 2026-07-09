package fun.commons.framework4j.redis.functional;

import fun.commons.framework4j.redis.annotation.RedisOn;
import fun.commons.framework4j.redis.config.MultiRedisAutoConfiguration;
import fun.commons.framework4j.redis.exception.RedisDataSourceException;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Redis 模板类型配置测试
 * <p>
 * 测试 template-type: string | object 配置功能
 *
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = TemplateTypeConfigurationTest.TestConfiguration.class)
@ActiveProfiles("template-type-test")
@DisplayName("Redis 模板类型配置测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemplateTypeConfigurationTest {

    @Autowired
    private MultiRedisManager multiRedisManager;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private StringTypeService stringTypeService;

    @Autowired
    private ObjectTypeService objectTypeService;

    @BeforeAll
    void setup() {
        log.info("========== 初始化模板类型测试环境 ==========");
        try {
            // 清空所有测试数据库
            multiRedisManager.getRedisTemplate("default").getConnectionFactory().getConnection().flushDb();
            multiRedisManager.getRedisTemplate("cache").getConnectionFactory().getConnection().flushDb();
            multiRedisManager.getRedisTemplate("business").getConnectionFactory().getConnection().flushDb();
            multiRedisManager.getRedisTemplate("session").getConnectionFactory().getConnection().flushDb();
            log.info("✅ 所有测试数据库已清空");
        } catch (Exception e) {
            log.warn("清空数据库失败: {}", e.getMessage());
        }
        log.info("========== 测试环境初始化完成 ==========\n");
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: 验证 STRING 类型数据源创建 StringRedisTemplate")
    void test01_StringTypeCreatesStringRedisTemplate() {
        log.info("========== Test 1: STRING 类型验证 ==========");

        // 验证默认数据源（STRING）
        RedisTemplate<?, ?> defaultTemplate = multiRedisManager.getRedisTemplate("default");
        assertThat(defaultTemplate).isInstanceOf(StringRedisTemplate.class);
        log.info("✅ default 数据源类型: StringRedisTemplate");

        // 验证 cache 数据源（显式 STRING）
        RedisTemplate<?, ?> cacheTemplate = multiRedisManager.getRedisTemplate("cache");
        assertThat(cacheTemplate).isInstanceOf(StringRedisTemplate.class);
        log.info("✅ cache 数据源类型: StringRedisTemplate");

        log.info("========== Test 1 通过 ==========\n");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: 验证 OBJECT 类型数据源创建 RedisTemplate<String, Object>")
    void test02_ObjectTypeCreatesObjectRedisTemplate() {
        log.info("========== Test 2: OBJECT 类型验证 ==========");

        // 验证 business 数据源（OBJECT）
        RedisTemplate<?, ?> businessTemplate = multiRedisManager.getRedisTemplate("business");
        assertThat(businessTemplate).isNotInstanceOf(StringRedisTemplate.class);
        assertThat(businessTemplate).isInstanceOf(RedisTemplate.class);
        log.info("✅ business 数据源类型: RedisTemplate<String, Object>");

        // 验证 session 数据源（OBJECT）
        RedisTemplate<?, ?> sessionTemplate = multiRedisManager.getRedisTemplate("session");
        assertThat(sessionTemplate).isNotInstanceOf(StringRedisTemplate.class);
        assertThat(sessionTemplate).isInstanceOf(RedisTemplate.class);
        log.info("✅ session 数据源类型: RedisTemplate<String, Object>");

        log.info("========== Test 2 通过 ==========\n");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2b: R5 修复 - default=STRING 注册 stringRedisTemplate 别名，OBJECT 数据源不注册")
    void test02b_StringRedisTemplateAliasRegistration() {
        log.info("========== Test 2b: stringRedisTemplate 别名注册验证 ==========");

        // default 是 STRING 主数据源，应注册 stringRedisTemplate 别名（Spring Boot 约定）
        assertThat(applicationContext.containsBean("stringRedisTemplate"))
                .as("default=STRING 时应注册 stringRedisTemplate 别名").isTrue();
        assertThat(applicationContext.isTypeMatch("stringRedisTemplate", StringRedisTemplate.class))
                .as("stringRedisTemplate 别名应为 StringRedisTemplate 类型").isTrue();

        // OBJECT 数据源（business/session）不应被注册为 stringRedisTemplate（R5 修复点）
        // 验证：businessRedisTemplate 不是 StringRedisTemplate
        RedisTemplate<?, ?> businessTemplate = multiRedisManager.getRedisTemplate("business");
        assertThat(businessTemplate).isNotInstanceOf(StringRedisTemplate.class);

        log.info("✅ R5 修复验证通过：default=STRING 注册别名，OBJECT 不影响");
        log.info("========== Test 2b 通过 ==========\n");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: STRING 类型数据源存储和读取字符串")
    void test03_StringTypeDataOperations() {
        log.info("========== Test 3: STRING 类型数据操作 ==========");

        String key = "test:string:key";
        String value = "test-string-value";

        stringTypeService.saveString(key, value);
        String retrieved = stringTypeService.getString(key);

        assertThat(retrieved).isEqualTo(value);
        log.info("✅ STRING 类型数据存取成功: {}", value);

        log.info("========== Test 3 通过 ==========\n");
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: OBJECT 类型数据源存储和读取复杂对象")
    void test04_ObjectTypeDataOperations() {
        log.info("========== Test 4: OBJECT 类型数据操作 ==========");

        String key = "test:user:1001";
        UserDTO user = new UserDTO();
        user.setId(1001L);
        user.setName("张三");
        user.setEmail("zhangsan@example.com");
        user.setAge(28);

        objectTypeService.saveUser(key, user);
        UserDTO retrieved = objectTypeService.getUser(key);

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getId()).isEqualTo(1001L);
        assertThat(retrieved.getName()).isEqualTo("张三");
        assertThat(retrieved.getEmail()).isEqualTo("zhangsan@example.com");
        assertThat(retrieved.getAge()).isEqualTo(28);

        log.info("✅ OBJECT 类型对象存取成功: {}", retrieved);
        log.info("========== Test 4 通过 ==========\n");
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: OBJECT 类型序列化验证（fastjson2）")
    void test05_ObjectTypeSerializationValidation() {
        log.info("========== Test 5: fastjson2 序列化验证 ==========");

        String key = "test:complex:obj";
        ComplexObject obj = new ComplexObject();
        obj.setStringField("测试字符串");
        obj.setIntField(12345);
        obj.setBoolField(true);
        obj.setNestedObject(new UserDTO(2001L, "李四", "lisi@test.com", 30));

        RedisTemplate<String, Object> template = multiRedisManager.getObjectRedisTemplate("business");
        template.opsForValue().set(key, obj, 60, TimeUnit.SECONDS);

        ComplexObject retrieved = (ComplexObject) template.opsForValue().get(key);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getStringField()).isEqualTo("测试字符串");
        assertThat(retrieved.getIntField()).isEqualTo(12345);
        assertThat(retrieved.isBoolField()).isTrue();
        assertThat(retrieved.getNestedObject()).isNotNull();
        assertThat(retrieved.getNestedObject().getName()).isEqualTo("李四");

        log.info("✅ 复杂对象序列化/反序列化成功");
        log.info("========== Test 5 通过 ==========\n");
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Manager API 类型安全获取 - getStringRedisTemplate()")
    void test06_ManagerTypeSafeStringTemplateRetrieval() {
        log.info("========== Test 6: getStringRedisTemplate() 类型安全 ==========");

        // 正确场景：STRING 类型数据源
        StringRedisTemplate cacheTemplate = multiRedisManager.getStringRedisTemplate("cache");
        assertThat(cacheTemplate).isNotNull();
        log.info("✅ 从 STRING 类型数据源获取 StringRedisTemplate 成功");

        // 错误场景：OBJECT 类型数据源
        assertThatThrownBy(() -> multiRedisManager.getStringRedisTemplate("business"))
            .isInstanceOf(RedisDataSourceException.class)
            .hasMessageContaining("配置为 RedisTemplate<String, Object>")
            .hasMessageContaining("无法转换为 StringRedisTemplate");

        log.info("✅ 类型不匹配时正确抛出异常");
        log.info("========== Test 6 通过 ==========\n");
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Manager API 类型安全获取 - getObjectRedisTemplate()")
    void test07_ManagerTypeSafeObjectTemplateRetrieval() {
        log.info("========== Test 7: getObjectRedisTemplate() 类型安全 ==========");

        // 正确场景：OBJECT 类型数据源
        RedisTemplate<String, Object> businessTemplate = multiRedisManager.getObjectRedisTemplate("business");
        assertThat(businessTemplate).isNotNull();
        log.info("✅ 从 OBJECT 类型数据源获取 RedisTemplate<String, Object> 成功");

        // 错误场景：STRING 类型数据源
        assertThatThrownBy(() -> multiRedisManager.getObjectRedisTemplate("cache"))
            .isInstanceOf(RedisDataSourceException.class)
            .hasMessageContaining("配置为 StringRedisTemplate")
            .hasMessageContaining("无法转换为 RedisTemplate<String, Object>");

        log.info("✅ 类型不匹配时正确抛出异常");
        log.info("========== Test 7 通过 ==========\n");
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: @RedisOn 注入 - STRING 类型字段匹配")
    void test08_RedisOnInjectionStringType() {
        log.info("========== Test 8: @RedisOn 注入 STRING 类型 ==========");

        assertThat(stringTypeService.getRedisTemplate()).isNotNull();
        assertThat(stringTypeService.getRedisTemplate()).isInstanceOf(StringRedisTemplate.class);

        String key = "test:inject:string";
        stringTypeService.saveString(key, "injected-value");
        String value = stringTypeService.getString(key);

        assertThat(value).isEqualTo("injected-value");
        log.info("✅ @RedisOn 注入 StringRedisTemplate 成功");
        log.info("========== Test 8 通过 ==========\n");
    }

    @Test
    @Order(9)
    @DisplayName("Test 9: @RedisOn 注入 - OBJECT 类型字段匹配")
    void test09_RedisOnInjectionObjectType() {
        log.info("========== Test 9: @RedisOn 注入 OBJECT 类型 ==========");

        assertThat(objectTypeService.getRedisTemplate()).isNotNull();
        assertThat(objectTypeService.getRedisTemplate()).isNotInstanceOf(StringRedisTemplate.class);

        String key = "test:inject:user:9001";
        UserDTO user = new UserDTO(9001L, "测试用户", "test@inject.com", 25);

        objectTypeService.saveUser(key, user);
        UserDTO retrieved = objectTypeService.getUser(key);

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getName()).isEqualTo("测试用户");
        log.info("✅ @RedisOn 注入 RedisTemplate<String, Object> 成功");
        log.info("========== Test 9 通过 ==========\n");
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: TTL 过期策略验证（OBJECT 类型）")
    void test10_ObjectTypeTTLValidation() throws InterruptedException {
        log.info("========== Test 10: OBJECT 类型 TTL 验证 ==========");

        String key = "test:ttl:user";
        UserDTO user = new UserDTO(1010L, "过期用户", "expire@test.com", 30);

        RedisTemplate<String, Object> template = multiRedisManager.getObjectRedisTemplate("session");
        template.opsForValue().set(key, user, 1, TimeUnit.SECONDS);

        UserDTO beforeExpire = (UserDTO) template.opsForValue().get(key);
        assertThat(beforeExpire).isNotNull();
        log.info("✅ 过期前可正常读取");

        // v2.1 P1: 改轮询替代 sleep(1100)，避免边界紧邻 flaky
        long deadline = System.currentTimeMillis() + 3000;
        UserDTO afterExpire;
        do {
            afterExpire = (UserDTO) template.opsForValue().get(key);
            if (afterExpire == null) break;
            Thread.sleep(100);
        } while (System.currentTimeMillis() < deadline);
        assertThat(afterExpire).isNull();
        log.info("✅ 过期后数据已清除");
        log.info("========== Test 10 通过 ==========\n");
    }

    // ========== 测试服务类 ==========

    @Service
    @RedisOn("cache")
    @Getter
    static class StringTypeService {
        @Autowired
        private StringRedisTemplate redisTemplate;

        public void saveString(String key, String value) {
            redisTemplate.opsForValue().set(key, value, 60, TimeUnit.SECONDS);
        }

        public String getString(String key) {
            return redisTemplate.opsForValue().get(key);
        }
    }

    @Service
    @RedisOn("business")
    @Getter
    static class ObjectTypeService {
        @Autowired
        @Qualifier("businessRedisTemplate")
        private RedisTemplate<String, Object> redisTemplate;

        public void saveUser(String key, UserDTO user) {
            redisTemplate.opsForValue().set(key, user, 120, TimeUnit.SECONDS);
        }

        @SuppressWarnings("unchecked")
        public UserDTO getUser(String key) {
            return (UserDTO) redisTemplate.opsForValue().get(key);
        }
    }

    // ========== 测试数据类 ==========

    @Data
    public static class UserDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;
        private String name;
        private String email;
        private Integer age;

        public UserDTO() {}

        public UserDTO(Long id, String name, String email, Integer age) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.age = age;
        }
    }

    @Data
    public static class ComplexObject implements Serializable {
        private static final long serialVersionUID = 1L;

        private String stringField;
        private int intField;
        private boolean boolField;
        private UserDTO nestedObject;
    }

    // ========== 测试配置 ==========

    @org.springframework.boot.SpringBootConfiguration
    @Import({
        MultiRedisAutoConfiguration.class,
        StringTypeService.class,
        ObjectTypeService.class
    })
    static class TestConfiguration {
    }
}
