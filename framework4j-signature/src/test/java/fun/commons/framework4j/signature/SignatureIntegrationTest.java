package fun.commons.framework4j.signature;

import fun.commons.framework4j.signature.config.SignatureProperties;
import fun.commons.framework4j.signature.service.InMemorySecretProvider;
import fun.commons.framework4j.signature.service.SecretProvider;
import fun.commons.framework4j.signature.service.SignatureService;
import fun.commons.framework4j.signature.util.BodyMd5Util;
import fun.commons.framework4j.signature.util.SignatureUtil;
import fun.commons.framework4j.web.cache.CachedBodyRequestWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Signature + RateLimit 端到端集成测试（embedded Redis）
 * <p>
 * 7 个场景覆盖 mc-java-security §6 五步校验 + mc-api-spec §8.5 限流。
 *
 * @since 2.1.0
 */
@SpringBootTest
@ActiveProfiles("integration-test")
class SignatureIntegrationTest {

    private static final String ACCESS_KEY = "test-app";
    private static final String SECRET = "test-secret-key-for-signature-integration-1234567890";

    @SpringBootApplication
    @Import(SignatureIntegrationTest.TestApp.class)
    static class TestApplication {}

    @TestConfiguration
    static class TestApp {
        @Bean
        public SecretProvider secretProvider() {
            InMemorySecretProvider provider = new InMemorySecretProvider();
            provider.register(ACCESS_KEY, SECRET);
            return provider;
        }
    }

    @Autowired
    private SignatureService signatureService;

