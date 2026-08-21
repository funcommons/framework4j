package fun.commons.framework4j.datasource.tracing;

import com.alibaba.druid.pool.DruidDataSource;
import lombok.extern.slf4j.Slf4j;
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
 * 在 {@link DruidDataSource} 初始化前注入 {@link TraceIdDruidFilter}。
 * <p>
 * v1.2.5 修复（下游 benefit4j 排查报告 #6）：配置查找改为
 * <b>per-datasource 优先、全局兜底</b> ——
 * {@code framework4j.datasource.datasources.{name}.sql-tracing.*} 存在则用之（覆盖项），
 * 否则回退全局 {@code framework4j.datasource.sql-tracing.*}（与主开关同 prefix，
 * 文档宣称的配置位置真正生效）。此前只查 per-datasource，只配全局时主开关亮了
 * 但 filter 静默不注入。跳过注入时现在有明确日志（此前完全静默、异常还被吞掉）。
 */
@Slf4j
public class SqlTracingBeanPostProcessor implements BeanPostProcessor, EnvironmentAware {

    private static final String GLOBAL_PREFIX = "framework4j.datasource.sql-tracing";

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

            if (tracingProps == null) {
                log.warn("【SQL-Tracing】数据源 '{}' 未找到 sql-tracing 配置（已查 per-datasource 与全局前缀 {}），跳过 trace_id 注入",
                        datasourceName, GLOBAL_PREFIX);
            } else if (!tracingProps.isEnabled()) {
                log.info("【SQL-Tracing】数据源 '{}' sql-tracing 已禁用（mode=DISABLED），跳过 trace_id 注入", datasourceName);
            } else {
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

    /**
     * per-datasource 优先（覆盖项），全局 {@code framework4j.datasource.sql-tracing.*} 兜底。
     */
    SqlTracingProperties getTracingProperties(String datasourceName) {
        String perDatasourcePrefix = "framework4j.datasource.datasources." + datasourceName + ".sql-tracing";
        SqlTracingProperties perDatasource = bind(perDatasourcePrefix, datasourceName);
        if (perDatasource != null) {
            return perDatasource;
        }
        return bind(GLOBAL_PREFIX, datasourceName);
    }

    private SqlTracingProperties bind(String prefix, String datasourceName) {
        try {
            return org.springframework.boot.context.properties.bind.Binder.get(environment)
                    .bind(prefix, SqlTracingProperties.class)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("【SQL-Tracing】绑定配置失败，prefix={}，datasource={}：{}", prefix, datasourceName, e.toString());
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
        log.info("【SQL-Tracing】数据源 '{}' 已注入 TraceIdDruidFilter（mode={}，topic={}）",
                datasourceName, props.getMode(), topic);
    }
}
