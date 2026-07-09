package fun.commons.framework4j.cache.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.cache.config.CacheProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 多级缓存服务（L1 Caffeine + L2 Redis + 防穿透 + 防击穿 + 防雪崩）
 *
 * @since 2.1.0
 */
@Slf4j
public class CacheService {

    /** 空值标记（防穿透） */
    public static final String NULL_MARKER = "__NULL__";

    /** v2.1 功能增强：per-call nullTtl 覆盖（ThreadLocal，get 后自动清理） */
    private final ThreadLocal<Long> nullTtlOverride = new ThreadLocal<>();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redisTemplate;
    private final CacheProperties properties;
    private final SingleFlightService singleFlightService;

    /** 每 prefix 一个 Caffeine 实例（独立配额）— v2.1 P1: 加 prefix 数量上限防 OOM */
    private final Map<String, Cache<String, String>> l1Caches = new ConcurrentHashMap<>();
    /** v2.1 P1: prefix 上限（默认 1024，超出后所有新 prefix 共用一个 "overflow" Caffeine） */
    private static final int MAX_PREFIXES = 1024;
    private static final String OVERFLOW_PREFIX = "__overflow__";

    public CacheService(StringRedisTemplate redisTemplate,
                        CacheProperties properties,
                        SingleFlightService singleFlightService) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.singleFlightService = singleFlightService;
    }

    /**
     * 读缓存（L1 → L2 → 单飞回源）
     *
     * @param prefix     业务前缀
     * @param key        缓存 key
     * @param ttlSeconds TTL（含 nullTtl 由 properties 决定）
     * @param loader     全未命中时回源 loader
     * @param valueType  返回类型（Jackson 反序列化）
     * @return 缓存值；loader 返回 null 时缓存空值标记
     */
    public <T> T get(String prefix, String key, long ttlSeconds,
                     Supplier<T> loader, Class<T> valueType) {
        return get(prefix, key, ttlSeconds, -1, loader, valueType);
    }

    /**
     * 读缓存（L1 → L2 → 单飞回源），支持自定义空值 TTL
     *
     * @param nullTtlSeconds 空值缓存 TTL（秒），-1 表示用全局默认
     */
    public <T> T get(String prefix, String key, long ttlSeconds, long nullTtlSeconds,
                     Supplier<T> loader, Class<T> valueType) {
        // 保存 per-call nullTtl override（loadAndFill 内读取）
        nullTtlOverride.set(nullTtlSeconds > 0 ? nullTtlSeconds : properties.getNullTtlSeconds());
        String fullKey = prefix + ":" + key;
        long effectiveTtl = ttlSeconds > 0 ? ttlSeconds : properties.getDefaultTtlSeconds();

        // 1) L1
        if (properties.getL1().isEnabled()) {
            String l1Val = getL1(prefix, fullKey);
            T decoded = decode(l1Val, valueType);
            if (decoded != null || NULL_MARKER.equals(l1Val)) {
                return decodeNull(decoded, l1Val);
            }
        }

        // 2) L2
        String l2Val = redisGet(fullKey);
        if (l2Val != null) {
            // 回填 L1
            if (properties.getL1().isEnabled()) putL1(prefix, fullKey, l2Val);
            T decoded = decode(l2Val, valueType);
            return decodeNull(decoded, l2Val);
        }

        // 3) 单飞回源
        if (!properties.getSingleFlight().isEnabled()) {
            return loadAndFill(prefix, fullKey, effectiveTtl, loader, valueType);
        }

        SingleFlightService.LeaderContext ctx = singleFlightService.tryAcquireLeader(fullKey);
        if (ctx != null) {
            // 我是 leader
            try {
                return loadAndFill(prefix, fullKey, effectiveTtl, loader, valueType);
            } finally {
                singleFlightService.releaseLeader(fullKey, ctx);
            }
        }

        // 我是 follower：等待 leader 回填
        T waited = singleFlightService.waitForLeader(fullKey, () -> {
            String v = redisGet(fullKey);
            return v != null ? decode(v, valueType) : null;
        });
        if (waited != null) {
            return waited;
        }

        // v2.1 P1: 等待超时后再做一次 tryAcquireLeader
        SingleFlightService.LeaderContext retryCtx = singleFlightService.tryAcquireLeader(fullKey);
        if (retryCtx != null) {
            try {
                return loadAndFill(prefix, fullKey, effectiveTtl, loader, valueType);
            } finally {
                singleFlightService.releaseLeader(fullKey, retryCtx);
            }
        }

        // 二次抢锁失败 → 兜底直接回源
        log.warn("[Cache] SingleFlight wait timeout, fallback to loader: {}", fullKey);
        return loadAndFill(prefix, fullKey, effectiveTtl, loader, valueType);
    }

    /**
     * 写缓存（L1+L2，含 TTL 抖动防雪崩）
     */
    public <T> void put(String prefix, String key, long ttlSeconds, T value) {
        // v2.1 P0: 统一走 putInternal，避免 indexOf(':') 误切
        putInternal(prefix, prefix + ":" + key, ttlSeconds, value);
    }

    /**
     * 删缓存（L1+L2 双删）
     */
    public void evict(String prefix, String key) {
        String fullKey = prefix + ":" + key;
        if (properties.getL1().isEnabled()) {
            // v2.1 P1: 用 getL1Cache 统一获取（含 overflow 兜底）
            getL1Cache(prefix).invalidate(l1EntryKey(prefix, fullKey));
        }
        try {
            redisTemplate.delete(fullKey);
        } catch (Exception e) {
            log.warn("[Cache] L2 evict failed: {}", e.getMessage());
        }
    }

    // ==================== 批量预热 ====================

    /**
     * v2.1 功能增强：批量预热缓存
     * <p>启动时调用，避免冷启动大量请求同时回源
     *
     * @param prefix 业务前缀
     * @param keys 要预热的 key 列表
     * @param ttlSeconds TTL
     * @param loader 批量加载函数（key → value）
     * @param valueType 返回类型
     */
    public <T> void warmup(String prefix, List<String> keys, long ttlSeconds,
                           java.util.function.Function<String, T> loader, Class<T> valueType) {
        if (keys == null || keys.isEmpty()) return;
        log.info("[Cache] warmup start: prefix={} count={}", prefix, keys.size());
        int success = 0;
        for (String key : keys) {
            try {
                T value = loader.apply(key);
                if (value != null) {
                    put(prefix, key, ttlSeconds, value);
                    success++;
                }
            } catch (Exception e) {
                log.warn("[Cache] warmup failed for key={}: {}", key, e.getMessage());
            }
        }
        log.info("[Cache] warmup done: prefix={} success={}/{}", prefix, success, keys.size());
    }

    // ==================== 内部 ====================

    private <T> T loadAndFill(String prefix, String fullKey, long ttlSeconds,
                              Supplier<T> loader, Class<T> valueType) {
        T value;
        try {
            value = loader.get();
        } catch (Exception e) {
            log.warn("[Cache] loader failed for {}: {}", fullKey, e.getMessage());
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }

        if (value == null) {
            // 防穿透：缓存空值标记（短 TTL）— v2.1: 支持 per-call nullTtl 覆盖
            Long override = nullTtlOverride.get();
            long nullTtl = override != null ? override : properties.getNullTtlSeconds();
            nullTtlOverride.remove();  // 清理 ThreadLocal
            String encoded = NULL_MARKER;
            if (properties.getL1().isEnabled()) putL1(prefix, fullKey, encoded);
            try {
                redisTemplate.opsForValue().set(fullKey, encoded, Duration.ofSeconds(nullTtl));
            } catch (Exception e) {
                log.warn("[Cache] L2 null put failed: {}", e.getMessage());
            }
            return null;
        }

        // v2.1 P0: 直接传完整 key + 清理 ThreadLocal
        nullTtlOverride.remove();
        putInternal(prefix, fullKey, ttlSeconds, value);
        return value;
    }

    /** 内部 put：不重新切分 key */
    private <T> void putInternal(String prefix, String fullKey, long ttlSeconds, T value) {
        long effectiveTtl = ttlSeconds > 0 ? ttlSeconds : properties.getDefaultTtlSeconds();
        long jitteredTtl = jitterTtl(effectiveTtl);

        String encoded = encode(value);
        if (properties.getL1().isEnabled()) putL1(prefix, fullKey, encoded);
        try {
            redisTemplate.opsForValue().set(fullKey, encoded, Duration.ofSeconds(jitteredTtl));
        } catch (Exception e) {
            log.warn("[Cache] L2 put failed: {}", e.getMessage());
        }
    }

    /** v2.1 P0 修复（第6轮）：OVERFLOW 独立 AtomicReference，避免嵌套 compute 导致 IllegalStateException */
    private final java.util.concurrent.atomic.AtomicReference<Cache<String, String>> overflowCache =
            new java.util.concurrent.atomic.AtomicReference<>();

    private Cache<String, String> getL1Cache(String prefix) {
        // 已存在的 prefix 直接复用
        Cache<String, String> existing = l1Caches.get(prefix);
        if (existing != null) return existing;

        // 超上限的新 prefix 走 OVERFLOW 实例（不注册到 map）
        if (l1Caches.size() >= MAX_PREFIXES) {
            return getOrInitOverflow();
        }

        // 注册新 prefix（原子 putIfAbsent）
        Cache<String, String> fresh = buildL1();
        Cache<String, String> prev = l1Caches.putIfAbsent(prefix, fresh);
        if (prev != null) return prev;

        // 注册后再次检查上限（多线程可能并发突破）
        if (l1Caches.size() > MAX_PREFIXES && !OVERFLOW_PREFIX.equals(prefix)) {
            // 尝试回滚刚加入的（CAS：仅当 value 仍是 fresh 才删）
            if (l1Caches.remove(prefix, fresh)) {
                return getOrInitOverflow();
            }
        }
        return fresh;
    }

    /** 懒初始化 OVERFLOW Caffeine 实例（AtomicReference CAS，无嵌套 compute） */
    private Cache<String, String> getOrInitOverflow() {
        Cache<String, String> cache = overflowCache.get();
        if (cache != null) return cache;
        Cache<String, String> fresh = buildL1();
        return overflowCache.compareAndSet(null, fresh) ? fresh : overflowCache.get();
    }

    /** v2.1 P1: L1 entry key 加 prefix namespace 防跨 prefix 污染（OVERFLOW 共享场景） */
    private static String l1EntryKey(String prefix, String fullKey) {
        return prefix + "#" + fullKey;
    }

    private String getL1(String prefix, String fullKey) {
        return getL1Cache(prefix).getIfPresent(l1EntryKey(prefix, fullKey));
    }

    private void putL1(String prefix, String fullKey, String value) {
        getL1Cache(prefix).put(l1EntryKey(prefix, fullKey), value);
    }

    private Cache<String, String> buildL1() {
        CacheProperties.L1Config cfg = properties.getL1();
        return Caffeine.newBuilder()
                .maximumSize(cfg.getMaxSize())
                .expireAfterWrite(Duration.ofSeconds(cfg.getExpireAfterWrite()))
                .build();
    }

    private String redisGet(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("[Cache] L2 get failed: {}", e.getMessage());
            return null;
        }
    }

    /** TTL 抖动 ±10%（防雪崩）— v2.1 P1: ThreadLocalRandom 替代 Math.random（无锁竞争） */
    private long jitterTtl(long ttl) {
        double ratio = java.util.concurrent.ThreadLocalRandom.current().nextDouble(-0.1, 0.1);
        long jitter = (long) (ttl * ratio);
        return Math.max(1, ttl + jitter);
    }

    @SuppressWarnings("unchecked")
    private <T> T decode(String raw, Class<T> valueType) {
        if (raw == null) return null;
        if (NULL_MARKER.equals(raw)) return null;
        if (valueType == String.class) return (T) raw;
        try {
            return MAPPER.readValue(raw, valueType);
        } catch (Exception e) {
            log.warn("[Cache] decode failed: {}", e.getMessage());
            return null;
        }
    }

    private <T> T decodeNull(T decoded, String raw) {
        return NULL_MARKER.equals(raw) ? null : decoded;
    }

    private <T> String encode(T value) {
        if (value == null) return NULL_MARKER;
        if (value instanceof String s) return s;
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Cache encode failed", e);
        }
    }
}
