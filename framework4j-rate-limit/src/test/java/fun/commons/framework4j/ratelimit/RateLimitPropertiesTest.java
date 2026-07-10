package fun.commons.framework4j.ratelimit;

import fun.commons.framework4j.ratelimit.config.RateLimitProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RateLimitProperties 配置测试")
class RateLimitPropertiesTest {

    @Test @DisplayName("默认值正确")
    void defaults() {
        RateLimitProperties p = new RateLimitProperties();
        assertThat(p.isEnabled()).isTrue();
        assertThat(p.getRedisName()).isEqualTo("default");
        assertThat(p.getDefaultLimit()).isEqualTo(100);
        assertThat(p.getDefaultWindow()).isEqualTo("1m");
        assertThat(p.getDefaultAlgorithm()).isEqualTo("sliding_window");
        assertThat(p.getDefaultScope()).isEqualTo("ip");
        assertThat(p.isIncludeHeaders()).isTrue();
        assertThat(p.getKeyPrefix()).isEqualTo("ratelimit");
    }

    @Test @DisplayName("白名单默认值")
    void whitelistDefaults() {
        RateLimitProperties p = new RateLimitProperties();
        assertThat(p.getWhitelistPaths()).contains("/actuator/**", "/health/**");
        assertThat(p.getWhitelistIps()).contains("127.0.0.1", "::1");
    }

    @Test @DisplayName("所有属性可修改")
    void allMutable() {
        RateLimitProperties p = new RateLimitProperties();
        p.setEnabled(false);
        p.setRedisName("custom");
        p.setDefaultLimit(500);
        p.setDefaultWindow("30s");
        p.setDefaultAlgorithm("fixed_window");
        p.setDefaultScope("user");
        p.setIncludeHeaders(false);
        p.setKeyPrefix("rl");
        p.setWhitelistPaths(java.util.List.of("/api/**"));
        p.setWhitelistIps(java.util.List.of("10.0.0.1"));

        assertThat(p.isEnabled()).isFalse();
        assertThat(p.getRedisName()).isEqualTo("custom");
        assertThat(p.getDefaultLimit()).isEqualTo(500);
        assertThat(p.getDefaultWindow()).isEqualTo("30s");
        assertThat(p.getDefaultAlgorithm()).isEqualTo("fixed_window");
        assertThat(p.getDefaultScope()).isEqualTo("user");
        assertThat(p.isIncludeHeaders()).isFalse();
        assertThat(p.getKeyPrefix()).isEqualTo("rl");
        assertThat(p.getWhitelistPaths()).containsExactly("/api/**");
        assertThat(p.getWhitelistIps()).containsExactly("10.0.0.1");
    }

    @Test @DisplayName("pathPatterns 默认空列表")
    void pathPatternsDefault() {
        RateLimitProperties p = new RateLimitProperties();
        assertThat(p.getPathPatterns()).isEmpty();
        assertThat(p.getExcludePathPatterns()).isEmpty();
    }

    @Test @DisplayName("pathPatterns 可设置")
    void pathPatternsMutable() {
        RateLimitProperties p = new RateLimitProperties();
        p.setPathPatterns(java.util.List.of("/v1/**"));
        p.setExcludePathPatterns(java.util.List.of("/v1/public/**"));
        assertThat(p.getPathPatterns()).containsExactly("/v1/**");
        assertThat(p.getExcludePathPatterns()).containsExactly("/v1/public/**");
    }

    @Test @DisplayName("白名单可为空列表")
    void whitelistCanBeEmpty() {
        RateLimitProperties p = new RateLimitProperties();
        p.setWhitelistPaths(java.util.List.of());
        p.setWhitelistIps(java.util.List.of());
        assertThat(p.getWhitelistPaths()).isEmpty();
        assertThat(p.getWhitelistIps()).isEmpty();
    }

    @Test @DisplayName("白名单可为 null（默认值）")
    void whitelistNullDefault() {
        RateLimitProperties p = new RateLimitProperties();
        // 默认不为 null（有默认值）
        assertThat(p.getWhitelistPaths()).isNotNull();
        assertThat(p.getWhitelistIps()).isNotNull();
    }
}
