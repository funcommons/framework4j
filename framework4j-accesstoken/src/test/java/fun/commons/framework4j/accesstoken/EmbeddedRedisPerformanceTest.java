package fun.commons.framework4j.accesstoken;

import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.accesstoken.exception.AuthException;
import fun.commons.framework4j.accesstoken.interceptor.TokenInterceptor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AccessToken 内存Redis性能测试
 *
 * 验证在内存Redis环境下各种操作的性能指标
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("embedded-redis")
class EmbeddedRedisPerformanceTest {

    @Resource
    private AccessTokenGenerator generator;

    @Resource
    private TokenInterceptor interceptor;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HandlerMethod handlerMethod;

    @SpringBootApplication
    static class TestApplication {}

    private static final int WARMUP_COUNT = 1000;
    private static final int TEST_COUNT = 10000;

    @BeforeEach
    void setUp() {
        warmup();
    }

    @AfterEach
    void tearDown() {
        cleanRedisData();
    }

    private void cleanRedisData() {
        // v2.1 P0 修复：补清 access:revoked + refresh:family + refresh:revoked 残留
        String[] patterns = {
                "accesstoken-embedded-test:*",
                "access:revoked:accesstoken-embedded-test",
                "refresh:family:accesstoken-embedded-test:*",
                "refresh:revoked:accesstoken-embedded-test:*"
        };
        for (String pattern : patterns) {
            Set<String> keys = stringRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        }
    }

    private void warmup() {
        // 预热JVM和Redis连接
        for (int i = 0; i < WARMUP_COUNT; i++) {
            try {
                Map<String, Object> claims = Map.of("uid", "warmup-" + i);
                String token = generator.generateToken("WEB", claims);
                if (token != null) {
                    generator.revokeToken(token);
                }
            } catch (Exception e) {
                // 忽略预热错误
            }
        }
    }

    // ==================== Token生成性能测试 ====================

    @Test
    @Tag("performance")
@DisplayName("Token生成性能测试 - 单线程")
    void testTokenGenerationPerformanceSingleThread() {
        Map<String, Object> claims = Map.of(
            "uid", "perf-test-user",
            "role", "user",
            "department", "engineering"
        );

        long startTime = System.nanoTime();
        int successCount = 0;

        for (int i = 0; i < TEST_COUNT; i++) {
            try {
                String token = generator.generateToken("WEB", claims);
                if (token != null && token.length() > 0) {
                    successCount++;
                }
            } catch (Exception e) {
                // 记录错误但不中断测试
            }
        }

        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        double throughput = (double) successCount / (duration / 1_000_000_000.0) * 1000;

        log.info("=== Token生成性能结果 ===");
        log.info("生成数量: " + TEST_COUNT);
        log.info("成功数量: " + successCount);
        log.info("总耗时: " + (duration / 1_000_000) + " ms");
        log.info("吞吐量: " + String.format("%.2f", throughput) + " ops/sec");
        log.info("平均延迟: " + String.format("%.3f", (double) duration / TEST_COUNT / 1_000_000) + " ms");

        assertEquals(TEST_COUNT, successCount, "所有Token都应该生成成功");
        assertTrue(throughput > 1000, "吞吐量应该超过1000 ops/sec");
    }

    @Test
    @Tag("performance")
@DisplayName("Token验证性能测试 - 单线程")
    void testTokenValidationPerformanceSingleThread() throws Exception {
        // 先生成一批Token
        String[] tokens = new String[1000];
        for (int i = 0; i < 1000; i++) {
            tokens[i] = generator.generateToken("WEB", Map.of("uid", "validate-" + i));
        }

        long startTime = System.nanoTime();
        int successCount = 0;

        // 验证每个Token 10次
        for (String token : tokens) {
            for (int j = 0; j < 10; j++) {
                try {
                    if (mockInterceptorCall(token, "WEB")) {
                        successCount++;
                    }
                } catch (Exception e) {
                    // 记录错误但不中断测试
                }
            }
        }

        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        double throughput = (double) successCount / (duration / 1_000_000_000.0) * 1000;

        log.info("=== Token验证性能结果 ===");
        log.info("验证数量: " + (1000 * 10));
        log.info("成功数量: " + successCount);
        log.info("总耗时: " + (duration / 1_000_000) + " ms");
        log.info("吞吐量: " + String.format("%.2f", throughput) + " ops/sec");
        log.info("平均延迟: " + String.format("%.3f", (double) duration / successCount / 1_000_000) + " ms");

        assertEquals(1000 * 10, successCount, "所有验证都应该成功");
        assertTrue(throughput > 5000, "验证吞吐量应该超过5000 ops/sec");
    }

