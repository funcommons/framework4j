package fun.commons.framework4j.tracelog.switcher;

import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.net.InetAddress;
import java.time.Duration;

/**
 * Redis Streams 开关同步监听器（高可用替代 {@link SwitchPubSubListener}）。
 * <p>
 * <ul>
 *   <li>通过 {@code framework4j.tracelog.switch.transport=streams} 启用</li>
 *   <li>使用 {@code XADD} 写入 + {@code XREADGROUP} 消费 + ack</li>
 *   <li>每个节点独立 consumer name（{@code <host>-<pid>}-<nanos>），避免重复消费</li>
 *   <li>stream key 自动 {@code MAXLEN ~ 10000} 截断</li>
 * </ul>
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §3.1.4</a>
 */
@Slf4j
@RequiredArgsConstructor
public class SwitchStreamsListener {

    public static final String STREAM_KEY_SUFFIX = ":stream";
    public static final String CONSUMER_GROUP = "tracelog-switch-consumers";

    private final TraceLogProperties props;
    private final StringRedisTemplate redis;
    private final SwitchRuleCache cache;

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private Subscription subscription;

    @PostConstruct
    public void start() {
        if (!"streams".equalsIgnoreCase(props.getSync().getTransport())) {
            log.debug("【TraceLog】SwitchStreamsListener 未启用 (transport={})", props.getSync().getTransport());
            return;
        }

        String streamKey = props.getSync().getChannel() + STREAM_KEY_SUFFIX;
        String consumerName = resolveConsumerName();

        // 确保 group 存在（XGROUP CREATE ... MKSTREAM）
        try {
            redis.opsForStream().createGroup(streamKey, ReadOffset.from("0"), CONSUMER_GROUP);
        } catch (Exception e) {
            // BUSYGROUP 错误可忽略（group 已存在）
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.debug("【TraceLog】consumer group 已存在: {}", CONSUMER_GROUP);
            } else {
                log.warn("【TraceLog】创建 consumer group 失败: {}", e.getMessage());
            }
        }

        container = StreamMessageListenerContainer.create(redis.getConnectionFactory());
        StreamReadOptions readOptions = StreamReadOptions.empty()
                .count(10)
                .block(Duration.ofMillis(500));
        // readOptions 配置在 container create 时应用（不同 API 版本）

        subscription = container.receive(
                Consumer.from(CONSUMER_GROUP, consumerName),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                new SwitchStreamListener(cache, redis));

        container.start();
        log.info("【TraceLog】SwitchStreamsListener 已启动: stream={}, consumer={}", streamKey, consumerName);
    }

    @PreDestroy
    public void stop() {
        if (container != null) {
            container.stop();
        }
        log.info("【TraceLog】SwitchStreamsListener 已停止");
    }

    /**
     * XADD 发布开关变更（由 {@link fun.commons.framework4j.tracelog.query.TraceLogQueryController#openSwitch} 调用）。
     */
    public void publish(SwitchRule rule) {
        if (rule == null) return;
        String streamKey = props.getSync().getChannel() + STREAM_KEY_SUFFIX;
        redis.opsForStream().add(streamKey, java.util.Map.of(
                "type", rule.getType(),
                "value", rule.getValue(),
                "level", rule.getLevel()
        ));
        // 自动截断（最多保留 10000 条）
        redis.opsForStream().trim(streamKey, 10_000, true);
    }

    private String resolveConsumerName() {
        try {
            String host = InetAddress.getLocalHost().getHostName().replace('.', '_');
            return host + "-" + ProcessHandle.current().pid() + "-" + System.nanoTime();
        } catch (Exception e) {
            return "consumer-" + System.nanoTime();
        }
    }

    /**
     * 单条消息处理：反序列化 payload + 更新本地缓存 + ack。
     */
    private static final class SwitchStreamListener implements StreamListener<String, MapRecord<String, String, String>> {
        private final SwitchRuleCache cache;
        private final StringRedisTemplate redis;

        SwitchStreamListener(SwitchRuleCache cache, StringRedisTemplate redis) {
            this.cache = cache;
            this.redis = redis;
        }

        @Override
        public void onMessage(MapRecord<String, String, String> message) {
            try {
                String type = message.getValue().get("type");
                String value = message.getValue().get("value");
                String level = message.getValue().get("level");
                if (type == null || value == null || level == null) return;

                SwitchRule rule = new SwitchRule(type, value, level);
                Boolean exists = redis.hasKey(rule.redisKey());
                if (Boolean.TRUE.equals(exists)) {
                    cache.put(rule);
                } else {
                    cache.invalidate(type, value);
                }
                // ack（消费者组确认）
                redis.opsForStream().acknowledge(message.getStream(), CONSUMER_GROUP, message.getId());
            } catch (Exception e) {
                log.warn("【TraceLog】Stream 消息处理失败: {}", e.getMessage());
            }
        }
    }
}