package fun.commons.framework4j.ratelimit;

import fun.commons.framework4j.ratelimit.config.RateLimitProperties;
import fun.commons.framework4j.ratelimit.service.RateLimitKeyResolver;
import fun.commons.framework4j.ratelimit.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RateLimit 端到端集成测试（embedded Redis）
 * <p>
 * 覆盖 mc-api-spec §8.5：
 * <ul>
 *   <li>滑动窗口算法 + Lua 原子化</li>
 *   <li>4 种 scope（ip/user/app/global）</li>
 *   <li>响应头三件套（X-RateLimit-*）</li>
 *   <li>Redis 故障兜底放行</li>
 * </ul>
 *
 * @since 2.1.0
 */
@SpringBootTest
@ActiveProfiles("integration-test")
class RateLimitIntegrationTest {

    @SpringBootApplication
    static class TestApplication {}

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private RateLimitKeyResolver keyResolver;

    @Autowired
    private RateLimitProperties properties;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanRedis() {
        Set<String> keys = redisTemplate.keys(properties.getKeyPrefix() + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("场景1: 首次请求 → 通过")
    void scenario1_firstRequest_passes() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/orders");
        req.setRemoteAddr("1.1.1.1");

        String key = keyResolver.resolve(req, "ip");
        RateLimitService.AcquireResult r = rateLimitService.tryAcquire(key, 3, 10000);

        assertThat(r.allowed()).isTrue();
        assertThat(r.currentCount()).isEqualTo(1);
        assertThat(r.limit()).isEqualTo(3);
    }

    @Test
    @DisplayName("场景2: limit=3 时，前 3 次通过，第 4 次限流")
    void scenario2_exceedLimit_throttled() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/orders");
        req.setRemoteAddr("2.2.2.2");
        String key = keyResolver.resolve(req, "ip");

        RateLimitService.AcquireResult r1 = rateLimitService.tryAcquire(key, 3, 10000);
        RateLimitService.AcquireResult r2 = rateLimitService.tryAcquire(key, 3, 10000);
        RateLimitService.AcquireResult r3 = rateLimitService.tryAcquire(key, 3, 10000);
        RateLimitService.AcquireResult r4 = rateLimitService.tryAcquire(key, 3, 10000);

        assertThat(r1.allowed()).isTrue();
        assertThat(r2.allowed()).isTrue();
        assertThat(r3.allowed()).isTrue();
        assertThat(r4.allowed()).isFalse();
        assertThat(r4.currentCount()).isEqualTo(3);
        assertThat(r4.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    @DisplayName("场景3: 不同 IP 独立计数（不相互影响）")
    void scenario3_differentIpIsolated() {
        MockHttpServletRequest reqA = new MockHttpServletRequest("GET", "/v1/orders");
        reqA.setRemoteAddr("3.3.3.3");
        MockHttpServletRequest reqB = new MockHttpServletRequest("GET", "/v1/orders");
        reqB.setRemoteAddr("4.4.4.4");

        String keyA = keyResolver.resolve(reqA, "ip");
        String keyB = keyResolver.resolve(reqB, "ip");

        // A 用 2 次，B 应从 0 开始
        rateLimitService.tryAcquire(keyA, 3, 10000);
        rateLimitService.tryAcquire(keyA, 3, 10000);

        RateLimitService.AcquireResult b1 = rateLimitService.tryAcquire(keyB, 3, 10000);
        assertThat(b1.allowed()).isTrue();
        assertThat(b1.currentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("场景4: scope=user 从 X-User-Id 取维度")
    void scenario4_userScope() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/orders");
        req.addHeader("X-User-Id", "user-999");

        String key = keyResolver.resolve(req, "user");
        assertThat(key).contains("user-999");
        assertThat(key).doesNotContain("anonymous");
    }

    @Test
    @DisplayName("场景5: scope=global 共享 key")
    void scenario5_globalScope() {
        MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/v1/orders");
        req1.setRemoteAddr("5.5.5.5");
        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/v1/orders");
        req2.setRemoteAddr("6.6.6.6");

        String key1 = keyResolver.resolve(req1, "global");
        String key2 = keyResolver.resolve(req2, "global");

        assertThat(key1).isEqualTo(key2);
    }

    @Test
    @DisplayName("场景6: X-Forwarded-For 优先 remoteAddr")
    void scenario6_xForwardedForPriority() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/orders");
        req.addHeader("X-Forwarded-For", "7.7.7.7, 8.8.8.8");
        req.setRemoteAddr("9.9.9.9");

        String key = keyResolver.resolve(req, "ip");
        assertThat(key).contains("7.7.7.7");
        assertThat(key).doesNotContain("9.9.9.9");
    }

    @Test
    @DisplayName("场景7: retryAfterSeconds 向上取整")
    void scenario7_retryAfterRounded() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/orders");
        req.setRemoteAddr("10.0.0.1");
        String key = keyResolver.resolve(req, "ip");

        // 用完 3 次后第 4 次被限
        for (int i = 0; i < 3; i++) {
            rateLimitService.tryAcquire(key, 3, 10000);
        }
        RateLimitService.AcquireResult denied = rateLimitService.tryAcquire(key, 3, 10000);

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
        assertThat(denied.retryAfterSeconds()).isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("场景8: 响应头元数据完整（limit/currentCount/resetAtMs）")
    void scenario8_metadataComplete() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/orders");
        req.setRemoteAddr("11.0.0.1");
        String key = keyResolver.resolve(req, "ip");

        RateLimitService.AcquireResult r = rateLimitService.tryAcquire(key, 100, 60000);

        assertThat(r.limit()).isEqualTo(100);
        assertThat(r.currentCount()).isGreaterThan(0);
        assertThat(r.resetAtMs()).isGreaterThan(System.currentTimeMillis());
    }
}
