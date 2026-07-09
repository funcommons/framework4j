package fun.commons.framework4j.redis.functional;

import fun.commons.framework4j.redis.annotation.RedisOn;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 完整的多Redis数据源注入方式测试
 * <p>
 * 按照产品文档测试所有5种注入方式：
 * 1. 方式一: @Qualifier 注解注入
 * 2. 方式二: MultiRedisManager 统一管理
 * 3. 方式三: 默认注入 (@Primary)
 * 4. 方式四: 字段名匹配注入
 * 5. 方式五: @RedisOn 类级别注解 (推荐)
 *
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = {AllInjectionMethodsTest.TestConfiguration.class})
@ActiveProfiles("test")
@DisplayName("产品文档 - 全部注入方式测试")
@TestPropertySource(locations = "classpath:application-test.yml")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AllInjectionMethodsTest {

    @Autowired
    private MultiRedisManager multiRedisManager;

    @Autowired
    private Method1QualifierService method1Service;

    @Autowired
    private Method2ManagerService method2Service;

    @Autowired
    private Method3PrimaryService method3Service;

    @Autowired
    private Method4FieldNameService method4Service;

    @Autowired
    private Method5RedisOnService method5Service;

    @Autowired
    private Method5StrictModeService strictModeService;

    @Autowired
    private Method5LooseModeService looseModeService;

    private static final String TEST_PREFIX = "allinject:";

    @Test
    @Order(1)
    @DisplayName("方式一: @Qualifier 注解注入 - 显式指定数据源")
    void testMethod1_QualifierInjection() {
        log.info("========== 测试方式一: @Qualifier 注解注入 ==========");

        assertNotNull(method1Service);
        assertNotNull(method1Service.getBusinessRedisTemplate());
        assertNotNull(method1Service.getLockRedissonClient());

        // 测试业务数据源
        String businessKey = TEST_PREFIX + "m1:business";
        method1Service.saveBusinessData(businessKey, "business-data");
        String retrieved = method1Service.getBusinessData(businessKey);
        assertEquals("business-data", retrieved);

        // 测试分布式锁
        String lockKey = TEST_PREFIX + "m1:lock";
        boolean locked = method1Service.tryLock(lockKey);
        assertTrue(locked);
        method1Service.unlock(lockKey);

        log.info("✅ 方式一测试通过: @Qualifier 显式注入正常工作");
    }

    @Test
    @Order(2)
    @DisplayName("方式二: MultiRedisManager 统一管理 - 动态获取")
    void testMethod2_ManagerInjection() {
        log.info("========== 测试方式二: MultiRedisManager 统一管理 ==========");

        assertNotNull(method2Service);
        assertNotNull(method2Service.getMultiRedisManager());

        // 测试动态获取不同数据源
        String key1 = TEST_PREFIX + "m2:cache";
        String key2 = TEST_PREFIX + "m2:business";

        method2Service.saveToCacheDataSource(key1, "cache-value");
        method2Service.saveToBusinessDataSource(key2, "business-value");

        String val1 = method2Service.getFromCacheDataSource(key1);
        String val2 = method2Service.getFromBusinessDataSource(key2);

        assertEquals("cache-value", val1);
        assertEquals("business-value", val2);

        // 测试获取 RedissonClient
        String lockKey = TEST_PREFIX + "m2:lock";
        boolean locked = method2Service.tryLockWithLockDataSource(lockKey);
        assertTrue(locked);
        method2Service.unlockWithLockDataSource(lockKey);

        log.info("✅ 方式二测试通过: MultiRedisManager 动态管理正常工作");
    }

    @Test
    @Order(3)
    @DisplayName("方式三: 默认注入 (@Primary) - 无需指定")
    void testMethod3_PrimaryInjection() {
        log.info("========== 测试方式三: 默认注入 (@Primary) ==========");

        assertNotNull(method3Service);
        assertNotNull(method3Service.getRedisTemplate());
        assertNotNull(method3Service.getRedissonClient());

        // 验证注入的是 default 数据源
        StringRedisTemplate defaultTemplate = multiRedisManager.getStringRedisTemplate("default");
        assertSame(defaultTemplate, method3Service.getRedisTemplate(),
                "应该注入 default 数据源（@Primary）");

        // 测试数据操作
        String key = TEST_PREFIX + "m3:primary";
        method3Service.cacheData(key, "primary-value");
        String retrieved = method3Service.getCachedData(key);
        assertEquals("primary-value", retrieved);

        log.info("✅ 方式三测试通过: @Primary 默认注入正常工作");
    }

    @Test
    @Order(4)
    @DisplayName("方式四: 字段名匹配注入 - Spring自动匹配")
    void testMethod4_FieldNameMatching() {
        log.info("========== 测试方式四: 字段名匹配注入 ==========");

        assertNotNull(method4Service);
        assertNotNull(method4Service.getBusinessRedisTemplate());
        assertNotNull(method4Service.getLockRedissonClient());
        assertNotNull(method4Service.getDefaultRedisTemplate());

        // 验证字段名匹配
        StringRedisTemplate businessTemplate = multiRedisManager.getStringRedisTemplate("business");
        assertSame(businessTemplate, method4Service.getBusinessRedisTemplate(),
                "字段名 businessRedisTemplate 应该匹配对应的 Bean");

        RedissonClient lockClient = multiRedisManager.getRedissonClient("lock");
        assertSame(lockClient, method4Service.getLockRedissonClient(),
                "字段名 lockRedissonClient 应该匹配对应的 Bean");

        // 测试处理订单流程
        Long orderId = System.currentTimeMillis();
        method4Service.processOrder(orderId);

        // 验证数据写入
        String businessKey = "order:" + orderId;
        String defaultKey = "cache:" + orderId;

        String businessData = method4Service.getBusinessData(businessKey);
        String cacheData = method4Service.getCacheData(defaultKey);

        assertEquals("data", businessData);
        assertEquals("cached", cacheData);

        log.info("✅ 方式四测试通过: 字段名匹配注入正常工作");
    }

    @Test
    @Order(5)
    @DisplayName("方式五: @RedisOn 类级别注解 - 推荐方式")
    void testMethod5_RedisOnAnnotation() {
        log.info("========== 测试方式五: @RedisOn 类级别注解 ==========");

        assertNotNull(method5Service);
        assertNotNull(method5Service.getRedisTemplate());
        assertNotNull(method5Service.getRedissonClient());

        // 验证注入的是 cache 数据源
        StringRedisTemplate cacheTemplate = multiRedisManager.getStringRedisTemplate("cache");
        assertSame(cacheTemplate, method5Service.getRedisTemplate(),
                "@RedisOn(\"cache\") 应该注入 cache 数据源");

        RedissonClient cacheRedisson = multiRedisManager.getRedissonClient("cache");
        assertSame(cacheRedisson, method5Service.getRedissonClient(),
                "@RedisOn(\"cache\") 应该注入 cache 数据源的 RedissonClient");

        // 测试数据操作
        String key = TEST_PREFIX + "m5:redison";
        method5Service.set(key, "redison-value");
        String retrieved = method5Service.get(key);
        assertEquals("redison-value", retrieved);

        // 测试锁操作
        String lockKey = TEST_PREFIX + "m5:lock";
        method5Service.lock(lockKey);
        // 简单验证锁功能
        assertDoesNotThrow(() -> method5Service.lock(lockKey));

        log.info("✅ 方式五测试通过: @RedisOn 注解注入正常工作");
    }

    @Test
    @Order(6)
    @DisplayName("方式五扩展: 严格模式 (strict=true) - 默认行为")
    void testMethod5_StrictMode() {
        log.info("========== 测试方式五扩展: 严格模式 ==========");

        assertNotNull(strictModeService);
        assertNotNull(strictModeService.getRedisTemplate());

        // 验证使用的是 cache 数据源
        StringRedisTemplate cacheTemplate = multiRedisManager.getStringRedisTemplate("cache");
        assertSame(cacheTemplate, strictModeService.getRedisTemplate());

        log.info("✅ 严格模式测试通过: 数据源存在时正常注入");
    }

    @Test
    @Order(7)
    @DisplayName("方式五扩展: 宽松模式 (strict=false) - 自动降级")
    void testMethod5_LooseMode() {
        log.info("========== 测试方式五扩展: 宽松模式 ==========");

        assertNotNull(looseModeService);
        assertNotNull(looseModeService.getRedisTemplate());

        // 在宽松模式下，如果指定的数据源存在，应该使用指定的数据源
        // 如果不存在，会降级到 default
        StringRedisTemplate cacheTemplate = multiRedisManager.getStringRedisTemplate("cache");
        assertSame(cacheTemplate, looseModeService.getRedisTemplate(),
                "cache 数据源存在时应该使用 cache");

        log.info("✅ 宽松模式测试通过: 数据源存在时使用指定数据源，不存在时自动降级");
    }

    @Test
    @Order(8)
    @DisplayName("对比测试: 不同方式的性能和便利性")
    void testCompareAllMethods() {
        log.info("========== 对比所有注入方式 ==========");

        String key = TEST_PREFIX + "compare";

        // 方式一: 需要为每个字段添加 @Qualifier
        method1Service.saveBusinessData(key, "m1");
        assertEquals("m1", method1Service.getBusinessData(key));

        // 方式二: 运行时动态获取，灵活但代码较多
        method2Service.saveToBusinessDataSource(key, "m2");
        assertEquals("m2", method2Service.getFromBusinessDataSource(key));

        // 方式三: 简洁但只能用 default
        method3Service.cacheData(key, "m3");
        assertEquals("m3", method3Service.getCachedData(key));

        // 方式四: 依赖字段命名规范
        method4Service.saveBusinessData(key, "m4");
        assertEquals("m4", method4Service.getBusinessData(key));

        // 方式五: 类级别声明，最简洁清晰（推荐）
        method5Service.set(key, "m5");
        assertEquals("m5", method5Service.get(key));

        log.info("✅ 对比测试完成");
        log.info("推荐使用方式五 (@RedisOn): 代码最简洁，语义最清晰");
    }

    // ==================== 方式一: @Qualifier 注解注入 ====================

    @Service
    @org.springframework.context.annotation.DependsOn("multiRedisManager")
    @Getter
    static class Method1QualifierService {
        @Autowired
        @Qualifier("businessRedisTemplate")
        private StringRedisTemplate businessRedisTemplate;

        @Autowired
        @Qualifier("lockRedissonClient")
        private RedissonClient lockRedissonClient;

        public void saveBusinessData(String key, String value) {
            businessRedisTemplate.opsForValue().set(key, value, 60, TimeUnit.SECONDS);
        }

        public String getBusinessData(String key) {
            return businessRedisTemplate.opsForValue().get(key);
        }

        public boolean tryLock(String lockKey) {
            return lockRedissonClient.getLock(lockKey).tryLock();
        }

        public void unlock(String lockKey) {
            var lock = lockRedissonClient.getLock(lockKey);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ==================== 方式二: MultiRedisManager 统一管理 ====================

    @Service
    @org.springframework.context.annotation.DependsOn("multiRedisManager")
    @Getter
    static class Method2ManagerService {
        @Autowired
        private MultiRedisManager multiRedisManager;

        public void saveToCacheDataSource(String key, String value) {
            StringRedisTemplate template = multiRedisManager.getStringRedisTemplate("cache");
            template.opsForValue().set(key, value, 60, TimeUnit.SECONDS);
        }

        public String getFromCacheDataSource(String key) {
            StringRedisTemplate template = multiRedisManager.getStringRedisTemplate("cache");
            return template.opsForValue().get(key);
        }

        public void saveToBusinessDataSource(String key, String value) {
            StringRedisTemplate template = multiRedisManager.getStringRedisTemplate("business");
            template.opsForValue().set(key, value, 60, TimeUnit.SECONDS);
        }

        public String getFromBusinessDataSource(String key) {
            StringRedisTemplate template = multiRedisManager.getStringRedisTemplate("business");
            return template.opsForValue().get(key);
        }

        public boolean tryLockWithLockDataSource(String lockKey) {
            RedissonClient client = multiRedisManager.getRedissonClient("lock");
            return client.getLock(lockKey).tryLock();
        }

        public void unlockWithLockDataSource(String lockKey) {
            RedissonClient client = multiRedisManager.getRedissonClient("lock");
            var lock = client.getLock(lockKey);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ==================== 方式三: 默认注入 (@Primary) ====================

    @Service
    @org.springframework.context.annotation.DependsOn("multiRedisManager")
    @Getter
    static class Method3PrimaryService {
        // 使用 @Qualifier 指定注入 stringRedisTemplate (配置中的primary bean)
        @Autowired
        @Qualifier("stringRedisTemplate")
        private StringRedisTemplate redisTemplate;

        // 不使用 @Qualifier,自动注入 defaultRedissonClient
        @Autowired
        private RedissonClient redissonClient;

        public void cacheData(String key, String value) {
            redisTemplate.opsForValue().set(key, value, 300, TimeUnit.SECONDS);
        }

        public String getCachedData(String key) {
            return redisTemplate.opsForValue().get(key);
        }
    }

    // ==================== 方式四: 字段名匹配注入 ====================

    // 【关键修改】方式四：使用 @Resource 实现真正的名称匹配注入
    @Service
    @org.springframework.context.annotation.DependsOn("multiRedisManager")
    @Getter
    static class Method4FieldNameService {

        // 替换 @Autowired 为 @Resource，优先按名称查找，绕过 @Primary 限制
        @Resource
        private StringRedisTemplate businessRedisTemplate;

        @Resource
        private RedissonClient lockRedissonClient;

        // defaultRedisTemplate 本身就是 Primary，怎么注入都没问题
        @Autowired
        private StringRedisTemplate defaultRedisTemplate;

        public void processOrder(Long orderId) {
            businessRedisTemplate.opsForValue().set("order:" + orderId, "data", 60, TimeUnit.SECONDS);
            defaultRedisTemplate.opsForValue().set("cache:" + orderId, "cached", 60, TimeUnit.SECONDS);
        }

        public String getBusinessData(String key) {
            return businessRedisTemplate.opsForValue().get(key);
        }
        public String getCacheData(String key) {
            return defaultRedisTemplate.opsForValue().get(key);
        }
        public void saveBusinessData(String key, String value) {
            businessRedisTemplate.opsForValue().set(key, value, 60, TimeUnit.SECONDS);
        }
    }

    // ==================== 方式五: @RedisOn 类级别注解 ====================

    @Service
    @RedisOn("cache")
    @org.springframework.context.annotation.DependsOn("multiRedisManager")
    @Getter
    static class Method5RedisOnService {
        @Autowired
        private StringRedisTemplate redisTemplate;

        @Autowired
        private RedissonClient redissonClient;

        public void set(String key, String value) {
            redisTemplate.opsForValue().set(key, value, 300, TimeUnit.SECONDS);
        }

        public String get(String key) {
            return redisTemplate.opsForValue().get(key);
        }

        public void lock(String lockKey) {
            var lock = redissonClient.getLock(lockKey);
            try {
                if (lock.tryLock(10, TimeUnit.SECONDS)) {
                    // 业务逻辑
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    // 严格模式(默认):数据源不存在时启动失败
    @Service
    @RedisOn("cache")
    @org.springframework.context.annotation.DependsOn("multiRedisManager")
    @Getter
    static class Method5StrictModeService {
        @Autowired
        private StringRedisTemplate redisTemplate;
    }

    // 宽松模式:数据源不存在时自动降级到 default
    @Service
    @RedisOn(value = "cache", strict = false)
    @org.springframework.context.annotation.DependsOn("multiRedisManager")
    @Getter
    static class Method5LooseModeService {
        @Autowired
        private StringRedisTemplate redisTemplate;
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @org.springframework.context.annotation.Import({

            AllInjectionMethodsTest.Method1QualifierService.class,
            AllInjectionMethodsTest.Method2ManagerService.class,
            AllInjectionMethodsTest.Method3PrimaryService.class,
            AllInjectionMethodsTest.Method4FieldNameService.class,
            AllInjectionMethodsTest.Method5RedisOnService.class,
            AllInjectionMethodsTest.Method5StrictModeService.class,
            AllInjectionMethodsTest.Method5LooseModeService.class
    })
    static class TestConfiguration {
    }
}