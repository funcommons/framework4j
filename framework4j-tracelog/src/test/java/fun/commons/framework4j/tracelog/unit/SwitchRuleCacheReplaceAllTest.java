package fun.commons.framework4j.tracelog.unit;

import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import fun.commons.framework4j.tracelog.switcher.SwitchRule;
import fun.commons.framework4j.tracelog.switcher.SwitchRuleCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SwitchRuleCache#replaceAll} 零窗口 diff 合并语义测试。
 * <p>
 * 背景：v1.3.3 之前 resync 用 clear()+重放，每 5s 制造一次瞬时空窗，
 * 提权请求会周期性 miss。replaceAll 必须保证：
 * <ol>
 *   <li>fresh 中存在的规则全程可读（无空窗）</li>
 *   <li>Redis 已删除的规则被精准移除</li>
 *   <li>与并发 put（Pub/Sub 增量）共存不丢活规则</li>
 * </ol>
 */
@DisplayName("SwitchRuleCache.replaceAll 零窗口替换")
class SwitchRuleCacheReplaceAllTest {

    private SwitchRuleCache cache;

    @BeforeEach
    void setUp() {
        cache = new SwitchRuleCache(new TraceLogProperties());
    }

    @Test
    @DisplayName("新增 + 精准失效：fresh 外的删、fresh 内的留")
    void diffMerge() {
        cache.put(new SwitchRule("user", "1", "DEBUG"));
        cache.put(new SwitchRule("user", "2", "DEBUG")); // 不在 fresh → 应删

        int removed = cache.replaceAll(List.of(
                new SwitchRule("user", "1", "TRACE"),   // 覆盖更新
                new SwitchRule("trace", "abc", "DEBUG") // 新增
        ));

        assertThat(removed).isEqualTo(1);
        assertThat(cache.get("user", "1")).isNotNull();
        assertThat(cache.get("user", "1").getLevel()).isEqualTo("TRACE"); // 覆盖生效
        assertThat(cache.get("user", "2")).isNull();                       // 精准失效
        assertThat(cache.get("trace", "abc")).isNotNull();                 // 新增生效
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("fresh 为空 → 清掉全部（Redis 已无任何开关）")
    void emptyFreshClearsAll() {
        cache.put(new SwitchRule("user", "1", "DEBUG"));
        int removed = cache.replaceAll(List.of());
        assertThat(removed).isEqualTo(1);
        assertThat(cache.size()).isZero();
    }

    @Test
    @DisplayName("null / 残缺规则条目被跳过")
    void skipInvalidEntries() {
        cache.put(new SwitchRule("user", "1", "DEBUG"));
        int removed = cache.replaceAll(java.util.Arrays.asList(
                null,
                new SwitchRule("user", null, "DEBUG"),
                new SwitchRule("user", "2", null)));
        // 残缺条目不算 fresh，但也不至于误删 —— user:1 在 fresh 外被删
        assertThat(removed).isEqualTo(1);
        assertThat(cache.get("user", "1")).isNull();
    }

    @Test
    @DisplayName("value 含冒号（URL 带端口）也能正确拆 key")
    void valueWithColon() {
        cache.put(new SwitchRule("url", "/api:8080/x", "DEBUG"));
        cache.replaceAll(List.of(new SwitchRule("url", "/api:8080/x", "DEBUG")));
        // 仍在缓存（没被误删）
        assertThat(cache.get("url", "/api:8080/x")).isNotNull();
        assertThat(cache.valuesOf("url")).contains("/api:8080/x");
    }

    @Test
    @DisplayName("零窗口：replaceAll 执行期间持续读 fresh 内的规则，永不 miss")
    void noMissWindowUnderConcurrentRead() throws Exception {
        // 预置 500 条，全部在 fresh 中
        for (int i = 0; i < 500; i++) {
            cache.put(new SwitchRule("user", "u" + i, "DEBUG"));
        }
        List<SwitchRule> fresh = new java.util.ArrayList<>();
        for (int i = 0; i < 500; i++) fresh.add(new SwitchRule("user", "u" + i, "DEBUG"));

        int readers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(readers + 1);
        CountDownLatch stop = new CountDownLatch(1);
        AtomicInteger misses = new AtomicInteger();

        // 读线程：持续读 u0..u499，直到 replaceAll 完成
        for (int r = 0; r < readers; r++) {
            pool.submit(() -> {
                while (stop.getCount() > 0) {
                    int i = (int) (Math.random() * 500);
                    if (cache.get("user", "u" + i) == null) misses.incrementAndGet();
                }
            });
        }
        // 写线程：反复 replaceAll（放大旧实现 clear 空窗被命中的概率）
        Thread writer = new Thread(() -> {
            for (int round = 0; round < 50; round++) {
                cache.replaceAll(fresh);
            }
        });
        writer.start();
        writer.join(10_000);
        stop.countDown();
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);

        assertThat(misses.get())
                .as("replaceAll 期间 fresh 内的规则不允许出现 miss（零窗口语义）")
                .isZero();
    }
}