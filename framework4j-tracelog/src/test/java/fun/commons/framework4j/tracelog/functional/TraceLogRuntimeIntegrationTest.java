package fun.commons.framework4j.tracelog.functional;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import fun.commons.framework4j.tracelog.appender.AsyncRedisLogAppender;
import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import fun.commons.framework4j.tracelog.store.LocalFallbackWriter;
import fun.commons.framework4j.tracelog.store.TraceLogStore;
import fun.commons.framework4j.tracelog.switcher.SwitchResyncScheduler;
import fun.commons.framework4j.tracelog.switcher.SwitchRule;
import fun.commons.framework4j.tracelog.switcher.SwitchRuleCache;
import fun.commons.framework4j.tracelog.switcher.SwitchStreamsListener;
import fun.commons.framework4j.tracelog.util.TenantKeyResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 运行链路集成测试（真实 Redis）：
 * <ol>
 *   <li><b>多节点聚合</b>：两个 Store 实例（模拟双节点）写同一 traceId → 同一 List 聚合、全局队列仅 1 条</li>
 *   <li><b>resync Redis→cache</b>：直写 Redis 开关 → resync() → 本地缓存命中；Redis 删除 → 精准失效</li>
 *   <li><b>Streams 生命周期</b>：XADD 发布 → 消费者组收到 → 缓存更新；key 删除后再发布 → 缓存失效</li>
 *   <li><b>停机 drain</b>：append 5 条（未满批）→ stop() → 全部落 Redis（flushed == appended）</li>
 * </ol>
 *
 * <p>运行方式：{@code mvn test -Dtracelog.integration.redis=true}
 * （Redis 来源：嵌入式 16380 优先，失败回退本机 6379 db15）
 */
@DisplayName("运行链路集成测试（多节点聚合 / resync / Streams / 停机 drain）")
@EnabledIfSystemProperty(named = "tracelog.integration.redis", matches = "true")
class TraceLogRuntimeIntegrationTest {

    private static RedisTestSupport.Holder redis;
    private static TraceLogProperties props;

    @BeforeAll
    static void setUp() {
        redis = RedisTestSupport.bootstrap();
        props = new TraceLogProperties();
        props.getCollection().setFallbackDir("/tmp/framework4j-tracelog-test-fallback");
    }

    @AfterAll
    static void tearDown() {
        RedisTestSupport.shutdown(redis);
    }

    @BeforeEach
    void clean() {
        Assumptions.assumeTrue(redis.available, "Redis 不可用（嵌入式 + 本机）, 跳过");
        RedisTestSupport.flush(redis);
    }

    // ==================== 1. 多节点聚合 ====================

    @Test
    @DisplayName("双节点写同一 traceId：日志聚合到同一 List，全局队列仅 1 条")
    void multiNodeAggregation() {
        // 两个 Store 实例 = 两个微服务节点，各自本地缓存都是"首次"
        TraceLogStore nodeA = new TraceLogStore(redis.redis, props);
        TraceLogStore nodeB = new TraceLogStore(redis.redis, props);
        String traceId = "aaaabbbbccccddddaaaabbbbccccdddd";

        nodeA.flushBatch(List.of(new TraceLogStore.LogItem(traceId, "{\"msg\":\"from-A\"}", true)));
        nodeB.flushBatch(List.of(new TraceLogStore.LogItem(traceId, "{\"msg\":\"from-B\"}", true)));
        nodeA.flushBatch(List.of(new TraceLogStore.LogItem(traceId, "{\"msg\":\"from-A-2\"}", true)));

        // 聚合：3 条在同一 List（顺序保留）
        assertThat(nodeA.rangeTraceLogs(traceId)).hasSize(3);
        assertThat(nodeA.rangeTraceLogs(traceId).get(0)).contains("from-A");
        assertThat(nodeA.rangeTraceLogs(traceId).get(2)).contains("from-A-2");

        // Lua 内 SETNX 原子判定：多节点重复触发只入队 1 次
        assertThat(redis.redis.opsForList().size("trace_global_queue")).isEqualTo(1);
    }

    // ==================== 2. resync Redis → 本地缓存 ====================

