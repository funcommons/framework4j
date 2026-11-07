package fun.commons.framework4j.web.config;

import fun.commons.framework4j.web.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * traceIdMdcFilter 行为测试（issue #25 mc-monitor 契约）
 * <p>覆盖：入站 X-Trace-Id 采纳 / 非法再生 / 响应头回显（含异常路径）/ MDC key 可配置 /
 * Micrometer Tracing 已写入 MDC 时不覆盖。
 */
@DisplayName("traceIdMdcFilter 契约测试（issue #25）")
class TraceIdMdcFilterTest {

    private static final String HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";

    private final TraceIdMdcAutoConfiguration config = new TraceIdMdcAutoConfiguration();

    @AfterEach
    void reset() {
        TraceContext.clear();
        MDC.clear();
    }

    private OncePerRequestFilter newFilter(String headerName, String mdcKey) {
        FilterRegistrationBean<OncePerRequestFilter> bean = config.traceIdMdcFilter(headerName, mdcKey);
        return bean.getFilter();
    }

    /** 在 chain 内捕获 MDC，模拟业务侧读取 */
    private FilterChain capturing(AtomicReference<String> seen) {
        return (req, res) -> seen.set(MDC.get(TraceContext.getMdcKey()));
    }

    @Nested
    @DisplayName("入站采纳")
    class InboundAdoptionTest {

        @Test
        @DisplayName("合法入站 X-Trace-Id（16~64 位白名单字符）应被采纳，且响应头回显同一值")
        void shouldAdoptValidInboundHeader() throws Exception {
            OncePerRequestFilter filter = newFilter(HEADER, MDC_KEY);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(HEADER, "0af7651916cd43dd8448eb211c80319c");
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicReference<String> seen = new AtomicReference<>();

            filter.doFilter(request, response, capturing(seen));

            assertThat(seen.get()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
            assertThat(response.getHeader(HEADER)).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        }

        @Test
        @DisplayName("缺失入站头时本地生成 UUID 并回显")
        void shouldGenerateWhenHeaderMissing() throws Exception {
            OncePerRequestFilter filter = newFilter(HEADER, MDC_KEY);
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicReference<String> seen = new AtomicReference<>();

            filter.doFilter(request, response, capturing(seen));

            assertThat(seen.get()).hasSize(36).contains("-");
            assertThat(response.getHeader(HEADER)).isEqualTo(seen.get());
        }

        @Test
        @DisplayName("非法入站头（<16 位）不采纳，重新生成")
        void shouldRegenerateWhenHeaderTooShort() throws Exception {
            OncePerRequestFilter filter = newFilter(HEADER, MDC_KEY);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(HEADER, "short-id");
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicReference<String> seen = new AtomicReference<>();

            filter.doFilter(request, response, capturing(seen));

            assertThat(seen.get()).isNotEqualTo("short-id").hasSize(36);
        }

        @Test
        @DisplayName("Micrometer Tracing 已写入 MDC 时透传不覆盖、不清理，仍回显响应头")
        void shouldPassthroughWhenMdcPrePopulated() throws Exception {
            OncePerRequestFilter filter = newFilter(HEADER, MDC_KEY);
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            MDC.put(MDC_KEY, "micrometer-wrote-this-000000");
            AtomicReference<String> seen = new AtomicReference<>();

            try {
                filter.doFilter(request, response, capturing(seen));
            } finally {
                // 透传分支不清理——生命周期归 Tracing 所有，此处模拟容器行为
            }
            assertThat(seen.get()).isEqualTo("micrometer-wrote-this-000000");
            assertThat(response.getHeader(HEADER)).isEqualTo("micrometer-wrote-this-000000");
            assertThat(MDC.get(MDC_KEY)).isEqualTo("micrometer-wrote-this-000000");
            MDC.remove(MDC_KEY);
        }
    }

    @Nested
    @DisplayName("异常路径与清理")
    class ExceptionPathTest {

        @Test
        @DisplayName("chain 抛异常时响应头已回显、MDC 已清理（finally）")
        void shouldEchoHeaderAndCleanMdcWhenChainThrows() throws Exception {
            OncePerRequestFilter filter = newFilter(HEADER, MDC_KEY);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(HEADER, "aaaa1111bbbb2222cccc3333dddd4444");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain throwing = (req, res) -> {
                throw new IllegalStateException("boom from controller");
            };

            assertThatThrownBy(() -> filter.doFilter(request, response, throwing))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(response.getHeader(HEADER)).isEqualTo("aaaa1111bbbb2222cccc3333dddd4444");
            assertThat(MDC.get(MDC_KEY)).isNull();
        }

