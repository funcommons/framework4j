package fun.commons.framework4j.redis.config;

import fun.commons.framework4j.redis.annotation.RedisOn;
import fun.commons.framework4j.redis.enums.TemplateType;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import fun.commons.framework4j.redis.properties.RedisDataSourceProperties;
import fun.commons.framework4j.redis.serializer.JsonRedisSerializer;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;

@Slf4j
@AutoConfiguration
@ConditionalOnClass({RedisTemplate.class, RedissonClient.class})
@ConditionalOnProperty(prefix = "framework4j.redis", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MultiRedisAutoConfiguration.MultiRedisPropertiesContainer.class)
@Import({MultiRedisAutoConfiguration.MultiRedisRegistrar.class})
public class MultiRedisAutoConfiguration {

    @Bean(destroyMethod = "destroy")
    @ConditionalOnMissingBean
    public MultiRedisManager multiRedisManager() {
        MultiRedisManager manager = new MultiRedisManager();
        log.info("【Multi-Redis】multiRedisManager，多Redis数据源管理器，负责管理和路由多个Redis实例");
        return manager;
    }

    @Bean
    public static RedisOnBeanPostProcessor redisOnBeanPostProcessor(MultiRedisManager manager, Environment environment) {
        RedisOnBeanPostProcessor processor = new RedisOnBeanPostProcessor(manager);
        processor.setEnvironment(environment);
        log.info("【Multi-Redis】redisOnBeanPostProcessor，@RedisOn注解处理器，支持类级别和字段级别的Redis数据源自动注入");
        return processor;
    }

    @Data
    @ConfigurationProperties(prefix = "framework4j.redis")
    public static class MultiRedisPropertiesContainer {
        private boolean enabled;
        private PrimaryConfig primary = new PrimaryConfig();
        private Map<String, RedisDataSourceProperties> datasources = new LinkedHashMap<>();

        @Data
        public static class PrimaryConfig {
            private String template;
            private String client;
        }
    }

    public static class MultiRedisRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {
        private Environment environment;

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
            MultiRedisPropertiesContainer properties = Binder.get(environment)
                    .bind("framework4j.redis", MultiRedisPropertiesContainer.class)
                    .orElse(null);

            if (properties == null || !properties.isEnabled() || properties.getDatasources().isEmpty()) {
                return;
            }

            log.info("【Multi-Redis】开始动态注册 Redis 数据源，共 {} 个配置", properties.getDatasources().size());

            Set<String> registeredNames = new HashSet<>();
            String targetTemplateName = determinePrimaryTemplate(properties);
            String targetClientName = determinePrimaryClient(properties, targetTemplateName);

            log.info("【Multi-Redis】Primary RedisTemplate: {}", targetTemplateName);
            if (targetClientName != null) {
                log.info("【Multi-Redis】Primary RedissonClient: {}", targetClientName);
            }

