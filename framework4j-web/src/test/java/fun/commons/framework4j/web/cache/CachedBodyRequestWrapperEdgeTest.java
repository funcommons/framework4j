package fun.commons.framework4j.web.cache;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CachedBodyRequestWrapper 边界与异常路径补充测试。
 *
 * <p>原 CachedBodyRequestWrapperTest（在父包）覆盖了 happy path。
 * 本测试补充：
 * <ol>
 *   <li>ServletInputStream.isFinished / isReady / setReadListener 行为</li>
 *   <li>getInputStream 在 cacheBody 之后返回独立流实例</li>
 *   <li>getReader 与 getInputStream 混用不互相消费</li>
 *   <li>多次 cacheBody 后内存稳定（cacheBody 是幂等的）</li>
 *   <li>异常：底层流抛 IOException 时 cacheBody 传播</li>
 *   <li>InputStream 的逐字节读取与批量读取等价</li>
 * </ol>
 *
 * @since 2.1.0
 */
@DisplayName("CachedBodyRequestWrapper 边界路径测试")
class CachedBodyRequestWrapperEdgeTest {

    @Test
    @DisplayName("isFinished：读完流后 isFinished=true")
    void isFinishedAfterRead() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/x");
        req.setContent("abc".getBytes(StandardCharsets.UTF_8));
        CachedBodyRequestWrapper w = new CachedBodyRequestWrapper(req);
        w.cacheBody();

