package fun.commons.framework4j.id;

import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import fun.commons.framework4j.id.strategy.IpHashWorkerIdStrategy;
import fun.commons.framework4j.id.util.IdAnalysisTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * IP Hash WorkerID 策略测试
 */
@DisplayName("IpHashWorkerIdStrategy 测试")
class IpHashWorkerIdStrategyTest {

    @Nested
    @DisplayName("getWorkerId 测试")
    class GetWorkerIdTests {

        @Test
        @DisplayName("返回有效范围内的 WorkerID")
        void shouldReturnValidWorkerId() {
            IpHashWorkerIdStrategy strategy = new IpHashWorkerIdStrategy();
            long workerId = strategy.getWorkerId();

            assertThat(workerId).isBetween(0L, 1023L);
        }

        @Test
        @DisplayName("多次调用返回相同值 (缓存)")
        void shouldReturnCachedValue() {
            IpHashWorkerIdStrategy strategy = new IpHashWorkerIdStrategy();

            long firstCall = strategy.getWorkerId();
            long secondCall = strategy.getWorkerId();
            long thirdCall = strategy.getWorkerId();

            assertThat(firstCall).isEqualTo(secondCall).isEqualTo(thirdCall);
        }

        @Test
        @DisplayName("同一实例始终返回相同 WorkerID")
        void shouldBeConsistentForSameInstance() {
            IpHashWorkerIdStrategy strategy = new IpHashWorkerIdStrategy();

            for (int i = 0; i < 100; i++) {
                long workerId = strategy.getWorkerId();
                assertThat(workerId).isBetween(0L, 1023L);
            }
        }
    }

    @Nested
    @DisplayName("线程安全测试")
    class ThreadSafetyTests {

        @Test
        @DisplayName("多线程并发获取 WorkerID")
        void shouldBeThreadSafe() throws InterruptedException {
            IpHashWorkerIdStrategy strategy = new IpHashWorkerIdStrategy();
            int threadCount = 10;
            long[] results = new long[threadCount];
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    results[index] = strategy.getWorkerId();
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // 所有线程应该获得相同的 WorkerID
            long expectedId = results[0];
            for (long result : results) {
                assertThat(result).isEqualTo(expectedId);
            }
        }
    }

    @Nested
    @DisplayName("与 SnowflakeDistributor 集成测试")
    class IntegrationTests {

        @Test
        @DisplayName("可正常用于 SnowflakeDistributor")
        void shouldWorkWithSnowflakeDistributor() {
            IpHashWorkerIdStrategy strategy = new IpHashWorkerIdStrategy();
            long workerId = strategy.getWorkerId();

            SnowflakeDistributor distributor = new SnowflakeDistributor(workerId);
            long id = distributor.nextId();

            assertThat(id).isPositive();

            // 从 ID 中提取的 WorkerID 应该匹配
            long extractedSdkWorkerId = IdAnalysisTool.extractSdkWorkerId(id);
            assertThat(extractedSdkWorkerId).isEqualTo(workerId);
        }
    }
}
