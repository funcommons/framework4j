package fun.commons.framework4j.web.config;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #21 / ADR-0009 缺口1:TraceConfig 装配顺序。
 * @ConditionalOnBean(Tracer) 必须保证在 Micrometer Tracer 装配之后评估 ——
 * v1.6.0 改为独立 @AutoConfiguration(afterName=MicrometerTracing) 经 imports 注册。
 */
class TraceConfigAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TraceConfig.class));

    @Configuration
    static class TracerStubConfig {
        @Bean
        Tracer tracer() {
            return Mockito.mock(Tracer.class);
        }
    }

    @Test
    @DisplayName("有 Tracer Bean:traceIdResponseHeaderFilter 注册(无论评估顺序)")
    void withTracer_filterRegistered() {
        // 先用户配置后 auto-config:模拟业务 @Configuration 先处理的场景
        runner.withUserConfiguration(TracerStubConfig.class).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasBean("traceIdResponseHeaderFilter");
            assertThat(ctx.getBean("traceIdResponseHeaderFilter")).isInstanceOf(Filter.class);
        });
    }

    @Test
    @DisplayName("无 Tracer Bean:整类跳过,上下文不炸")
    void withoutTracer_skipped() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean("traceIdResponseHeaderFilter");
        });
    }

    @Test
    @DisplayName("无 micrometer-tracing 类:整类跳过(@ConditionalOnClass)")
    void withoutTracerClass_skipped() {
        runner.withClassLoader(new FilteredClassLoader(Tracer.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean("traceIdResponseHeaderFilter"));
    }

    @Test
    @DisplayName("framework4j.api.trace.enabled=false:有 Tracer 也不注册")
    void disabledByProperty() {
        runner.withUserConfiguration(TracerStubConfig.class)
                .withPropertyValues("framework4j.api.trace.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean("traceIdResponseHeaderFilter"));
    }
}
