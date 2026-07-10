package fun.commons.framework4j.sensitive;

import fun.commons.framework4j.sensitive.util.AesGcmCryptoUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * AES-256-GCM 加解密边界测试
 *
 * @since 2.1.0
 */
@DisplayName("AES-256-GCM 边界测试")
class AesGcmCryptoUtilBoundaryTest {

    private static final byte[] KEY = AesGcmCryptoUtil.deriveKey("test-key-for-aes-256-gcm-boundary");

    @Test
    @DisplayName("空字符串加密 → 解密返回空字符串")
    void emptyStringRoundTrip() {
        String cipher = AesGcmCryptoUtil.encrypt(KEY, "");
        assertThat(AesGcmCryptoUtil.decrypt(KEY, cipher)).isEmpty();
    }

    @Test
    @DisplayName("中文字符串加解密")
    void chineseStringRoundTrip() {
        String plain = "身份证:110101199001011234 手机:13812345678";
        String cipher = AesGcmCryptoUtil.encrypt(KEY, plain);
        assertThat(AesGcmCryptoUtil.decrypt(KEY, cipher)).isEqualTo(plain);
    }

    @Test
    @DisplayName("超长字符串（1MB）加解密")
    void largeStringRoundTrip() {
        String plain = "x".repeat(1024 * 1024);
        String cipher = AesGcmCryptoUtil.encrypt(KEY, plain);
        assertThat(AesGcmCryptoUtil.decrypt(KEY, cipher)).isEqualTo(plain);
    }

    @Test
    @DisplayName("特殊字符（emoji / 换行 / tab）加解密")
    void specialCharsRoundTrip() {
        String plain = "🎉\n\t\r\\\"'<>{}[]()";
        String cipher = AesGcmCryptoUtil.encrypt(KEY, plain);
        assertThat(AesGcmCryptoUtil.decrypt(KEY, cipher)).isEqualTo(plain);
    }

    @Test
    @DisplayName("密文 Base64 格式校验")
    void ciphertextIsBase64() {
        String cipher = AesGcmCryptoUtil.encrypt(KEY, "test");
        assertThatCode(() -> Base64.getDecoder().decode(cipher)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("密文长度 > 明文（IV + Tag 开销）")
    void cipherLongerThanPlain() {
        String plain = "short";
        String cipher = AesGcmCryptoUtil.encrypt(KEY, plain);
        byte[] cipherBytes = Base64.getDecoder().decode(cipher);
        // IV(12) + ciphertext(plain.length) + tag(16) >= 12 + 5 + 16 = 33
        assertThat(cipherBytes.length).isGreaterThan(plain.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    @DisplayName("篡改密文 → 解密失败")
    void tamperedCipherFails() {
        String cipher = AesGcmCryptoUtil.encrypt(KEY, "secret");
        byte[] raw = Base64.getDecoder().decode(cipher);
        raw[raw.length - 1] ^= 0x01; // 翻转 tag 最后一位
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> AesGcmCryptoUtil.decrypt(KEY, tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decrypt failed");
    }

    @Test
    @DisplayName("截断密文 → 解密失败")
    void truncatedCipherFails() {
        String cipher = AesGcmCryptoUtil.encrypt(KEY, "secret");
        String truncated = cipher.substring(0, cipher.length() / 2);
        assertThatThrownBy(() -> AesGcmCryptoUtil.decrypt(KEY, truncated))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("无效 Base64 → 解密失败")
    void invalidBase64Fails() {
        assertThatThrownBy(() -> AesGcmCryptoUtil.decrypt(KEY, "!!!not-base64!!!"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("deriveKey 相同输入 → 相同输出")
    void deriveKeyDeterministic() {
        byte[] k1 = AesGcmCryptoUtil.deriveKey("same-key-padding-padding-padding-pad!");
        byte[] k2 = AesGcmCryptoUtil.deriveKey("same-key-padding-padding-padding-pad!");
        assertThat(k1).isEqualTo(k2);
        assertThat(k1).hasSize(32); // AES-256
    }

    @Test
    @DisplayName("deriveKey 不同输入 → 不同输出")
    void deriveKeyDifferent() {
        byte[] k1 = AesGcmCryptoUtil.deriveKey("key1-padding-padding-padding-padding-pad!");
        byte[] k2 = AesGcmCryptoUtil.deriveKey("key2-padding-padding-padding-padding-pad!");
        assertThat(k1).isNotEqualTo(k2);
    }

    @Test
    @DisplayName("错误密钥 → GCM Tag 校验失败")
    void wrongKeyFails() {
        byte[] key1 = AesGcmCryptoUtil.deriveKey("correct-key-padding-padding-padding-pad!");
        byte[] key2 = AesGcmCryptoUtil.deriveKey("wrong-key-padding-padding-padding-pad!");
        String cipher = AesGcmCryptoUtil.encrypt(key1, "secret");
        assertThatThrownBy(() -> AesGcmCryptoUtil.decrypt(key2, cipher))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("并发加解密（ThreadLocal<Cipher> 线程安全）")
    void concurrentEncryptDecrypt() throws Exception {
        int threads = 20;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.atomic.AtomicInteger failures = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    String plain = "concurrent-" + idx;
                    String cipher = AesGcmCryptoUtil.encrypt(KEY, plain);
                    String decrypted = AesGcmCryptoUtil.decrypt(KEY, cipher);
                    if (!plain.equals(decrypted)) failures.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();
        assertThat(failures.get()).isZero();
    }
}
