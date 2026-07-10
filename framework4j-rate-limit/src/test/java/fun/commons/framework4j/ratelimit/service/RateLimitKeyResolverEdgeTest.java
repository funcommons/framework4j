package fun.commons.framework4j.ratelimit.service;

import fun.commons.framework4j.ratelimit.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RateLimitKeyResolver 边界 / 异常路径补充测试。
 *
 * <p>原 RateLimitKeyResolverTest 覆盖了 happy path 的 4 种 scope。本测试补充：
 * <ol>
 *   <li>X-Forwarded-For 单值（无逗号）</li>
 *   <li>X-Forwarded-For 为空字符串</li>
 *   <li>X-Forwarded-For 前导逗号</li>
 *   <li>X-Real-IP 优先级（X-Forwarded-For 缺失时）</li>
 *   <li>RemoteAddr 为 null → "unknown"</li>
 *   <li>未知 scope → fallback 到 ip（switch default）</li>
 *   <li>scope=空字符串 → 默认</li>
 *   <li>自定义 keyPrefix 生效</li>
 *   <li>URI 含 query string（getRequestURI 不含 query）</li>
 *   <li>X-User-Id 为空字符串 → fallback IP</li>
 *   <li>X-Access-Key 为空字符串 → unknown</li>
 * </ol>
 *
 * @since 2.1.0
 */
@DisplayName("RateLimitKeyResolver 边界路径测试")
class RateLimitKeyResolverEdgeTest {

    private RateLimitProperties props;
    private RateLimitKeyResolver resolver;

    @BeforeEach
    void setUp() {
        props = new RateLimitProperties();
        props.setKeyPrefix("ratelimit");
        resolver = new RateLimitKeyResolver(props);
    }

    @Test
    @DisplayName("X-Forwarded-For 单值（无逗号）：直接取整段")
    void xffSingleValue() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/x");
        req.addHeader("X-Forwarded-For", "10.0.0.55");
        req.setRemoteAddr("127.0.0.1");

        assertThat(resolver.resolve(req, "ip")).contains("10.0.0.55");
    }

    @Test
    @DisplayName("X-Forwarded-For 为空字符串：回退到 X-Real-IP")
    void xffEmpty() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/x");
        req.addHeader("X-Forwarded-For", "");
        req.addHeader("X-Real-IP", "10.5.5.5");
        req.setRemoteAddr("127.0.0.1");

        assertThat(resolver.resolve(req, "ip")).contains("10.5.5.5");
    }

    @Test
    @DisplayName("X-Forwarded-For 与 X-Real-IP 都缺失：用 remoteAddr")
    void fallBackToRemoteAddr() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/x");
        req.setRemoteAddr("172.16.0.1");
        assertThat(resolver.resolve(req, "ip")).contains("172.16.0.1");
    }

    @Test
    @DisplayName("remoteAddr 为 null：dimension 为 'unknown'")
    void remoteAddrNull() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/x");
        req.setRemoteAddr(null);
        String key = resolver.resolve(req, "ip");
        assertThat(key).contains("unknown");
    }

    @Test
    @DisplayName("未知 scope → switch default 使用 ip 解析逻辑，但保留原 scope 字符串")
    void unknownScopeFallsToIp() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/x");
        req.setRemoteAddr("1.1.1.1");
        String key = resolver.resolve(req, "totally-bogus");
        // dimension 走 default 分支 = resolveIp（1.1.1.1），但 scope 字符串保留
        assertThat(key).contains("1.1.1.1");
        assertThat(key).contains("totally-bogus");
    }

    @Test
    @DisplayName("scope 为空字符串 → 走默认 ip")
    void emptyScopeFallsToDefault() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/x");
        req.setRemoteAddr("2.2.2.2");
        props.setDefaultScope("global");
        String key = resolver.resolve(req, "");
        assertThat(key).startsWith("ratelimit:global:global:");
    }

    @Test
    @DisplayName("自定义 keyPrefix 生效")
    void customKeyPrefix() {
        props.setKeyPrefix("rl");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/x");
        String key = resolver.resolve(req, "global");
        assertThat(key).startsWith("rl:global:global:");
    }

    @Test
    @DisplayName("X-User-Id 为空字符串 → fallback ip")
    void emptyUserIdFallsBackToIp() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/x");
        req.addHeader("X-User-Id", "");
        req.setRemoteAddr("3.3.3.3");
        String key = resolver.resolve(req, "user");
        assertThat(key).startsWith("ratelimit:ip:3.3.3.3:");
    }

    @Test
    @DisplayName("X-Access-Key 为空字符串 → dimension='unknown'")
    void emptyAccessKeyBecomesUnknown() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/x");
        req.addHeader("X-Access-Key", "");
        String key = resolver.resolve(req, "app");
        assertThat(key).contains(":app:unknown:");
    }

    @Test
    @DisplayName("URI 多段路径完整保留")
    void multiSegmentUriPreserved() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/orders/123/items");
        String key = resolver.resolve(req, "global");
        assertThat(key).endsWith(":/v1/orders/123/items");
    }

    @Test
    @DisplayName("X-Forwarded-For 前导空格 + 多 IP：取第一个 trimmed")
    void xffWithLeadingSpaces() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/x");
        req.addHeader("X-Forwarded-For", "  8.8.8.8 , 9.9.9.9");
        req.setRemoteAddr("127.0.0.1");
        String key = resolver.resolve(req, "ip");
        assertThat(key).contains("8.8.8.8");
        assertThat(key).doesNotContain("9.9.9.9");
    }
}
