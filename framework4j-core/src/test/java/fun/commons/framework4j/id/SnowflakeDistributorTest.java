package fun.commons.framework4j.id;

import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * 雪花算法分发器测试
 */
@DisplayName("SnowflakeDistributor 测试")
class SnowflakeDistributorTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("有效 WorkerID 范围 (0-1023)")
        void shouldAcceptValidWorkerIds() {
            // 边界值测试
            assertThatCode(() -> new SnowflakeDistributor(0)).doesNotThrowAnyException();
            assertThatCode(() -> new SnowflakeDistributor(1023)).doesNotThrowAnyException();
            assertThatCode(() -> new SnowflakeDistributor(512)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(longs = {-1, -100, 1024, 2000, Long.MAX_VALUE})
        @DisplayName("无效 WorkerID 应抛出异常")
        void shouldRejectInvalidWorkerIds(long invalidId) {
            assertThatThrownBy(() -> new SnowflakeDistributor(invalidId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("WorkerID must be between 0 and 1023");
        }

        @Test
        @DisplayName("WorkerID 正确拆解为 Hutool workerId/datacenterId")
        void shouldCorrectlySplitWorkerId() {
            // sdkWorkerId = 0 -> workerId=0, datacenterId=0
            SnowflakeDistributor dist0 = new SnowflakeDistributor(0);
            assertThat(dist0.getHutoolWorkerId()).isZero();
            assertThat(dist0.getHutoolDatacenterId()).isZero();

            // sdkWorkerId = 31 -> workerId=31, datacenterId=0
            SnowflakeDistributor dist31 = new SnowflakeDistributor(31);
            assertThat(dist31.getHutoolWorkerId()).isEqualTo(31);
            assertThat(dist31.getHutoolDatacenterId()).isZero();

            // sdkWorkerId = 32 -> workerId=0, datacenterId=1
            SnowflakeDistributor dist32 = new SnowflakeDistributor(32);
            assertThat(dist32.getHutoolWorkerId()).isZero();
            assertThat(dist32.getHutoolDatacenterId()).isEqualTo(1);

            // sdkWorkerId = 1023 -> workerId=31, datacenterId=31
            SnowflakeDistributor dist1023 = new SnowflakeDistributor(1023);
            assertThat(dist1023.getHutoolWorkerId()).isEqualTo(31);
            assertThat(dist1023.getHutoolDatacenterId()).isEqualTo(31);

            // sdkWorkerId = 500 -> 500 = 15*32 + 20 -> workerId=20, datacenterId=15
            SnowflakeDistributor dist500 = new SnowflakeDistributor(500);
            assertThat(dist500.getHutoolWorkerId()).isEqualTo(500 & 31); // 20
            assertThat(dist500.getHutoolDatacenterId()).isEqualTo(500 >> 5); // 15
        }
    }

    @Nested
    @DisplayName("ID 生成测试")
    class IdGenerationTests {

        @Test
        @DisplayName("生成的 ID 应为正数")
        void shouldGeneratePositiveIds() {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);
            for (int i = 0; i < 1000; i++) {
                assertThat(distributor.nextId()).isPositive();
            }
        }

        @Test
        @DisplayName("生成的 ID 应单调递增")
        void shouldGenerateMonotonicallyIncreasingIds() {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);
            long lastId = 0;
            for (int i = 0; i < 10000; i++) {
                long currentId = distributor.nextId();
                assertThat(currentId).isGreaterThan(lastId);
                lastId = currentId;
            }
        }

        @Test
        @DisplayName("批量生成 ID 应唯一")
        void shouldGenerateUniqueIds() {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);
            Set<Long> ids = new HashSet<>();
            int count = 100000;

            for (int i = 0; i < count; i++) {
                ids.add(distributor.nextId());
            }

            assertThat(ids).hasSize(count);
        }

        @Test
        @DisplayName("nextIds 批量生成正确数量")
        void shouldGenerateCorrectBatchCount() {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);

            List<Long> ids10 = distributor.nextIds(10);
            assertThat(ids10).hasSize(10);

            List<Long> ids1000 = distributor.nextIds(1000);
            assertThat(ids1000).hasSize(1000);
        }

        @Test
        @DisplayName("nextIds 参数校验")
        void shouldValidateBatchCount() {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);

            assertThatThrownBy(() -> distributor.nextIds(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Count must be positive");

            assertThatThrownBy(() -> distributor.nextIds(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("并发测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("多线程并发生成 ID 唯一性")
        void shouldGenerateUniqueIdsUnderConcurrency() throws InterruptedException {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);
            int threadCount = 10;
            int idsPerThread = 10000;
            Set<Long> allIds = ConcurrentHashMap.newKeySet();
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < idsPerThread; j++) {
                            allIds.add(distributor.nextId());
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(allIds).hasSize(threadCount * idsPerThread);
        }

        @Test
        @DisplayName("高并发压力测试")
        void shouldHandleHighConcurrency() throws InterruptedException {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);
            int threadCount = 50;
            int idsPerThread = 5000;
            Set<Long> allIds = ConcurrentHashMap.newKeySet();
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < idsPerThread; j++) {
                            allIds.add(distributor.nextId());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // 同时启动所有线程
            endLatch.await(60, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(allIds).hasSize(threadCount * idsPerThread);
        }
    }

    @Nested
    @DisplayName("Getter 方法测试")
    class GetterTests {

        @Test
        @DisplayName("getSdkWorkerId 返回正确值")
        void shouldReturnCorrectSdkWorkerId() {
            for (long id = 0; id <= 1023; id += 100) {
                SnowflakeDistributor distributor = new SnowflakeDistributor(id);
                assertThat(distributor.getSdkWorkerId()).isEqualTo(id);
            }
        }

        @Test
        @DisplayName("Epoch 常量正确")
        void shouldHaveCorrectEpoch() {
            // 2024-01-01 00:00:00 UTC+8 = 1704067200000L
            assertThat(SnowflakeDistributor.HARDCODED_EPOCH).isEqualTo(1704067200000L);
        }
    }

    @Nested
    @DisplayName("性能测试")
    class PerformanceTests {

        @Test
        @DisplayName("单线程 QPS 测试")
        void shouldMeetPerformanceRequirements() {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);
            int warmup = 10000;
            int count = 100000;

            // 预热
            for (int i = 0; i < warmup; i++) {
                distributor.nextId();
            }

            // 计时
            long start = System.nanoTime();
            for (int i = 0; i < count; i++) {
                distributor.nextId();
            }
            long end = System.nanoTime();

            double durationMs = (end - start) / 1_000_000.0;
            double qps = count / (durationMs / 1000);

            System.out.printf("Generated %d IDs in %.2f ms (%.0f QPS)%n", count, durationMs, qps);

            // 单线程应该轻松达到 100k+ QPS
            assertThat(qps).isGreaterThan(50000);
        }
    }
}
