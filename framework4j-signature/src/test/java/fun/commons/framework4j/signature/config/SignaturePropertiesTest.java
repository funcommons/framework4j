package fun.commons.framework4j.signature.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SignatureProperties 默认值 / 覆盖 / 边界值 / 嵌套配置测试。
 *
 * @since 2.1.0
 */
@DisplayName("SignatureProperties 配置默认值与覆盖测试")
class SignaturePropertiesTest {

    @Test
    @DisplayName("默认值：与文档对齐")
    void defaults() {
        SignatureProperties p = new SignatureProperties();
        assertThat(p.isEnabled()).isTrue();
        assertThat(p.getTimestampToleranceMs()).isEqualTo(300_000L);
        assertThat(p.getNonceTtlSeconds()).isEqualTo(600L);
        assertThat(p.getPathPatterns()).isEmpty();
        assertThat(p.getExcludePathPatterns()).isEmpty();
        assertThat(p.getNonceKeyPrefix()).isEqualTo("signature:nonce");
        assertThat(p.getRedisName()).isEqualTo("default");

        SignatureProperties.HeaderNames hn = p.getHeaderNames();
        assertThat(hn.getAccessKey()).isEqualTo("X-Access-Key");
        assertThat(hn.getTimestamp()).isEqualTo("X-Timestamp");
        assertThat(hn.getNonce()).isEqualTo("X-Nonce");
        assertThat(hn.getSignature()).isEqualTo("X-Signature");
    }

    @Test
    @DisplayName("顶层字段 setter 往返")
    void topLevelRoundTrip() {
        SignatureProperties p = new SignatureProperties();
        p.setEnabled(false);
        p.setTimestampToleranceMs(60_000L);
        p.setNonceTtlSeconds(120L);
        p.setPathPatterns(List.of("/v1/sign/**"));
        p.setExcludePathPatterns(List.of("/v1/health"));
        p.setNonceKeyPrefix("sn");
        p.setRedisName("sig-redis");

        assertThat(p.isEnabled()).isFalse();
        assertThat(p.getTimestampToleranceMs()).isEqualTo(60_000L);
        assertThat(p.getNonceTtlSeconds()).isEqualTo(120L);
        assertThat(p.getPathPatterns()).containsExactly("/v1/sign/**");
        assertThat(p.getExcludePathPatterns()).containsExactly("/v1/health");
        assertThat(p.getNonceKeyPrefix()).isEqualTo("sn");
        assertThat(p.getRedisName()).isEqualTo("sig-redis");
    }

    @Test
    @DisplayName("HeaderNames 嵌套字段 setter 往返")
    void headerNamesRoundTrip() {
        SignatureProperties p = new SignatureProperties();
        SignatureProperties.HeaderNames hn = p.getHeaderNames();
        hn.setAccessKey("X-AK");
        hn.setTimestamp("X-Ts");
        hn.setNonce("X-Nc");
        hn.setSignature("X-Sig");

        assertThat(p.getHeaderNames().getAccessKey()).isEqualTo("X-AK");
        assertThat(p.getHeaderNames().getTimestamp()).isEqualTo("X-Ts");
        assertThat(p.getHeaderNames().getNonce()).isEqualTo("X-Nc");
        assertThat(p.getHeaderNames().getSignature()).isEqualTo("X-Sig");
    }

    @Test
    @DisplayName("HeaderNames 替换整个对象生效")
    void replaceHeaderNames() {
        SignatureProperties p = new SignatureProperties();
        SignatureProperties.HeaderNames custom = new SignatureProperties.HeaderNames();
        custom.setAccessKey("X-AK2");
        p.setHeaderNames(custom);
        assertThat(p.getHeaderNames()).isSameAs(custom);
        assertThat(p.getHeaderNames().getAccessKey()).isEqualTo("X-AK2");
    }

    @Test
    @DisplayName("边界：timestampToleranceMs = 0 / 负数 保留")
    void toleranceBoundary() {
        SignatureProperties p = new SignatureProperties();
        p.setTimestampToleranceMs(0L);
        assertThat(p.getTimestampToleranceMs()).isZero();
        p.setTimestampToleranceMs(-1L);
        assertThat(p.getTimestampToleranceMs()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("边界：null list / null string 保留")
    void nullValuesKept() {
        SignatureProperties p = new SignatureProperties();
        p.setPathPatterns(null);
        p.setExcludePathPatterns(null);
        p.setNonceKeyPrefix(null);
        p.setRedisName(null);
        p.setHeaderNames(null);
        assertThat(p.getPathPatterns()).isNull();
        assertThat(p.getExcludePathPatterns()).isNull();
        assertThat(p.getNonceKeyPrefix()).isNull();
        assertThat(p.getRedisName()).isNull();
        assertThat(p.getHeaderNames()).isNull();
    }

    @Test
    @DisplayName("注解：prefix = framework4j.signature")
    void annotationPrefix() {
        ConfigurationProperties ann =
                SignatureProperties.class.getAnnotation(ConfigurationProperties.class);
        assertThat(ann).isNotNull();
        assertThat(ann.prefix()).isEqualTo("framework4j.signature");
    }

    @Test
    @DisplayName("HeaderNames 默认实例独立：多次 new 不共享字段引用")
    void headerNamesInstancesAreIndependent() {
        SignatureProperties a = new SignatureProperties();
        SignatureProperties b = new SignatureProperties();
        a.getHeaderNames().setAccessKey("Mutated");
        assertThat(b.getHeaderNames().getAccessKey()).isEqualTo("X-Access-Key");
    }
}