            properties.getDatasources().forEach((key, config) -> {
                String[] aliases = key.split(",");
                String primaryName = aliases[0].trim();

                List<String> allNames = new ArrayList<>();
                Collections.addAll(allNames, aliases);
                if (config.getAliases() != null) allNames.addAll(config.getAliases());

                // A. Factory
                String factoryBeanName = primaryName + "ConnectionFactory";
                AbstractBeanDefinition factoryDef = createConnectionFactoryBeanDef(config);
                boolean isFactoryPrimary = false;
                for (String name : allNames) {
                    if ((name.trim() + "RedisTemplate").equals(targetTemplateName)) isFactoryPrimary = true;
                }
                if (isFactoryPrimary) factoryDef.setPrimary(true);
                registry.registerBeanDefinition(factoryBeanName, factoryDef);
                log.info("【Multi-Redis】{}，Lettuce连接工厂，host={}:{}，db={}{}",
                        factoryBeanName,
                        config.getHost(),
                        config.getPort(),
                        config.getDatabase(),
                        isFactoryPrimary ? "，@Primary" : "");

                // B. Redisson
                String redissonBeanName = null;
                if (config.getRedisson().isEnabled()) {
                    redissonBeanName = primaryName + "RedissonClient";
                    AbstractBeanDefinition redissonDef = createRedissonBeanDef(config);
                    boolean isRedissonPrimary = redissonBeanName.equals(targetClientName);
                    if (isRedissonPrimary) {
                        redissonDef.setPrimary(true);
                    }
                    registry.registerBeanDefinition(redissonBeanName, redissonDef);
                    log.info("【Multi-Redis】{}，Redisson客户端，支持分布式锁/集合等高级特性{}",
                            redissonBeanName,
                            isRedissonPrimary ? "，@Primary" : "");
                }

                // C. Template
                String mainTemplateBeanName = primaryName + "RedisTemplate";
                AbstractBeanDefinition templateDef;

                // 根据配置的模板类型创建不同的 Bean
                String templateTypeDesc;
                if (config.getTemplateType() == TemplateType.OBJECT) {
                    // 创建 RedisTemplate<String, Object> - 使用 Jackson 序列化
                    templateDef = BeanDefinitionBuilder.genericBeanDefinition(RedisTemplate.class)
                            .addPropertyValue("connectionFactory", new RuntimeBeanReference(factoryBeanName))
                            .setInitMethodName("afterPropertiesSet")
                            .getBeanDefinition();

                    // 手动设置序列化器
                    templateDef.getPropertyValues().add("keySerializer", RedisSerializer.string());
                    templateDef.getPropertyValues().add("valueSerializer", new JsonRedisSerializer<>(Object.class));
                    templateDef.getPropertyValues().add("hashKeySerializer", RedisSerializer.string());
                    templateDef.getPropertyValues().add("hashValueSerializer", new JsonRedisSerializer<>(Object.class));

                    templateTypeDesc = "RedisTemplate<String,Object>，Jackson序列化";
                } else {
                    // 创建 StringRedisTemplate（默认）
                    templateDef = BeanDefinitionBuilder.genericBeanDefinition(StringRedisTemplate.class)
                            .addConstructorArgReference(factoryBeanName)
                            .getBeanDefinition();

                    templateTypeDesc = "StringRedisTemplate，字符串序列化";
                }

                boolean isTemplatePrimary = mainTemplateBeanName.equals(targetTemplateName);
                if (isTemplatePrimary) {
                    templateDef.setPrimary(true);
                    // v2.1 P0 修复：仅 STRING 类型注册 stringRedisTemplate 别名。
                    // OBJECT 类型注册该别名会破坏 Spring Boot 约定（容器中 stringRedisTemplate 应是 StringRedisTemplate），
                    // 导致 @Autowired StringRedisTemplate 注入失败或类型转换异常。
                    if (config.getTemplateType() != TemplateType.OBJECT
                            && !registry.containsBeanDefinition("stringRedisTemplate")
                            && !registry.isAlias("stringRedisTemplate")) {
                        registry.registerAlias(mainTemplateBeanName, "stringRedisTemplate");
                    }
                }

                if (!registry.containsBeanDefinition(mainTemplateBeanName)) {
                    registry.registerBeanDefinition(mainTemplateBeanName, templateDef);
                    log.info("【Multi-Redis】{}，{}{}",
                            mainTemplateBeanName,
                            templateTypeDesc,
                            isTemplatePrimary ? "，@Primary" : "");
                }

                registeredNames.add(primaryName);

                for (String name : allNames) {
                    // 1. Template Alias
                    String aliasTemplateName = name + "RedisTemplate";
                    if (!aliasTemplateName.equals(mainTemplateBeanName)) {
                        if (!registry.containsBeanDefinition(aliasTemplateName) && !registry.isAlias(aliasTemplateName)) {
                            registry.registerAlias(mainTemplateBeanName, aliasTemplateName);
                        }
                    }

                    // 2. Short Name Alias
                    if (!registry.containsBeanDefinition(name) && !registry.isAlias(name)) {
                        registry.registerAlias(mainTemplateBeanName, name);
                    }

                    // 3. Redisson Alias
                    if (redissonBeanName != null) {
                        String clientAlias = name + "RedissonClient";
                        if (!clientAlias.equals(redissonBeanName)) {
                            if (!registry.containsBeanDefinition(clientAlias) && !registry.isAlias(clientAlias)) {
                                registry.registerAlias(redissonBeanName, clientAlias);
                            }
                        }
                    }

                    registeredNames.add(name);
                }
            });

