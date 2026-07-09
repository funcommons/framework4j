package fun.commons.framework4j.datasource.config;

import fun.commons.framework4j.datasource.annotation.DataSourceOn;
import fun.commons.framework4j.datasource.exception.DataSourceException;
import fun.commons.framework4j.datasource.manager.MultiDataSourceManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ReflectionUtils;

import jakarta.annotation.Resource;
import javax.sql.DataSource;

/**
 * {@code @DataSourceOn} 注解处理器（BeanPostProcessor）
 * <p>
 * 支持类级别 + 字段级别的数据源自动注入：
 * <ul>
 *   <li>类级别：标注在类上，类中所有 DataSource/SqlSessionFactory/SqlSessionTemplate/TransactionManager/JdbcTemplate 字段自动注入指定数据源</li>
 *   <li>字段级别：标注在字段上，仅该字段注入指定数据源</li>
 * </ul>
 *
 * <p>{@code strict} 模式（默认 true）：数据源不存在时抛异常；{@code strict=false} 时回退到 default 数据源
 *
 * @since 2.0.0（从 MultiDataSourceAutoConfiguration 内嵌静态类提为顶级类）
 */
@Slf4j
public class DataSourceOnBeanPostProcessor implements BeanPostProcessor {

    private final MultiDataSourceManager manager;

    public DataSourceOnBeanPostProcessor(MultiDataSourceManager manager) {
        this.manager = manager;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();
        DataSourceOn typeAnnotation = AnnotationUtils.findAnnotation(targetClass, DataSourceOn.class);
        String defaultName = (typeAnnotation != null) ? typeAnnotation.value() : null;
        boolean typeStrict = (typeAnnotation != null) && typeAnnotation.strict();

        ReflectionUtils.doWithFields(targetClass, field -> {
            DataSourceOn fieldAnnotation = field.getAnnotation(DataSourceOn.class);
            boolean hasExplicitInjection = field.isAnnotationPresent(Resource.class)
                    || field.isAnnotationPresent(Autowired.class)
                    || field.isAnnotationPresent(Qualifier.class);

            if (fieldAnnotation == null && hasExplicitInjection) return;

            String datasourceName = null;
            boolean strict = true;

            if (fieldAnnotation != null) {
                datasourceName = fieldAnnotation.value();
                strict = fieldAnnotation.strict();
            } else if (defaultName != null && isDataSourceType(field.getType())) {
                datasourceName = defaultName;
                strict = typeStrict;
            }

            if (datasourceName != null) {
                injectDataSource(bean, field, datasourceName, strict);
            }
        });
        return bean;
    }

    private boolean isDataSourceType(Class<?> type) {
        return DataSource.class.isAssignableFrom(type)
                || SqlSessionFactory.class.isAssignableFrom(type)
                || SqlSessionTemplate.class.isAssignableFrom(type)
                || PlatformTransactionManager.class.isAssignableFrom(type)
                || org.springframework.jdbc.core.JdbcTemplate.class.isAssignableFrom(type);
    }

    private void injectDataSource(Object bean, java.lang.reflect.Field field, String name, boolean strict) {
        if (!manager.containsDatasource(name)) {
            if (strict) {
                throw new DataSourceException("数据源 [" + name + "] 未配置, Bean: "
                        + bean.getClass().getSimpleName() + ", Field: " + field.getName());
            } else {
                if (manager.containsDatasource("default")) {
                    name = "default";
                } else {
                    log.warn("数据源 [{}] 不存在且无默认数据源，跳过注入: {}", name, field.getName());
                    return;
                }
            }
        }

        Object resource = null;
        Class<?> fieldType = field.getType();

        try {
            if (DataSource.class.isAssignableFrom(fieldType)) {
                resource = manager.getDataSource(name);
            } else if (SqlSessionFactory.class.isAssignableFrom(fieldType)) {
                resource = manager.getSqlSessionFactory(name);
            } else if (SqlSessionTemplate.class.isAssignableFrom(fieldType)) {
                resource = manager.getSqlSessionTemplate(name);
            } else if (PlatformTransactionManager.class.isAssignableFrom(fieldType)) {
                resource = manager.getTransactionManager(name);
            } else if (org.springframework.jdbc.core.JdbcTemplate.class.isAssignableFrom(fieldType)) {
                resource = manager.getJdbcTemplate(name);
            }
        } catch (Exception e) {
            // v2.1 P0 修复：strict=true 时 getDataSource 等抛 DataSourceException 被 catch 后仅 warn，
            // 字段静默留 null，违反 strict 语义。strict 时直接抛让上层感知。
            if (strict) {
                throw new DataSourceException("strict=true 时获取数据源组件失败 [" + name + "]: " + e.getMessage(), e);
            }
            log.warn("获取数据源组件失败 [{}]: {}", name, e.getMessage());
        }

        if (resource != null) {
            ReflectionUtils.makeAccessible(field);
            ReflectionUtils.setField(field, bean, resource);
        }
    }
}
