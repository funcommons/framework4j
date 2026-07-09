package fun.commons.framework4j.signature;

import fun.commons.framework4j.signature.util.MacUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MacUtil 测试（ThreadLocal<Mac> + reset 防残留）
 *
 * @since 2.1.0
 */
@DisplayName("MacUtil 单元测试")
class MacUtilTest {

    @Test
    @DisplayName("相同输入产生相同 HMAC")
    void shouldProduceSameHmacForSameInput() {
        byte[] key = "secret-key-1234567890".getBytes(StandardCharsets.UTF_8);
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);

        byte[] h1 = MacUtil.hmacSha256(key, data);
        byte[] h2 = MacUtil.hmacSha256(key, data);

        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(32);  // SHA-256 = 32 字节
    }

    @Test
    @DisplayName("不同 secret 产生不同 HMAC")
    void shouldProduceDifferentHmacForDifferentKey() {
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);

        byte[] h1 = MacUtil.hmacSha256("key1".getBytes(StandardCharsets.UTF_8), data);
        byte[] h2 = MacUtil.hmacSha256("key2".getBytes(StandardCharsets.UTF_8), data);

        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    @DisplayName("reset 后无残留状态（连续调用不影响结果）")
    void shouldResetMacStateBetweenCalls() {
        byte[] key = "secret-key".getBytes(StandardCharsets.UTF_8);

        // 连续多次调用，验证每次都从干净状态开始
        byte[] h1 = MacUtil.hmacSha256(key, "data1".getBytes(StandardCharsets.UTF_8));
        byte[] h2 = MacUtil.hmacSha256(key, "data2".getBytes(StandardCharsets.UTF_8));
        byte[] h1Again = MacUtil.hmacSha256(key, "data1".getBytes(StandardCharsets.UTF_8));

        assertThat(h1).isEqualTo(h1Again);
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    @DisplayName("BASE64 编码长度为 44 字符（32 字节 → Base64）")
    void shouldEncodeToBase64() {
        String b64 = MacUtil.hmacSha256Base64("secret", "data");
        assertThat(b64).hasSize(44);
        // 应是合法 Base64
        assertThat(java.util.Base64.getDecoder().decode(b64)).hasSize(32);
    }

    @Test
    @DisplayName("ThreadLocal 隔离：多线程并发调用无干扰")
    void shouldBeThreadSafe() throws Exception {
        int threads = 20;
        Thread[] ts = new Thread[threads];
        byte[][] results = new byte[threads][];
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            ts[i] = new Thread(() -> {
                try {
                    results[idx] = MacUtil.hmacSha256(
                            "shared-key".getBytes(StandardCharsets.UTF_8),
                            ("data-" + idx).getBytes(StandardCharsets.UTF_8));
                } finally {
                    latch.countDown();
                }
            });
            ts[i].start();
        }
        latch.await();

        // 每个线程结果不同（不同 data）
        for (int i = 1; i < threads; i++) {
            assertThat(results[i]).isNotEqualTo(results[i - 1]);
        }
    }
}
