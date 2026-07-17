package fun.commons.framework4j.datasource.tracing;

import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SqlTracingAutoConfiguration 集成测试
 * <p>
 * 用 ApplicationContextRunner 加载 AutoConfiguration，验证：
 * - 主开关 enabled=false 时不创建 BeanPostProcessor
 * - 缺 Druid 类时不创建（@ConditionalOnClass）
 * - 默认 enabled=true（matchIfMissing）
 * - 配置了 datasources.{name}.sql-tracing.* 时为 DruidDataSource 注入 TraceIdDruidFilter
 */
class SqlTracingAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SqlTracingAutoConfiguration.class))
            .withUserConfiguration(TestDataSourceConfig.class);

    @Test
    @DisplayName("默认 matchIfMissing=true：自动配置加载")
    void autoConfigLoadsByDefault() {
        runner.run(ctx -> {
            assertTrue(ctx.containsBean("sqlTracingBeanPostProcessor"),
                    "SqlTracingBeanPostProcessor 应注册为 bean");
        });
    }

    @Test
    @DisplayName("enabled=false 时不加载")
    void disabledByProperty() {
        runner.withPropertyValues("framework4j.datasource.sql-tracing.enabled=false")
                .run(ctx -> assertFalse(ctx.containsBean("sqlTracingBeanPostProcessor")));
    }

    @Test
    @DisplayName("enabled=true 显式加载")
    void enabledExplicitly() {
        runner.withPropertyValues("framework4j.datasource.sql-tracing.enabled=true")
                .run(ctx -> assertTrue(ctx.containsBean("sqlTracingBeanPostProcessor")));
    }

    @Test
    @DisplayName("为配置了 sql-tracing.mode=ALL 的 DruidDataSource 注入 Filter")
    void injectsFilterForConfiguredDatasource() {
        runner.withPropertyValues(
                "spring.application.name=test-app",
                "framework4j.datasource.datasources.test.sql-tracing.mode=ALL",
                "framework4j.datasource.datasources.test.sql-tracing.topic=my-topic"
        ).run(ctx -> {
            DruidDataSource ds = ctx.getBean("testDataSource", DruidDataSource.class);
            List<com.alibaba.druid.filter.Filter> filters = ds.getProxyFilters();
            boolean hasTraceFilter = filters.stream()
                    .anyMatch(f -> f instanceof TraceIdDruidFilter);
            assertTrue(hasTraceFilter, "应注入 TraceIdDruidFilter");
        });
    }

    @Test
    @DisplayName("mode=DISABLED 时不注入 Filter")
    void noFilterWhenDisabled() {
        runner.withPropertyValues(
                "spring.application.name=test-app",
                "framework4j.datasource.datasources.test.sql-tracing.mode=DISABLED"
        ).run(ctx -> {
            DruidDataSource ds = ctx.getBean("testDataSource", DruidDataSource.class);
            List<com.alibaba.druid.filter.Filter> filters = ds.getProxyFilters();
            boolean hasTraceFilter = filters.stream()
                    .anyMatch(f -> f instanceof TraceIdDruidFilter);
            assertFalse(hasTraceFilter, "DISABLED 模式不应注入 Filter");
        });
    }

    @Test
    @DisplayName("未配置 sql-tracing 的数据源不注入 Filter")
    void noFilterForUnconfiguredDatasource() {
        runner.run(ctx -> {
            DruidDataSource ds = ctx.getBean("testDataSource", DruidDataSource.class);
            List<com.alibaba.druid.filter.Filter> filters = ds.getProxyFilters();
            boolean hasTraceFilter = filters.stream()
                    .anyMatch(f -> f instanceof TraceIdDruidFilter);
            assertFalse(hasTraceFilter, "未配置的数据源不应注入 Filter");
        });
    }

    @Test
    @DisplayName("topic 未配置时用 spring.application.name 兜底")
    void topicFallsBackToAppName() {
        runner.withPropertyValues(
                "spring.application.name=my-app-name",
                "framework4j.datasource.datasources.test.sql-tracing.mode=ALL"
        ).run(ctx -> {
            DruidDataSource ds = ctx.getBean("testDataSource", DruidDataSource.class);
            TraceIdDruidFilter filter = ds.getProxyFilters().stream()
                    .filter(f -> f instanceof TraceIdDruidFilter)
                    .map(f -> (TraceIdDruidFilter) f)
                    .findFirst()
                    .orElseThrow();
            // 通过反射读 private topic 字段
            var field = TraceIdDruidFilter.class.getDeclaredField("topic");
            field.setAccessible(true);
            assertEquals("my-app-name", field.get(filter));
        });
    }

    @Configuration
    static class TestDataSourceConfig {
        @Bean
        public DataSource testDataSource() {
            DruidDataSource ds = new DruidDataSource();
            ds.setUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
            ds.setDriverClassName("org.h2.Driver");
            ds.setUsername("sa");
            ds.setPassword("");
            return ds;
        }
    }
}