        @Test
        @DisplayName("正常路径 MDC 请求结束即清理（线程池复用防泄漏）")
        void shouldCleanMdcAfterRequest() throws Exception {
            OncePerRequestFilter filter = newFilter(HEADER, MDC_KEY);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(HEADER, "aaaa1111bbbb2222cccc3333dddd4444");

            filter.doFilter(request, new MockHttpServletResponse(),
                    new org.springframework.mock.web.MockFilterChain());

            assertThat(MDC.get(MDC_KEY)).isNull();
        }
    }

    @Nested
    @DisplayName("sanitize 规则")
    class SanitizeTest {

        @Test
        @DisplayName("16 位 hex / 64 位 / 含点下划线连字符 → 合法")
        void shouldAcceptValidFormats() {
            assertThat(TraceIdMdcAutoConfiguration.sanitize("0af7651916cd43dd")).isEqualTo("0af7651916cd43dd");
            assertThat(TraceIdMdcAutoConfiguration.sanitize("a".repeat(64))).isEqualTo("a".repeat(64));
            assertThat(TraceIdMdcAutoConfiguration.sanitize("  trace.1234_id-5678  ".trim()))
                    .isEqualTo("trace.1234_id-5678");
            assertThat(TraceIdMdcAutoConfiguration.sanitize("b3b5a2ec4d1f4c2a9e8d7f6a5b4c3d2e"))
                    .isEqualTo("b3b5a2ec4d1f4c2a9e8d7f6a5b4c3d2e");
        }

        @Test
        @DisplayName("过短/过长/非法字符/null → 不合法")
        void shouldRejectInvalidFormats() {
            assertThat(TraceIdMdcAutoConfiguration.sanitize(null)).isNull();
            assertThat(TraceIdMdcAutoConfiguration.sanitize("short")).isNull();
            assertThat(TraceIdMdcAutoConfiguration.sanitize("a".repeat(65))).isNull();
            assertThat(TraceIdMdcAutoConfiguration.sanitize("has space 123456789")).isNull();
            assertThat(TraceIdMdcAutoConfiguration.sanitize("注入<script>xx1234")).isNull();
            assertThat(TraceIdMdcAutoConfiguration.sanitize("中文测试0012345678")).isNull();
        }
    }

    @Nested
    @DisplayName("自定义 MDC key")
    class CustomMdcKeyTest {

        @Test
        @DisplayName("自定义 mdc-key 时 filter 写入自定义键，TraceContext.getMdcKey() 同步")
        void shouldUseConfiguredMdcKey() throws Exception {
            OncePerRequestFilter filter = newFilter(HEADER, "x_request_id");
            assertThat(TraceContext.getMdcKey()).isEqualTo("x_request_id");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(HEADER, "aaaa1111bbbb2222cccc3333dddd4444");
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicReference<String> seen = new AtomicReference<>();
            FilterChain chain = (req, res) -> seen.set(MDC.get("x_request_id"));

            filter.doFilter(request, response, chain);

            assertThat(seen.get()).isEqualTo("aaaa1111bbbb2222cccc3333dddd4444");
            assertThat(response.getHeader(HEADER)).isEqualTo("aaaa1111bbbb2222cccc3333dddd4444");
            assertThat(MDC.get(MDC_KEY)).isNull(); // 旧 key 不写
        }

        @Test
        @DisplayName("setTraceId 写入当前配置的 mdc-key")
        void setTraceIdShouldUseConfiguredKey() {
            TraceContext.setMdcKey("custom_key");
            TraceContext.setTraceId("aaaa1111bbbb2222cccc3333dddd4444");
            assertThat(MDC.get("custom_key")).isEqualTo("aaaa1111bbbb2222cccc3333dddd4444");
            assertThat(TraceContext.getTraceId()).isEqualTo("aaaa1111bbbb2222cccc3333dddd4444");
        }

        @Test
        @DisplayName("clear() 重置 mdc-key 为默认 traceId")
        void clearShouldResetMdcKey() {
            TraceContext.setMdcKey("custom_key");
            TraceContext.clear();
            assertThat(TraceContext.getMdcKey()).isEqualTo(MDC_KEY);
        }
    }
}
