package fun.commons.framework4j.redis.performance;

import fun.commons.framework4j.redis.manager.MultiRedisManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis 性能测试
 *
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = {RedisPerformanceTest.TestConfiguration.class})
@ActiveProfiles("test")
@Tag("performance")
@DisplayName("Redis 性能测试")
@TestPropertySource(locations = "classpath:application-test.yml")
class RedisPerformanceTest {

    @Autowired
    private MultiRedisManager multiRedisManager;

    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate = multiRedisManager.getStringRedisTemplate("default");
    }

    // v2.1 P0: 测试结束清理 perf:* key，防止跨测试/跨运行残留污染 Redis
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        Set<String> keys = redisTemplate.keys("perf:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @Tag("performance")
@DisplayName("性能测试 - 单线程写入")
    void testSingleThreadWrite() {
        int operationCount = 10000;
        PerformanceResult result = new PerformanceResult("单线程写入");

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < operationCount; i++) {
            String key = "perf:write:" + i;
            String value = "value-" + i;

            try {
                redisTemplate.opsForValue().set(key, value);
                result.incrementSuccess();
            } catch (Exception e) {
                result.incrementFailure();
                log.error("写入失败: key={}", key, e);
            }
        }

        long endTime = System.currentTimeMillis();
        result.setDuration(endTime - startTime);
        result.setTotalOperations(operationCount);

        // v2.1 P0: 性能测试必须验证功能正确性，仅 printResult 不够
        assertThat(result.getSuccessCount().longValue())
                .as("所有操作应成功，实际失败: %d", result.getFailureCount().longValue())
                .isEqualTo((long) operationCount);
        printResult(result);
    }

    @Test
    @Tag("performance")
@DisplayName("性能测试 - 单线程读取")
    void testSingleThreadRead() {
        // 准备测试数据
        int operationCount = 10000;
        for (int i = 0; i < operationCount; i++) {
            String key = "perf:read:" + i;
            String value = "value-" + i;
            redisTemplate.opsForValue().set(key, value);
        }

        PerformanceResult result = new PerformanceResult("单线程读取");
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < operationCount; i++) {
            String key = "perf:read:" + i;

            try {
                String value = redisTemplate.opsForValue().get(key);
                if (value != null) {
                    result.incrementSuccess();
                } else {
                    result.incrementFailure();
                }
            } catch (Exception e) {
                result.incrementFailure();
                log.error("读取失败: key={}", key, e);
            }
        }

        long endTime = System.currentTimeMillis();
        result.setDuration(endTime - startTime);
        result.setTotalOperations(operationCount);

        // v2.1 P0: 性能测试必须验证功能正确性，仅 printResult 不够
        assertThat(result.getSuccessCount().longValue())
                .as("所有操作应成功，实际失败: %d", result.getFailureCount().longValue())
                .isEqualTo((long) operationCount);
        printResult(result);
    }

    @Test
    @Tag("performance")
@DisplayName("性能测试 - 多线程并发写入")
    void testMultiThreadWrite() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 1000;
        int totalOperations = threadCount * operationsPerThread;

        PerformanceResult result = new PerformanceResult("多线程并发写入");
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.currentTimeMillis();

        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String key = "perf:concurrent:write:" + threadId + ":" + i;
                        String value = "value-" + threadId + "-" + i;

                        try {
                            redisTemplate.opsForValue().set(key, value);
                            result.incrementSuccess();
                        } catch (Exception e) {
                            result.incrementFailure();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // v2.1 P0: 断言 latch.await 返回值 + 加功能断言（成功 == 总操作数）
        assertTrue(latch.await(60, TimeUnit.SECONDS), "所有线程应在 60s 内完成");
        executor.shutdown();

        long endTime = System.currentTimeMillis();
        result.setDuration(endTime - startTime);
        result.setTotalOperations(totalOperations);

        // v2.1 P0: 性能测试必须验证功能正确性，仅 printResult 不够
        assertThat(result.getSuccessCount().longValue())
                .as("所有操作应成功，实际失败: %d", result.getFailureCount().longValue())
                .isEqualTo((long) totalOperations);
        printResult(result);
    }

    @Test
    @Tag("performance")
