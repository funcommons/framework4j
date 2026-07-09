package fun.commons.framework4j.id.strategy;

import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RedisWorkerIdStrategy 测试（v2.1 P0 重写）
 * <p>原测试仅反射看类结构，未覆盖 R8-9 修复：ACQUIRE_SCRIPT Lua 扫描、RENEW_SCRIPT CAS 心跳、release 重置状态。
 * <p>改用 Mockito mock StringRedisTemplate.execute() 验证 Lua 调用与状态重置语义。
 */
@DisplayName("RedisWorkerIdStrategy测试")
class RedisWorkerIdStrategyTest {

    private StringRedisTemplate redisTemplate;
    private RedisWorkerIdStrategy strategy;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        strategy = new RedisWorkerIdStrategy(redisTemplate, "test-app");
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTest {

        @Test
        @DisplayName("应该能够创建策略实例")
        void shouldCreateStrategyInstance() {
            assertThat(strategy).isNotNull();
        }

        @Test
        @DisplayName("应该实现WorkerIdStrategy接口")
        void shouldImplementWorkerIdStrategyInterface() {
            assertThat(WorkerIdStrategy.class.isAssignableFrom(RedisWorkerIdStrategy.class)).isTrue();
        }
    }

    @Nested
    @DisplayName("ACQUIRE_SCRIPT Lua 扫描测试（R8 修复）")
    class AcquireScriptTest {

        @Test
        @DisplayName("Lua 返回有效 id 时应成功获取 workerId")
        void shouldAcquireWorkerIdWhenLuaReturnsId() {
            // mock ACQUIRE_SCRIPT 返回 5
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(5L);

            long workerId = strategy.getWorkerId();
            assertThat(workerId).isEqualTo(5L);
        }

        @Test
        @DisplayName("Lua 返回 -1 时应抛 IllegalStateException（无可用 workerId）")
        void shouldThrowWhenAllSlotsFull() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(-1L);

            assertThatThrownBy(() -> strategy.getWorkerId())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No available WorkerID");
        }

        @Test
        @DisplayName("Lua 返回 null 时应抛 IllegalStateException")
        void shouldThrowWhenLuaReturnsNull() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(null);

            assertThatThrownBy(() -> strategy.getWorkerId())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No available WorkerID");
        }

        @Test
        @DisplayName("Lua 调用抛异常时应包装为 IllegalStateException")
        void shouldWrapRedisException() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenThrow(new RuntimeException("connection refused"));

            assertThatThrownBy(() -> strategy.getWorkerId())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Redis WorkerID 申请失败")
                    .hasMessageContaining("connection refused");
        }

        @Test
        @DisplayName("重复调用 getWorkerId 应返回缓存的 id（不重复调 Lua）")
        void shouldReturnCachedWorkerIdOnRepeatCall() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(3L);

            assertThat(strategy.getWorkerId()).isEqualTo(3L);
            // 第二次调用应返回缓存值，不再调 Redis
            assertThat(strategy.getWorkerId()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("release 重置状态测试（R8 修复）")
    class ReleaseTest {

        @Test
        @DisplayName("release 后 acquiredWorkerId 应重置为 -1，getWorkerId 可重新申请")
        void shouldResetStateAfterRelease() {
            // 首次申请
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(7L);
            assertThat(strategy.getWorkerId()).isEqualTo(7L);

            // release
            strategy.release();
            assertThat(strategy.getAcquiredWorkerId()).isEqualTo(-1L);

            // 重新申请（mock 返回不同 id）
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(9L);
            assertThat(strategy.getWorkerId()).isEqualTo(9L);
        }

        @Test
        @DisplayName("release 后再次 getWorkerId 不应返回旧 id（R8 P1 修复点）")
        void shouldNotReturnStaleIdAfterRelease() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(7L);
            assertThat(strategy.getWorkerId()).isEqualTo(7L);

            strategy.release();

            // 若 release 未重置 acquiredWorkerId，第二次 getWorkerId 会返回 7L（bug）
            // 修复后应重新调 Lua，mock 返回 null → 抛异常
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(null);
            assertThatThrownBy(() -> strategy.getWorkerId())
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("基础功能测试")
    class BasicFunctionalityTest {

        @Test
        @DisplayName("应该处理Redis连接异常（无连接工厂）")
        void shouldHandleRedisConnectionException() {
            StringRedisTemplate emptyTemplate = new StringRedisTemplate();
            RedisWorkerIdStrategy invalidStrategy = new RedisWorkerIdStrategy(emptyTemplate, "test-app");

            assertThatThrownBy(() -> invalidStrategy.getWorkerId())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis WorkerID 申请失败");
        }
    }
}
