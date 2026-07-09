package fun.commons.framework4j.redis.config;

import fun.commons.framework4j.redis.annotation.RedisOn;
import fun.commons.framework4j.redis.exception.RedisDataSourceException;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.ReflectionUtils;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * {@code @RedisOn} 注解处理器（BeanPostProcessor）
 * <p>
 * 支持类级别 + 字段级别的 Redis 数据源自动注入：
 * <ul>
 *   <li>类级别：标注在类上，类中所有 StringRedisTemplate/RedisTemplate/RedissonClient 字段自动注入指定数据源</li>
 *   <li>字段级别：标注在字段上，仅该字段注入指定数据源</li>
 * </ul>
 *
 * <p>{@code strict} 模式（默认 true）：数据源不存在时抛异常；{@code strict=false} 时回退到 default 数据源
 *
 * @since 2.0.0（从 MultiRedisAutoConfiguration 内嵌静态类提为顶级类）
 */
public class RedisOnBeanPostProcessor implements BeanPostProcessor, EnvironmentAware {

    private final MultiRedisManager manager;
    private Environment environment;

    public RedisOnBeanPostProcessor(MultiRedisManager manager) {
        this.manager = manager;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();
        RedisOn typeAnnotation = AnnotationUtils.findAnnotation(targetClass, RedisOn.class);
        String defaultName = (typeAnnotation != null) ? resolvePlaceholder(typeAnnotation.value()) : null;
        boolean typeStrict = (typeAnnotation != null) && typeAnnotation.strict();

        ReflectionUtils.doWithFields(targetClass, field -> {
            RedisOn fieldAnnotation = field.getAnnotation(RedisOn.class);

            boolean hasExplicitInjection = field.isAnnotationPresent(Resource.class)
                    || field.isAnnotationPresent(Qualifier.class);

            if (fieldAnnotation == null && hasExplicitInjection) {
                return;
            }

            String datasourceName = null;
            boolean strict = true;

            if (fieldAnnotation != null) {
                datasourceName = resolvePlaceholder(fieldAnnotation.value());
                strict = fieldAnnotation.strict();
            } else if (defaultName != null && isRedisType(field.getType())) {
                datasourceName = defaultName;
                strict = typeStrict;
            }

            if (datasourceName != null) {
                injectRedis(bean, field, datasourceName, strict);
            }
        });
        return bean;
    }

    private boolean isRedisType(Class<?> type) {
        return StringRedisTemplate.class.isAssignableFrom(type)
                || RedisTemplate.class.isAssignableFrom(type)
                || RedissonClient.class.isAssignableFrom(type);
    }

    private void injectRedis(Object bean, java.lang.reflect.Field field, String name, boolean strict) {
        if (!manager.containsDatasource(name)) {
            if (strict) {
                throw new RedisDataSourceException("Redis数据源 [" + name + "] 未配置，Bean: " + bean.getClass().getSimpleName());
            } else {
                name = "default";
                if (!manager.containsDatasource(name)) return;
            }
        }

        Object resource = null;

        if (StringRedisTemplate.class.equals(field.getType())) {
            resource = manager.getRedisTemplate(name);
            if (resource != null && !(resource instanceof StringRedisTemplate)) {
                throw new RedisDataSourceException(String.format(
                    "类型不匹配：数据源 [%s] 配置为 RedisTemplate<String, Object> (template-type: object)，"
                    + "但字段 [%s] 期望 StringRedisTemplate。"
                    + "请修改字段类型为 RedisTemplate<String, Object> 或重新配置数据源为 template-type: string",
                    name, field.getName()
                ));
            }
        } else if (RedisTemplate.class.isAssignableFrom(field.getType())) {
            resource = manager.getRedisTemplate(name);
        } else if (RedissonClient.class.isAssignableFrom(field.getType())) {
            resource = manager.getRedissonClient(name);
        }

        if (resource != null) {
            ReflectionUtils.makeAccessible(field);
            ReflectionUtils.setField(field, bean, resource);
        }
    }

    private String resolvePlaceholder(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        try {
            if (environment != null) {
                String resolved = environment.resolvePlaceholders(value);
                return resolved != null ? resolved : value;
            }
        } catch (Exception e) {
            // 解析失败时返回原始值
        }
        return value;
    }
}
