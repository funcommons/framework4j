package fun.commons.framework4j.signature;

import fun.commons.framework4j.signature.config.SignatureProperties;
import fun.commons.framework4j.signature.exception.SignatureException;
import fun.commons.framework4j.signature.service.InMemorySecretProvider;
import fun.commons.framework4j.signature.service.SignatureService;
import fun.commons.framework4j.signature.util.SignatureUtil;
import fun.commons.framework4j.web.cache.CachedBodyRequestWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * SignatureService 五步校验单元测试（mock Redis）
 *
 * @since 2.1.0
 */
@DisplayName("SignatureService 五步校验测试")
class SignatureServiceTest {

    private StringRedisTemplate redisTemplate;
    private SignatureProperties properties;
    private InMemorySecretProvider secretProvider;
    private SignatureService service;

    private static final String SECRET = "test-secret-key-1234567890";
    private static final String ACCESS_KEY = "app-1";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        properties = new SignatureProperties();
        properties.setTimestampToleranceMs(60_000);  // 1min
        properties.setNonceTtlSeconds(600);

        secretProvider = new InMemorySecretProvider();
        secretProvider.register(ACCESS_KEY, SECRET);

        service = new SignatureService(redisTemplate, properties, secretProvider);
    }

    /** 构造合法请求并 mock Redis nonce 通过 */
    private MockHttpServletRequest buildValidRequest(String body) {
        long ts = System.currentTimeMillis();
        String nonce = java.util.UUID.randomUUID().toString();
        String bodyMd5 = fun.commons.framework4j.signature.util.BodyMd5Util.md5Hex(
                body != null ? body.getBytes() : new byte[0]);
        String stringToSign = SignatureUtil.buildStringToSign("POST", "/v1/orders",
                String.valueOf(ts), nonce, bodyMd5);
        String signature = SignatureUtil.sign(SECRET, stringToSign);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/orders");
        req.addHeader("X-Access-Key", ACCESS_KEY);
        req.addHeader("X-Timestamp", String.valueOf(ts));
        req.addHeader("X-Nonce", nonce);
        req.addHeader("X-Signature", signature);
        if (body != null) req.setContent(body.getBytes());
        return req;
    }

    @Test
    @DisplayName("1) Header 缺失 → PARAM_MISSING")
    void shouldThrowWhenHeaderMissing() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/orders");
        // 完全无 Header
        assertThatThrownBy(() -> service.validate(req))
                .isInstanceOf(SignatureException.class)
                .hasMessageContaining("缺失");
    }

    @Test
    @DisplayName("1b) 仅 AccessKey 缺失 → PARAM_MISSING")
    void shouldThrowWhenAccessKeyMissing() {
        MockHttpServletRequest req = buildValidRequest(null);
        // 移除 AccessKey
        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/v1/orders");
        req2.addHeader("X-Timestamp", req.getHeader("X-Timestamp"));
        req2.addHeader("X-Nonce", req.getHeader("X-Nonce"));
        req2.addHeader("X-Signature", req.getHeader("X-Signature"));
        assertThatThrownBy(() -> service.validate(req2))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("2) timestamp 过期 → PARAM_FORMAT_ERROR")
    void shouldThrowWhenTimestampExpired() {
        MockHttpServletRequest req = buildValidRequest(null);
        // 改 timestamp 为 1 小时前
        long oldTs = System.currentTimeMillis() - 3_600_000L;
        req.removeHeader("X-Timestamp");
        req.addHeader("X-Timestamp", String.valueOf(oldTs));

        assertThatThrownBy(() -> service.validate(req))
                .isInstanceOf(SignatureException.class)
                .hasMessageContaining("时间戳");
    }

    @Test
    @DisplayName("2b) timestamp 格式错误 → PARAM_FORMAT_ERROR")
    void shouldThrowWhenTimestampNotNumber() {
        MockHttpServletRequest req = buildValidRequest(null);
        req.removeHeader("X-Timestamp");
        req.addHeader("X-Timestamp", "not-a-number");
        assertThatThrownBy(() -> service.validate(req))
                .isInstanceOf(SignatureException.class)
                .hasMessageContaining("格式");
    }

    @Test
    @DisplayName("3) nonce 重放（Redis 返回 0）→ SIGNATURE_ERROR")
    void shouldThrowWhenNonceReplayed() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(0L);

        MockHttpServletRequest req = buildValidRequest(null);
        assertThatThrownBy(() -> service.validate(req))
                .isInstanceOf(SignatureException.class)
                .hasMessageContaining("nonce");
    }

    @Test
    @DisplayName("3b) Redis 异常 → MIDDLEWARE_ERROR")
    void shouldThrowWhenRedisFails() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("connection refused"));

        MockHttpServletRequest req = buildValidRequest(null);
        assertThatThrownBy(() -> service.validate(req))
                .isInstanceOf(SignatureException.class)
                .hasMessageContaining("中间件");
    }

    @Test
    @DisplayName("4) AccessKey 未知 → UNAUTHORIZED")
    void shouldThrowWhenAccessKeyUnknown() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        MockHttpServletRequest req = buildValidRequest(null);
        req.removeHeader("X-Access-Key");
        req.addHeader("X-Access-Key", "unknown-app");
        assertThatThrownBy(() -> service.validate(req))
                .isInstanceOf(SignatureException.class)
                .hasMessageContaining("AccessKey");
    }

    @Test
    @DisplayName("5) 签名不匹配 → SIGNATURE_ERROR")
    void shouldThrowWhenSignatureMismatch() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        MockHttpServletRequest req = buildValidRequest(null);
        req.removeHeader("X-Signature");
        req.addHeader("X-Signature", "tampered-signature-value-base64-xxx");
        assertThatThrownBy(() -> service.validate(req))
                .isInstanceOf(SignatureException.class)
                .hasMessageContaining("不匹配");
    }

    @Test
    @DisplayName("完整流程：合法请求 + Redis nonce 通过 + CachedBodyRequestWrapper → 通过")
    void shouldPassWithValidRequestAndCachedBody() throws Exception {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        String body = "{\"order_id\":\"abc\",\"amount\":100}";
        MockHttpServletRequest raw = buildValidRequest(body);

        // 用实际 body 重算签名（mock setContent 后）
        String bodyMd5 = fun.commons.framework4j.signature.util.BodyMd5Util.md5Hex(body.getBytes());
        String stringToSign = SignatureUtil.buildStringToSign("POST", "/v1/orders",
                raw.getHeader("X-Timestamp"), raw.getHeader("X-Nonce"), bodyMd5);
        String signature = SignatureUtil.sign(SECRET, stringToSign);

        // 直接重建请求（MockHttpServletRequest 不支持 removeHeader）
        MockHttpServletRequest finalReq = new MockHttpServletRequest("POST", "/v1/orders");
        finalReq.addHeader("X-Access-Key", ACCESS_KEY);
        finalReq.addHeader("X-Timestamp", raw.getHeader("X-Timestamp"));
        finalReq.addHeader("X-Nonce", raw.getHeader("X-Nonce"));
        finalReq.addHeader("X-Signature", signature);
        finalReq.setContent(body.getBytes());

        CachedBodyRequestWrapper finalWrapped = new CachedBodyRequestWrapper(finalReq);
        finalWrapped.cacheBody();

        assertThatCode(() -> service.validate(finalWrapped)).doesNotThrowAnyException();
    }
}