@DisplayName("性能测试 - 多线程并发读取")
    void testMultiThreadRead() throws InterruptedException {
        // 准备测试数据
        int threadCount = 10;
        int operationsPerThread = 1000;
        for (int t = 0; t < threadCount; t++) {
            for (int i = 0; i < operationsPerThread; i++) {
                String key = "perf:concurrent:read:" + t + ":" + i;
                String value = "value-" + t + "-" + i;
                redisTemplate.opsForValue().set(key, value);
            }
        }

        int totalOperations = threadCount * operationsPerThread;
        PerformanceResult result = new PerformanceResult("多线程并发读取");
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.currentTimeMillis();

        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String key = "perf:concurrent:read:" + threadId + ":" + i;

                        try {
                            String value = redisTemplate.opsForValue().get(key);
                            if (value != null) {
                                result.incrementSuccess();
                            } else {
                                result.incrementFailure();
                            }
                        } catch (Exception e) {
                            result.incrementFailure();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // v2.1 P0: 断言 latch.await 返回值 + 加功能断言（成功 == 总操作数）
        assertTrue(latch.await(60, TimeUnit.SECONDS), "所有线程应在 60s 内完成");
        executor.shutdown();

        long endTime = System.currentTimeMillis();
        result.setDuration(endTime - startTime);
        result.setTotalOperations(totalOperations);

        // v2.1 P0: 性能测试必须验证功能正确性，仅 printResult 不够
        assertThat(result.getSuccessCount().longValue())
                .as("所有操作应成功，实际失败: %d", result.getFailureCount().longValue())
                .isEqualTo((long) totalOperations);
        printResult(result);
    }

    @Test
    @Tag("performance")
@DisplayName("性能测试 - Pipeline 批量操作")
    void testPipelineBatch() {
        int batchSize = 1000;
        int batchCount = 10;
        int totalOperations = batchSize * batchCount;

        PerformanceResult result = new PerformanceResult("Pipeline 批量操作");
        long startTime = System.currentTimeMillis();

        for (int batch = 0; batch < batchCount; batch++) {
            final int batchId = batch;
            try {
                redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<?>) connection -> {
                    for (int i = 0; i < batchSize; i++) {
                        String key = "perf:pipeline:" + batchId + ":" + i;
                        String value = "value-" + batchId + "-" + i;
                        connection.stringCommands().set(key.getBytes(), value.getBytes());
                    }
                    return null;
                });
                result.incrementSuccess(batchSize);
            } catch (Exception e) {
                result.incrementFailure(batchSize);
                log.error("Pipeline 批量操作失败: batch={}", batch, e);
            }
        }

        long endTime = System.currentTimeMillis();
        result.setDuration(endTime - startTime);
        result.setTotalOperations(totalOperations);

        // v2.1 P0: 性能测试必须验证功能正确性，仅 printResult 不够
        assertThat(result.getSuccessCount().longValue())
                .as("Pipeline 所有批次应成功，实际失败: %d", result.getFailureCount().longValue())
                .isEqualTo((long) totalOperations);
        printResult(result);
    }

    @Test
    @Tag("performance")
@DisplayName("性能测试 - 延迟测试（P99）")
    void testLatency() {
        int operationCount = 1000;
        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < operationCount; i++) {
            String key = "perf:latency:" + i;
            String value = "value-" + i;

            long startTime = System.nanoTime();
            try {
                redisTemplate.opsForValue().set(key, value);
            } catch (Exception e) {
                log.error("操作失败", e);
            }
            long endTime = System.nanoTime();

            long latencyNanos = endTime - startTime;
            latencies.add(latencyNanos);
        }

        // 计算延迟统计
        latencies.sort(Long::compareTo);
        long p50 = latencies.get((int) (operationCount * 0.50));
        long p95 = latencies.get((int) (operationCount * 0.95));
        long p99 = latencies.get((int) (operationCount * 0.99));
        long max = latencies.get(operationCount - 1);

        log.info("=== 延迟测试结果 ===");
        log.info("P50: {} ms", p50 / 1_000_000.0);
        log.info("P95: {} ms", p95 / 1_000_000.0);
        log.info("P99: {} ms", p99 / 1_000_000.0);
        log.info("MAX: {} ms", max / 1_000_000.0);
    }

    /**
     * 打印性能测试结果
     */
    private void printResult(PerformanceResult result) {
        log.info("=== {} 性能测试结果 ===", result.getTestName());
        log.info("总操作数: {}", result.getTotalOperations());
        log.info("成功操作数: {}", result.getSuccessCount());
        log.info("失败操作数: {}", result.getFailureCount());
        log.info("耗时: {} ms", result.getDuration());
        log.info("QPS: {}", result.getQps());
        log.info("平均延迟: {} ms", result.getAvgLatency());
        log.info("====================================");
    }

    /**
     * 性能测试结果
     */
    @lombok.Getter
    @lombok.Setter
    static class PerformanceResult {
        private String testName;
        private int totalOperations;
        private AtomicLong successCount = new AtomicLong(0);
        private AtomicLong failureCount = new AtomicLong(0);
        private long duration; // 毫秒

        public PerformanceResult(String testName) {
            this.testName = testName;
        }

        public void incrementSuccess() {
            successCount.incrementAndGet();
        }

        public void incrementSuccess(int count) {
            successCount.addAndGet(count);
        }

        public void incrementFailure() {
            failureCount.incrementAndGet();
        }

        public void incrementFailure(int count) {
            failureCount.addAndGet(count);
        }

        public long getQps() {
            if (duration == 0) return 0;
            return (long) ((double) totalOperations / duration * 1000);
        }

        public double getAvgLatency() {
            if (totalOperations == 0) return 0;
            return (double) duration / totalOperations;
        }
    }

    /**
     * 测试配置类
     */
    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class TestConfiguration {
    }
}
