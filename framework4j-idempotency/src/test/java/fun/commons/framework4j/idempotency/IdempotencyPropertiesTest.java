package fun.commons.framework4j.idempotency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IdempotencyProperties 默认值 / 覆盖 / 边界值 / 注解元信息测试。
 *
 * @since 2.1.0
 */
@DisplayName("IdempotencyProperties 配置默认值与覆盖测试")
class IdempotencyPropertiesTest {

    @Test
    @DisplayName("默认值：与文档对齐（默认 enabled=false，opt-in）")
    void defaults() {
        IdempotencyProperties p = new IdempotencyProperties();
        assertThat(p.isEnabled()).isFalse();
        assertThat(p.getHeaderName()).isEqualTo("Idempotency-Key");
        assertThat(p.getKeyPrefix()).isEqualTo("idem");
        assertThat(p.getTtlSeconds()).isEqualTo(172_800L);
        assertThat(p.isBodyHashRequired()).isTrue();
        assertThat(p.getRedisName()).isEqualTo("default");
        assertThat(p.getPathPatterns()).containsExactly("/api/**");
        assertThat(p.getExcludePathPatterns()).isEmpty();
    }

    @Test
    @DisplayName("setter/getter 往返一致")
    void setterRoundTrip() {
        IdempotencyProperties p = new IdempotencyProperties();
        p.setEnabled(true);
        p.setHeaderName("X-Idem");
        p.setKeyPrefix("custom");
        p.setTtlSeconds(60L);
        p.setBodyHashRequired(false);
        p.setRedisName("idem-redis");
        p.setPathPatterns(List.of("/v1/orders/**", "/v1/pay/**"));
        p.setExcludePathPatterns(List.of("/v1/health"));

        assertThat(p.isEnabled()).isTrue();
        assertThat(p.getHeaderName()).isEqualTo("X-Idem");
        assertThat(p.getKeyPrefix()).isEqualTo("custom");
        assertThat(p.getTtlSeconds()).isEqualTo(60L);
        assertThat(p.isBodyHashRequired()).isFalse();
        assertThat(p.getRedisName()).isEqualTo("idem-redis");
        assertThat(p.getPathPatterns()).containsExactly("/v1/orders/**", "/v1/pay/**");
        assertThat(p.getExcludePathPatterns()).containsExactly("/v1/health");
    }

    @Test
    @DisplayName("边界：ttlSeconds = 0 / 负数 / Long.MAX_VALUE 保留（无校验）")
    void ttlBoundary() {
        IdempotencyProperties p = new IdempotencyProperties();
        p.setTtlSeconds(0L);
        assertThat(p.getTtlSeconds()).isZero();
        p.setTtlSeconds(-10L);
        assertThat(p.getTtlSeconds()).isEqualTo(-10L);
        p.setTtlSeconds(Long.MAX_VALUE);
        assertThat(p.getTtlSeconds()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("边界：null 字符串字段保留 null")
    void nullStringsKept() {
        IdempotencyProperties p = new IdempotencyProperties();
        p.setHeaderName(null);
        p.setKeyPrefix(null);
        p.setRedisName(null);
        assertThat(p.getHeaderName()).isNull();
        assertThat(p.getKeyPrefix()).isNull();
        assertThat(p.getRedisName()).isNull();
    }

    @Test
    @DisplayName("边界：空字符串 headerName / keyPrefix 保留")
    void emptyStringsKept() {
        IdempotencyProperties p = new IdempotencyProperties();
        p.setHeaderName("");
        p.setKeyPrefix("");
        assertThat(p.getHeaderName()).isEmpty();
        assertThat(p.getKeyPrefix()).isEmpty();
    }

    @Test
    @DisplayName("边界：pathPatterns / excludePathPatterns 设为 null")
    void nullListsKept() {
        IdempotencyProperties p = new IdempotencyProperties();
        p.setPathPatterns(null);
        p.setExcludePathPatterns(null);
        assertThat(p.getPathPatterns()).isNull();
        assertThat(p.getExcludePathPatterns()).isNull();
    }

    @Test
    @DisplayName("注解：@ConfigurationProperties prefix = framework4j.idempotency")
    void annotationPrefix() {
        ConfigurationProperties ann =
                IdempotencyProperties.class.getAnnotation(ConfigurationProperties.class);
        assertThat(ann).isNotNull();
        assertThat(ann.prefix()).isEqualTo("framework4j.idempotency");
    }

    @Test
    @DisplayName("默认值规则：每次 new 出来的实例彼此独立")
    void newInstancesAreIndependent() {
        IdempotencyProperties a = new IdempotencyProperties();
        IdempotencyProperties b = new IdempotencyProperties();
        a.setEnabled(true);
        a.setTtlSeconds(1L);
        assertThat(b.isEnabled()).isFalse();
        assertThat(b.getTtlSeconds()).isEqualTo(172_800L);
    }
}
