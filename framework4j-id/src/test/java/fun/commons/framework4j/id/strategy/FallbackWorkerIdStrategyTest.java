package fun.commons.framework4j.id.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FallbackWorkerIdStrategy 测试（issue #26 fail-soft 降级）
 * <p>覆盖：Redis 连接失败降级 IP Hash、槽位耗尽不降级、降级结果缓存、真实 RedisWorkerIdStrategy 组合、
 * WorkerIdRedisException 向后兼容性。
 */
@DisplayName("FallbackWorkerIdStrategy测试（issue #26）")
class FallbackWorkerIdStrategyTest {

    @Nested
    @DisplayName("降级语义")
    class FallbackSemanticsTest {

        @Test
        @DisplayName("Redis 连接失败（WorkerIdRedisException）应降级 IP Hash 并成功返回")
        void shouldFallbackToIpOnRedisException() {
            WorkerIdStrategy failing = mock(WorkerIdStrategy.class);
            when(failing.getWorkerId()).thenThrow(
                    new WorkerIdRedisException("[ID-SDK] Redis WorkerID 申请失败: connection refused",
                            new RuntimeException("connection refused")));

            FallbackWorkerIdStrategy strategy = new FallbackWorkerIdStrategy(failing, new IpHashWorkerIdStrategy());
            long workerId = strategy.getWorkerId();

            assertThat(workerId).isBetween(0L, 1023L);
        }

        @Test
        @DisplayName("槽位耗尽（普通 IllegalStateException）应原样抛出，不降级")
        void shouldRethrowSlotExhaustion() {
            WorkerIdStrategy failing = mock(WorkerIdStrategy.class);
            when(failing.getWorkerId()).thenThrow(
                    new IllegalStateException("[ID-SDK] CRITICAL: No available WorkerID (0-1023) in Redis"));

            FallbackWorkerIdStrategy strategy = new FallbackWorkerIdStrategy(failing, new IpHashWorkerIdStrategy());

            assertThatThrownBy(strategy::getWorkerId)
                    .isInstanceOf(IllegalStateException.class)
                    .isNotInstanceOf(WorkerIdRedisException.class)
                    .hasMessageContaining("No available WorkerID");
        }

        @Test
        @DisplayName("delegate 成功时直接透传，不触发降级")
        void shouldDelegateWhenHealthy() {
            WorkerIdStrategy healthy = mock(WorkerIdStrategy.class);
            when(healthy.getWorkerId()).thenReturn(42L);

            FallbackWorkerIdStrategy strategy = new FallbackWorkerIdStrategy(healthy, new IpHashWorkerIdStrategy());

            assertThat(strategy.getWorkerId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("降级结果应缓存——后续调用返回同一 id")
        void shouldCacheFallbackResult() {
            WorkerIdStrategy failing = mock(WorkerIdStrategy.class);
            when(failing.getWorkerId()).thenThrow(
                    new WorkerIdRedisException("boom", new RuntimeException("boom")));

            FallbackWorkerIdStrategy strategy = new FallbackWorkerIdStrategy(failing, new IpHashWorkerIdStrategy());
            long first = strategy.getWorkerId();
            long second = strategy.getWorkerId();

            assertThat(second).isEqualTo(first);
        }

        @Test
        @DisplayName("真实 RedisWorkerIdStrategy + Redis 连接异常 → 自动降级为 IP Hash")
        void shouldWorkWithRealRedisStrategy() {
            StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenThrow(new RuntimeException("connection refused"));
            RedisWorkerIdStrategy redis = new RedisWorkerIdStrategy(redisTemplate, "fallback-test-app");

            FallbackWorkerIdStrategy strategy = new FallbackWorkerIdStrategy(redis, new IpHashWorkerIdStrategy());

            assertThat(strategy.getWorkerId()).isBetween(0L, 1023L);
        }

        @Test
        @DisplayName("WorkerIdRedisException 应是 IllegalStateException 子类（向后兼容）")
        void workerIdRedisExceptionShouldRemainIllegalState() {
            assertThat(new WorkerIdRedisException("x", new RuntimeException("y")))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("真实 Redis 租约联动（复用既有 mock 模式）")
    class RealRedisLeaseTest {

        @Test
        @DisplayName("Redis 恢复场景：delegate 正常返回租约 id，降级分支不介入")
        void shouldUseDelegateWhenRedisRecovers() {
            StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(7L);
            RedisWorkerIdStrategy redis = new RedisWorkerIdStrategy(redisTemplate, "recovered-app");

            FallbackWorkerIdStrategy strategy = new FallbackWorkerIdStrategy(redis, new IpHashWorkerIdStrategy());

            assertThat(strategy.getWorkerId()).isEqualTo(7L);
        }
    }
}
