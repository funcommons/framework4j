package fun.commons.framework4j.datasource.tracing;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * v2.1 拆出顶级类：原 {@link SqlTracingAutoConfiguration} 自身 implements BeanPostProcessor，
 * 违反 mc-java-spec 规范"BeanPostProcessor 必须顶级类"。
 * <p>
 * 在 {@link DruidDataSource} 初始化前注入 {@link TraceIdDruidFilter}，按每数据源 sql-tracing.* 配置。
 */
public class SqlTracingBeanPostProcessor implements BeanPostProcessor, EnvironmentAware {

    private Environment environment;
    private String applicationName;
    private TraceIdProvider traceIdProvider;

    @Override
    public void setEnvironment(@NonNull Environment environment) {
        this.environment = environment;
        this.applicationName = environment.getProperty("spring.application.name", "unknown");
        this.traceIdProvider = createTraceIdProvider();
    }

    @Override
    public Object postProcessBeforeInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        if (bean instanceof DruidDataSource druidDataSource) {
            String datasourceName = extractDatasourceName(beanName);
            SqlTracingProperties tracingProps = getTracingProperties(datasourceName);

            if (tracingProps != null && tracingProps.isEnabled()) {
                addTraceFilter(druidDataSource, datasourceName, tracingProps);
            }
        }
        return bean;
    }

    private String extractDatasourceName(String beanName) {
        if (beanName.endsWith("DataSource")) {
            return beanName.substring(0, beanName.length() - "DataSource".length());
        }
        return beanName;
    }

    private SqlTracingProperties getTracingProperties(String datasourceName) {
        try {
            String prefix = "framework4j.datasource.datasources." + datasourceName + ".sql-tracing";
            return org.springframework.boot.context.properties.bind.Binder.get(environment)
                    .bind(prefix, SqlTracingProperties.class)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private TraceIdProvider createTraceIdProvider() {
        // v2.1: 当前固定用 DefaultTraceIdProvider（从 MDC 读）。
        // 未来按 SqlTracingProperties.provider (auto/micrometer/mdc/none) 用 @ConditionalOnProperty 装配。
        return new DefaultTraceIdProvider();
    }

    private void addTraceFilter(DruidDataSource dataSource, String datasourceName, SqlTracingProperties props) {
        String topic = StringUtils.hasText(props.getTopic()) ? props.getTopic() : applicationName;
        TraceIdDruidFilter filter = new TraceIdDruidFilter(traceIdProvider, topic, props.getMode());

        List<com.alibaba.druid.filter.Filter> filters = dataSource.getProxyFilters();
        if (filters == null) {
            filters = new ArrayList<>();
        } else {
            filters = new ArrayList<>(filters);
        }
        filters.add(filter);
        dataSource.setProxyFilters(filters);
    }
}