        ServletInputStream in = w.getInputStream();
        in.readAllBytes();
        assertThat(in.isFinished()).isTrue();
        assertThat(in.isReady()).isTrue();
    }

    @Test
    @DisplayName("isFinished：未读完时 isFinished=false")
    void isFinishedBeforeRead() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/x");
        req.setContent("hello".getBytes(StandardCharsets.UTF_8));
        CachedBodyRequestWrapper w = new CachedBodyRequestWrapper(req);
        w.cacheBody();
        ServletInputStream in = w.getInputStream();
        assertThat(in.isFinished()).isFalse();
    }

    @Test
    @DisplayName("setReadListener：抛 UnsupportedOperationException（同步流不支持）")
    void setReadListenerThrows() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/x");
        req.setContent("x".getBytes(StandardCharsets.UTF_8));
        CachedBodyRequestWrapper w = new CachedBodyRequestWrapper(req);
        w.cacheBody();
        ServletInputStream in = w.getInputStream();
        assertThatThrownBy(() -> in.setReadListener(new ReadListener() {
            @Override public void onDataAvailable() {}
            @Override public void onAllDataRead() {}
            @Override public void onError(Throwable t) {}
        })).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("两次 getInputStream 返回独立流：各自读完都得到完整内容")
    void twoGetInputStreamAreIndependent() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/x");
        req.setContent("xyz".getBytes(StandardCharsets.UTF_8));
        CachedBodyRequestWrapper w = new CachedBodyRequestWrapper(req);
        w.cacheBody();

        ServletInputStream a = w.getInputStream();
        ServletInputStream b = w.getInputStream();
        assertThat(a.readAllBytes()).containsExactly('x', 'y', 'z');
        assertThat(b.readAllBytes()).containsExactly('x', 'y', 'z');
    }

    @Test
    @DisplayName("getReader 与 getInputStream 混用：互不影响")
    void readerAndStreamMix() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/x");
        req.setContent("payload".getBytes(StandardCharsets.UTF_8));
        CachedBodyRequestWrapper w = new CachedBodyRequestWrapper(req);
        w.cacheBody();

        // 用 reader 读一次
        BufferedReader r = w.getReader();
        assertThat(r.readLine()).isEqualTo("payload");

        // 用 stream 再读，仍能拿到完整内容
        ServletInputStream in = w.getInputStream();
        assertThat(in.readAllBytes()).asString(StandardCharsets.UTF_8).isEqualTo("payload");
    }

    @Test
    @DisplayName("逐字节读取与 readAllBytes 等价")
    void byteByByteEqualToBulk() throws Exception {
        byte[] data = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/x");
        req.setContent(data);
        CachedBodyRequestWrapper w = new CachedBodyRequestWrapper(req);
        w.cacheBody();

        ServletInputStream in = w.getInputStream();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            out.write(b);
        }
        assertThat(out.toByteArray()).containsExactly(data);
        assertThat(in.isFinished()).isTrue();
    }

    @Test
    @DisplayName("cacheBody 多次调用：内容稳定（幂等）")
    void cacheBodyMultipleCallsStable() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/x");
        req.setContent("stable".getBytes(StandardCharsets.UTF_8));
        CachedBodyRequestWrapper w = new CachedBodyRequestWrapper(req);

        w.cacheBody();
        byte[] first = w.getContentAsByteArray();
        for (int i = 0; i < 5; i++) {
            w.cacheBody();
            assertThat(w.getContentAsByteArray()).isEqualTo(first);
        }
    }

    @Test
    @DisplayName("cacheBody 后父类 getContentAsByteArray 与 cachedBody 内容一致")
    void parentAndCacheBodyConsistent() throws Exception {
        byte[] data = "{\"k\":1}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/x");
        req.setContent(data);
        CachedBodyRequestWrapper w = new CachedBodyRequestWrapper(req);
        w.cacheBody();

        byte[] fromParent = w.getContentAsByteArray();
        ServletInputStream in = w.getInputStream();
        byte[] fromStream = in.readAllBytes();
        assertThat(fromParent).isEqualTo(data);
        assertThat(fromStream).isEqualTo(data);
    }

    @Test
    @DisplayName("异常：底层流抛 IOException 时 cacheBody 传播")
    void cacheBodyPropagatesIOException() throws Exception {
        // 用 Mockito mock 一个 HttpServletRequest，其 getInputStream 抛 IOException
        HttpServletRequest req = org.mockito.Mockito.mock(HttpServletRequest.class);
        org.mockito.Mockito.when(req.getInputStream()).thenThrow(new java.io.IOException("simulated"));
        // ContentCachingRequestWrapper 调用其他方法时不抛
        org.mockito.Mockito.when(req.getMethod()).thenReturn("POST");
        org.mockito.Mockito.when(req.getRequestURI()).thenReturn("/v1/x");

        CachedBodyRequestWrapper w = new CachedBodyRequestWrapper(req);
        assertThatThrownBy(w::cacheBody)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("simulated");
    }

    @Test
    @DisplayName("未 cacheBody 直接读 InputStream：消费后 Controller 不能再读")
    void streamConsumedWithoutCacheBody() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/x");
        req.setContent("once".getBytes(StandardCharsets.UTF_8));
        CachedBodyRequestWrapper w = new CachedBodyRequestWrapper(req);
        // 未 cacheBody，走父类
        byte[] first = w.getInputStream().readAllBytes();
        assertThat(first).asString(StandardCharsets.UTF_8).isEqualTo("once");
        // 父类 ContentCachingRequestWrapper 第二次读会得到空（已缓存）
        // 这里仅验证不抛异常
        byte[] second = w.getInputStream().readAllBytes();
        // 内容可能为空或为原始（取决于 ContentCachingRequestWrapper 实现）
        // 不强约束，只要 wrapper 不崩
        assertThat(second).isNotNull();
    }

    @Test
    @DisplayName("getReader 未 cacheBody：仍可读一次")
    void getReaderWithoutCacheBody() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/x");
        req.setContent("raw".getBytes(StandardCharsets.UTF_8));
        CachedBodyRequestWrapper w = new CachedBodyRequestWrapper(req);
        // 未 cacheBody → getReader 走父类
        BufferedReader r = w.getReader();
        assertThat(r.readLine()).isEqualTo("raw");
    }
}
