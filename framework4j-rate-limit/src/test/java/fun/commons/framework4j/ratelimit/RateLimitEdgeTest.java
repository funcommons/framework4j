package fun.commons.framework4j.ratelimit;

import fun.commons.framework4j.ratelimit.config.RateLimitProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RateLimit 边界测试")
class RateLimitEdgeTest {
    @Test @DisplayName("limit=1 极端值")
    void limitOne() {
        RateLimitProperties p = new RateLimitProperties();
        p.setDefaultLimit(1);
        assertThat(p.getDefaultLimit()).isEqualTo(1);
    }
    @Test @DisplayName("limit=0")
    void limitZero() {
        RateLimitProperties p = new RateLimitProperties();
        p.setDefaultLimit(0);
        assertThat(p.getDefaultLimit()).isZero();
    }
    @Test @DisplayName("window=1ms")
    void windowOneMs() {
        RateLimitProperties p = new RateLimitProperties();
        p.setDefaultWindow("1ms");
        assertThat(p.getDefaultWindow()).isEqualTo("1ms");
    }
    @Test @DisplayName("scope=global")
    void scopeGlobal() {
        RateLimitProperties p = new RateLimitProperties();
        p.setDefaultScope("global");
        assertThat(p.getDefaultScope()).isEqualTo("global");
    }
    @Test @DisplayName("algorithm=fixed_window")
    void algorithmFixed() {
        RateLimitProperties p = new RateLimitProperties();
        p.setDefaultAlgorithm("fixed_window");
        assertThat(p.getDefaultAlgorithm()).isEqualTo("fixed_window");
    }
    @Test @DisplayName("includeHeaders=false")
    void headersDisabled() {
        RateLimitProperties p = new RateLimitProperties();
        p.setIncludeHeaders(false);
        assertThat(p.isIncludeHeaders()).isFalse();
    }
    @Test @DisplayName("白名单含多个通配符")
    void whitelistMulti() {
        RateLimitProperties p = new RateLimitProperties();
        p.setWhitelistPaths(java.util.List.of("/a/**", "/b/**", "/c/**"));
        assertThat(p.getWhitelistPaths()).hasSize(3);
    }
}
