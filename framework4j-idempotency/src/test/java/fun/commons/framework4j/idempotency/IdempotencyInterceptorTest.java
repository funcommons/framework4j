package fun.commons.framework4j.idempotency;

import fun.commons.framework4j.api.ApiCode;
import fun.commons.framework4j.web.cache.CachedBodyRequestWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IdempotencyInterceptor 单元测试（mock StringRedisTemplate）
 */
@DisplayName("IdempotencyInterceptor 单元测试")
class IdempotencyInterceptorTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private IdempotencyInterceptor interceptor;
    private IdempotencyProperties properties;

    private static final String VALID_UUID = "7f8e9a4b-c2d1-4a8e-b3c5-1d2e3f4a5b6c";
    private static final String BAD_UUID = "not-a-uuid";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        properties = new IdempotencyProperties();
        properties.setEnabled(true);
        properties.setTtlSeconds(60L);

        interceptor = new IdempotencyInterceptor(redisTemplate, properties, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    @DisplayName("无 header = 放行，不触 Redis")
    void noHeaderPassesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/orders");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(req, resp, new Object()));
        verifyNoInteractions(valueOps);
    }

    @Test
    @DisplayName("非法 UUID v4 = 400 PARAM_FORMAT_ERROR")
    void invalidUuidReturns400() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/orders");
        req.addHeader("Idempotency-Key", BAD_UUID);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(req, resp, new Object()));
        assertEquals(400, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("\"code\":" + ApiCode.PARAM_FORMAT_ERROR.getCode()));
    }

    // v2.1 P1: 参数化覆盖 UUID v4 正则边界
    @org.junit.jupiter.params.ParameterizedTest(name = "[{index}] uuid=\"{0}\"")
    @org.junit.jupiter.api.DisplayName("UUID v4 校验：非法格式 = 400")
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "not-a-uuid",                                          // 普通字符串
            "12345678-1234-1234-1234-123456789012",                // 第3段不以4开头（v1）
            "7f8e9a4b-c2d1-38e-b3c5-1d2e3f4a5b6c",                  // 第3段以3开头
            "7f8e9a4b-c2d1-4a8e-c3c5-1d2e3f4a5b6c",                  // 第4段以c开头（应8/9/a/b）
            "7f8e9a4b-c2d1-4a8e-b3c5-1d2e3f4a5b6",                  // 长度不足
            "7f8e9a4b-c2d1-4a8e-b3c5-1d2e3f4a5b6c1",                // 长度超长
            "7f8e9a4bc2d14a8eb3c51d2e3f4a5b6c",                     // 无分隔符
            "",                                                     // 空字符串
            "g1234567-1234-4234-8234-123456789012",                // 含非法 hex 字符 g
    })
    void invalidUuidVariantsReturn400(String uuid) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/orders");
        req.addHeader("Idempotency-Key", uuid);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        // 空字符串走 key.isEmpty() → 放行（return true），不返 400
        if (uuid.isEmpty()) {
            assertTrue(interceptor.preHandle(req, resp, new Object()),
                    "空字符串 header 应放行（key == null || key.isEmpty()）");
            return;
        }
        // 非法 UUID 应返 400
        assertFalse(interceptor.preHandle(req, resp, new Object()),
                "非法 UUID 应返 false: " + uuid);
        assertEquals(400, resp.getStatus(), "应返 400: " + uuid);
    }

    @Test
    @DisplayName("首次请求 = Lua SETNX PENDING 成功 = 放行")
    void firstRequestSetsPending() throws Exception {
        // v2.1: Lua 脚本返回 null 表示首次设置成功
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(null);

        MockHttpServletRequest rawReq = new MockHttpServletRequest("POST", "/api/orders");
        rawReq.addHeader("Idempotency-Key", VALID_UUID);
        rawReq.setContent("{\"a\":1}".getBytes());
        // v2.1 P0: hashBody 用 CachedBodyRequestWrapper（Filter 已 cacheBody），测试需 wrap + cacheBody
        fun.commons.framework4j.web.cache.CachedBodyRequestWrapper req =
                new fun.commons.framework4j.web.cache.CachedBodyRequestWrapper(rawReq);
        req.cacheBody();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(req, resp, new Object()));

        // v2.1: 验证 Lua execute 被调用（替代原 setIfAbsent）
        verify(redisTemplate).execute(any(), anyList(), any(Object[].class));
    }

    @Test
    @DisplayName("v1.2.7: 重入守卫 —— 同一请求第二次 preHandle 直接放行，不再触 Redis（双注册场景锁定）")
    void reentrantSameRequestPassesWithoutRedis() throws Exception {
        // 首次 SETNX 成功（Lua 返回 null）
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(null);

        MockHttpServletRequest rawReq = new MockHttpServletRequest("POST", "/api/orders");
        rawReq.addHeader("Idempotency-Key", VALID_UUID);
        rawReq.setContent("{\"a\":1}".getBytes());
        CachedBodyRequestWrapper req = new CachedBodyRequestWrapper(rawReq);
        req.cacheBody();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        // 第一次进入：SETNX 成功，放行
        assertTrue(interceptor.preHandle(req, resp, new Object()));
        // 第二次进入（模拟拦截器被重复注册）：重入守卫直接放行，不应再读 Redis 后 409 自己
        assertTrue(interceptor.preHandle(req, resp, new Object()));
        // Redis Lua 只被调用一次（第二次进入未触 Redis）
        verify(redisTemplate, times(1)).execute(any(), anyList(), any(Object[].class));
        assertEquals(200, resp.getStatus());
    }

    @Test
    @DisplayName("v1.2.7: PENDING 并发态 → 409 且消息提示稍后重试（区别于普通重复提交）")
    void pendingConcurrentStateReturns409WithRetryHint() throws Exception {
        // bodyHashRequired=false → bodyHash="*"；已存在值 hash 匹配但状态为 PENDING
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn("*|PENDING");
        properties.setBodyHashRequired(false);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/orders");
        req.addHeader("Idempotency-Key", VALID_UUID);
        req.setContent("{\"a\":1}".getBytes());
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(req, resp, new Object()));
        assertEquals(409, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("仍在处理中"),
                "PENDING 并发态消息应提示稍后重试，实际: " + resp.getContentAsString());
    }

    @Test
    @DisplayName("同 key + 同 body + 已 OK = 回放缓存响应")
    void replayCachedBody() throws Exception {
        String cachedResp = "{\"code\":0,\"data\":{\"id\":42}}";
        // v2.1: Lua 返回已存在值 = hash|OK:body
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn("*|OK:" + cachedResp);

        properties.setBodyHashRequired(false);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/orders");
        req.addHeader("Idempotency-Key", VALID_UUID);
        req.setContent("{\"a\":1}".getBytes());
        MockHttpServletResponse resp = new MockHttpServletResponse();

        req.setContent(new byte[0]);

        assertFalse(interceptor.preHandle(req, resp, new Object()));
        assertEquals(200, resp.getStatus());
        assertEquals(cachedResp, resp.getContentAsString());
    }

    @Test
    @DisplayName("同 key + 不同 body = 409 DUPLICATE_SUBMIT")
    void differentBodySameKeyReturns409() throws Exception {
        // v2.1: Lua 返回已存在值 = OTHER_HASH|PENDING（与当前 bodyHash "*" 不同 → 409）
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn("OTHER_HASH|PENDING");

        properties.setBodyHashRequired(false);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/orders");
        req.addHeader("Idempotency-Key", VALID_UUID);
        req.setContent("{\"a\":1}".getBytes());
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(req, resp, new Object()));
        assertEquals(409, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("\"code\":" + ApiCode.DUPLICATE_SUBMIT.getCode()));
    }

    @Test
    @DisplayName("afterCompletion 2xx 写入 OK:body marker（R6 修复回归）")
    void afterCompletionSuccessWritesOkMarker() throws Exception {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(null);

        MockHttpServletRequest rawReq = new MockHttpServletRequest("POST", "/api/orders");
        rawReq.addHeader("Idempotency-Key", VALID_UUID);
        rawReq.setContent("{\"a\":1}".getBytes());
        CachedBodyRequestWrapper req = new CachedBodyRequestWrapper(rawReq);
        req.cacheBody();
        org.springframework.web.util.ContentCachingResponseWrapper resp =
                new org.springframework.web.util.ContentCachingResponseWrapper(new MockHttpServletResponse());

        assertTrue(interceptor.preHandle(req, resp, new Object()));
        // 模拟 Controller 写响应体
        resp.getWriter().write("{\"code\":0,\"data\":{\"id\":1}}");
        resp.setStatus(200);

        interceptor.afterCompletion(req, resp, new Object(), null);

        // 验证写入了 hash|OK:body marker
        verify(valueOps).set(anyString(), contains("OK:"), eq(properties.getTtlSeconds()), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("afterCompletion 非 2xx 删除 redisKey（R6 P1 修复回归）")
    void afterCompletionNon2xxDeletesRedisKey() throws Exception {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(null);

        MockHttpServletRequest rawReq = new MockHttpServletRequest("POST", "/api/orders");
        rawReq.addHeader("Idempotency-Key", VALID_UUID);
        rawReq.setContent("{\"a\":1}".getBytes());
        CachedBodyRequestWrapper req = new CachedBodyRequestWrapper(rawReq);
        req.cacheBody();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(req, resp, new Object()));
        resp.setStatus(500);  // 非 2xx

        interceptor.afterCompletion(req, resp, new Object(), null);

        // R6 修复：非 2xx 应 delete redisKey，让客户端可重试
        verify(redisTemplate).delete(anyString());
        // 不应写入 marker
        verify(valueOps, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("afterCompletion 异常时删除 redisKey（R6 P1 修复回归）")
    void afterCompletionExceptionDeletesRedisKey() throws Exception {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(null);

        MockHttpServletRequest rawReq = new MockHttpServletRequest("POST", "/api/orders");
        rawReq.addHeader("Idempotency-Key", VALID_UUID);
        rawReq.setContent("{\"a\":1}".getBytes());
        CachedBodyRequestWrapper req = new CachedBodyRequestWrapper(rawReq);
        req.cacheBody();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(req, resp, new Object()));

        // Controller 抛异常
        interceptor.afterCompletion(req, resp, new Object(), new RuntimeException("NPE"));

        verify(redisTemplate).delete(anyString());
        verify(valueOps, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("并发：50 线程同 key 同 body 同时 preHandle，应只 1 放行（R6 修复核心）")
    void concurrentSameKeyOnlyOnePasses() throws Exception {
        // Lua 返回 null（首次）→ 放行；后续线程 Lua 返回 hash|PENDING → 409
        // 模拟：第一次 execute 返回 null，后续返回 "hash|PENDING"
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenAnswer(inv -> {
            int n = callCount.incrementAndGet();
            return n == 1 ? null : "abc123|PENDING";
        });

        int threadCount = 50;
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicInteger passCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger rejectCount = new java.util.concurrent.atomic.AtomicInteger(0);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        MockHttpServletRequest rawReq = new MockHttpServletRequest("POST", "/api/orders");
                        rawReq.addHeader("Idempotency-Key", VALID_UUID);
                        rawReq.setContent("{\"a\":1}".getBytes());
                        CachedBodyRequestWrapper req = new CachedBodyRequestWrapper(rawReq);
                        req.cacheBody();
                        MockHttpServletResponse resp = new MockHttpServletResponse();

                        startLatch.await();
                        boolean pass = interceptor.preHandle(req, resp, new Object());
                        if (pass) passCount.incrementAndGet();
                        else rejectCount.incrementAndGet();
                    } catch (Exception e) {
                        rejectCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(30, java.util.concurrent.TimeUnit.SECONDS),
                    "所有线程应在 30s 内完成");

            assertEquals(1, passCount.get(), "应只 1 个线程放行，实际: " + passCount.get());
            assertEquals(threadCount - 1, rejectCount.get(), "其余应被拒绝");
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
        }
    }
}
