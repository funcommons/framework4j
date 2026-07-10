package fun.commons.framework4j.ratelimit;

import fun.commons.framework4j.ratelimit.config.RateLimitProperties;
import fun.commons.framework4j.ratelimit.exception.RateLimitException;
import fun.commons.framework4j.ratelimit.interceptor.RateLimitInterceptor;
import fun.commons.framework4j.ratelimit.service.RateLimitKeyResolver;
import fun.commons.framework4j.ratelimit.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RateLimitInterceptor 单元测试 — 覆盖响应头三件套（X-RateLimit-* + Retry-After）。
 *
 * <p>v2.2 之前失败路径只设 Retry-After，X-RateLimit-* 缺失，违反 README「三件套」承诺。
 */
class RateLimitInterceptorTest {

    private RateLimitInterceptor interceptor;
    private RateLimitService stubService;
    private RateLimitProperties properties;
    private Object dummyHandler;

    @BeforeEach
    void setUp() throws Exception {
        properties = new RateLimitProperties();
        properties.setIncludeHeaders(true);
        properties.setDefaultLimit(3);
        properties.setDefaultWindow("1m");
        properties.setDefaultScope("ip");

        // stubRateLimitService 直接返回结果，绕过 Redis
        stubService = new RateLimitService(org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class)) {
            @Override
            public AcquireResult tryAcquire(String key, int limit, long windowMs) {
                if (key.endsWith(":limited")) {
                    return new AcquireResult(false, limit, limit, System.currentTimeMillis() + 30000);
                }
                return new AcquireResult(true, 1, limit, System.currentTimeMillis() + 60000);
            }
        };

        RateLimitKeyResolver keyResolver = new RateLimitKeyResolver(properties) {
            @Override
            public String resolve(HttpServletRequest request, String scope) {
                if ("5.5.5.5".equals(request.getRemoteAddr())) return "test:limited";
                return "test:ok";
            }
        };

        interceptor = new RateLimitInterceptor(stubService, keyResolver, properties);

        // 找一个有 @RateLimit 注解的 stub controller
        Method m = StubController.class.getMethod("limited");
        dummyHandler = new HandlerMethod(new StubController(), m);
    }

    @Test
    @DisplayName("v2.2 成功路径：响应头三件套 + 无 Retry-After")
    void successPath_hasAllThreeHeaders() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/x");
        req.setRemoteAddr("1.1.1.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean passed = interceptor.preHandle(req, resp, dummyHandler);

        assertThat(passed).isTrue();
        assertThat(resp.getHeader("X-RateLimit-Limit")).isEqualTo("3");
        assertThat(resp.getHeader("X-RateLimit-Remaining")).isEqualTo("2");
        assertThat(resp.getHeader("X-RateLimit-Reset")).isNotNull();
        assertThat(resp.getHeader("Retry-After")).isNull();
    }

    @Test
    @DisplayName("v2.2 失败路径：响应头三件套 + Retry-After 全设")
    void failPath_hasAllFourHeaders() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/x");
        req.setRemoteAddr("5.5.5.5");   // 触发 :limited 分支
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean passed = interceptor.preHandle(req, resp, dummyHandler);

        assertThat(passed).isFalse();
        assertThat(resp.getStatus()).isEqualTo(429);
        // v2.2 P1 修复：失败路径也要三件套
        assertThat(resp.getHeader("X-RateLimit-Limit")).isEqualTo("3");
        assertThat(resp.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(resp.getHeader("X-RateLimit-Reset")).isNotNull();
        assertThat(resp.getHeader("Retry-After")).isEqualTo("30");
    }

    @Test
    @DisplayName("includeHeaders=false 时不设任何响应头")
    void includeHeadersFalse_noHeaders() throws Exception {
        properties.setIncludeHeaders(false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/x");
        req.setRemoteAddr("1.1.1.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        interceptor.preHandle(req, resp, dummyHandler);

        assertThat(resp.getHeader("X-RateLimit-Limit")).isNull();
        assertThat(resp.getHeader("X-RateLimit-Remaining")).isNull();
        assertThat(resp.getHeader("X-RateLimit-Reset")).isNull();
    }

    @SuppressWarnings("unused")
    static class StubController {
        @fun.commons.framework4j.ratelimit.annotation.RateLimit(limit = 3, window = "1m", scope = "ip")
        public void limited() {}
    }
}