            BeanDefinitionBuilder initBuilder = BeanDefinitionBuilder.genericBeanDefinition(ManagerInitializer.class);
            initBuilder.addConstructorArgReference("multiRedisManager");
            initBuilder.addConstructorArgValue(registeredNames);
            registry.registerBeanDefinition("multiRedisManagerInitializer", initBuilder.getBeanDefinition());
        }

        private String determinePrimaryTemplate(MultiRedisPropertiesContainer properties) {
            String configured = properties.getPrimary().getTemplate();
            if (StringUtils.hasText(configured)) {
                if ("stringRedisTemplate".equals(configured) && properties.getDatasources().containsKey("default")) {
                    return "defaultRedisTemplate";
                }
                return configured;
            }
            if (properties.getDatasources().containsKey("default")) return "defaultRedisTemplate";
            if (!properties.getDatasources().isEmpty()) return properties.getDatasources().keySet().iterator().next().split(",")[0].trim() + "RedisTemplate";
            return null;
        }

        private String determinePrimaryClient(MultiRedisPropertiesContainer properties, String primaryTemplateName) {
            String configured = properties.getPrimary().getClient();
            if (StringUtils.hasText(configured)) return configured;

            if (primaryTemplateName != null) {
                String potentialClientName = primaryTemplateName.replace("RedisTemplate", "RedissonClient");
                for (Map.Entry<String, RedisDataSourceProperties> entry : properties.getDatasources().entrySet()) {
                    String name = entry.getKey().split(",")[0].trim();
                    if ((name + "RedissonClient").equals(potentialClientName) && entry.getValue().getRedisson().isEnabled()) {
                        return potentialClientName;
                    }
                }
            }

            if (properties.getDatasources().containsKey("default") && properties.getDatasources().get("default").getRedisson().isEnabled()) {
                return "defaultRedissonClient";
            }

            for (Map.Entry<String, RedisDataSourceProperties> entry : properties.getDatasources().entrySet()) {
                if (entry.getValue().getRedisson().isEnabled()) {
                    return entry.getKey().split(",")[0].trim() + "RedissonClient";
                }
            }
            return null;
        }

        private AbstractBeanDefinition createConnectionFactoryBeanDef(RedisDataSourceProperties config) {
            RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
            redisConfig.setHostName(config.getHost());
            redisConfig.setPort(config.getPort());
            redisConfig.setDatabase(config.getDatabase());
            if (StringUtils.hasText(config.getPassword())) redisConfig.setPassword(RedisPassword.of(config.getPassword()));

            GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
            RedisDataSourceProperties.PoolConfig sourcePool = config.getLettuce().getPool();
            poolConfig.setMaxTotal(sourcePool.getMaxActive());
            poolConfig.setMaxIdle(sourcePool.getMaxIdle());
            poolConfig.setMinIdle(sourcePool.getMinIdle());
            poolConfig.setMaxWait(Duration.ofMillis(sourcePool.getMaxWait()));

            LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                    .commandTimeout(config.getTimeout())
                    .poolConfig(poolConfig)
                    .build();

            return BeanDefinitionBuilder.genericBeanDefinition(LettuceConnectionFactory.class)
                    .addConstructorArgValue(redisConfig)
                    .addConstructorArgValue(clientConfig)
                    .setInitMethodName("afterPropertiesSet")
                    .getBeanDefinition();
        }

        private AbstractBeanDefinition createRedissonBeanDef(RedisDataSourceProperties config) {
            Config redissonConfig = new Config();
            String address = "redis://" + config.getHost() + ":" + config.getPort();
            redissonConfig.useSingleServer()
                    .setAddress(address)
                    .setDatabase(config.getDatabase())
                    .setPassword(StringUtils.hasText(config.getPassword()) ? config.getPassword() : null)
                    .setTimeout((int) config.getTimeout().toMillis())
                    .setConnectionMinimumIdleSize(config.getLettuce().getPool().getMinIdle())
                    .setConnectionPoolSize(config.getLettuce().getPool().getMaxActive());

            return BeanDefinitionBuilder.genericBeanDefinition(Redisson.class)
                    .setFactoryMethod("create")
                    .addConstructorArgValue(redissonConfig)
                    .setDestroyMethodName("shutdown")
                    .getBeanDefinition();
        }
    }

    public static class ManagerInitializer {
        public ManagerInitializer(MultiRedisManager manager, Set<String> names) {
            manager.addSpringDataSourceNames(names);
        }
    }
}