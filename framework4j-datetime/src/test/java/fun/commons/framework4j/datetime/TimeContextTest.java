package fun.commons.framework4j.datetime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TimeContext 测试
 * 验证时间上下文管理器的线程安全性和功能正确性
 */
@DisplayName("TimeContext 测试")
class TimeContextTest {

    @Nested
    @DisplayName("基本功能测试")
    class BasicFunctionalityTest {

        @Test
        @DisplayName("默认情况下isUseLocal应该返回false")
        void shouldReturnFalseByDefault() {
            // When
            boolean useLocal = TimeContext.isUseLocal();

            // Then
            assertThat(useLocal).isFalse();
        }

        @Test
        @DisplayName("setUseLocal(true)后isUseLocal应该返回true")
        void shouldReturnTrueAfterSettingUseLocalTrue() {
            // When
            TimeContext.setUseLocal(true);
            boolean useLocal = TimeContext.isUseLocal();

            // Then
            assertThat(useLocal).isTrue();

            // Cleanup
            TimeContext.clear();
        }

        @Test
        @DisplayName("setUseLocal(false)后isUseLocal应该返回false")
        void shouldReturnFalseAfterSettingUseLocalFalse() {
            // When
            TimeContext.setUseLocal(false);
            boolean useLocal = TimeContext.isUseLocal();

            // Then
            assertThat(useLocal).isFalse();

            // Cleanup
            TimeContext.clear();
        }

        @Test
        @DisplayName("clear后isUseLocal应该返回false")
        void shouldReturnFalseAfterClear() {
            // Given
            TimeContext.setUseLocal(true);
            assertThat(TimeContext.isUseLocal()).isTrue();

            // When
            TimeContext.clear();

            // Then
            assertThat(TimeContext.isUseLocal()).isFalse();
        }
    }

    @Nested
    @DisplayName("线程安全性测试")
    @Execution(ExecutionMode.CONCURRENT)
    class ThreadSafetyTest {

        @Test
        @DisplayName("多线程环境下每个线程应该有独立的上下文")
        void eachThreadShouldHaveIndependentContext() throws InterruptedException {
            final int threadCount = 5;
            Thread[] threads = new Thread[threadCount];
            boolean[] results = new boolean[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                threads[i] = new Thread(() -> {
                    // 每个线程设置不同的值
                    TimeContext.setUseLocal(threadIndex % 2 == 0);
                    // 稍微等待以确保并发
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    results[threadIndex] = TimeContext.isUseLocal();
                    TimeContext.clear();
                });
            }

            // 启动所有线程
            for (Thread thread : threads) {
                thread.start();
            }

            // 等待所有线程完成
            for (Thread thread : threads) {
                thread.join();
            }

            // 验证每个线程都保持了独立的上下文
            for (int i = 0; i < threadCount; i++) {
                assertThat(results[i]).isEqualTo(i % 2 == 0);
            }
        }

