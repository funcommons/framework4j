package fun.commons.framework4j.web.test;

import org.springframework.mock.web.MockHttpServletRequest;

/**
 * MockHttpServletRequest / Redis key 测试 fixtures
 *
 * @since 2.1.0
 */
public final class TestRedisRequests {

    private TestRedisRequests() {}

    public static MockHttpServletRequest get(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    public static MockHttpServletRequest post(String uri) {
        return new MockHttpServletRequest("POST", uri);
    }

    public static MockHttpServletRequest put(String uri) {
        return new MockHttpServletRequest("PUT", uri);
    }

    public static MockHttpServletRequest delete(String uri) {
        return new MockHttpServletRequest("DELETE", uri);
    }

    /** 带 Authorization Bearer Token */
    public static MockHttpServletRequest withBearer(String uri, String method, String token) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.addHeader("Authorization", "Bearer " + token);
        return req;
    }
}
