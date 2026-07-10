package fun.commons.framework4j.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import fun.commons.framework4j.web.cache.CachedBodyRequestWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * CachedBodyRequestWrapper 测试
 * <p>
 * 验证 body 缓存 + 重放 InputStream（修 Spring ContentCachingRequestWrapper 不重放流的 bug）
 */
@DisplayName("CachedBodyRequestWrapper 测试")
class CachedBodyRequestWrapperTest {

    @Test
    @DisplayName("cacheBody：空 body → 不抛异常")
    void cacheBodyEmpty() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/test");
        CachedBodyRequestWrapper wrapper = new CachedBodyRequestWrapper(req);
        wrapper.cacheBody();
        assertThat(wrapper.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("cacheBody：有 body → getContentAsByteArray 返回原始内容")
    void cacheBodyWithContent() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/test");
        req.setContent("{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8));
        CachedBodyRequestWrapper wrapper = new CachedBodyRequestWrapper(req);
        wrapper.cacheBody();
        assertThat(wrapper.getContentAsByteArray()).isEqualTo("{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("getInputStream：cacheBody 后可读取完整内容")
    void getInputStreamAfterCacheBody() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/test");
        req.setContent("hello-world".getBytes(StandardCharsets.UTF_8));
        CachedBodyRequestWrapper wrapper = new CachedBodyRequestWrapper(req);
        wrapper.cacheBody();

        byte[] buf = wrapper.getInputStream().readAllBytes();
        assertThat(new String(buf, StandardCharsets.UTF_8)).isEqualTo("hello-world");
    }

    @Test
    @DisplayName("getInputStream：多次读取返回相同内容（重放）")
    void getInputStreamMultipleReads() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/test");
        req.setContent("repeatable".getBytes(StandardCharsets.UTF_8));
        CachedBodyRequestWrapper wrapper = new CachedBodyRequestWrapper(req);
        wrapper.cacheBody();

        String read1 = new String(wrapper.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String read2 = new String(wrapper.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(read1).isEqualTo("repeatable");
        assertThat(read2).isEqualTo("repeatable");
    }

    @Test
    @DisplayName("getReader：cacheBody 后可读取完整内容")
    void getReaderAfterCacheBody() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/test");
        req.setContent("reader-test".getBytes(StandardCharsets.UTF_8));
        CachedBodyRequestWrapper wrapper = new CachedBodyRequestWrapper(req);
        wrapper.cacheBody();

        BufferedReader reader = wrapper.getReader();
        assertThat(reader.readLine()).isEqualTo("reader-test");
    }

    @Test
    @DisplayName("getReader：多次读取返回相同内容（重放）")
    void getReaderMultipleReads() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/test");
        req.setContent("multi-read".getBytes(StandardCharsets.UTF_8));
        CachedBodyRequestWrapper wrapper = new CachedBodyRequestWrapper(req);
        wrapper.cacheBody();

        assertThat(wrapper.getReader().readLine()).isEqualTo("multi-read");
        assertThat(wrapper.getReader().readLine()).isEqualTo("multi-read");
    }

    @Test
    @DisplayName("cacheBody：调用两次不报错（幂等）")
    void cacheBodyIdempotent() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/test");
        req.setContent("twice".getBytes(StandardCharsets.UTF_8));
        CachedBodyRequestWrapper wrapper = new CachedBodyRequestWrapper(req);
        wrapper.cacheBody();
        // 第二次 cacheBody 不应改变缓存内容
        assertThatCode(() -> wrapper.cacheBody()).doesNotThrowAnyException();
        assertThat(new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8)).isEqualTo("twice");
    }

    @Test
    @DisplayName("getInputStream：未 cacheBody → 走父类（可能只读一次）")
    void getInputStreamWithoutCacheBody() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/test");
        req.setContent("no-cache".getBytes(StandardCharsets.UTF_8));
        CachedBodyRequestWrapper wrapper = new CachedBodyRequestWrapper(req);
        // 不调 cacheBody，直接读 → 走父类 ContentCachingRequestWrapper 的 getInputStream
        byte[] buf = wrapper.getInputStream().readAllBytes();
        assertThat(new String(buf, StandardCharsets.UTF_8)).isEqualTo("no-cache");
    }

    @Test
    @DisplayName("中文 body 缓存 + 重放")
    void chineseBodyRoundTrip() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/test");
        req.setContent("中文测试内容".getBytes(StandardCharsets.UTF_8));
        CachedBodyRequestWrapper wrapper = new CachedBodyRequestWrapper(req);
        wrapper.cacheBody();

        String read = new String(wrapper.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(read).isEqualTo("中文测试内容");
    }

    @Test
    @DisplayName("大 body（100KB）缓存 + 重放")
    void largeBodyRoundTrip() throws Exception {
        String large = "x".repeat(100 * 1024);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/test");
        req.setContent(large.getBytes(StandardCharsets.UTF_8));
        CachedBodyRequestWrapper wrapper = new CachedBodyRequestWrapper(req);
        wrapper.cacheBody();

        String read = new String(wrapper.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(read).isEqualTo(large);
        assertThat(read.length()).isEqualTo(100 * 1024);
    }

    @Test
    @DisplayName("二进制 body（非 UTF-8）缓存 + 重放")
    void binaryBodyRoundTrip() throws Exception {
        byte[] binary = new byte[256];
        for (int i = 0; i < 256; i++) binary[i] = (byte) i;
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/test");
        req.setContent(binary);
        CachedBodyRequestWrapper wrapper = new CachedBodyRequestWrapper(req);
        wrapper.cacheBody();

        byte[] read = wrapper.getInputStream().readAllBytes();
        assertThat(read).isEqualTo(binary);
    }
}
