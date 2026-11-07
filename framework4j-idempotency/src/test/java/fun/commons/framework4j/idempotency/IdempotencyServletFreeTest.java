package fun.commons.framework4j.idempotency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #27 回归：spring-boot-starter-web 改 optional 后，WebFlux / 非 servlet 消费方
 * classpath 上没有 servlet / spring-webmvc 类——IdempotencyAutoConfiguration 必须
 * 整体静默跳过（v1.9 补 @ConditionalOnClass(WebMvcConfigurer) +
 * {@code @ConditionalOnWebApplication(SERVLET)}），不得 NoClassDefFoundError。
 * <p>
 * 用普通 {@code ApplicationContextRunner}（Web 专属 runner 自身依赖 servlet 类，无法在
 * 过滤 classloader 下工作）。修复前本测试失败于解析 @Import(IdempotencyWebMvcConfig) 时的
 * 类加载错误；修复后条件评估阶段即静默跳过。
 */
@DisplayName("issue #27：无 servlet 环境装配静默跳过")
class IdempotencyServletFreeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempotencyAutoConfiguration.class))
            // 模拟 WebFlux 消费方：servlet API 与 spring-webmvc 均不在 classpath
            .withClassLoader(new FilteredClassLoader(
                    "jakarta.servlet", "org.springframework.web.servlet"));

    @Test
    @DisplayName("无 servlet/webmvc 类：自动配置跳过，上下文不炸、零 bean")
    void shouldSkipQuietlyWithoutServletClasses() {
        runner.withPropertyValues(
                "framework4j.idempotency.enabled=true",
                "spring.application.name=servlet-free-test"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(IdempotencyInterceptor.class);
            assertThat(context).doesNotHaveBean(IdempotencyBodyCacheFilter.class);
        });
    }

    @Test
    @DisplayName("守卫类别误判防护：WebMvcConfigurer 类确实被过滤（测试自校验）")
    void filteredClassLoaderActuallyHidesWebClasses() {
        runner.run(context -> {
            ClassLoader cl = context.getClassLoader();
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> Class.forName(WebMvcConfigurer.class.getName(), false, cl))
                    .isInstanceOf(ClassNotFoundException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> Class.forName("jakarta.servlet.http.HttpServletRequest", false, cl))
                    .isInstanceOf(ClassNotFoundException.class);
            // Redis/Jackson 仍在（@ConditionalOnClass 其余条件满足，靠 web 条件跳过）
            assertThat(cl.getResource("com/fasterxml/jackson/databind/ObjectMapper.class")).isNotNull();
            assertThat(cl.getResource("org/springframework/data/redis/core/StringRedisTemplate.class")).isNotNull();
        });
    }
}
