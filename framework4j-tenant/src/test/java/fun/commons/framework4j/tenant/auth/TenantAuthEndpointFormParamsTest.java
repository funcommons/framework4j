package fun.commons.framework4j.tenant.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端点参数解析(benefit4j 真进程踩坑固化):上游 CachedBodyRequestWrapper 可能先于
 * Tomcat form 解析消费请求体 → @RequestParam 全空。本端点手动解析 form body + query 兜底。
 */
class TenantAuthEndpointFormParamsTest {

    @Test
    @DisplayName("form-urlencoded body 三参数解析(curl -d 默认形态)")
    void formBody() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setContentType("application/x-www-form-urlencoded");
        req.setContent("grant_type=client_credentials&client_id=PLATFORM&client_secret=s%403"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<String, String> params = TenantAuthEndpoint.formParams(req);
        assertThat(params).containsEntry("grant_type", "client_credentials")
                .containsEntry("client_id", "PLATFORM")
                .containsEntry("client_secret", "s@3");   // URL 解码
    }

    @Test
    @DisplayName("query string 形态(client_id 在 URL 上)")
    void queryString() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setQueryString("grant_type=client_credentials&client_id=1001&client_secret=x");
        req.addParameter("grant_type", "client_credentials");
        req.addParameter("client_id", "1001");
        req.addParameter("client_secret", "x");
        Map<String, String> params = TenantAuthEndpoint.formParams(req);
        assertThat(params).containsEntry("client_id", "1001");
    }

    @Test
    @DisplayName("body 已被上游消费(getInputStream 抛错)→ 回退 query,不炸")
    void bodyConsumed_fallbackToQuery() {
        MockHttpServletRequest req = new MockHttpServletRequest() {
            @Override
            public jakarta.servlet.ServletInputStream getInputStream() {
                throw new IllegalStateException("stream already consumed");
            }
        };
        req.setContentType("application/x-www-form-urlencoded");
        req.addParameter("client_id", "q-1");   // query 兜底
        Map<String, String> params = TenantAuthEndpoint.formParams(req);
        assertThat(params).containsEntry("client_id", "q-1").doesNotContainKey("grant_type");
    }

    @Test
    @DisplayName("body 与 query 并存:body 优先(query 不覆盖已解析值)")
    void bodyWinsOverQuery() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setContentType("application/x-www-form-urlencoded");
        req.setContent("client_id=body-id".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        req.addParameter("client_id", "query-id");
        assertThat(TenantAuthEndpoint.formParams(req)).containsEntry("client_id", "body-id");
    }
}
