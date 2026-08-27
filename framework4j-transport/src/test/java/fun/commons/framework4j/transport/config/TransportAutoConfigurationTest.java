package fun.commons.framework4j.transport.config;

import fun.commons.framework4j.transport.HttpTransport;
import fun.commons.framework4j.transport.RestTemplateHttpTransport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TransportAutoConfiguration 装配测试（v1.4.2 / Issue #18 修复锁定）。
 * <p>
 * 核心回归断言：业务方声明 ≥2 个 RestTemplate Bean 时容器能启动
 * （v1.4.2 之前 framework4jHttpTransport(RestTemplate) 形参按类型注入歧义，
 * 直接 NoUniqueBeanDefinitionException）。同时锁定各场景的复用语义不变。
 */
@DisplayName("Transport 自动装配：多 RestTemplate 歧义处理（v1.4.2）")
class TransportAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TransportAutoConfiguration.class));

    /** 取 HttpTransport 实际复用的底层 RestTemplate（复用语义断言用） */
    private static RestTemplate usedTemplate(org.springframework.context.ApplicationContext ctx) {
        return ((RestTemplateHttpTransport) ctx.getBean(HttpTransport.class)).getRestTemplate();
    }

    @Test
    @DisplayName("业务方 0 个 RestTemplate：框架兜底实例被复用（存量语义不变）")
    void zeroBusinessRestTemplate() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(HttpTransport.class);
            assertThat(ctx).hasSingleBean(RestTemplate.class);
            assertThat(usedTemplate(ctx))
                    .isSameAs(ctx.getBean("framework4jRestTemplate", RestTemplate.class));
        });
    }

    @Test
    @DisplayName("业务方 1 个 RestTemplate：框架兜底让位，复用业务实例（存量语义不变）")
    void singleBusinessRestTemplate() {
        runner.withUserConfiguration(SingleRestTemplateConfig.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(HttpTransport.class);
            assertThat(ctx).doesNotHaveBean("framework4jRestTemplate");
            assertThat(usedTemplate(ctx)).isSameAs(ctx.getBean("businessRestTemplate", RestTemplate.class));
        });
    }

    @Test
    @DisplayName("核心回归：业务方 2 个 RestTemplate 也能启动，不再 NoUniqueBeanDefinitionException")
    void twoBusinessRestTemplatesStartsUp() {
        runner.withUserConfiguration(TwoRestTemplatesConfig.class).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(HttpTransport.class);
            assertThat(ctx).doesNotHaveBean("framework4jRestTemplate");

            // 未 pin 时降级内置默认实例：既不是 a 也不是 b
            RestTemplate used = usedTemplate(ctx);
            assertThat(used).isNotSameAs(ctx.getBean("webhookRestTemplate", RestTemplate.class));
            assertThat(used).isNotSameAs(ctx.getBean("callbackRestTemplate", RestTemplate.class));
        });
    }

    @Test
    @DisplayName("rest-template-bean-name pin：2 个 RestTemplate 时按名复用指定实例")
    void pinnedRestTemplateWins() {
        runner.withUserConfiguration(TwoRestTemplatesConfig.class)
                .withPropertyValues("framework4j.transport.rest-template-bean-name=callbackRestTemplate")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(usedTemplate(ctx))
                            .isSameAs(ctx.getBean("callbackRestTemplate", RestTemplate.class));
                });
    }

    @Test
    @DisplayName("2 个 RestTemplate 其中 1 个 @Primary：复用主 Bean（不降级）")
    void primaryRestTemplateWins() {
        runner.withUserConfiguration(TwoWithPrimaryConfig.class).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(usedTemplate(ctx)).isSameAs(ctx.getBean("primaryRestTemplate", RestTemplate.class));
        });
    }

    @Test
    @DisplayName("enabled=false：全部 Bean 不注册（关闭途径不变）")
    void disabledByProperty() {
        runner.withPropertyValues("framework4j.transport.enabled=false").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(HttpTransport.class);
            assertThat(ctx).doesNotHaveBean(RestTemplate.class);
        });
    }

    // ==================== 用户配置夹具 ====================

    @Configuration
    static class SingleRestTemplateConfig {
        @Bean
        RestTemplate businessRestTemplate() {
            return new RestTemplate();
        }
    }

    @Configuration
    static class TwoRestTemplatesConfig {
        @Bean
        RestTemplate webhookRestTemplate() {
            return new RestTemplate();
        }

        @Bean
        RestTemplate callbackRestTemplate() {
            return new RestTemplate();
        }
    }

    @Configuration
    static class TwoWithPrimaryConfig {
        @Bean
        @Primary
        RestTemplate primaryRestTemplate() {
            return new RestTemplate();
        }

        @Bean
        RestTemplate secondaryRestTemplate() {
            return new RestTemplate();
        }
    }
}
