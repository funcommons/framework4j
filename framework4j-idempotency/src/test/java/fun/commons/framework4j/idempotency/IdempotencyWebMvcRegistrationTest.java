package fun.commons.framework4j.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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
 * 锁定 v1.2.5 修复：{@link IdempotencyWebMvcConfig} 必须被
 * {@link IdempotencyAutoConfiguration} 引入，拦截器必须真正进入 MVC 拦截链。
 * <p>
 * 背景：v2.1 从 @Component 扫描迁移到显式 @Bean 时，{@code IdempotencyWebMvcConfig}
 * 被漏挂（既不在 AutoConfiguration.imports，也未被 @Import），拦截器 Bean 创建了
 * 但 preHandle 永不执行，幂等形同虚设（下游 benefit4j 排查报告 #5）。
 */
@SpringJUnitConfig(IdempotencyWebMvcRegistrationTest.TestConfig.class)
@WebAppConfiguration
@TestPropertySource(properties = "framework4j.idempotency.enabled=true")
@DisplayName("Idempotency 拦截器 MVC 注册（v1.2.5 修复锁定）")
class IdempotencyWebMvcRegistrationTest {

    @Configuration
    @EnableWebMvc
    @Import(IdempotencyAutoConfiguration.class)
    static class TestConfig {

        /** mock 掉 Redis 依赖，注册测试不需要真实 Redis */
        @Bean
        MultiRedisManager multiRedisManager() {
            MultiRedisManager manager = mock(MultiRedisManager.class);
            when(manager.getStringRedisTemplate(anyString()))
                    .thenReturn(mock(StringRedisTemplate.class));
            return manager;
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("IdempotencyWebMvcConfig 被 @Import 加载为 Bean")
    void webMvcConfigBeanPresent() {
        assertThat(context.getBean(IdempotencyWebMvcConfig.class)).isNotNull();
        assertThat(context.getBean(IdempotencyInterceptor.class)).isNotNull();
    }

    @Test
    @DisplayName("IdempotencyInterceptor 真正进入 MVC 拦截链（修复核心断言）")
    void interceptorRegisteredIntoMvcChain() {
        assertThat(handlerMapping.getAdaptedInterceptors())
                .filteredOn(MappedInterceptor.class::isInstance)
                .extracting(i -> ((MappedInterceptor) i).getInterceptor())
                .anyMatch(IdempotencyInterceptor.class::isInstance);
    }

    @Test
    @DisplayName("默认关闭：enabled 缺省时自动配置整体不加载（opt-in 语义不回归）")
    void disabledByDefault() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempotencyAutoConfiguration.class))
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(IdempotencyInterceptor.class);
                    assertThat(ctx).doesNotHaveBean(IdempotencyWebMvcConfig.class);
                });
    }
}
