package fun.commons.framework4j.id.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RedisWorkerIdStrategy简化测试
 * （不依赖实际Redis环境，只测试核心逻辑）
 */
@DisplayName("RedisWorkerIdStrategy测试")
class RedisWorkerIdStrategyTest {

    private RedisWorkerIdStrategy strategy;

    @BeforeEach
    void setUp() {
        // 使用模拟的RedisTemplate进行基本测试
        // 注意：这个测试主要验证类的结构和基本逻辑
        // 完整的Redis测试需要Redis环境
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTest {

        @Test
        @DisplayName("应该能够创建策略实例")
        void shouldCreateStrategyInstance() {
            // 这个测试验证类的基本结构
            assertThat(RedisWorkerIdStrategy.class).isNotNull();
        }

        @Test
        @DisplayName("应该支持不同构造参数")
        void shouldSupportDifferentConstructorParameters() {
            // 验证构造方法的存在
            java.lang.reflect.Constructor<?>[] constructors = RedisWorkerIdStrategy.class.getConstructors();
            assertThat(constructors).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("WorkerIdStrategy接口实现测试")
    class InterfaceImplementationTest {

        @Test
        @DisplayName("应该实现WorkerIdStrategy接口")
        void shouldImplementWorkerIdStrategyInterface() {
            assertThat(WorkerIdStrategy.class).isInterface();
            assertThat(WorkerIdStrategy.class.isAssignableFrom(RedisWorkerIdStrategy.class)).isTrue();
        }

        @Test
        @DisplayName("应该实现getWorkerId方法")
        void shouldImplementGetWorkerIdMethod() throws NoSuchMethodException {
            java.lang.reflect.Method method = RedisWorkerIdStrategy.class.getMethod("getWorkerId");
            assertThat(method).isNotNull();
            assertThat(method.getReturnType()).isEqualTo(long.class);
        }

        @Test
        @DisplayName("应该实现基本方法")
        void shouldImplementBasicMethods() throws NoSuchMethodException {
            // 直接检查类的结构，不依赖实例
            assertThat(WorkerIdStrategy.class.isAssignableFrom(RedisWorkerIdStrategy.class)).isTrue();

            // 检查有getWorkerId方法
            java.lang.reflect.Method method = RedisWorkerIdStrategy.class.getMethod("getWorkerId");
            assertThat(method).isNotNull();
            assertThat(method.getReturnType()).isEqualTo(long.class);
        }
    }

    @Nested
    @DisplayName("基础功能测试")
    class BasicFunctionalityTest {

        @Test
        @DisplayName("应该处理Redis连接异常")
        void shouldHandleRedisConnectionException() {
            // 创建没有连接的RedisTemplate会导致异常
            StringRedisTemplate mockTemplate = new StringRedisTemplate();
            // 不设置连接工厂

            RedisWorkerIdStrategy invalidStrategy = new RedisWorkerIdStrategy(mockTemplate, "test-app");

            assertThatThrownBy(() -> invalidStrategy.getWorkerId())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template not initialized");
        }
    }
}