    @Test
    @DisplayName("resync：Redis 开关直写 → 本地缓存命中；Redis 删除 → 精准失效（diff 合并）")
    void resyncRedisToCache() {
        SwitchRuleCache cache = new SwitchRuleCache(props);
        SwitchResyncScheduler scheduler = new SwitchResyncScheduler(props, redis.redis, cache);

        // 预置本地脏数据（模拟上一轮残留）：Redis 已没有 → 应被 diff 精准清掉
        cache.put(new SwitchRule("user", "stale-user", "DEBUG"));

        // Redis 直写两条开关（模拟"管控中心在别处开启"）
        redis.redis.opsForValue().set("log_switch:id:user:10086", "DEBUG", Duration.ofSeconds(60));
        redis.redis.opsForValue().set("log_switch:id:url:/v1/**", "TRACE", Duration.ofSeconds(60));

        scheduler.resync();

        assertThat(cache.get("user", "10086")).isNotNull();
        assertThat(cache.get("url", "/v1/**").getLevel()).isEqualTo("TRACE");
        assertThat(cache.get("user", "stale-user")).as("Redis 已不存在的规则被精准失效").isNull();
        assertThat(cache.valuesOf("url")).contains("/v1/**");

        // Redis 删掉其中一条 → 再 resync → 仅该条失效，另一条仍在（零窗口 diff 语义）
        redis.redis.delete("log_switch:id:user:10086");
        scheduler.resync();
        assertThat(cache.get("user", "10086")).isNull();
        assertThat(cache.get("url", "/v1/**")).isNotNull();
    }

    // ==================== 3. Streams 生命周期 ====================

    @Test
    @DisplayName("Streams：XADD 发布 → 消费者组异步消费 → 缓存更新；key 删除后再发布 → 缓存失效")
    void streamsLifecycle() throws Exception {
        props.getSync().setTransport("streams");
        try {
            SwitchRuleCache cache = new SwitchRuleCache(props);
            SwitchStreamsListener listener = new SwitchStreamsListener(props, redis.redis, cache);
            SwitchRule rule = new SwitchRule("user", "20001", "DEBUG");

            // Redis 先写开关 key（listener 校验 hasKey），再启动订阅，再 XADD
            redis.redis.opsForValue().set(rule.redisKey(), "DEBUG", Duration.ofSeconds(60));
            listener.start();
            try {
                listener.publish(rule); // XADD + MAXLEN trim

                await("收到 Streams 消息并写入缓存").atMost(Duration.ofSeconds(5))
                        .untilAsserted(() -> assertThat(cache.get("user", "20001")).isNotNull());

                // key 删除后再发布同规则 → 消费侧 hasKey=false → 缓存失效
                redis.redis.delete(rule.redisKey());
                listener.publish(rule);
                await("key 已删 → 缓存被失效").atMost(Duration.ofSeconds(5))
                        .untilAsserted(() -> assertThat(cache.get("user", "20001")).isNull());
            } finally {
                listener.stop();
            }
        } finally {
            props.getSync().setTransport("pubsub"); // 还原，避免影响其他测试
        }
    }

    // ==================== 4. 停机 drain ====================

    @Test
    @DisplayName("停机 drain：append 5 条未满批 → stop() 后全部落 Redis（flushed == appended）")
    void gracefulShutdownDrain() throws Exception {
        TraceLogStore store = new TraceLogStore(redis.redis, props);
        LocalFallbackWriter fallback = new LocalFallbackWriter(props);
        TenantKeyResolver tenant = new TenantKeyResolver(props, null);

        TraceLogProperties p = new TraceLogProperties();
        p.getCollection().setFallbackDir(props.getCollection().getFallbackDir());
        p.getCollection().setWorkerCount(1);
        p.getCollection().setFlushBatchSize(100);   // 5 条永远攒不满 → 只能靠 stop() drain
        p.getCollection().setFlushIntervalMs(60_000); // 定时器 60s 不触发, 隔离验证 stop 路径

        AsyncRedisLogAppender appender = new AsyncRedisLogAppender(p, store, fallback, tenant);
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();

        String traceId = "dddd0000dddd0000dddd0000dddd0000";
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (int i = 0; i < 5; i++) {
            LoggingEvent ev = new LoggingEvent(
                    "org.test", lc.getLogger("fun.commons.test.Drain"),
                    Level.INFO, "drain-msg-" + i, null, null);
            ev.setMDCPropertyMap(Map.of("traceId", traceId));
            appender.doAppend(ev);
        }

        // stop(): Disruptor 排空 + 未满批残留 flush
        appender.stop();

        assertThat(appender.getFlushedCount())
                .as("停机 drain 后未满批的日志必须全部落 Redis")
                .isEqualTo(5);
        assertThat(appender.getAppendedCount()).isEqualTo(5);
        assertThat(store.rangeTraceLogs(traceId))
                .hasSize(5)
                .allSatisfy(line -> assertThat(line).contains("drain-msg-"));
    }
}