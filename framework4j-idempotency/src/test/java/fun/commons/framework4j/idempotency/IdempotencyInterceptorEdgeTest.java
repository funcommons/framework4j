package fun.commons.framework4j.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.api.ApiCode;
import fun.commons.framework4j.web.cache.CachedBodyRequestWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * IdempotencyInterceptor 边界路径补充测试。
 *
 * <p>原 IdempotencyInterceptorTest 已覆盖主要 path。本测试补充：
 * <ol>
 *   <li>GET 方法直接放行（v2.2 P1：仅写方法生效）</li>
 *   <li>PUT/PATCH/DELETE 也走幂等校验</li>
 *   <li>OPTIONS / HEAD 直接放行</li>
 *   <li>bodyHashRequired=true 且无 wrapper → 抛 IOException</li>
 *   <li>异常缓存值（无 '|' 分隔符）→ 409</li>
 *   <li>响应已 OK 但 hash 不同 → 409</li>
 *   <li>空 body + bodyHashRequired=false → 放行（hash 为 '*'）</li>
 *   <li>自定义 headerName 生效</li>
 * </ol>
 *
 * @since 2.1.0
 */
@DisplayName("IdempotencyInterceptor 边界路径测试")
class IdempotencyInterceptorEdgeTest {

    private StringRedisTemplate redisTemplate;
    private IdempotencyInterceptor interceptor;
    private IdempotencyProperties properties;

    private static final String VALID_UUID = "7f8e9a4b-c2d1-4a8e-b3c5-1d2e3f4a5b6c";

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        properties = new IdempotencyProperties();
        properties.setEnabled(true);
        properties.setBodyHashRequired(false);
        properties.setTtlSeconds(60L);
        interceptor = new IdempotencyInterceptor(redisTemplate, properties, new ObjectMapper());
    }

    @Test
    @DisplayName("GET 方法直接放行（即使有 Idempotency-Key）")
    void getMethodSkipsCheck() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        req.addHeader("Idempotency-Key", VALID_UUID);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean pass = interceptor.preHandle(req, resp, new Object());
        assertThat(pass).isTrue();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("OPTIONS / HEAD 方法直接放行")
    void optionsHeadSkipped() throws Exception {
        for (String m : new String[]{"OPTIONS", "HEAD"}) {
            MockHttpServletRequest req = new MockHttpServletRequest(m, "/api/orders");
            req.addHeader("Idempotency-Key", VALID_UUID);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean pass = interceptor.preHandle(req, resp, new Object());
            assertThat(pass).as("method %s 应放行", m).isTrue();
        }
    }

    @Test
    @DisplayName("PUT / PATCH / DELETE 也走幂等校验（写方法白名单）")
    void putPatchDeleteAreChecked() throws Exception {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(null);
        for (String m : new String[]{"PUT", "PATCH", "DELETE"}) {
            MockHttpServletRequest rawReq = new MockHttpServletRequest(m, "/api/orders");
            rawReq.addHeader("Idempotency-Key", VALID_UUID);
            rawReq.setContent("{\"a\":1}".getBytes());
            CachedBodyRequestWrapper req = new CachedBodyRequestWrapper(rawReq);
            req.cacheBody();
            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean pass = interceptor.preHandle(req, resp, new Object());
            assertThat(pass).as("method %s 首次应放行", m).isTrue();
        }
    }

    @Test
    @DisplayName("headerName 自定义：使用 properties 配置的 header")
    void customHeaderName() throws Exception {
        properties.setHeaderName("X-Idem-Key");
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(null);

        MockHttpServletRequest rawReq = new MockHttpServletRequest("POST", "/api/x");
        rawReq.addHeader("X-Idem-Key", VALID_UUID);
        rawReq.setContent("{}".getBytes());
        CachedBodyRequestWrapper req = new CachedBodyRequestWrapper(rawReq);
        req.cacheBody();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(req, resp, new Object())).isTrue();
    }

    @Test
    @DisplayName("响应已 OK 但 hash 不同 → 409")
    void okMarkerWithDifferentHashReturns409() throws Exception {
        // Lua 返回 otherHash|OK:cachedBody，当前 bodyHash='*'（bodyHashRequired=false）
        // → existingHash=otherHash, bodyHash='*'，不等 → 409
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenReturn("OTHERHASH|OK:{\"ok\":1}");

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/orders");
        req.addHeader("Idempotency-Key", VALID_UUID);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean pass = interceptor.preHandle(req, resp, new Object());
        assertThat(pass).isFalse();
        assertThat(resp.getStatus()).isEqualTo(409);
        assertThat(resp.getContentAsString())
                .contains("\"code\":" + ApiCode.DUPLICATE_SUBMIT.getCode());
    }

    @Test
    @DisplayName("异常缓存值（无 '|' 分隔符）→ 409")
    void malformedCacheValueReturns409() throws Exception {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenReturn("malformed-no-separator");

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/orders");
        req.addHeader("Idempotency-Key", VALID_UUID);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean pass = interceptor.preHandle(req, resp, new Object());
        assertThat(pass).isFalse();
        assertThat(resp.getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("PENDING 状态 + hash 不匹配 → 409")
    void pendingWithDifferentHashReturns409() throws Exception {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenReturn("OTHERHASH|PENDING");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/orders");
        req.addHeader("Idempotency-Key", VALID_UUID);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(req, resp, new Object())).isFalse();
        assertThat(resp.getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("bodyHashRequired=true 且无 wrapper → 抛 IOException（fail-secure）")
    void bodyHashRequiredWithoutWrapperThrows() throws Exception {
        properties.setBodyHashRequired(true);
        // 注意：MockHttpServletRequest 不是 ContentCachingRequestWrapper
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/orders");
        req.addHeader("Idempotency-Key", VALID_UUID);
        req.setContent("{}".getBytes());
        MockHttpServletResponse resp = new MockHttpServletResponse();

        try {
            interceptor.preHandle(req, resp, new Object());
            assertThat(false).as("应抛 IOException").isTrue();
        } catch (java.io.IOException ex) {
            assertThat(ex.getMessage()).contains("IdempotencyBodyCacheFilter");
        }
    }

    @Test
    @DisplayName("afterCompletion：未设置 attribute → 直接返回（无 Redis 操作）")
    void afterCompletionWithoutAttribute() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/orders");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        // 不调 preHandle，无 ATTR_REDIS_KEY
        interceptor.afterCompletion(req, resp, new Object(), null);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("空 body + bodyHashRequired=false → 放行（hash='*'）")
    void emptyBodyWithHashDisabledPasses() throws Exception {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(null);
        MockHttpServletRequest rawReq = new MockHttpServletRequest("POST", "/api/orders");
        rawReq.addHeader("Idempotency-Key", VALID_UUID);
        rawReq.setContent(new byte[0]);
        CachedBodyRequestWrapper req = new CachedBodyRequestWrapper(rawReq);
        req.cacheBody();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(req, resp, new Object())).isTrue();
    }

    @Test
    @DisplayName("响应已 OK + hash 匹配（bodyHashRequired=false 时 hash 都是 '*'）→ 200 回放")
    void okWithMatchingHashReplaysCachedBody() throws Exception {
        String cachedBody = "{\"code\":0,\"data\":1}";
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenReturn("*|OK:" + cachedBody);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/orders");
        req.addHeader("Idempotency-Key", VALID_UUID);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean pass = interceptor.preHandle(req, resp, new Object());
        assertThat(pass).isFalse();
        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(resp.getContentAsString()).isEqualTo(cachedBody);
    }
}