        @Test
        @DisplayName("并发设置和读取不应该互相干扰")
        void concurrentSettingAndReadingShouldNotInterfere() throws InterruptedException {
            final int iterations = 1000;
            // v2.1 P1: 收集子线程异常，避免 AssertionError 被吞导致假通过
            java.util.concurrent.atomic.AtomicReference<Throwable> firstError = new java.util.concurrent.atomic.AtomicReference<>();
            Thread[] threads = new Thread[2];

            threads[0] = new Thread(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        TimeContext.setUseLocal(true);
                        assertThat(TimeContext.isUseLocal()).isTrue();
                        TimeContext.clear();
                        assertThat(TimeContext.isUseLocal()).isFalse();
                    }
                } catch (Throwable e) {
                    firstError.compareAndSet(null, e);
                }
            });

            threads[1] = new Thread(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        TimeContext.setUseLocal(false);
                        assertThat(TimeContext.isUseLocal()).isFalse();
                        TimeContext.clear();
                        assertThat(TimeContext.isUseLocal()).isFalse();
                    }
                } catch (Throwable e) {
                    firstError.compareAndSet(null, e);
                }
            });

            // 同时启动两个线程
            threads[0].start();
            threads[1].start();

            // 等待完成
            threads[0].join();
            threads[1].join();

            // v2.1 P1: 子线程异常应传播到主线程
            if (firstError.get() != null) {
                throw new AssertionError("子线程断言失败: " + firstError.get().getMessage(), firstError.get());
            }
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTest {

        @Test
        @DisplayName("多次调用setUseLocal应该正确覆盖之前的值")
        void multipleSetCallsShouldOverridePreviousValue() {
            // When
            TimeContext.setUseLocal(true);
            assertThat(TimeContext.isUseLocal()).isTrue();

            TimeContext.setUseLocal(false);
            assertThat(TimeContext.isUseLocal()).isFalse();

            TimeContext.setUseLocal(true);
            assertThat(TimeContext.isUseLocal()).isTrue();

            // Cleanup
            TimeContext.clear();
        }

        @Test
        @DisplayName("clear后再次设置应该正常工作")
        void shouldWorkNormallyAfterClearAndSetAgain() {
            // Given
            TimeContext.setUseLocal(true);
            TimeContext.clear();
            assertThat(TimeContext.isUseLocal()).isFalse();

            // When
            TimeContext.setUseLocal(true);

            // Then
            assertThat(TimeContext.isUseLocal()).isTrue();

            // Cleanup
            TimeContext.clear();
        }

        @Test
        @DisplayName("clear没有设置过的线程不应该抛出异常")
        void clearOnUnsetThreadShouldNotThrowException() {
            // When & Then - 应该不抛出异常
            TimeContext.clear();
            assertThat(TimeContext.isUseLocal()).isFalse();
        }

        @Test
        @DisplayName("同一个线程内多次clear应该是安全的")
        void multipleClearCallsShouldBeSafe() {
            // Given
            TimeContext.setUseLocal(true);

            // When & Then - 多次clear不应该抛出异常
            TimeContext.clear();
            TimeContext.clear();
            TimeContext.clear();

            assertThat(TimeContext.isUseLocal()).isFalse();
        }
    }

    @Nested
    @DisplayName("内存泄漏防护测试")
    class MemoryLeakProtectionTest {

        @Test
        @DisplayName("clear应该移除ThreadLocal值防止内存泄漏")
        void clearShouldRemoveThreadLocalValue() {
            // Given
            TimeContext.setUseLocal(true);
            assertThat(TimeContext.isUseLocal()).isTrue();

            // When
            TimeContext.clear();

            // Then
            assertThat(TimeContext.isUseLocal()).isFalse();
            // 注意：我们无法直接测试ThreadLocal是否真的被清除，但可以通过行为间接验证
        }

        @Test
        @DisplayName("大量线程操作后clear应该正常工作")
        void clearShouldWorkCorrectlyAfterManyThreadsOperations() throws InterruptedException {
            final int threadCount = 50;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    TimeContext.setUseLocal(true);
                    TimeContext.clear();
                });
            }

            // 启动所有线程
            for (Thread thread : threads) {
                thread.start();
            }

            // 等待所有线程完成
            for (Thread thread : threads) {
                thread.join();
            }

            // 验证当前线程的状态
            assertThat(TimeContext.isUseLocal()).isFalse();
        }
    }

    @Nested
    @DisplayName("实际使用场景测试")
    class RealWorldScenarioTest {

        @Test
        @DisplayName("应该支持Web请求的生命周期管理")
        void shouldSupportWebRequestLifecycle() {
            // 模拟Web请求的开始
            TimeContext.setUseLocal(true);
            assertThat(TimeContext.isUseLocal()).isTrue();

            // 模拟Web请求的处理
            // 在这个过程中，所有时间格式化都应该使用本地格式
            assertThat(TimeContext.isUseLocal()).isTrue();

            // 模拟Web请求的结束
            TimeContext.clear();
            assertThat(TimeContext.isUseLocal()).isFalse();
        }

        @Test
        @DisplayName("应该支持异步任务的时间格式化")
        void shouldSupportAsyncTaskTimeFormatting() {
            // 主线程设置
            TimeContext.setUseLocal(false);

            // 异步任务
            Thread asyncTask = new Thread(() -> {
                // 异步线程应该有独立的上下文
                assertThat(TimeContext.isUseLocal()).isFalse(); // 默认值

                TimeContext.setUseLocal(true);
                assertThat(TimeContext.isUseLocal()).isTrue();

                TimeContext.clear();
            });

            asyncTask.start();
            try {
                asyncTask.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 主线程的上下文应该不受影响
            assertThat(TimeContext.isUseLocal()).isFalse();

            // Cleanup
            TimeContext.clear();
        }
    }

    // ========== 测试辅助类 ==========

    static class TestObject {
        private java.time.OffsetDateTime time;

        public java.time.OffsetDateTime getTime() {
            return time;
        }

        public void setTime(java.time.OffsetDateTime time) {
            this.time = time;
        }
    }
}