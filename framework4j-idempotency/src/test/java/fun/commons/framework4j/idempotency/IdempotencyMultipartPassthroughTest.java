package fun.commons.framework4j.idempotency;

import fun.commons.framework4j.web.cache.CachedBodyRequestWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingResponseWrapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * v1.7.2 幂等契约回归（2026-09-21-04 BL03 P1-1）：multipart 请求整体透传。
 * <p>契约口径（与 token-gateway IdempotencyWebFilter 同步收窄）：幂等语义仅覆盖 JSON 请求体，
 * multipart 上传不在幂等保护范围 —— 拦截器不做任何裁决（不校验键、不触 Redis、不读 body），
 * BodyCacheFilter 不包装不缓存（保住 multipart boundary 流式解析）。
 */
@DisplayName("Idempotency multipart 透传契约 (v1.7.2)")
class IdempotencyMultipartPassthroughTest {

    private static final String VALID_UUID = "7f8e9a4b-c2d1-4a8e-b3c5-1d2e3f4a5b6c";
    private static final String MULTIPART_CONTENT_TYPE = "multipart/form-data; boundary=----JavaFormBoundary7MA4YWxk";

    private StringRedisTemplate redisTemplate;
    private IdempotencyInterceptor interceptor;
    private IdempotencyProperties properties;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        properties = new IdempotencyProperties();
        properties.setEnabled(true);
        properties.setTtlSeconds(60L);
        interceptor = new IdempotencyInterceptor(redisTemplate, properties,
                new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private MockHttpServletRequest multipartRequest(String key) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tenant/oss/upload/image");
        req.setContentType(MULTIPART_CONTENT_TYPE);
        if (key != null) {
            req.addHeader("Idempotency-Key", key);
        }
        req.setContent("--boundary\r\ncontent-disposition: form-data; name=\"file\"; filename=\"a.png\"\r\n"
                .getBytes());
        return req;
    }

    @Test
    @DisplayName("multipart POST + 合法键 = 拦截器直放行，不触 Redis 不读 body")
    void multipartWithValidKeyPassesThrough() throws Exception {
        MockHttpServletRequest req = multipartRequest(VALID_UUID);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(req, resp, new Object()));
        assertEquals(200, resp.getStatus());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("multipart POST + 非法键 = 仍放行（裁决跳过先于 UUID 校验，不误伤 400）")
    void multipartWithMalformedKeyStillPasses() throws Exception {
        MockHttpServletRequest req = multipartRequest("not-a-uuid");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(req, resp, new Object()));
        assertEquals(200, resp.getStatus());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("multipart POST + 重放态同键 = 仍透传（multipart 不进任何裁决分支）")
    void multipartIgnoresExistingIdempotencyState() throws Exception {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn("*|PENDING");

        MockHttpServletRequest req = multipartRequest(VALID_UUID);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(req, resp, new Object()));
        assertEquals(200, resp.getStatus());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("JSON 语义回归：application/json + 合法键 = 照常裁决（Lua SETNX 被调用）")
    void jsonRequestsStillAdjudicated() throws Exception {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(null);

        MockHttpServletRequest rawReq = new MockHttpServletRequest("POST", "/api/orders");
        rawReq.setContentType("application/json");
        rawReq.addHeader("Idempotency-Key", VALID_UUID);
        rawReq.setContent("{\"a\":1}".getBytes());
        CachedBodyRequestWrapper req = new CachedBodyRequestWrapper(rawReq);
        req.cacheBody();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(req, resp, new Object()));
        verify(redisTemplate).execute(any(), anyList(), any(Object[].class));
    }

    @Test
    @DisplayName("multipart Content-Type 大小写混合 = 同样透传（前缀匹配容错）")
    void multipartContentTypeCaseInsensitive() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/upload");
        req.setContentType("Multipart/Form-Data; boundary=x");

        assertTrue(IdempotencyBodyCacheFilter.isMultipart(req));
    }

    @Test
    @DisplayName("缺失/畸形 Content-Type = 非 multipart（JSON 语义兜底不扩大透传面）")
    void missingOrMalformedContentTypeIsNotMultipart() {
        MockHttpServletRequest missing = new MockHttpServletRequest("POST", "/api/orders");
        assertFalse(IdempotencyBodyCacheFilter.isMultipart(missing));

        MockHttpServletRequest malformed = new MockHttpServletRequest("POST", "/api/orders");
        malformed.setContentType("multipart");
        assertFalse(IdempotencyBodyCacheFilter.isMultipart(malformed));
    }

    @Test
    @DisplayName("过滤器：multipart 请求原样透传 —— request/response 均不被包装，body 不被读")
    void filterSkipsWrappingForMultipart() throws Exception {
        IdempotencyBodyCacheFilter filter = new IdempotencyBodyCacheFilter();
        MockHttpServletRequest req = multipartRequest(VALID_UUID);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertSame(req, chain.getRequest(), "multipart 请求必须原样进链（不得包 CachedBodyRequestWrapper）");
        assertSame(resp, chain.getResponse(), "multipart 响应必须原样进链（不得包 ContentCachingResponseWrapper）");
    }

    @Test
    @DisplayName("过滤器回归：JSON 请求仍被包装缓存 + 响应统一刷出（v2.1 行为不变）")
    void filterStillWrapsJsonRequests() throws Exception {
        IdempotencyBodyCacheFilter filter = new IdempotencyBodyCacheFilter();
        MockHttpServletRequest rawReq = new MockHttpServletRequest("POST", "/api/orders");
        rawReq.setContentType("application/json");
        rawReq.setContent("{\"a\":1}".getBytes());
        MockHttpServletResponse rawResp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(rawReq, rawResp, chain);

        assertInstanceOf(CachedBodyRequestWrapper.class, chain.getRequest(),
                "JSON 请求必须仍被 CachedBodyRequestWrapper 包装");

        // 拦截器 hashBody 依赖：包装后的请求能拿到缓存 body
        CachedBodyRequestWrapper wrapped = (CachedBodyRequestWrapper) chain.getRequest();
        assertArrayEquals("{\"a\":1}".getBytes(), wrapped.getContentAsByteArray());

        // chain 内写响应 → finally copyBodyToResponse 刷到真实 response（回放链路依赖）
        ContentCachingResponseWrapper wrappedResp =
                (ContentCachingResponseWrapper) chain.getResponse();
        wrappedResp.getWriter().write("{\"code\":0}");
        wrappedResp.copyBodyToResponse();
        assertEquals("{\"code\":0}", rawResp.getContentAsString());
    }

    @Test
    @DisplayName("端到端：multipart 带键请求在过滤器放行后，拦截器同样直放行（宿主故障场景闭环）")
    void endToEndMultipartWithKeyNeverEntersAdjudication() throws Exception {
        IdempotencyBodyCacheFilter filter = new IdempotencyBodyCacheFilter();
        MockHttpServletRequest req = multipartRequest(VALID_UUID);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);
        // 拦截器拿到的是过滤链放行后的原样请求（无 wrapper）—— 此前该场景
        // hashBody fail-secure 抛 IOException → 500(10001)；现应直接透传
        assertTrue(interceptor.preHandle(
                (jakarta.servlet.http.HttpServletRequest) chain.getRequest(),
                (jakarta.servlet.http.HttpServletResponse) chain.getResponse(), new Object()));
        verifyNoInteractions(redisTemplate);
    }
}
