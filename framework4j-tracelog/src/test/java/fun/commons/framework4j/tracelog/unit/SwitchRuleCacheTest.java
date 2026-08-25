package fun.commons.framework4j.tracelog.unit;

import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import fun.commons.framework4j.tracelog.switcher.SwitchRule;
import fun.commons.framework4j.tracelog.switcher.SwitchRuleCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SwitchRuleCache 本地缓存")
class SwitchRuleCacheTest {

    private SwitchRuleCache cache;

    @BeforeEach
    void setUp() {
        cache = new SwitchRuleCache(new TraceLogProperties());
    }

    @Test
    @DisplayName("put + get")
    void putAndGet() {
        cache.put(new SwitchRule("user", "10086", "DEBUG"));
        SwitchRule got = cache.get("user", "10086");
        assertThat(got).isNotNull();
        assertThat(got.getLevel()).isEqualTo("DEBUG");
    }

    @Test
    @DisplayName("miss → null")
    void miss() {
        assertThat(cache.get("user", "99999")).isNull();
    }

    @Test
    @DisplayName("覆盖 put")
    void overwrite() {
        cache.put(new SwitchRule("user", "10086", "DEBUG"));
        cache.put(new SwitchRule("user", "10086", "TRACE"));
        assertThat(cache.get("user", "10086").getLevel()).isEqualTo("TRACE");
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("invalidate 删除单条")
    void invalidate() {
        cache.put(new SwitchRule("user", "10086", "DEBUG"));
        cache.invalidate("user", "10086");
        assertThat(cache.get("user", "10086")).isNull();
    }

    @Test
    @DisplayName("clear 清空全部")
    void clearAll() {
        cache.put(new SwitchRule("user", "1", "DEBUG"));
        cache.put(new SwitchRule("user", "2", "DEBUG"));
        cache.put(new SwitchRule("trace", "abc", "TRACE"));
        cache.clear();
        assertThat(cache.size()).isEqualTo(0);
        assertThat(cache.get("user", "1")).isNull();
    }

    @Test
    @DisplayName("matchDimension 等价于 get")
    void matchDimension() {
        cache.put(new SwitchRule("user", "10086", "DEBUG"));
        assertThat(cache.matchDimension("user", "10086")).isNotNull();
        assertThat(cache.matchDimension("user", "99999")).isNull();
    }
}