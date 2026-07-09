package fun.commons.framework4j.ratelimit;

import fun.commons.framework4j.ratelimit.config.RateLimitProperties;
import fun.commons.framework4j.ratelimit.service.RateLimitKeyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RateLimitKeyResolver 测试（4 种 scope）
 *
 * @since 2.1.0
 */
@DisplayName("RateLimitKeyResolver 4 种 scope 测试")
class RateLimitKeyResolverTest {

    private RateLimitKeyResolver resolver;

    @BeforeEach
    void setUp() {
        RateLimitProperties props = new RateLimitProperties();
        props.setKeyPrefix("ratelimit");
        resolver = new RateLimitKeyResolver(props);
    }

    @Test
    @DisplayName("scope=ip：从 remoteAddr 取")
    void resolveIp() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/orders");
        req.setRemoteAddr("192.168.1.100");

        String key = resolver.resolve(req, "ip");
        assertThat(key).isEqualTo("ratelimit:ip:192.168.1.100:/v1/orders");
    }

    @Test
    @DisplayName("scope=ip：X-Forwarded-For 优先")
    void resolveIpWithXForwardedFor() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/orders");
        req.addHeader("X-Forwarded-For", "10.0.0.1, 192.168.1.1");
        req.setRemoteAddr("192.168.1.100");

        String key = resolver.resolve(req, "ip");
        assertThat(key).contains("10.0.0.1");
        assertThat(key).doesNotContain("192.168.1.100");
    }

    @Test
    @DisplayName("scope=user：从 X-User-Id 取")
    void resolveUser() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/orders");
        req.addHeader("X-User-Id", "u-12345");

        String key = resolver.resolve(req, "user");
        assertThat(key).isEqualTo("ratelimit:user:u-12345:/v1/orders");
    }

    @Test
    @DisplayName("scope=user：缺 X-User-Id 回退 IP（不再 anonymous）")
    void resolveUserFallback() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/orders");
        req.setRemoteAddr("9.9.9.9");
        String key = resolver.resolve(req, "user");
        // v2.1 P0: scope 降级为 ip，dimension 是真实 IP
        assertThat(key).contains("ratelimit:ip:9.9.9.9:");
        assertThat(key).doesNotContain("anonymous");
    }

    @Test
    @DisplayName("scope=app：从 X-Access-Key 取")
    void resolveApp() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/orders");
        req.addHeader("X-Access-Key", "app-001");

        String key = resolver.resolve(req, "app");
        assertThat(key).isEqualTo("ratelimit:app:app-001:/v1/orders");
    }

    @Test
    @DisplayName("scope=global：固定 'global'")
    void resolveGlobal() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/orders");
        String key = resolver.resolve(req, "global");
        assertThat(key).isEqualTo("ratelimit:global:global:/v1/orders");
    }

    @Test
    @DisplayName("scope=app：缺 X-Access-Key 走 unknown")
    void resolveAppFallback() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/orders");
        String key = resolver.resolve(req, "app");
        assertThat(key).contains("unknown");
    }

    @Test
    @DisplayName("scope=null：走默认（ip）")
    void resolveNullScope() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/orders");
        req.setRemoteAddr("1.2.3.4");
        String key = resolver.resolve(req, null);
        assertThat(key).startsWith("ratelimit:ip:");
    }
}
