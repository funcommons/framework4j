package fun.commons.framework4j.id.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import fun.commons.framework4j.id.strategy.WorkerIdStrategy;
import fun.commons.framework4j.openid.config.OpenIdAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Id/OpenId 自动装配条件测试（issue #26）
 * <p>
 * v1.8 起激活语义改为<strong>显式启用</strong>（matchIfMissing=false），本测试锁定新契约：
 * <ul>
 *   <li>缺省/显式 false → 无任何 bean（无 Redis 的测试/CI 不再启动即炸）</li>
 *   <li>显式 true + ip 策略 → 正常装配且零 Redis 依赖</li>
 *   <li>redis 策略 + Redis 不可达 → fail-soft 降级 IP Hash（fallback-to-ip 默认 true）；关闭降级则启动失败</li>
 *   <li>openid.fail-fast 子开关经 @Bean 方法条件真正生效（v1.8 顺带修复惰性条件）</li>
 * </ul>
 */
@DisplayName("Id/OpenId 自动装配条件测试（issue #26）")
class IdSdkAutoConfigurationConditionalTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdSdkAutoConfiguration.class));

    /** 构造抛连接异常的 StringRedisTemplate（模拟 Redis 不可达） */
    private StringRedisTemplate unreachableRedisTemplate() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("connection refused"));
        return template;
    }

    @Nested
    @DisplayName("显式启用契约（matchIfMissing=false）")
    class ExplicitEnablementTest {

        @Test
        @DisplayName("framework4j.id.enabled 缺省 → 不激活，无任何 bean")
        void shouldNotActivateByDefault() {
            runner.run(context -> {
                assertThat(context).doesNotHaveBean(WorkerIdStrategy.class);
                assertThat(context).doesNotHaveBean(SnowflakeDistributor.class);
            });
        }

        @Test
        @DisplayName("framework4j.id.enabled=false → 不激活")
        void shouldNotActivateWhenDisabled() {
            runner.withPropertyValues("framework4j.id.enabled=false").run(context -> {
                assertThat(context).doesNotHaveBean(WorkerIdStrategy.class);
                assertThat(context).doesNotHaveBean(SnowflakeDistributor.class);
            });
        }

        @Test
        @DisplayName("enabled=true + ip 策略 → 正常装配，零 Redis 依赖")
        void shouldActivateWithIpStrategy() {
            runner.withPropertyValues(
                    "framework4j.id.enabled=true",
                    "framework4j.id.worker.strategy=ip",
                    "framework4j.id.mybatis.enabled=false"
            ).run(context -> {
                assertThat(context).hasSingleBean(WorkerIdStrategy.class);
                assertThat(context).hasSingleBean(SnowflakeDistributor.class);
            });
        }

        @Test
        @DisplayName("mybatis.enabled 缺省（父开关开启）→ 子开关默认跟随，mpIdGenerator 注册")
        void mybatisSubToggleDefaultsOnWhenParentEnabled() {
            runner.withPropertyValues(
                    "framework4j.id.enabled=true",
                    "framework4j.id.worker.strategy=ip"
            ).run(context -> assertThat(context).hasBean("mpIdGenerator"));
        }
    }

    @Nested
    @DisplayName("fail-soft 降级（fallback-to-ip）")
    class FallbackTest {

        @Test
        @DisplayName("redis 策略 + Redis 不可达 + 默认 fallback-to-ip=true → 降级 IP Hash，上下文正常启动")
        void shouldDegradeToIpWhenRedisUnreachable() {
            runner.withBean(StringRedisTemplate.class,
                            IdSdkAutoConfigurationConditionalTest.this::unreachableRedisTemplate)
                    .withPropertyValues(
                            "spring.application.name=runner-app",
                            "framework4j.id.enabled=true",
                            "framework4j.id.worker.strategy=redis"
                    ).run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(SnowflakeDistributor.class);
                    });
        }

        @Test
        @DisplayName("redis 策略 + Redis 不可达 + fallback-to-ip=false → 保持 1.7.x 启动失败语义")
        void shouldFailStartupWhenFallbackDisabled() {
            runner.withBean(StringRedisTemplate.class,
                            IdSdkAutoConfigurationConditionalTest.this::unreachableRedisTemplate)
                    .withPropertyValues(
                            "spring.application.name=runner-app",
                            "framework4j.id.enabled=true",
                            "framework4j.id.worker.strategy=redis",
                            "framework4j.id.worker.fallback-to-ip=false"
                    ).run(context -> {
                        assertThat(context).hasFailed();
                        // 失败链: BeanCreationException → WorkerIdRedisException → RuntimeException(connection refused)
                        assertThat(context.getStartupFailure())
                                .hasStackTraceContaining("WorkerIdRedisException")
                                .hasStackTraceContaining("Redis WorkerID 申请失败");
                    });
        }
    }

    @Nested
    @DisplayName("OpenID 显式启用 + fail-fast 惰性条件修复")
    class OpenIdConditionalTest {

        private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenIdAutoConfiguration.class))
                .withBean(ObjectMapper.class);

        @Test
        @DisplayName("framework4j.openid.enabled 缺省 → 不激活，4 bean 全不注册")
        void openIdShouldNotActivateByDefault() {
            webRunner.run(context -> {
                assertThat(context).doesNotHaveBean("framework4jOpenIdJacksonModuleRegistrar");
                assertThat(context).doesNotHaveBean("framework4jOpenIdWebMvcConfigurer");
                assertThat(context).doesNotHaveBean("framework4jOpenIdArgumentResolverRegistrar");
                assertThat(context).doesNotHaveBean("framework4jOpenIdFailFastValidator");
            });
        }

        @Test
        @DisplayName("openid.enabled=true → 4 bean 注册")
        void openIdShouldActivateWhenExplicitlyEnabled() {
            webRunner.withPropertyValues("framework4j.openid.enabled=true").run(context -> {
                assertThat(context).hasBean("framework4jOpenIdJacksonModuleRegistrar");
                assertThat(context).hasBean("framework4jOpenIdWebMvcConfigurer");
                assertThat(context).hasBean("framework4jOpenIdArgumentResolverRegistrar");
                assertThat(context).hasBean("framework4jOpenIdFailFastValidator");
            });
        }

        @Test
        @DisplayName("openid.enabled=true + fail-fast=false → validator 不注册（v1.8 惰性条件修复）")
        void failFastSubToggleShouldTakeEffect() {
            webRunner.withPropertyValues(
                    "framework4j.openid.enabled=true",
                    "framework4j.openid.fail-fast=false"
            ).run(context -> {
                assertThat(context).hasBean("framework4jOpenIdJacksonModuleRegistrar");
                assertThat(context).doesNotHaveBean("framework4jOpenIdFailFastValidator");
            });
        }
    }
}