    // ==================== 并发性能测试 ====================

    @Test
    @Tag("performance")
@DisplayName("并发Token生成性能测试")
    void testConcurrentTokenGenerationPerformance() throws InterruptedException {
        int threadCount = 10;
        int tokensPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger totalSuccess = new AtomicInteger(0);
        AtomicLong totalDuration = new AtomicLong(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadIndex = t;
            executor.submit(() -> {
                try {
                    long startTime = System.nanoTime();
                    int successCount = 0;

                    for (int i = 0; i < tokensPerThread; i++) {
                        Map<String, Object> claims = Map.of(
                            "uid", "concurrent-" + threadIndex + "-" + i,
                            "deviceId", "device-" + threadIndex + "-" + i,
                            "threadId", threadIndex
                        );
                        String token = generator.generateToken("APP", claims);
                        if (token != null && token.length() > 0) {
                            successCount++;
                        }
                    }

                    long endTime = System.nanoTime();
                    totalDuration.addAndGet(endTime - startTime);
                    totalSuccess.addAndGet(successCount);

                } finally {
                    latch.countDown();
                }
            });
        }

        // v2.1 P1: 断言 latch.await 返回值，超时则失败
        assertTrue(latch.await(30, TimeUnit.SECONDS), "所有线程应在 30s 内完成");
        executor.shutdown();
        if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
        int totalTokens = threadCount * tokensPerThread;
        double avgThroughput = (double) totalSuccess.get() / (totalDuration.get() / 1_000_000_000.0) * 1000;

        log.info("=== 并发Token生成性能结果 ===");
        log.info("线程数: " + threadCount);
        log.info("每线程Token数: " + tokensPerThread);
        log.info("总Token数: " + totalTokens);
        log.info("成功数量: " + totalSuccess.get());
        log.info("成功率: " + String.format("%.2f%%", (double) totalSuccess.get() / totalTokens * 100));
        log.info("平均吞吐量: " + String.format("%.2f", avgThroughput) + " ops/sec");

        assertEquals(totalTokens, totalSuccess.get(), "所有并发Token都应该生成成功");
    }

    @Test
    @Tag("performance")
@DisplayName("并发Token验证性能测试")
    void testConcurrentTokenValidationPerformance() throws InterruptedException {
        // 先生成一批Token
        String[] tokens = new String[500];
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = generator.generateToken("WEB", Map.of("uid", "concurrent-validate-" + i));
        }

