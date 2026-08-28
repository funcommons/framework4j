package fun.commons.framework4j.tenant.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * X-User-Id 上下文:解析 → ThreadLocal → 请求结束清理(Tomcat 线程复用,不清理 = 串用户)。
 * 红线:返回值永不鉴权(契约文档化,本测试只验证传递与清理,不验证权限语义)。
 */
class UserIdContextTest {

    private final UserIdContext.UserIdContextInterceptor interceptor =
            new UserIdContext.UserIdContextInterceptor();

    @AfterEach
    void tearDown() {
        UserIdContext.clear();
    }

    @Test
    @DisplayName("X-User-Id 头 → ThreadLocal 可读;无头 → null")
    void parsesHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "u-123");
        interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
        assertThat(UserIdContext.currentUserId()).isEqualTo("u-123");

        interceptor.afterCompletion(req, new MockHttpServletResponse(), new Object(), null);
        assertThat(UserIdContext.currentUserId()).isNull();
    }

    @Test
    @DisplayName("无 X-User-Id 头 → currentUserId() 为 null(不强制,必填由网关保证)")
    void missingHeader_null() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
        assertThat(UserIdContext.currentUserId()).isNull();
    }

    @Test
    @DisplayName("afterCompletion 必清理(防线程复用串用户)")
    void alwaysCleared() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "u-1");
        interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
        assertThat(UserIdContext.currentUserId()).isEqualTo("u-1");

        interceptor.afterCompletion(req, new MockHttpServletResponse(), new Object(), null);
        assertThat(UserIdContext.currentUserId()).isNull();

        // 二次请求(无线程复用模拟):不残留
        interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
        assertThat(UserIdContext.currentUserId()).isNull();
    }
}
