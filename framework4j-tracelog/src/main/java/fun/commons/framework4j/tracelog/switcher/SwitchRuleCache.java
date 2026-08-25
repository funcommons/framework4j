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
     * 清空全部（用于断连重拉时全量替换）。
     */
    public void clear() {
        cache.invalidateAll();
        dimensionIndex.clear();
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