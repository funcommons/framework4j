package fun.commons.framework4j.datasource.tracing;

import com.alibaba.druid.pool.DruidDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * SQL 追踪自动配置
 * <p>
 * v2.1 重构：原配置类自身 implements BeanPostProcessor，违反"BeanPostProcessor 必须顶级类"规范。
 * 现在改为注册 {@link SqlTracingBeanPostProcessor} 作为独立 Bean。
 *
 * <p>主开关：{@code framework4j.datasource.sql-tracing.enabled=true}（默认 true）
 *
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(DruidDataSource.class)
@ConditionalOnProperty(
        prefix = "framework4j.datasource.sql-tracing",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SqlTracingAutoConfiguration {

    @Bean
    public static SqlTracingBeanPostProcessor sqlTracingBeanPostProcessor() {
        log.info("【SQL-Tracing】sqlTracingBeanPostProcessor，Druid Filter 注入 trace_id");
        return new SqlTracingBeanPostProcessor();
    }
}
