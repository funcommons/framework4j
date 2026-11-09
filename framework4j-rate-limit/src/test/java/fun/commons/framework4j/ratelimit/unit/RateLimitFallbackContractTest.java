package fun.commons.framework4j.ratelimit.unit;

import fun.commons.framework4j.ratelimit.config.RateLimitAutoConfiguration;
import fun.commons.framework4j.ratelimit.config.RateLimitProperties;
import fun.commons.framework4j.ratelimit.service.RateLimitService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Issue #33 回归（v1.10.1）：
 * <ul>
 *   <li>①fail-open 放行形带 {@code fallback=true} 显式标志——调用方不再靠 currentCount==0 指纹判别</li>
 *   <li>②fallback 计数按限流键化（全局计数无法定位哪个业务键在降级）</li>
 *   <li>③path-patterns 空值 → 拦截器不注册（兑现"仅注解触发"注释语义，此前 /** 隐式全站缺省限流）</li>
 * </ul>
 */
@DisplayName("issue #33：fallback 标志 + 键化计数 + 空值语义")
class RateLimitFallbackContractTest {

    private StringRedisTemplate failingTemplate() {
        StringRedisTemplate t = mock(StringRedisTemplate.class);
        when(t.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("connection refused"));
        return t;
    }

    @Nested
    @DisplayName("①fallback 标志位")
    class FallbackFlagTest {

        @Test
        @DisplayName("Redis 故障 → allowed=true + fallback=true（降级放行显式可辨）")
        void failOpenCarriesFallbackFlag() {
            RateLimitService service = new RateLimitService(failingTemplate());

            RateLimitService.AcquireResult r = service.tryAcquire("register:u1", 100, 60_000);

            assertThat(r.allowed()).isTrue();
            assertThat(r.fallback()).as("降级放行必须带 fallback 标志").isTrue();
            assertThat(r.currentCount()).isZero();
        }

        @Test
        @DisplayName("fixed_window Redis 故障同样带 fallback 标志")
        void fixedWindowFailOpenCarriesFlag() {
            RateLimitService service = new RateLimitService(failingTemplate());

            RateLimitService.AcquireResult r = service.tryAcquireFixedWindow("export:u1", 10, 60);

            assertThat(r.allowed()).isTrue();
            assertThat(r.fallback()).isTrue();
        }

        @Test
        @DisplayName("正常 Lua 放行 → fallback=false 且 currentCount>=1（契约另一侧）")
        void normalPassHasNoFallbackFlag() {
            StringRedisTemplate t = mock(StringRedisTemplate.class);
            when(t.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(List.of(1L, 5L, System.currentTimeMillis() + 60_000, 100L));
            RateLimitService service = new RateLimitService(t);

            RateLimitService.AcquireResult r = service.tryAcquire("api:u2", 100, 60_000);

            assertThat(r.allowed()).isTrue();
            assertThat(r.fallback()).isFalse();
            assertThat(r.currentCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("四参构造源码兼容：fallback=false")
        void fourArgConstructorSourceCompat() {
            RateLimitService.AcquireResult r =
                    new RateLimitService.AcquireResult(true, 3, 100, System.currentTimeMillis());
            assertThat(r.fallback()).isFalse();
        }
    }

    @Nested
    @DisplayName("②fallback 计数键化")
    class KeyedFallbackCountTest {

        @Test
        @DisplayName("不同限流键分别计数，全局计数为总和")
        void fallbackCountKeyedByRateLimitKey() {
            RateLimitService service = new RateLimitService(failingTemplate());

            service.tryAcquire("register:u1", 100, 60_000);
            service.tryAcquire("register:u1", 100, 60_000);
            service.tryAcquire("export:u2", 10, 60_000);

            assertThat(service.getFallbackCount("register:u1")).isEqualTo(2);
            assertThat(service.getFallbackCount("export:u2")).isEqualTo(1);
            assertThat(service.getFallbackCount("other:never-failed")).isZero();
            assertThat(service.getFallbackCountsByKey())
                    .containsEntry("register:u1", 2L).containsEntry("export:u2", 1L);
            assertThat(service.getFallbackCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("③path-patterns 空值语义（兑现注释）")
    class EmptyPathPatternsTest {

        /** 直调 rateLimitWebMvcConfig（无实例字段），用计数子类数注册数（registry 无 getRegistrations API） */
        private int registrations(RateLimitProperties properties) {
            RateLimitAutoConfiguration config = new RateLimitAutoConfiguration();
            WebMvcConfigurer configurer = config.rateLimitWebMvcConfig(
                    mock(fun.commons.framework4j.ratelimit.interceptor.RateLimitInterceptor.class), properties);
            class CountingRegistry extends InterceptorRegistry {
                int count;
                @Override
                public InterceptorRegistration addInterceptor(org.springframework.web.servlet.HandlerInterceptor i) {
                    count++;
                    return super.addInterceptor(i);
                }
            }
            CountingRegistry registry = new CountingRegistry();
            configurer.addInterceptors(registry);
            return registry.count;
        }

        @Test
        @DisplayName("path-patterns 空 → 拦截器不注册（仅 @RateLimit 注解触发）")
        void emptyPatternsRegisterNothing() {
            RateLimitProperties properties = new RateLimitProperties();
            properties.setPathPatterns(null);
            assertThat(registrations(properties)).isZero();

            properties.setPathPatterns(List.of());
            assertThat(registrations(properties)).isZero();
        }

        @Test
        @DisplayName("path-patterns 非空 → 正常注册")
        void configuredPatternsRegister() {
            RateLimitProperties properties = new RateLimitProperties();
            properties.setPathPatterns(List.of("/v1/api/**"));
            assertThat(registrations(properties)).isEqualTo(1);
        }
    }
}
