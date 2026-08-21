package fun.commons.framework4j.datasource.tracing;

import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SqlTracingBeanPostProcessor} 配置查找测试（v1.2.5，下游 benefit4j 排查报告 #6）。
 * <p>
 * 锁定：per-datasource 优先（覆盖项）、全局 {@code framework4j.datasource.sql-tracing.*} 兜底、
 * 都未配置时安全跳过。修复前只查 per-datasource，只配全局时主开关亮了但 filter 静默不注入。
 */
@DisplayName("SqlTracingBeanPostProcessor 配置查找（per-datasource 优先 + 全局兜底）")
class SqlTracingBeanPostProcessorConfigLookupTest {

    private SqlTracingBeanPostProcessor processorWith(MockEnvironment env) {
        env.setProperty("spring.application.name", "test-app");
        SqlTracingBeanPostProcessor processor = new SqlTracingBeanPostProcessor();
        processor.setEnvironment(env);
        return processor;
    }

    @Test
    @DisplayName("只配全局 prefix → 兜底生效，注入 filter（修复核心场景）")
    void globalOnlyFallbackInjectsFilter() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("framework4j.datasource.sql-tracing.mode", "WRITE_ONLY");
        SqlTracingBeanPostProcessor processor = processorWith(env);

        DruidDataSource ds = new DruidDataSource();
        processor.postProcessBeforeInitialization(ds, "defaultDataSource");

        assertThat(ds.getProxyFilters())
                .anyMatch(TraceIdDruidFilter.class::isInstance);
    }

    @Test
    @DisplayName("per-datasource 覆盖全局：per 配 DISABLED → 不注入（优先级证明）")
    void perDatasourceOverridesGlobal() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("framework4j.datasource.sql-tracing.mode", "ALL");
        env.setProperty("framework4j.datasource.datasources.default.sql-tracing.mode", "DISABLED");
        SqlTracingBeanPostProcessor processor = processorWith(env);

        DruidDataSource ds = new DruidDataSource();
        processor.postProcessBeforeInitialization(ds, "defaultDataSource");

        assertThat(ds.getProxyFilters())
                .noneMatch(TraceIdDruidFilter.class::isInstance);
    }

    @Test
    @DisplayName("只配 per-datasource → 注入（既有行为不回归）")
    void perDatasourceOnlyInjectsFilter() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("framework4j.datasource.datasources.order.sql-tracing.mode", "ALL");
        SqlTracingBeanPostProcessor processor = processorWith(env);

        DruidDataSource ds = new DruidDataSource();
        processor.postProcessBeforeInitialization(ds, "orderDataSource");

        assertThat(ds.getProxyFilters())
                .anyMatch(TraceIdDruidFilter.class::isInstance);
    }

    @Test
    @DisplayName("两级都未配置 → 安全跳过，不注入不抛异常")
    void noConfigSkipsSafely() {
        MockEnvironment env = new MockEnvironment();
        SqlTracingBeanPostProcessor processor = processorWith(env);

        DruidDataSource ds = new DruidDataSource();
        processor.postProcessBeforeInitialization(ds, "defaultDataSource");

        assertThat(ds.getProxyFilters())
                .noneMatch(TraceIdDruidFilter.class::isInstance);
    }

    @Test
    @DisplayName("getTracingProperties：全局兜底返回默认 mode=ALL")
    void lookupFallsBackToGlobalDefaults() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("framework4j.datasource.sql-tracing.topic", "custom-topic");
        SqlTracingBeanPostProcessor processor = processorWith(env);

        SqlTracingProperties props = processor.getTracingProperties("default");

        assertThat(props).isNotNull();
        assertThat(props.getTopic()).isEqualTo("custom-topic");
        assertThat(props.isEnabled()).isTrue();
    }
}
