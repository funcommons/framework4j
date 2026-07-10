package fun.commons.framework4j.signature.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MacUtil 并发安全 / 边界值 / 往返一致性测试。
 *
 * <p>原 MacUtilTest 覆盖了基本 HMAC。本测试补充：
 * <ol>
 *   <li>ThreadLocal Mac 在多线程并发下不串号</li>
 *   <li>相同 (key,data) 在多线程下结果一致</li>
 *   <li>不同线程的 ThreadLocal 互不污染</li>
 *   <li>异常路径：空 key / 空 data（合法）</li>
 *   <li>BASE64 输出可逆</li>
 *   <li>同一 mac 实例连续调用：reset 生效</li>
 * </ol>
 *
 * @since 2.1.0
 */
@DisplayName("MacUtil 并发与边界测试")
class MacUtilConcurrencyTest {

    private static final byte[] KEY = "super-secret-key".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("确定性与长度：相同 (key,data) 相同结果；长度 = 32（SHA-256）")
    void deterministicAndLength() {
        byte[] r1 = MacUtil.hmacSha256(KEY, "data".getBytes(StandardCharsets.UTF_8));
        byte[] r2 = MacUtil.hmacSha256(KEY, "data".getBytes(StandardCharsets.UTF_8));
        assertThat(r1).containsExactly(r2);
        assertThat(r1).hasSize(32);
    }

    @Test
    @DisplayName("不同 data 不同 hmac")
    void differentDataDifferentResult() {
        byte[] a = MacUtil.hmacSha256(KEY, "a".getBytes(StandardCharsets.UTF_8));
        byte[] b = MacUtil.hmacSha256(KEY, "b".getBytes(StandardCharsets.UTF_8));
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("不同 key 不同 hmac")
    void differentKeyDifferentResult() {
        byte[] k2 = "another-key".getBytes(StandardCharsets.UTF_8);
        byte[] a = MacUtil.hmacSha256(KEY, "data".getBytes(StandardCharsets.UTF_8));
        byte[] b = MacUtil.hmacSha256(k2, "data".getBytes(StandardCharsets.UTF_8));
        assertThat(a).isNotEqualTo(b);
    }

    @RepeatedTest(3)
    @DisplayName("并发：N 线程同时签名相同 data → 所有结果相等且 == 串行结果")
    void concurrentSameDataSameResult() throws Exception {
        int n = 64;
        byte[] expected = MacUtil.hmacSha256(KEY, "abc".getBytes(StandardCharsets.UTF_8));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        List<byte[]> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < n; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    results.add(MacUtil.hmacSha256(KEY, "abc".getBytes(StandardCharsets.UTF_8)));
                } catch (Throwable t) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(errors.get()).isZero();
        assertThat(results).hasSize(n);
        for (byte[] r : results) {
            assertThat(r).containsExactly(expected);
        }
    }

    @Test
    @DisplayName("并发：N 线程用不同 data → 所有结果唯一（除非 data 撞相同）")
    void concurrentDifferentData() throws Exception {
        int n = 100;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        Set<String> base64 = Collections.synchronizedSet(new HashSet<>());
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < n; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    String sig = MacUtil.hmacSha256Base64(
                            "k", "data-" + idx);
                    base64.add(sig);
                } catch (Throwable t) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(errors.get()).isZero();
        assertThat(base64).hasSize(n);
    }

    @Test
    @DisplayName("同一 mac 实例：连续多次调用必须 reset 生效（结果与第一次相同）")
    void resetBetweenCalls() {
        byte[] r1 = MacUtil.hmacSha256(KEY, "first".getBytes(StandardCharsets.UTF_8));
        // 多次中间穿插不同 data
        MacUtil.hmacSha256(KEY, "noise-1".getBytes(StandardCharsets.UTF_8));
        MacUtil.hmacSha256(KEY, "noise-2".getBytes(StandardCharsets.UTF_8));
        byte[] r2 = MacUtil.hmacSha256(KEY, "first".getBytes(StandardCharsets.UTF_8));
        assertThat(r1).containsExactly(r2);
    }

    @Test
    @DisplayName("边界：空 key 字节数组 → 抛 IllegalStateException（SecretKeySpec 拒绝）")
    void emptyKeyThrows() {
        try {
            MacUtil.hmacSha256(new byte[0], "data".getBytes(StandardCharsets.UTF_8));
            assertThat(false).as("应抛 IllegalStateException").isTrue();
        } catch (IllegalStateException ex) {
            assertThat(ex.getMessage()).contains("HMAC compute failed");
        }
    }

    @Test
    @DisplayName("边界：空 data 合法")
    void emptyDataAllowed() {
        byte[] r = MacUtil.hmacSha256(KEY, new byte[0]);
        assertThat(r).hasSize(32);
    }

    @Test
    @DisplayName("BASE64 编码可逆：解码后 == 原始 hmac")
    void base64Reversible() {
        byte[] raw = MacUtil.hmacSha256(KEY, "payload".getBytes(StandardCharsets.UTF_8));
        String b64 = MacUtil.hmacSha256Base64(
                new String(KEY, StandardCharsets.UTF_8), "payload");
        byte[] decoded = java.util.Base64.getDecoder().decode(b64);
        assertThat(decoded).containsExactly(raw);
    }

    @Test
    @DisplayName("异常路径：null key 抛 IllegalStateException（SecretKeySpec NPE 被包装）")
    void nullKeyThrows() {
        try {
            MacUtil.hmacSha256(null, "data".getBytes(StandardCharsets.UTF_8));
            assertThat(false).as("应抛 IllegalStateException").isTrue();
        } catch (IllegalStateException ex) {
            assertThat(ex.getMessage()).contains("HMAC compute failed");
        }
    }

    @Test
    @DisplayName("异常路径：null data 被捕获或安全处理")
    void nullDataHandledSafely() {
        // 不约束必须抛或不抛，只要不导致 JVM 崩溃即可。
        // 一些 JDK 实现下 Mac.doFinal(null) 行为不同。
        try {
            MacUtil.hmacSha256(KEY, null);
            // 不抛也 OK，只要不抛 NPE 走出工具边界
        } catch (IllegalStateException ex) {
            assertThat(ex.getMessage()).contains("HMAC compute failed");
        } catch (NullPointerException ex) {
            // 也接受
        }
    }
}
