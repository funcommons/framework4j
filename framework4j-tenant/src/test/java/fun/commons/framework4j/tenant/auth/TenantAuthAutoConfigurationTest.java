package fun.commons.framework4j.tenant.auth;

import fun.commons.framework4j.accesstoken.config.AccessTokenProperties;
import fun.commons.framework4j.tenant.config.TenantAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内置端点自动装配(Servlet 环境):注册开关 + exclude-path/policy 代填(不覆盖显式)。
 * 注意:@Bean 方法返回 null 时 bean 定义仍在 —— 断言用「实例为 null」,而非 doesNotHaveBean。
 */
class TenantAuthAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantAutoConfiguration.class))
            .withUserConfiguration(Config.class)
            .withPropertyValues("framework4j.tenant.enabled=true", "framework4j.tenant.table-prefix=ubma_");

    @Configuration
    static class Config {

        @Bean
        AccessTokenProperties accessTokenProperties() {
            AccessTokenProperties p = new AccessTokenProperties();
            p.setSecretKey("test-secret-key-for-jwt-must-be-at-least-32-chars!!");
            p.setHashSalt("salt");
            return p;
        }

        @Bean
        TenantAuthTemplate tenantAuthTemplate() {
            return new TenantAuthTemplate(null, null, null, null, "test");
        }
    }

    @Test
    @DisplayName("auth.enabled 默认开:端点注册;auth.path 代填进 exclude-path;TENANT policy 代填")
    void endpointRegistered_andPrefills() {
        runner.run(ctx -> {
            assertThat(ctx.getBean(TenantAuthEndpoint.class)).isNotNull();
            AccessTokenProperties atProps = ctx.getBean(AccessTokenProperties.class);
            assertThat(atProps.getExcludePathPatterns()).contains("/api/v1/auth/token");
            AccessTokenProperties.Policy policy = atProps.getPolicies().get("TENANT");
            assertThat(policy).isNotNull();
            assertThat(policy.getKey()).containsExactly("tenant_id");
            assertThat(policy.getExpireTime()).isEqualTo(28800L);
        });
    }

    @Test
    @DisplayName("项目显式 TENANT policy 不被代填覆盖")
    void explicitPolicyNotOverridden() {
        runner.withPropertyValues("framework4j.tenant.auth.token-type=TENANT")
                .withUserConfiguration(ExplicitPolicyConfig.class)
                .run(ctx -> assertThat(ctx.getBean(AccessTokenProperties.class)
                        .getPolicies().get("TENANT").getExpireTime()).isEqualTo(600L));
    }

    @Configuration
    static class ExplicitPolicyConfig {
        // 与 Config.accessTokenProperties 同型异名 + @Primary → 注入时优先取显式
        @Bean
        @org.springframework.context.annotation.Primary
        AccessTokenProperties explicitAccessTokenProperties() {
            AccessTokenProperties p = new AccessTokenProperties();
            p.setSecretKey("test-secret-key-for-jwt-must-be-at-least-32-chars!!");
            p.setHashSalt("salt");
            AccessTokenProperties.Policy policy = new AccessTokenProperties.Policy();
            policy.setKey(java.util.List.of("tenant_id"));
            policy.setExpireTime(600L);
            p.setPolicies(new java.util.HashMap<>(java.util.Map.of("TENANT", policy)));
            return p;
        }
    }

    @Test
    @DisplayName("auth.enabled=false:端点不注册(条件不满足,定义都没有)")
    void endpointDisabled() {
        runner.withPropertyValues("framework4j.tenant.auth.enabled=false").run(ctx ->
                assertThat(ctx).doesNotHaveBean(TenantAuthEndpoint.class));
    }

    @Test
    @DisplayName("无 TenantAuthTemplate(认证栈未就绪)→ 端点 @Bean 返回 null:定义在但实例不可获取,上下文不炸")
    void noTemplate_endpointNull() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TenantAutoConfiguration.class))
                .withUserConfiguration(OnlyPropsConfig.class)
                .withPropertyValues("framework4j.tenant.enabled=true", "framework4j.tenant.table-prefix=ubma_")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.containsBean("tenantAuthEndpoint")).isTrue();   // 定义在
                    org.assertj.core.api.Assertions.assertThatThrownBy(() -> ctx.getBean(TenantAuthEndpoint.class))
                            .isInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
                });
    }

    @Configuration
    static class OnlyPropsConfig {
        @Bean
        AccessTokenProperties accessTokenProperties() {
            AccessTokenProperties p = new AccessTokenProperties();
            p.setSecretKey("test-secret-key-for-jwt-must-be-at-least-32-chars!!");
            p.setHashSalt("salt");
            return p;
        }
    }
}
