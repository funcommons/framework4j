package fun.commons.framework4j.tracelog.switcher;

import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 开关规则定时重拉调度器。
 * <p>
 * <ul>
 *   <li>启动时（{@link #onInit}）一次性 SCAN 加载所有 {@code log_switch:*} 规则</li>
 *   <li>每 {@code switch-resync-interval-seconds}（默认 5s）周期性全量 SCAN + 替换本地缓存</li>
 *   <li>用于 Pub/Sub 断连窗口期的兜底同步</li>
 * </ul>
 *
 * @see <a href="file:../动态追踪日志 SDK 技��方案.md">设计文档 §3.1.3 / §3.1.4</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SwitchResyncScheduler {

    private final TraceLogProperties props;
    private final StringRedisTemplate redis;
    private final SwitchRuleCache cache;

    /**
     * 启动时一次性加载。
     */
    @org.springframework.context.event.EventListener(
            org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onInit() {
        log.info("【TraceLog】启动加载开关规则...");
        resync();
    }

    /**
     * 周期性重拉（默认 5s）。
     */
    @Scheduled(fixedDelayString = "#{${framework4j.tracelog.switch.resync-interval-seconds:5} * 1000}",
            initialDelayString = "#{${framework4j.tracelog.switch.resync-interval-seconds:5} * 1000}")
    public void resync() {
        try {
            Map<String, SwitchRule> fresh = scan();
            // 替换式更新：先 clear 再 put 避免脏数据
            cache.clear();
            fresh.values().forEach(cache::put);
            log.debug("【TraceLog】开关规则重拉完成: count={}", fresh.size());
        } catch (Exception e) {
            log.warn("【TraceLog】开关规则重拉失败: err={}", e.getMessage());
        }
    }

    private Map<String, SwitchRule> scan() {
        Map<String, SwitchRule> result = new ConcurrentHashMap<>();
        ScanOptions options = ScanOptions.scanOptions().match("log_switch:id:*").count(100).build();
        try (Cursor<byte[]> cursor = redis.getRequiredConnectionFactory()
                .getConnection().keyCommands().scan(options)) {
            while (cursor.hasNext()) {
                String key = new String(cursor.next());
                String value = redis.opsForValue().get(key);
                if (value == null) continue;
                SwitchRule rule = parseRedisKey(key, value);
                if (rule != null) {
                    result.put(key, rule);
                }
            }
        }
        return result;
    }

    private SwitchRule parseRedisKey(String redisKey, String level) {
        // log_switch:id:{type}:{value}
        String prefix = "log_switch:id:";
        if (!redisKey.startsWith(prefix)) return null;
        String rest = redisKey.substring(prefix.length());
        int colon = rest.indexOf(':');
        if (colon < 0) return null;
        String type = rest.substring(0, colon);
        String value = rest.substring(colon + 1);
        return new SwitchRule(type, value, level);
    }
}