    @Autowired
    private SignatureProperties signatureProperties;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanRedis() {
        Set<String> keys = redisTemplate.keys(signatureProperties.getNonceKeyPrefix() + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("场景1: 完整合法请求 → 通过")
    void scenario1_validRequest_passes() {
        MockHttpServletRequest req = buildValidRequest("POST", "/v1/api/orders", "{\"a\":1}");
        CachedBodyRequestWrapper wrapped = wrap(req);
        assertThatCodeDoesNotThrow(() -> signatureService.validate(wrapped));
    }

    @Test
    @DisplayName("场景2: 缺失 Header → 10101 PARAM_MISSING")
    void scenario2_missingHeader_throws10101() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/api/orders");
        // 无任何签名头
        fun.commons.framework4j.signature.exception.SignatureException ex = assertThrows(
                fun.commons.framework4j.signature.exception.SignatureException.class,
                () -> signatureService.validate(req));
        assertThat(ex.getCode()).isEqualTo(10101);
    }

    @Test
    @DisplayName("场景3: 时间戳过期 → 10102 PARAM_FORMAT_ERROR")
    void scenario3_expiredTimestamp_throws10102() {
        MockHttpServletRequest req = buildValidRequest("POST", "/v1/api/orders", null);
        long oldTs = System.currentTimeMillis() - 600_000;  // 10 分钟前
        req.removeHeader("X-Timestamp");
        req.addHeader("X-Timestamp", String.valueOf(oldTs));

        // 重算签名（用旧 timestamp）
        long ts = oldTs;
        String nonce = req.getHeader("X-Nonce");
        String bodyMd5 = BodyMd5Util.md5Hex(new byte[0]);
        String stringToSign = SignatureUtil.buildStringToSign("POST", "/v1/api/orders",
                String.valueOf(ts), nonce, bodyMd5);
        String sig = SignatureUtil.sign(SECRET, stringToSign);
        req.removeHeader("X-Signature");
        req.addHeader("X-Signature", sig);

        fun.commons.framework4j.signature.exception.SignatureException ex = assertThrows(
                fun.commons.framework4j.signature.exception.SignatureException.class,
                () -> signatureService.validate(req));
        assertThat(ex.getCode()).isEqualTo(10102);
    }

    @Test
    @DisplayName("场景4: 重复 nonce → 10302 SIGNATURE_ERROR（重放检测）")
    void scenario4_replayedNonce_throws10302() throws Exception {
        MockHttpServletRequest req = buildValidRequest("POST", "/v1/api/orders", null);
        CachedBodyRequestWrapper wrapped = wrap(req);

        // 首次校验通过
        assertThatCodeDoesNotThrow(() -> signatureService.validate(wrapped));

        // 同 nonce 再次校验 → 重放检测
        fun.commons.framework4j.signature.exception.SignatureException ex = assertThrows(
                fun.commons.framework4j.signature.exception.SignatureException.class,
                () -> signatureService.validate(wrapped));
        assertThat(ex.getCode()).isEqualTo(10302);
    }

    @Test
    @DisplayName("场景5: 错误签名 → 10302 SIGNATURE_ERROR（不匹配）")
    void scenario5_wrongSignature_throws10302() {
        MockHttpServletRequest req = buildValidRequest("POST", "/v1/api/orders", null);
        req.removeHeader("X-Signature");
        req.addHeader("X-Signature", "tampered-signature-base64-xxxxx");

        fun.commons.framework4j.signature.exception.SignatureException ex = assertThrows(
                fun.commons.framework4j.signature.exception.SignatureException.class,
                () -> signatureService.validate(req));
        assertThat(ex.getCode()).isEqualTo(10302);
    }

    @Test
    @DisplayName("场景6: 未知 AccessKey → 10200 UNAUTHORIZED")
    void scenario6_unknownAccessKey_throws10200() {
        MockHttpServletRequest req = buildValidRequest("POST", "/v1/api/orders", null);
        req.removeHeader("X-Access-Key");
        req.addHeader("X-Access-Key", "unknown-app");

        fun.commons.framework4j.signature.exception.SignatureException ex = assertThrows(
                fun.commons.framework4j.signature.exception.SignatureException.class,
                () -> signatureService.validate(req));
        assertThat(ex.getCode()).isEqualTo(10200);
    }

    @Test
    @DisplayName("场景7: 改 body 后旧签名失效 → 10302（body MD5 校验）")
    void scenario7_tamperedBody_throws10302() throws Exception {
        MockHttpServletRequest req = buildValidRequest("POST", "/v1/api/orders", "{\"a\":1}");
        // 改 body 但不重算签名
        req.setContent("{\"a\":2,\"hacked\":true}".getBytes());
        CachedBodyRequestWrapper wrapped = wrap(req);

        fun.commons.framework4j.signature.exception.SignatureException ex = assertThrows(
                fun.commons.framework4j.signature.exception.SignatureException.class,
                () -> signatureService.validate(wrapped));
        assertThat(ex.getCode()).isEqualTo(10302);
    }

    // ==================== 辅助方法 ====================

    private MockHttpServletRequest buildValidRequest(String method, String path, String body) {
        long ts = System.currentTimeMillis();
        String nonce = java.util.UUID.randomUUID().toString();
        byte[] bodyBytes = body != null ? body.getBytes() : new byte[0];
        String bodyMd5 = BodyMd5Util.md5Hex(bodyBytes);
        String stringToSign = SignatureUtil.buildStringToSign(method, path,
                String.valueOf(ts), nonce, bodyMd5);
        String signature = SignatureUtil.sign(SECRET, stringToSign);

        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.addHeader("X-Access-Key", ACCESS_KEY);
        req.addHeader("X-Timestamp", String.valueOf(ts));
        req.addHeader("X-Nonce", nonce);
        req.addHeader("X-Signature", signature);
        if (body != null) req.setContent(bodyBytes);
        return req;
    }

    private CachedBodyRequestWrapper wrap(MockHttpServletRequest req) {
        try {
            CachedBodyRequestWrapper wrapped = new CachedBodyRequestWrapper(req);
            wrapped.cacheBody();
            return wrapped;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertThatCodeDoesNotThrow(Runnable r) {
        try { r.run(); }
        catch (Exception e) { throw new AssertionError("不应抛异常: " + e.getMessage(), e); }
    }
}
