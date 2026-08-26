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
 * 锁定 v1.4.1（Issue #17）修复：显式配置空 path-patterns 时【跳过】TokenInterceptor 注册。
 * <p>
 * 原行为：addPathPatterns(空列表) 的 Spring 语义为拦截 /**（与"空=不拦截"直觉相反，
 * 测试环境误配后整站 401）。默认值为 /** 不受影响；本测试只锁定"显式空列表 → 不注册"。
 * 对照组：TokenInterceptorRegistrationTest（默认配置 → 正常注册）。
 */
@SpringJUnitConfig(TokenInterceptorEmptyPatternsTest.TestConfig.class)
@WebAppConfiguration
@TestPropertySource(properties = {
        "framework4j.access-token.enabled=true",
        "framework4j.access-token.path-patterns=",
        "spring.application.name=empty-patterns-test"
})
@DisplayName("空 path-patterns 跳过注册（v1.4.1 / Issue #17）")
class TokenInterceptorEmptyPatternsTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AccessTokenProperties properties;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("空 path-patterns 绑定为空列表")
    void emptyPatternsBound() {
        assertThat(properties.getPathPatterns()).isEmpty();
    }

    @Test
    @DisplayName("TokenInterceptor Bean 仍创建，但不进入 MVC 拦截链")
    void interceptorNotRegisteredIntoMvcChain() {
        assertThat(context.getBean(TokenInterceptor.class)).isNotNull();

        var adapted = handlerMapping.getAdaptedInterceptors();
        boolean registered = adapted != null && java.util.Arrays.stream(adapted)
                .filter(MappedInterceptor.class::isInstance)
                .map(i -> ((MappedInterceptor) i).getInterceptor())
                .anyMatch(TokenInterceptor.class::isInstance);
        assertThat(registered)
                .as("空 path-patterns 应跳过注册（不拦截任何路径），打 WARN 而非静默全拦")
                .isFalse();
    }

    @Configuration
    @EnableWebMvc
    @Import(AccessTokenAutoConfiguration.class)
    static class TestConfig {

        /** mock 掉 Redis 依赖，注册测试不需要真实 Redis（与 TokenInterceptorRegistrationTest 同构） */
        @Bean
        MultiRedisManager multiRedisManager() {
            MultiRedisManager manager = mock(MultiRedisManager.class);
            when(manager.getStringRedisTemplate(anyString()))
                    .thenReturn(mock(StringRedisTemplate.class));
            return manager;
        }
    }
}