        int threadCount = 20;
        int validationsPerThread = 250;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger totalSuccess = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadIndex = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < validationsPerThread; i++) {
                        int tokenIndex = (threadIndex * validationsPerThread + i) % tokens.length;
                        try {
                            if (mockInterceptorCall(tokens[tokenIndex], "WEB")) {
                                totalSuccess.incrementAndGet();
                            }
                        } catch (Exception e) {
                            // 记录错误但不中断测试
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // v2.1 P1: 断言 latch.await 返回值，超时则失败
        assertTrue(latch.await(30, TimeUnit.SECONDS), "所有线程应在 30s 内完成");
        executor.shutdown();
        if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
        int totalValidations = threadCount * validationsPerThread;
        double successRate = (double) totalSuccess.get() / totalValidations * 100;

        log.info("=== 并发Token验证性能结果 ===");
        log.info("线程数: " + threadCount);
        log.info("每线程验证数: " + validationsPerThread);
        log.info("总验证数: " + totalValidations);
        log.info("成功验证数: " + totalSuccess.get());
        log.info("成功率: " + String.format("%.2f%%", successRate));

        assertTrue(successRate > 95.0, "并发验证成功率应该超过95%");
    }

    // ==================== 内存使用测试 ====================

    @Test
    @Tag("performance")
@DisplayName("内存使用效率测试")
    void testMemoryUsageEfficiency() throws Exception {
        int tokenCount = 10000;

        long beforeMemory = getUsedMemory();

        // 生成大量Token
        String[] tokens = new String[tokenCount];
        for (int i = 0; i < tokenCount; i++) {
            tokens[i] = generator.generateToken("WEB", Map.of(
                "uid", "memory-test-" + i,
                "timestamp", System.currentTimeMillis(),
                "data", "test-data-string-" + i
            ));
        }

        long afterGeneration = getUsedMemory();

        // 验证所有Token
        for (String token : tokens) {
            mockInterceptorCall(token, "WEB");
        }

        long afterValidation = getUsedMemory();

        long generationMemory = afterGeneration - beforeMemory;
        long totalMemory = afterValidation - beforeMemory;
        double avgMemoryPerToken = (double) totalMemory / tokenCount;

        log.info("=== 内存使用效率结果 ===");
        log.info("Token数量: " + tokenCount);
        log.info("生成内存消耗: " + (generationMemory / 1024 / 1024) + " MB");
        log.info("总内存消耗: " + (totalMemory / 1024 / 1024) + " MB");
        log.info("平均每Token: " + String.format("%.2f", avgMemoryPerToken) + " bytes");

        // 内存使用应该在合理范围内
        // 注意：JVM内存测量不够精确，包含了GC、对象对齐等开销
        // 实际Token数据较小，但测量值会包含额外开销
        assertTrue(avgMemoryPerToken < 5 * 1024, "每个Token平均内存使用应该小于5KB（包含JVM开销）");
        assertTrue(totalMemory < 100 * 1024 * 1024, "总内存使用应该小于100MB");
    }

    // ==================== Redis操作性能测试 ====================

    @Test
    @Tag("performance")
@DisplayName("Redis操作性能测试")
    void testRedisOperationPerformance() throws Exception {
        int operationCount = 1000;

        long startTime = System.nanoTime();

        // 生成Token
        String[] tokens = new String[operationCount];
        for (int i = 0; i < operationCount; i++) {
            tokens[i] = generator.generateToken("ADMIN", Map.of("uid", "redis-test-" + i));
        }

        long generationTime = System.nanoTime();

        // 注销所有Token
        for (String token : tokens) {
            generator.revokeToken(token);
        }

        long revocationTime = System.nanoTime();

        // 清理Redis
        cleanRedisData();

        long cleanupTime = System.nanoTime();

        log.info("=== Redis操作性能结果 ===");
        log.info("操作数量: " + operationCount);
        log.info("生成耗时: " + String.format("%.2f", (generationTime - startTime) / 1_000_000.0) + " ms");
        log.info("注销耗时: " + String.format("%.2f", (revocationTime - generationTime) / 1_000_000.0) + " ms");
        log.info("清理耗时: " + String.format("%.2f", (cleanupTime - revocationTime) / 1_000_000.0) + " ms");
        log.info("总耗时: " + String.format("%.2f", (cleanupTime - startTime) / 1_000_000.0) + " ms");

        // Redis操作应该相对快速
        assertTrue((cleanupTime - startTime) / 1_000_000 < 5000, "Redis总操作时间应该小于5秒");
    }

    // ==================== 辅助方法 ====================

    private long getUsedMemory() {
        System.gc(); // 建议垃圾回收
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    private boolean mockInterceptorCall(String token, String requiredType) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Mock注解
        RequiresToken annotation = Mockito.mock(RequiresToken.class);
        Mockito.when(annotation.value()).thenReturn(requiredType);
        Mockito.doReturn(AuthException.class).when(annotation).exception();
        Mockito.when(handlerMethod.getMethodAnnotation(RequiresToken.class)).thenReturn(annotation);

        return interceptor.preHandle(request, response, handlerMethod);
    }
}