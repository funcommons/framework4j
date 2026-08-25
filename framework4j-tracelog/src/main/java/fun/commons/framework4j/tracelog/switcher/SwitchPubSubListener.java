package fun.commons.framework4j.tracelog.switcher;

import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * Redis Pub/Sub 开关变更监听器。
 * <p>
 * <ul>
 *   <li>启动时 {@link #start()} 注册 RedisMessageListenerContainer</li>
 *   <li>收到消息 → {@link SwitchRule#fromPayload(String)} 反序列化 → 更新 {@link SwitchRuleCache}</li>
 *   <li>支持 {@code pattern} 订阅：频道名匹配 {@code framework4j.tracelog.switch-channel}</li>
 * </ul>
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §3.1.3</a>
 */
@Slf4j
@RequiredArgsConstructor
public class SwitchPubSubListener implements MessageListener {

    private final TraceLogProperties props;
    private final StringRedisTemplate redis;
    private final SwitchRuleCache cache;
    /**
     * 注入 Spring Boot 默认提供的 listener container（{@code spring-boot-starter-data-redis} 会自动装配）。
     */
    private final RedisMessageListenerContainer container;

    @PostConstruct
    public void start() {
        String channel = props.getSync().getChannel();
        container.addMessageListener(this, new PatternTopic(channel));
        log.info("【TraceLog】SwitchPubSubListener 已启动, 订阅频道: {}", channel);
    }

    @PreDestroy
    public void stop() {
        container.removeMessageListener(this);
        log.info("【TraceLog】SwitchPubSubListener 已停止");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        SwitchRule rule = SwitchRule.fromPayload(body);
        if (rule == null) {
            log.warn("【TraceLog】开关消息解析失败: {}", body);
            return;
        }
        // 检查 Redis 中规则是否还存在（防止 Pub/Sub 消息比 SET 慢导致 stale put）
        Boolean exists = redis.hasKey(rule.redisKey());
        if (Boolean.TRUE.equals(exists)) {
            cache.put(rule);
            log.debug("【TraceLog】开关更新: {}", rule);
        } else {
            cache.invalidate(rule.getType(), rule.getValue());
            log.debug("【TraceLog】开关失效: {}", rule);
        }
    }
}