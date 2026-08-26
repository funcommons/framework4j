package fun.commons.framework4j.tracelog.switcher;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 开关规则本地缓存（Caffeine）。
 * <p>
 * 数据来源：
 * <ul>
 *   <li>启动时 {@link SwitchPubSubListener} 从 Redis 加载已存在的规则</li>
 *   <li>运行时通过 Pub/Sub 增量同步</li>
 *   <li>断连时 {@link SwitchResyncScheduler} 每 5s 全量重拉</li>
 * </ul>
 *
 * <p>Key 格式：{@code {type}:{value}} → {@link SwitchRule}
 */
@Slf4j
public class SwitchRuleCache {

    private final Cache<String, SwitchRule> cache;
    /** 维度索引：user → set of values（用于快速查询） */
    private final Map<String, java.util.Set<String>> dimensionIndex = new ConcurrentHashMap<>();

    public SwitchRuleCache(TraceLogProperties props) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(props.getSync().getRuleCacheSize())
                .expireAfterWrite(Duration.ofSeconds(props.getSync().getMaxTtlSeconds()))
                .build();
    }

    /**
     * 按 key 获取规则（{@code type:value}）。
     */
    public SwitchRule get(String type, String value) {
        return cache.getIfPresent(key(type, value));
    }

    /**
     * 判定指定维度是否命中提权。
     */
    public SwitchRule matchDimension(String type, String value) {
        return get(type, value);
    }

    /**
     * 放入或覆盖一条规则。
     */
    public void put(SwitchRule rule) {
        if (rule == null || rule.getType() == null || rule.getValue() == null) return;
        cache.put(key(rule.getType(), rule.getValue()), rule);
        dimensionIndex
                .computeIfAbsent(rule.getType(), k -> ConcurrentHashMap.newKeySet())
                .add(rule.getValue());
    }

    /**
     * 删除一条规则。
     */
    public void invalidate(String type, String value) {
        cache.invalidate(key(type, value));
        java.util.Set<String> set = dimensionIndex.get(type);
        if (set != null) set.remove(value);
    }

    /**
     * 清空全部（仅测试/停机用；周期重拉请用 {@link #replaceAll}）。
     */
    public void clear() {
        cache.invalidateAll();
        dimensionIndex.clear();
    }

    /**
     * 全量重拉后的 diff 合并（零窗口替换）。
     * <p>
     * 相比 {@code clear() + 重放}：新规则先 put、仅精准 invalidate Redis 已失效的条目，
     * 全程不存在"缓存被清空"的瞬间 —— 周期重拉（默认 5s）不再造成提权请求瞬时 miss。
     *
     * <p>并发语义：与 {@link #put}（Pub/Sub 增量）并发安全 ——
     * pub/sub 消息到达时对应 Redis key 必已写入（控制器先 SET 后 PUBLISH），
     * 故不会出现在 fresh 快照之外而被误删的活规则；即使极端时序下被误删，
     * 下轮重拉（≤5s）即恢复。
     *
     * @param freshRules Redis SCAN 得到的全量规则
     * @return 本次失效（删除）的规则条数
     */
    public synchronized int replaceAll(java.util.Collection<SwitchRule> freshRules) {
        java.util.Set<String> freshKeys = new java.util.HashSet<>();
        if (freshRules != null) {
            for (SwitchRule rule : freshRules) {
                if (rule == null || rule.getType() == null || rule.getValue() == null) continue;
                put(rule);
                freshKeys.add(key(rule.getType(), rule.getValue()));
            }
        }
        // 精准失效：缓存里有、Redis 快照里没有的（已过期/被删）
        int removed = 0;
        for (String k : java.util.List.copyOf(cache.asMap().keySet())) {
            if (!freshKeys.contains(k)) {
                int colon = k.indexOf(':');
                if (colon > 0) {
                    invalidate(k.substring(0, colon), k.substring(colon + 1));
                    removed++;
                }
            }
        }
        return removed;
    }

    /**
     * 当前缓存的规则数量（指标）。
     */
    public long size() {
        return cache.estimatedSize();
    }

    /**
     * 获取指定 type 的所有 value（用于 url 维度批量 Ant 匹配）。
     *
     * @param type 维度类型（user / trace / url / order）
     * @return 不可变 value 集合
     */
    public java.util.Set<String> valuesOf(String type) {
        java.util.Set<String> set = dimensionIndex.get(type);
        return set == null ? java.util.Collections.emptySet() : java.util.Collections.unmodifiableSet(set);
    }

    private static String key(String type, String value) {
        return type + ":" + value;
    }
}