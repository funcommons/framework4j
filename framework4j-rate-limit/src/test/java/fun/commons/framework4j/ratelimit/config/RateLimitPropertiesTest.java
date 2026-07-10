package fun.commons.framework4j.ratelimit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RateLimitProperties 默认值 / 覆盖 / 边界值 / 注解元信息测试。
 *
 * @since 2.1.0
 */
@DisplayName("RateLimitProperties 配置默认值与覆盖测试")
class RateLimitPropertiesTest {

    @Test
    @DisplayName("默认值：所有字段符合 v2.1 文档约定")
    void defaultsAreAsDocumented() {
        RateLimitProperties p = new RateLimitProperties();

        assertThat(p.isEnabled()).isTrue();
        assertThat(p.getPathPatterns()).isEmpty();
        assertThat(p.getExcludePathPatterns()).isEmpty();
        assertThat(p.getRedisName()).isEqualTo("default");
        assertThat(p.getDefaultLimit()).isEqualTo(100);
        assertThat(p.getDefaultWindow()).isEqualTo("1m");
        assertThat(p.getDefaultAlgorithm()).isEqualTo("sliding_window");
        assertThat(p.getDefaultScope()).isEqualTo("ip");
        assertThat(p.isIncludeHeaders()).isTrue();
        assertThat(p.getKeyPrefix()).isEqualTo("ratelimit");
        assertThat(p.getWhitelistPaths())
                .containsExactly("/actuator/**", "/health/**", "/v1/auth/login");
        assertThat(p.getWhitelistIps())
                .containsExactly("127.0.0.1", "::1");
    }

    @Test
    @DisplayName("setter/getter 往返：每个字段独立覆盖")
    void setterRoundTrip() {
        RateLimitProperties p = new RateLimitProperties();
        p.setEnabled(false);
        p.setPathPatterns(List.of("/v1/**"));
        p.setExcludePathPatterns(List.of("/v1/internal/**"));
        p.setRedisName("ratelimit-redis");
        p.setDefaultLimit(500);
        p.setDefaultWindow("5m");
        p.setDefaultAlgorithm("token_bucket");
        p.setDefaultScope("user");
        p.setIncludeHeaders(false);
        p.setKeyPrefix("rl");
        p.setWhitelistPaths(List.of("/internal/**"));
        p.setWhitelistIps(List.of("10.0.0.1"));

        assertThat(p.isEnabled()).isFalse();
        assertThat(p.getPathPatterns()).containsExactly("/v1/**");
        assertThat(p.getExcludePathPatterns()).containsExactly("/v1/internal/**");
        assertThat(p.getRedisName()).isEqualTo("ratelimit-redis");
        assertThat(p.getDefaultLimit()).isEqualTo(500);
        assertThat(p.getDefaultWindow()).isEqualTo("5m");
        assertThat(p.getDefaultAlgorithm()).isEqualTo("token_bucket");
        assertThat(p.getDefaultScope()).isEqualTo("user");
        assertThat(p.isIncludeHeaders()).isFalse();
        assertThat(p.getKeyPrefix()).isEqualTo("rl");
        assertThat(p.getWhitelistPaths()).containsExactly("/internal/**");
        assertThat(p.getWhitelistIps()).containsExactly("10.0.0.1");
    }

    @Test
    @DisplayName("边界：defaultLimit = 0 / 负数 不做防御性校验（@Data 不强制）")
    void defaultLimitZeroOrNegative() {
        RateLimitProperties p = new RateLimitProperties();
        p.setDefaultLimit(0);
        assertThat(p.getDefaultLimit()).isZero();
        p.setDefaultLimit(-1);
        assertThat(p.getDefaultLimit()).isEqualTo(-1);
    }

    @Test
    @DisplayName("边界：whitelist 设为 null 后 getter 返回 null（不重新归零）")
    void whitelistNullIsKept() {
        RateLimitProperties p = new RateLimitProperties();
        p.setWhitelistPaths(null);
        p.setWhitelistIps(null);
        assertThat(p.getWhitelistPaths()).isNull();
        assertThat(p.getWhitelistIps()).isNull();
    }

    @Test
    @DisplayName("边界：defaultWindow 空字符串 / 自定义格式不校验")
    void defaultWindowFreeForm() {
        RateLimitProperties p = new RateLimitProperties();
        p.setDefaultWindow("");
        assertThat(p.getDefaultWindow()).isEmpty();
        p.setDefaultWindow("anything-not-a-duration");
        assertThat(p.getDefaultWindow()).isEqualTo("anything-not-a-duration");
    }

    @Test
    @DisplayName("注解：@ConfigurationProperties prefix = framework4j.rate-limit")
    void annotationPrefix() {
        ConfigurationProperties ann =
                RateLimitProperties.class.getAnnotation(ConfigurationProperties.class);
        assertThat(ann).isNotNull();
        assertThat(ann.prefix()).isEqualTo("framework4j.rate-limit");
    }

    @Test
    @DisplayName("defaultLimit 边界：Integer.MAX_VALUE 不溢出（无算术）")
    void defaultLimitMaxInt() {
        RateLimitProperties p = new RateLimitProperties();
        p.setDefaultLimit(Integer.MAX_VALUE);
        assertThat(p.getDefaultLimit()).isEqualTo(Integer.MAX_VALUE);
    }
}
