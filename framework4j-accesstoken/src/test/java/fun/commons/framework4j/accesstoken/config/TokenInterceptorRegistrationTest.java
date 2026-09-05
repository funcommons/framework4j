package fun.commons.framework4j.accesstoken.config;

import fun.commons.framework4j.accesstoken.interceptor.TokenInterceptor;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 锁定 v1.2.8 修复：{@link AccessTokenWebMvcConfig} 必须被 {@link AccessTokenAutoConfiguration}
 * 引入，{@link TokenInterceptor} 必须真正进入 MVC 拦截链。
 * <p>
 * 背景：v1.2.7 及以前该注册类是孤儿（有 @Configuration 但无处加载），TokenInterceptor Bean
 * 创建了但 @RequiresToken 永不生效、TokenContext 永不填充 —— 下游 benefit4j 报告的
 * "claims → TokenContext 链路问题"（getClaim 全 null）实际根因即此（claims 链路本身无故障，
 * WebIntegrationTest 早已证明，只是测试自建了拦截器注册）。
 * <p>
 * 与 idempotency 的 IdempotencyWebMvcRegistrationTest 同构（v1.2.5 同类问题）。
 */
@SpringJUnitConfig(TokenInterceptorRegistrationTest.TestConfig.class)
@WebAppConfiguration
@TestPropertySource(properties = {
        "framework4j.access-token.policies.APP.key[0]=uid",
        "framework4j.access-token.secret-key=test-secret-key-for-jwt-must-be-at-least-32-chars",
        "framework4j.access-token.hash-salt=test-salt",

        "framework4j.access-token.enabled=true",
        "spring.application.name=registration-test"
})
@DisplayName("TokenInterceptor MVC 注册（v1.2.8 修复锁定）")
class TokenInterceptorRegistrationTest {

    @Configuration
    @EnableWebMvc
    @Import(AccessTokenAutoConfiguration.class)
    static class TestConfig {

        /** mock 掉 Redis 依赖，注册测试不需要真实 Redis */
        @Bean
        MultiRedisManager multiRedisManager() {
            MultiRedisManager manager = mock(MultiRedisManager.class);
            when(manager.getStringRedisTemplate(anyString()))
                    .thenReturn(mock(StringRedisTemplate.class));
            return manager;
        }
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("AccessTokenWebMvcConfig 被 @Import 加载为 Bean")
    void webMvcConfigBeanPresent() {
        assertThat(context.getBean(AccessTokenWebMvcConfig.class)).isNotNull();
        assertThat(context.getBean(TokenInterceptor.class)).isNotNull();
    }

    @Test
    @DisplayName("TokenInterceptor 真正进入 MVC 拦截链（修复核心断言）")
    void interceptorRegisteredIntoMvcChain() {
        assertThat(handlerMapping.getAdaptedInterceptors())
                .filteredOn(MappedInterceptor.class::isInstance)
                .extracting(i -> ((MappedInterceptor) i).getInterceptor())
                .anyMatch(TokenInterceptor.class::isInstance);
    }
}
