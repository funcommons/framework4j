package fun.commons.framework4j.redis.functional;

import fun.commons.framework4j.redis.annotation.RedisOn;
import fun.commons.framework4j.redis.config.MultiRedisAutoConfiguration;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CompletableFuture; // 新增
import java.util.concurrent.ExecutionException; // 新增
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @RedisOn 注解功能测试
 */
@Slf4j
@SpringBootTest(classes = {RedisOnAnnotationTest.TestConfiguration.class})
@ActiveProfiles("test")
@DisplayName("@RedisOn 注解功能测试")
@TestPropertySource(locations = "classpath:application-test.yml")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedisOnAnnotationTest {

    @Autowired
    private MultiRedisManager multiRedisManager;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private BusinessService businessService;

    @Autowired
    private LockService lockService;

    @Autowired
    private MixedService mixedService;

    private static final String TEST_KEY_PREFIX = "redison:test:";
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

    // ... (Test 01-05 保持不变) ...
    @Test
    @Order(1)
    @DisplayName("测试1: 验证 MultiRedisManager 初始化")
    void test01_VerifyMultiRedisManager() {
        assertNotNull(multiRedisManager, "MultiRedisManager 应该被成功注入");
        assertTrue(multiRedisManager.containsDatasource("default"), "应包含 default 数据源");
        assertTrue(multiRedisManager.containsDatasource("cache"), "应包含 cache 数据源");
        assertTrue(multiRedisManager.containsDatasource("business"), "应包含 business 数据源");
        assertTrue(multiRedisManager.containsDatasource("lock"), "应包含 lock 数据源");
        log.info("✅ MultiRedisManager 包含所有配置的数据源");
    }

    @Test
    @Order(2)
    @DisplayName("测试2: @RedisOn(\"cache\") - 基本注入")
    void test02_CacheServiceBasicInjection() {
        assertNotNull(cacheService, "CacheService 应该被成功创建");
        assertNotNull(cacheService.getRedisTemplate(), "StringRedisTemplate 应该被自动注入");
        assertNotNull(cacheService.getRedissonClient(), "RedissonClient 应该被自动注入");
        log.info("✅ @RedisOn(\"cache\") 成功注入 StringRedisTemplate 和 RedissonClient");
    }

    @Test
    @Order(3)
    @DisplayName("测试3: @RedisOn(\"cache\") - StringRedisTemplate 数据操作")
    void test03_CacheServiceDataOperation() {
        String key = TEST_KEY_PREFIX + "cache:test3";
        String value = "cache-value-" + System.currentTimeMillis();
        cacheService.set(key, value, 60);
        String retrieved = cacheService.get(key);
        assertEquals(value, retrieved, "读取的值应该与写入的值相同");
        cacheService.delete(key);
        assertNull(cacheService.get(key), "删除后应该读取不到数据");
        log.info("✅ @RedisOn(\"cache\") StringRedisTemplate 数据操作正常");
    }

    @Test
    @Order(4)
    @DisplayName("测试4: @RedisOn(\"cache\") - RedissonClient RBucket 操作")
    void test04_CacheServiceRedissonBucket() {
        String key = TEST_KEY_PREFIX + "cache:bucket:test4";
        String value = "redisson-value-" + System.currentTimeMillis();
        cacheService.setBucket(key, value, 60);
        String retrieved = cacheService.getBucket(key);
        assertEquals(value, retrieved, "RBucket 读取的值应该与写入的值相同");
        cacheService.deleteBucket(key);
        log.info("✅ @RedisOn(\"cache\") RedissonClient RBucket 操作正常");
    }

    @Test
    @Order(5)
    @DisplayName("测试5: @RedisOn(\"business\") - 业务数据操作")
    void test05_BusinessServiceDataOperation() {
        assertNotNull(businessService, "BusinessService 应该被成功创建");
        assertNotNull(businessService.getRedisTemplate(), "StringRedisTemplate 应该被自动注入");
        String orderId = "ORDER-" + System.currentTimeMillis();
        String orderData = "{\"id\":\"" + orderId + "\",\"amount\":100.00}";
        businessService.saveOrder(orderId, orderData);
        String retrieved = businessService.getOrder(orderId);
        assertEquals(orderData, retrieved, "订单数据应该正确保存和读取");
        businessService.deleteOrder(orderId);
        log.info("✅ @RedisOn(\"business\") 业务数据操作正常");
    }

    @Test
    @Order(6)
    @DisplayName("测试6: @RedisOn(\"lock\") - 分布式锁操作")
    void test06_LockServiceDistributedLock() throws ExecutionException, InterruptedException {
        assertNotNull(lockService, "LockService 应该被成功创建");
        assertNotNull(lockService.getRedissonClient(), "RedissonClient 应该被自动注入");

        String lockKey = TEST_KEY_PREFIX + "lock:test6";

        // 1. 主线程获取锁
        boolean locked = lockService.tryLock(lockKey, 5);
        assertTrue(locked, "主线程应该成功获取锁");

        // 2. 【修复点】使用另一个线程尝试获取同一把锁
        // Redisson 锁是可重入的，如果是同一个线程，tryLock 会返回 true。
        // 为了验证互斥性，必须使用不同的线程。
        CompletableFuture<Boolean> otherThreadAttempt = CompletableFuture.supplyAsync(() -> {
            return lockService.tryLockNoWait(lockKey);
        });

        // 等待结果
        boolean lockedByOther = otherThreadAttempt.get();
        assertFalse(lockedByOther, "锁被主线程持有时，其他线程不应该能再次获取");

        // 3. 释放锁
        lockService.unlock(lockKey);

        // 4. 释放后，其他线程应该能再次获取
        CompletableFuture<Boolean> retryAttempt = CompletableFuture.supplyAsync(() -> {
            boolean success = lockService.tryLockNoWait(lockKey);
            if (success) {
                lockService.unlock(lockKey); // 获取成功后记得释放
            }
            return success;
        });

        assertTrue(retryAttempt.get(), "锁释放后，其他线程应该能再次获取");

        log.info("✅ @RedisOn(\"lock\") 分布式锁操作正常 (已验证跨线程互斥性)");
    }

    // ... (Test 07-10 保持不变) ...

    @Test
    @Order(7)
    @DisplayName("测试7: 数据源隔离验证")
    void test07_DataSourceIsolation() {
        String key = TEST_KEY_PREFIX + "isolation";
        String cacheValue = "cache-data";
        String businessValue = "business-data";

        cacheService.set(key, cacheValue, 60);
        businessService.saveOrder(key, businessValue);

        String cacheRead = cacheService.get(key);
        String businessRead = businessService.getOrder(key);

        assertEquals(cacheValue, cacheRead, "cache 数据源应该读取到自己的数据");
        assertEquals(businessValue, businessRead, "business 数据源应该读取到自己的数据");
        assertNotEquals(cacheRead, businessRead, "不同数据源的数据应该隔离");

        cacheService.delete(key);
        businessService.deleteOrder(key);
        log.info("✅ 数据源隔离验证通过");
    }

    @Test
    @Order(8)
    @DisplayName("测试8: 混合使用 - @RedisOn + @Qualifier")
    void test08_MixedUsage() {
        assertNotNull(mixedService, "MixedService 应该被成功创建");
        assertNotNull(mixedService.getDefaultRedis(), "默认 Redis 应该通过 @RedisOn 注入");
        assertNotNull(mixedService.getBusinessRedis(), "业务 Redis 应该通过 @Qualifier 注入");

        String key = TEST_KEY_PREFIX + "mixed";
        mixedService.saveToDefault(key, "default-value");
        mixedService.saveToBusiness(key, "business-value");

        String defaultValue = mixedService.getFromDefault(key);
        String businessValue = mixedService.getFromBusiness(key);

        assertEquals("default-value", defaultValue);
        assertEquals("business-value", businessValue);
        log.info("✅ 混合使用 @RedisOn + @Qualifier 正常");
    }

    @Test
    @Order(9)
    @DisplayName("测试9: 验证注入的是正确的数据源实例")
    void test09_VerifyCorrectDataSourceInstance() {
        StringRedisTemplate cacheTemplate = multiRedisManager.getStringRedisTemplate("cache");
        assertSame(cacheTemplate, cacheService.getRedisTemplate(),
                "CacheService 应该注入 cache 数据源的 Template");

        RedissonClient cacheRedisson = multiRedisManager.getRedissonClient("cache");
        assertSame(cacheRedisson, cacheService.getRedissonClient(),
                "CacheService 应该注入 cache 数据源的 RedissonClient");

        StringRedisTemplate businessTemplate = multiRedisManager.getStringRedisTemplate("business");
        assertSame(businessTemplate, businessService.getRedisTemplate(),
                "BusinessService 应该注入 business 数据源的 Template");

        log.info("✅ 注入的数据源实例验证正确");
    }

    @Test
    @Order(10)
    @DisplayName("测试10: 并发场景下的数据源隔离")
    void test10_ConcurrentDataSourceIsolation() throws InterruptedException {
        String key = TEST_KEY_PREFIX + "concurrent";

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                cacheService.set(key + ":cache:" + i, "cache-" + i, 60);
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                businessService.saveOrder(key + ":business:" + i, "business-" + i);
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        for (int i = 0; i < 100; i++) {
            String cacheVal = cacheService.get(key + ":cache:" + i);
            String businessVal = businessService.getOrder(key + ":business:" + i);
            assertEquals("cache-" + i, cacheVal);
            assertEquals("business-" + i, businessVal);
        }
        log.info("✅ 并发场景下数据源隔离正常");
    }

    // ==================== 测试用服务类 ====================

    @Service
    @RedisOn("cache")
    @org.springframework.context.annotation.DependsOn("multiRedisManager")
    @Getter
    static class CacheService {
        private StringRedisTemplate redisTemplate;
        private RedissonClient redissonClient;

        public void set(String key, String value, long seconds) {
            redisTemplate.opsForValue().set(key, value, seconds, TimeUnit.SECONDS);
        }
        public String get(String key) {
            return redisTemplate.opsForValue().get(key);
        }
        public void delete(String key) {
            redisTemplate.delete(key);
        }
        public void setBucket(String key, String value, long seconds) {
            RBucket<String> bucket = redissonClient.getBucket(key);
            bucket.set(value, seconds, TimeUnit.SECONDS);
        }
        public String getBucket(String key) {
            RBucket<String> bucket = redissonClient.getBucket(key);
            return bucket.get();
        }
        public void deleteBucket(String key) {
            redissonClient.getBucket(key).delete();
        }
    }

    @Service
    @RedisOn("business")
    @org.springframework.context.annotation.DependsOn("multiRedisManager")
    @Getter
    static class BusinessService {
        private StringRedisTemplate redisTemplate;
        private RedissonClient redissonClient;

        public void saveOrder(String orderId, String orderData) {
            String key = "order:" + orderId;
            redisTemplate.opsForValue().set(key, orderData, 300, TimeUnit.SECONDS);
        }
        public String getOrder(String orderId) {
            String key = "order:" + orderId;
            return redisTemplate.opsForValue().get(key);
        }
        public void deleteOrder(String orderId) {
            String key = "order:" + orderId;
            redisTemplate.delete(key);
        }
    }

    @Service
    @RedisOn("lock")
    @org.springframework.context.annotation.DependsOn("multiRedisManager")
    @Getter
    static class LockService {
        private RedissonClient redissonClient;

        public boolean tryLock(String lockKey, long waitSeconds) {
            RLock lock = redissonClient.getLock(lockKey);
            try {
                return lock.tryLock(waitSeconds, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        public boolean tryLockNoWait(String lockKey) {
            RLock lock = redissonClient.getLock(lockKey);
            return lock.tryLock();
        }

        public void unlock(String lockKey) {
            RLock lock = redissonClient.getLock(lockKey);
            // 只解锁自己持有的
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Service
    @RedisOn("default")
    @org.springframework.context.annotation.DependsOn("multiRedisManager")
    @Getter
    static class MixedService {
        private StringRedisTemplate defaultRedis;

        @Autowired
        @Qualifier("businessRedisTemplate")
        private StringRedisTemplate businessRedis;

        public void saveToDefault(String key, String value) {
            defaultRedis.opsForValue().set(key, value, 60, TimeUnit.SECONDS);
        }
        public String getFromDefault(String key) {
            return defaultRedis.opsForValue().get(key);
        }
        public void saveToBusiness(String key, String value) {
            businessRedis.opsForValue().set(key, value, 60, TimeUnit.SECONDS);
        }
        public String getFromBusiness(String key) {
            return businessRedis.opsForValue().get(key);
        }
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.context.annotation.Import({
            MultiRedisAutoConfiguration.class,
            RedisOnAnnotationTest.CacheService.class,
            RedisOnAnnotationTest.BusinessService.class,
            RedisOnAnnotationTest.LockService.class,
            RedisOnAnnotationTest.MixedService.class
    })
    static class TestConfiguration {
    }
}