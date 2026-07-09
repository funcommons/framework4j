package fun.commons.framework4j.ratelimit;

import fun.commons.framework4j.ratelimit.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * RateLimitService 测试（sliding_window Lua 结果解析）
 *
 * @since 2.1.0
 */
@DisplayName("RateLimitService sliding_window 测试")
class RateLimitServiceTest {

    private StringRedisTemplate redisTemplate;
    private RateLimitService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        service = new RateLimitService(redisTemplate);
    }

    @Test
    @DisplayName("Lua 返回 allowed=1 → 放行")
    void shouldAllowWhenLuaReturnsAllowed() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 5L, System.currentTimeMillis() + 60000, 100L));

        RateLimitService.AcquireResult r = service.tryAcquire("test:key", 100, 60000);

        assertThat(r.allowed()).isTrue();
        assertThat(r.currentCount()).isEqualTo(5);
        assertThat(r.limit()).isEqualTo(100);
    }

    @Test
    @DisplayName("Lua 返回 allowed=0 → 限流")
    void shouldDenyWhenLuaReturnsDenied() {
        long resetAt = System.currentTimeMillis() + 30000;
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 100L, resetAt, 100L));

        RateLimitService.AcquireResult r = service.tryAcquire("test:key", 100, 60000);

        assertThat(r.allowed()).isFalse();
        assertThat(r.currentCount()).isEqualTo(100);
        assertThat(r.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Lua 异常 → 兜底放行（避免拖垮业务）")
    void shouldFallbackWhenRedisFails() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("connection refused"));

        RateLimitService.AcquireResult r = service.tryAcquire("test:key", 100, 60000);

        assertThat(r.allowed()).isTrue();
        assertThat(r.limit()).isEqualTo(100);
    }

    @Test
    @DisplayName("Lua 返回 null → 兜底放行")
    void shouldFallbackWhenLuaReturnsNull() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(null);

        RateLimitService.AcquireResult r = service.tryAcquire("test:key", 100, 60000);
        assertThat(r.allowed()).isTrue();
    }

    @Test
    @DisplayName("Lua 返回不足 4 元素 → 兜底放行")
    void shouldFallbackWhenLuaReturnsShortList() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 5L));

        RateLimitService.AcquireResult r = service.tryAcquire("test:key", 100, 60000);
        assertThat(r.allowed()).isTrue();
    }

    @Test
    @DisplayName("retryAfterSeconds 向上取整")
    void shouldRoundUpRetryAfterSeconds() {
        long resetAt = System.currentTimeMillis() + 500;  // 0.5 秒后重置
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 100L, resetAt, 100L));

        RateLimitService.AcquireResult r = service.tryAcquire("test:key", 100, 60000);
        assertThat(r.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("buildException：构造 RateLimitException 含完整元数据")
    void shouldBuildExceptionWithMetadata() {
        long resetAt = System.currentTimeMillis() + 60000;
        RateLimitService.AcquireResult r = new RateLimitService.AcquireResult(false, 100, 100, resetAt);

        fun.commons.framework4j.ratelimit.exception.RateLimitException ex = service.buildException(r);

        assertThat(ex.getLimit()).isEqualTo(100);
        assertThat(ex.getCurrentCount()).isEqualTo(100);
        assertThat(ex.getRetryAfterSeconds()).isGreaterThan(0);
    }
}
