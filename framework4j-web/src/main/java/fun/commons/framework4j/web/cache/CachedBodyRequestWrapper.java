package fun.commons.framework4j.web.cache;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * v2.1 P0 修复：缓存 request body 并支持多次读取的 wrapper。
 * <p>原 {@link ContentCachingRequestWrapper} 仅缓存 content，不重放 InputStream；
 * 拦截器 {@code readAllBytes()} 后流被耗尽，Controller @RequestBody 拿到空 body。
 * <p>本类在 preHandle 阶段调 {@link #cacheBody()} 一次性读尽原始流并缓存，
 * 之后 {@link #getInputStream()} / {@link #getReader()} 返回基于缓存的 ByteArrayInputStream，
 * 让 Controller 可正常解析 @RequestBody。
 *
 * @since 2.1.0
 */
public class CachedBodyRequestWrapper extends ContentCachingRequestWrapper {

    private byte[] cachedBody;

    public CachedBodyRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    /**
     * 一次性读取并缓存原始 InputStream。preHandle 阶段调用。
     * 调用后 getContentAsByteArray() / getInputStream() 都返回缓存内容。
     */
    public void cacheBody() throws IOException {
        if (cachedBody != null) return;
        // 借助父类的 getInputStream（已 override 为读取底层流并缓存到父类 buf）
        super.getInputStream().readAllBytes();
        cachedBody = getContentAsByteArray();
    }

    @Override
    public ServletInputStream getInputStream() throws java.io.IOException {
        if (cachedBody == null) {
            return super.getInputStream();
        }
        return new ServletInputStream() {
            private final ByteArrayInputStream in = new ByteArrayInputStream(cachedBody);

            @Override
            public boolean isFinished() { return in.available() == 0; }

            @Override
            public boolean isReady() { return true; }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int read() { return in.read(); }
        };
    }

    @Override
    public BufferedReader getReader() throws java.io.IOException {
        if (cachedBody == null) {
            return super.getReader();
        }
        return new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(cachedBody), StandardCharsets.UTF_8));
    }
}
