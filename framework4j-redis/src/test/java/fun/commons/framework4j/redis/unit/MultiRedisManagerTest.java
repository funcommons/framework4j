package fun.commons.framework4j.redis.unit;

import fun.commons.framework4j.redis.exception.RedisDataSourceException;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import fun.commons.framework4j.redis.properties.RedisDataSourceProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MultiRedisManager 单元测试
 *
 * @since 1.0.0
 */
@DisplayName("MultiRedisManager 单元测试")
class MultiRedisManagerTest {

    private MultiRedisManager manager;

    @BeforeEach
    void setUp() {
        manager = new MultiRedisManager();
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.destroy();
        }
    }

    @Test
    @DisplayName("测试添加数据源")
    void testAddDataSource() {
        // 准备数据
        RedisDataSourceProperties config = createTestConfig("test");

        // 执行
        manager.addDataSource(config);

        // 验证
        assertTrue(manager.containsDatasource("test"));
        assertNotNull(manager.getRedisTemplate("test"));
    }

    @Test
    @DisplayName("测试获取不存在的数据源")
    void testGetNonExistentDataSource() {
        // 验证
        assertThrows(RedisDataSourceException.class, () -> {
            manager.getRedisTemplate("non-existent");
        });
    }

    @Test
    @DisplayName("测试获取所有数据源名称")
    void testGetAllDatasourceNames() {
        // 准备数据
        manager.addDataSource(createTestConfig("source1"));
        manager.addDataSource(createTestConfig("source2"));
        manager.addDataSource(createTestConfig("source3"));

        // 执行
        List<String> names = manager.getAllDatasourceNames();

        // 验证
        assertEquals(3, names.size());
        assertTrue(names.contains("source1"));
        assertTrue(names.contains("source2"));
        assertTrue(names.contains("source3"));
    }

    @Test
    @DisplayName("测试移除数据源")
    void testRemoveDataSource() {
        // 准备数据
        manager.addDataSource(createTestConfig("test"));
        assertTrue(manager.containsDatasource("test"));

        // 执行
        manager.removeDatasource("test");

        // 验证
        assertFalse(manager.containsDatasource("test"));
    }

    @Test
    @DisplayName("测试移除不存在的数据源")
    void testRemoveNonExistentDataSource() {
        // 执行 - 不应该抛出异常
        assertDoesNotThrow(() -> {
            manager.removeDatasource("non-existent");
        });
    }

    @Test
    @DisplayName("测试注册 RedisTemplate")
    void testRegisterRedisTemplate() {
        // 准备数据
        RedisDataSourceProperties config = createTestConfig("test");
        StringRedisTemplate template = new StringRedisTemplate();

        // 执行
        manager.registerRedisTemplate("test", template);

        // 验证
        assertTrue(manager.containsDatasource("test"));
        assertEquals(template, manager.getRedisTemplate("test"));
    }

    @Test
    @DisplayName("测试注册 RedissonClient")
    void testRegisterRedissonClient() {
        // 准备数据
        manager.addDataSource(createTestConfig("test"));

        // 验证 - 由于没有真实的 Redis 连接，RedissonClient 为 null
        RedissonClient client = manager.getRedissonClient("test");
        assertNull(client);
    }

    @Test
    @DisplayName("测试健康检查失败")
    void testCheckHealthFailed() {
        // 准备数据 - 使用不存在的Redis服务器(错误的端口)
        RedisDataSourceProperties config = createTestConfig("test");
        config.setPort(9999); // 使用一个不存在的端口
        config.setTimeout(Duration.ofMillis(1000)); // 设置较短的超时时间
        manager.addDataSource(config);

        // 执行 - 由于连接到不存在的端口，健康检查会失败
        boolean healthy = manager.checkHealth("test");

        // 验证
        assertFalse(healthy);
    }

    @Test
    @DisplayName("测试销毁所有数据源")
    void testDestroy() {
        // 准备数据
        manager.addDataSource(createTestConfig("source1"));
        manager.addDataSource(createTestConfig("source2"));

        // 执行
        manager.destroy();

        // 验证
        assertEquals(0, manager.getAllDatasourceNames().size());
    }

    /**
     * 创建测试配置
     */
    private RedisDataSourceProperties createTestConfig(String name) {
        RedisDataSourceProperties config = new RedisDataSourceProperties();
        config.setName(name);
        config.setHost("localhost");
        config.setPort(6379);
        config.setDatabase(0);

        // 禁用 Redisson（避免创建真实连接）
        config.getRedisson().setEnabled(false);

        return config;
    }

    @Test
    @DisplayName("v2.1 R5 修复：checkHealth 用 try-with-resources 关闭 RedisConnection（连接不泄漏）")
    void testCheckHealthClosesConnection() {
        // mock RedisTemplate + ConnectionFactory + Connection
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> mockTemplate = mock(RedisTemplate.class);
        RedisConnectionFactory mockFactory = mock(RedisConnectionFactory.class);
        RedisConnection mockConnection = mock(RedisConnection.class);
        when(mockConnection.ping()).thenReturn("PONG");
        when(mockFactory.getConnection()).thenReturn(mockConnection);
        when(mockTemplate.getConnectionFactory()).thenReturn(mockFactory);

        manager.registerRedisTemplate("close-test", mockTemplate);

        boolean healthy = manager.checkHealth("close-test");

        assertTrue(healthy, "健康检查应成功");
        // R5 修复核心：try-with-resources 应调用 connection.close()
        verify(mockConnection, times(1)).close();
        verify(mockFactory, times(1)).getConnection();
    }

    @Test
    @DisplayName("v2.1 R5 修复：checkHealth 异常时也关闭 RedisConnection")
    void testCheckHealthClosesConnectionOnException() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> mockTemplate = mock(RedisTemplate.class);
        RedisConnectionFactory mockFactory = mock(RedisConnectionFactory.class);
        RedisConnection mockConnection = mock(RedisConnection.class);
        // ping 抛异常
        when(mockConnection.ping()).thenThrow(new RuntimeException("Redis timeout"));
        when(mockFactory.getConnection()).thenReturn(mockConnection);
        when(mockTemplate.getConnectionFactory()).thenReturn(mockFactory);

        manager.registerRedisTemplate("exception-test", mockTemplate);

        boolean healthy = manager.checkHealth("exception-test");

        assertFalse(healthy, "健康检查应失败");
        // R5 修复核心：异常路径也需 close()
        verify(mockConnection, times(1)).close();
    }
}
