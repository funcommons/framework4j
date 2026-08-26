package fun.commons.framework4j.tracelog.functional;

import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import fun.commons.framework4j.tracelog.store.TraceLogStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TraceLogStore 集成测试。
 * <p>
 * Redis 来源优先级：
 * <ol>
 *   <li>嵌入式 Redis（端口 16380，测试自管生命周期）</li>
 *   <li>本机 Redis（localhost:6379，docker run -p 6379:6379 redis 即可）</li>
 * </ol>
 * 两者都不可用时测试跳过（Assumptions）。
 * <p>
 * 验证：
 * <ul>
 *   <li>SETNX 分布式首次标记生效</li>
 *   <li>Lua 容量裁剪（LLEN 超限弹队头）</li>
 *   <li>LTRIM 单 trace 限制</li>
 *   <li>Pipeline 批写正确</li>
 * </ul>
 *
 * <p>运行方式：{@code mvn test -Dtracelog.integration.redis=true}
 */
@DisplayName("TraceLogStore 集成测试（嵌入式/本机 Redis）")
@EnabledIfSystemProperty(named = "tracelog.integration.redis", matches = "true")
class TraceLogStoreIntegrationTest {

    private static final int EMBEDDED_PORT = 16380;
    private static final int LOCAL_PORT = 6379;
    /** 测试专用 db（回退本机 Redis 时绝不触碰业务库 0，且清理用 flushDb 只清本库） */
    private static final int TEST_DATABASE = 15;

    private static RedisServer redisServer;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static TraceLogStore store;
    private static boolean redisAvailable = false;

    @BeforeAll
    static void startRedis() throws Exception {
        // 1. 优先尝试嵌入式 Redis
        try {
            redisServer = new RedisServer(EMBEDDED_PORT);
            redisServer.start();
            redisAvailable = connectAndPing(EMBEDDED_PORT);
        } catch (Exception e) {
            System.err.println("【集成测试】嵌入式 Redis 启动失败: " + e.getMessage());
        }

        // 2. 回退到本机 Redis（docker run -p 6379:6379 redis）
        if (!redisAvailable) {
            System.err.println("【集成测试】回退尝试本机 Redis localhost:" + LOCAL_PORT);
            redisAvailable = connectAndPing(LOCAL_PORT);
        }

        if (redisAvailable) {
            TraceLogProperties props = new TraceLogProperties();
            // 测试用小容量配置便于触发裁剪
            props.getStorage().setGlobalMaxTraces(3);
            props.getStorage().setSingleTraceMaxLogs(5);
            store = new TraceLogStore(redis, props);
        } else {
            System.err.println("【集成测试】无可用 Redis（嵌入式 + 本机均失败）, 测试将跳过");
        }
    }

    private static boolean connectAndPing(int port) {
        try {
            RedisStandaloneConfiguration conf = new RedisStandaloneConfiguration("localhost", port);
            conf.setDatabase(TEST_DATABASE);
            connectionFactory = new LettuceConnectionFactory(conf);
            connectionFactory.afterPropertiesSet();
            redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
            return "PONG".equalsIgnoreCase(redis.getConnectionFactory().getConnection().ping());
        } catch (Exception e) {
            if (connectionFactory != null) {
                try { connectionFactory.destroy(); } catch (Exception ignore) { /* nop */ }
                connectionFactory = null;
            }
            return false;
        }
    }

    @AfterAll
    static void stopRedis() throws Exception {
        if (connectionFactory != null) connectionFactory.destroy();
        if (redisServer != null) redisServer.stop();
    }

    @BeforeEach
    void cleanRedis() {
        org.junit.jupiter.api.Assumptions.assumeTrue(redisAvailable, "Redis 不可用（嵌入式 + 本机）, 跳过");
        // flushDb 只清 TEST_DATABASE（15），绝不影响本机 Redis 的业务库
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("Pipeline 批写 + 读取")
    void pipelineWriteAndRead() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        List<TraceLogStore.LogItem> batch = List.of(
                new TraceLogStore.LogItem(traceId, "{\"msg\":\"line1\"}", true),
                new TraceLogStore.LogItem(traceId, "{\"msg\":\"line2\"}", false)
        );

        store.flushBatch(batch);

        List<String> read = store.rangeTraceLogs(traceId);
        assertThat(read).hasSize(2);
        assertThat(read.get(0)).contains("line1");
        assertThat(read.get(1)).contains("line2");
    }

    @Test
    @DisplayName("SETNX 首次标记 → Lua 仅首次执行")
    void setnxFirstTime() {
        String traceId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        // 多次 flush 都标记为 firstSeen=true（模拟多节点）
        for (int i = 0; i < 5; i++) {
            List<TraceLogStore.LogItem> batch = List.of(
                    new TraceLogStore.LogItem(traceId, "{\"msg\":\"" + i + "\"}", true));
            store.flushBatch(batch);
        }

        // 全局队列中应该只有 1 个该 traceId（Lua 后续被 SETNX 跳过）
        Long queueLen = redis.opsForList().size("trace_global_queue");
        assertThat(queueLen).isEqualTo(1);

        // 但日志 List 有 5 条
        assertThat(store.rangeTraceLogs(traceId)).hasSize(5);
    }

    @Test
    @DisplayName("LTRIM 限制单 trace 最大条数")
    void ltrimSingleTrace() {
        String traceId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        List<TraceLogStore.LogItem> batch = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            batch.add(new TraceLogStore.LogItem(traceId, "{\"i\":" + i + "}", i == 0));
        }
        store.flushBatch(batch);

        // 配置 singleTraceMaxLogs=5，应被裁剪
        List<String> logs = store.rangeTraceLogs(traceId);
        assertThat(logs).hasSize(5);
        // 保留最后 5 条 (i=5..9)
        assertThat(logs.get(4)).contains("\"i\":9");
    }

    @Test
    @DisplayName("Lua 全局容量裁剪（globalMaxTraces=3 触发弹队头）")
    void luaGlobalCapacity() {
        // 4 个不同 traceId，超过上限 3 → 弹掉最早的 1 个
        for (int i = 0; i < 4; i++) {
            String traceId = String.format("%032x", i); // 32 hex 0,1,2,3
            store.flushBatch(List.of(
                    new TraceLogStore.LogItem(traceId, "{\"i\":" + i + "}", true)));
        }

        // 全局队列长度 = 3
        Long queueLen = redis.opsForList().size("trace_global_queue");
        assertThat(queueLen).isEqualTo(3);

        // 最早的 traceId=0 应被删除
        assertThat(redis.hasKey("trace_log:00000000000000000000000000000000")).isFalse();
        // 后续 traceId=1,2,3 应保留
        assertThat(redis.hasKey("trace_log:00000000000000000000000000000001")).isTrue();
        assertThat(redis.hasKey("trace_log:00000000000000000000000000000002")).isTrue();
        assertThat(redis.hasKey("trace_log:00000000000000000000000000000003")).isTrue();
    }

    @Test
    @DisplayName("TTL 设置（24h 默认）")
    void ttlSet() {
        String traceId = "cccccccccccccccccccccccccccccccc";
        store.flushBatch(List.of(
                new TraceLogStore.LogItem(traceId, "{\"msg\":\"x\"}", true)));

        Long ttl = redis.getExpire("trace_log:" + traceId);
        assertThat(ttl).isBetween(86_300L, 86_400L); // 24h ± few seconds
    }
}