package fun.commons.framework4j.datasource.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import fun.commons.framework4j.datasource.annotation.DataSourceOn;
import fun.commons.framework4j.datasource.manager.MultiDataSourceManager;
import fun.commons.framework4j.datasource.properties.DataSourceProperties;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
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
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.*;

/**
 * 多数据源自动配置
 * <p>
 * 适配 Spring Boot 3.2.0 / Spring Framework 6.1.1 标准写法
 * [DEBUG 版本 2.0.7] 移除 factoryBeanObjectType 手动设置，使用纯净 RootBeanDefinition 交由 Spring 自动推断
 *
 * @since 2.0.7
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({DruidDataSource.class, MybatisSqlSessionFactoryBean.class})
@ConditionalOnProperty(prefix = "framework4j.datasource", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MultiDataSourceAutoConfiguration.MultiDataSourcePropertiesContainer.class)
@Import({MultiDataSourceAutoConfiguration.MultiDataSourceRegistrar.class})
public class MultiDataSourceAutoConfiguration {

    @Bean
    public static DataSourceOnBeanPostProcessor dataSourceOnBeanPostProcessor(MultiDataSourceManager manager) {
        DataSourceOnBeanPostProcessor processor = new DataSourceOnBeanPostProcessor(manager);
        log.info("【Multi-DataSource】dataSourceOnBeanPostProcessor，@DataSourceOn注解处理器，支持类级别和字段级别的数据源自动注入");
        return processor;
    }

    // SqlTracingAutoConfiguration 已独立注册到 AutoConfiguration.imports
    // 主开关：framework4j.datasource.sql-tracing.enabled（默认 true）

    /**
     * MyBatis Plus 内置插件自动装配（方案 C）
     * <p>默认加载分页 + 防全表更新；乐观锁 / 多租户需 yml 开启。
     * <p>用户自定义 {@code @Bean MybatisPlusInterceptor} 时自动退让（{@code @ConditionalOnMissingBean}）。
     */
    @org.springframework.context.annotation.Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(
            type = "com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(
            name = "com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "framework4j.datasource", name = "enabled", havingValue = "true")
    public com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor framework4jMybatisPlusInterceptor(
            MultiDataSourcePropertiesContainer container) {
        DataSourceProperties.MybatisPlusPluginsConfig cfg = null;
        // 从 datasources.default 取插件配置（全局生效）
        DataSourceProperties defaultDs = container.getDatasources().get("default");
        if (defaultDs != null) {
            cfg = defaultDs.getMybatisPlusPlugins();
        }
        if (cfg == null) {
            cfg = new DataSourceProperties.MybatisPlusPluginsConfig();
        }

        com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor interceptor =
                new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();

        // 1. 分页（默认 true）
        if (cfg.isPagination()) {
            com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor page =
                    new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor();
            if (cfg.getDbType() != null && !cfg.getDbType().isEmpty()) {
                try {
                    page.setDbType(com.baomidou.mybatisplus.annotation.DbType.valueOf(cfg.getDbType()));
                } catch (IllegalArgumentException e) {
                    log.warn("【Multi-DataSource】MyBatis Plus db-type={} 无效，用自动检测", cfg.getDbType());
                }
            }
            interceptor.addInnerInterceptor(page);
            log.info("【Multi-DataSource】MyBatis Plus 分页插件已加载（dbType={}）",
                    cfg.getDbType() != null ? cfg.getDbType() : "auto");
        }

        // 2. 防全表更新（默认 true）
        if (cfg.isBlockAttack()) {
            interceptor.addInnerInterceptor(
                    new com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor());
            log.info("【Multi-DataSource】MyBatis Plus 防全表更新插件已加载");
        }

        // 3. 乐观锁（默认 false）
        if (cfg.isOptimisticLock()) {
            interceptor.addInnerInterceptor(
                    new com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor());
            log.info("【Multi-DataSource】MyBatis Plus 乐观锁插件已加载");
        }

        // 4. 多租户（默认 false）— 仅注册拦截器框架，业务方需提供 TenantLineHandler Bean
        if (cfg.isDataPermission()) {
            log.warn("【Multi-DataSource】MyBatis Plus 多租户已开启，业务方需自行注册 TenantLineHandler Bean 提供 tenantId");
        }

        return interceptor;
    }

    @Data
    @ConfigurationProperties(prefix = "framework4j.datasource")
    public static class MultiDataSourcePropertiesContainer {
        private boolean enabled;
        private Map<String, DataSourceProperties> datasources = new LinkedHashMap<>();
    }

    /**
     * 多数据源注册器
     */
    public static class MultiDataSourceRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {
        private Environment environment;

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
            MultiDataSourcePropertiesContainer properties = Binder.get(environment)
                    .bind("framework4j.datasource", MultiDataSourcePropertiesContainer.class)
                    .orElse(null);

            if (properties == null || !properties.isEnabled() || properties.getDatasources().isEmpty()) {
                return;
            }

            log.info("【Multi-DataSource】开始动态注册数据源，共 {} 个配置", properties.getDatasources().size());

            Set<String> registeredNames = new HashSet<>();
            String primaryConfigKey = determinePrimaryConfigKey(properties);

            // 验证别名冲突（防止多个 @Primary bean）
            validateAliasConflicts(properties, primaryConfigKey);

            properties.getDatasources().forEach((configKey, config) -> {
                String primaryName = configKey.trim();
                List<String> aliases = new ArrayList<>();
                if (config.getAliases() != null) {
                    aliases.addAll(config.getAliases());
                }

                boolean isPrimary = primaryName.equals(primaryConfigKey);
                if (!isPrimary && aliases.contains(primaryConfigKey)) {
                    isPrimary = true;
                }

                String primaryFlag = isPrimary ? "，@Primary" : "";

                // A. DataSource
                String dataSourceBeanName = primaryName + "DataSource";
                AbstractBeanDefinition dataSourceDef = createDataSourceBeanDef(config);
                if (isPrimary) dataSourceDef.setPrimary(true);
                registry.registerBeanDefinition(dataSourceBeanName, dataSourceDef);
                log.info("【Multi-DataSource】{}，Druid数据源，url={}{}",
                        dataSourceBeanName, maskPassword(config.getUrl()), primaryFlag);

                // B. SqlSessionFactory
                String factoryBeanName = primaryName + "SqlSessionFactory";
                AbstractBeanDefinition factoryDef = createSqlSessionFactoryBeanDef(dataSourceBeanName, config);
                if (isPrimary) factoryDef.setPrimary(true);
                registry.registerBeanDefinition(factoryBeanName, factoryDef);
                log.info("【Multi-DataSource】{}，MyBatis-Plus SqlSessionFactory{}",
                        factoryBeanName, primaryFlag);

                // C. SqlSessionTemplate
                String templateBeanName = primaryName + "SqlSessionTemplate";
                AbstractBeanDefinition templateDef = BeanDefinitionBuilder.genericBeanDefinition(SqlSessionTemplate.class)
                        .addConstructorArgReference(factoryBeanName)
                        .getBeanDefinition();
                if (isPrimary) templateDef.setPrimary(true);
                registry.registerBeanDefinition(templateBeanName, templateDef);
                log.info("【Multi-DataSource】{}，MyBatis SqlSessionTemplate{}",
                        templateBeanName, primaryFlag);

                // D. TransactionManager
                String txManagerBeanName = primaryName + "TransactionManager";
                AbstractBeanDefinition txManagerDef = BeanDefinitionBuilder.genericBeanDefinition(DataSourceTransactionManager.class)
                        .addConstructorArgReference(dataSourceBeanName)
                        .getBeanDefinition();
                if (isPrimary) txManagerDef.setPrimary(true);
                registry.registerBeanDefinition(txManagerBeanName, txManagerDef);
                log.info("【Multi-DataSource】{}，Spring事务管理器{}",
                        txManagerBeanName, primaryFlag);

                // E. JdbcTemplate
                String jdbcTemplateBeanName = primaryName + "JdbcTemplate";
                AbstractBeanDefinition jdbcTemplateDef = BeanDefinitionBuilder.genericBeanDefinition(org.springframework.jdbc.core.JdbcTemplate.class)
                        .addConstructorArgReference(dataSourceBeanName)
                        .getBeanDefinition();
                if (isPrimary) jdbcTemplateDef.setPrimary(true);
                registry.registerBeanDefinition(jdbcTemplateBeanName, jdbcTemplateDef);
                log.info("【Multi-DataSource】{}，Spring JdbcTemplate{}",
                        jdbcTemplateBeanName, primaryFlag);

                registeredNames.add(primaryName);

                // 注册别名
                for (String alias : aliases) {
                    String trimmedAlias = alias.trim();
                    if (trimmedAlias.equals(primaryName)) continue;

                    registerAlias(registry, dataSourceBeanName, trimmedAlias + "DataSource");
                    registerAlias(registry, factoryBeanName, trimmedAlias + "SqlSessionFactory");
                    registerAlias(registry, templateBeanName, trimmedAlias + "SqlSessionTemplate");
                    registerAlias(registry, txManagerBeanName, trimmedAlias + "TransactionManager");
                    registerAlias(registry, jdbcTemplateBeanName, trimmedAlias + "JdbcTemplate");
                    registerAlias(registry, dataSourceBeanName, trimmedAlias);

                    registeredNames.add(trimmedAlias);
                }

                // 为主名称注册简短别名
                registerAlias(registry, dataSourceBeanName, primaryName);
            });

            // 注册 Manager
            BeanDefinitionBuilder managerBuilder = BeanDefinitionBuilder.genericBeanDefinition(MultiDataSourceManager.class);
            managerBuilder.addPropertyValue("springDataSourceNames", registeredNames);
            // v2.1: 声明 destroy 方法，应用关闭时关闭所有 Druid 连接池
            managerBuilder.setDestroyMethodName("destroy");
            registry.registerBeanDefinition("multiDataSourceManager", managerBuilder.getBeanDefinition());
        }

        private void registerAlias(BeanDefinitionRegistry registry, String beanName, String alias) {
            if (!registry.containsBeanDefinition(alias) && !registry.isAlias(alias)) {
                registry.registerAlias(beanName, alias);
            }
        }

        /**
         * 隐藏 JDBC URL 中的密码信息
         */
        private String maskPassword(String url) {
            if (url == null) return "null";
            // 隐藏 password= 参数
            return url.replaceAll("password=[^&;]*", "password=***");
        }

        /**
         * Determines which datasource should be marked as @Primary.
         * <p>
         * Selection priority:
         * <ol>
         *   <li>Datasource named "default" (if exists)</li>
         *   <li>First configured datasource in YAML order</li>
         * </ol>
         *
         * @param properties Multi-datasource configuration container
         * @return Primary datasource key, or null if no datasources configured
         */
        private String determinePrimaryConfigKey(MultiDataSourcePropertiesContainer properties) {
            // Priority 1: If "default" datasource exists, use it
            if (properties.getDatasources().containsKey("default")) {
                log.info("Primary datasource auto-selected: 'default'");
                return "default";
            }

            // Priority 2: Use first configured datasource (LinkedHashMap maintains YAML order)
            if (!properties.getDatasources().isEmpty()) {
                String firstKey = properties.getDatasources().keySet().iterator().next();
                log.info("Primary datasource auto-selected: '{}' (first configured)", firstKey);
                return firstKey;
            }

            return null;
        }

        /**
         * 验证别名配置，防止多个数据源被标记为 @Primary
         * <p>
         * 检测场景：如果某个数据源的 alias 包含了 primary 数据源的名称，会导致多个 @Primary bean 冲突
         * <p>
         * 示例错误配置:
         * <pre>
         * datasources:
         *   default:  # ← 自动成为 @Primary
         *     ...
         *   business:
         *     aliases: [default]  # ← 错误! 会导致 business 也成为 @Primary
         * </pre>
         *
         * @param properties Multi-datasource configuration container
         * @param primaryKey Primary datasource key
         * @throws IllegalStateException 如果检测到别名冲突
         */
        private void validateAliasConflicts(MultiDataSourcePropertiesContainer properties, String primaryKey) {
            if (primaryKey == null) {
                return;
            }

            properties.getDatasources().forEach((name, config) -> {
                if (config.getAliases() != null) {
                    for (String alias : config.getAliases()) {
                        String trimmedAlias = alias.trim();
                        if (trimmedAlias.equals(primaryKey) && !name.equals(primaryKey)) {
                            String errorMsg = String.format(
                                "❌ Alias 冲突检测失败!\n" +
                                "数据源 '%s' 的 alias '%s' 与 primary 数据源名称冲突。\n" +
                                "这会导致多个 @Primary bean，Spring 容器启动失败。\n\n" +
                                "错误配置:\n" +
                                "  datasources:\n" +
                                "    %s:  # ← Primary 数据源\n" +
                                "      ...\n" +
                                "    %s:\n" +
                                "      aliases: [%s]  # ← 错误! 移除此 alias\n\n" +
                                "修复方法:\n" +
                                "  1. 移除 '%s' 数据源的 alias '%s'\n" +
                                "  2. 或者将 '%s' 数据源重命名为其他名称\n\n" +
                                "详见文档: 多Datasource数据源注入器产品文档v2.md - FAQ Q15",
                                name, trimmedAlias, primaryKey, name, trimmedAlias, name, trimmedAlias, name
                            );
                            log.error(errorMsg);
                            throw new IllegalStateException(errorMsg);
                        }
                    }
                }
            });

            log.info("✅ Alias 冲突验证通过，primary 数据源: '{}'", primaryKey);
        }

        private AbstractBeanDefinition createDataSourceBeanDef(DataSourceProperties config) {
            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(DruidDataSource.class);
            builder.addPropertyValue("url", config.getUrl());
            builder.addPropertyValue("username", config.getUsername());
            builder.addPropertyValue("password", config.getPassword());
            builder.addPropertyValue("driverClassName", config.getDriverClassName());

            if (config.getDruid() != null) {
                builder.addPropertyValue("initialSize", config.getDruid().getInitialSize());
                builder.addPropertyValue("minIdle", config.getDruid().getMinIdle());
                builder.addPropertyValue("maxActive", config.getDruid().getMaxActive());
                builder.addPropertyValue("maxWait", config.getDruid().getMaxWait());
                builder.addPropertyValue("testWhileIdle", config.getDruid().isTestWhileIdle());
                builder.addPropertyValue("testOnBorrow", config.getDruid().isTestOnBorrow());
                builder.addPropertyValue("testOnReturn", config.getDruid().isTestOnReturn());
                builder.addPropertyValue("validationQuery", config.getDruid().getValidationQuery());
                builder.addPropertyValue("timeBetweenEvictionRunsMillis", config.getDruid().getTimeBetweenEvictionRunsMillis());
                builder.addPropertyValue("minEvictableIdleTimeMillis", config.getDruid().getMinEvictableIdleTimeMillis());

                if (StringUtils.hasText(config.getDruid().getFilters())) {
                    try {
                        builder.addPropertyValue("filters", config.getDruid().getFilters());
                    } catch (Exception e) {
                        log.warn("设置 Druid filters 失败: {}", e.getMessage());
                    }
                }

                if (StringUtils.hasText(config.getDruid().getConnectionProperties())) {
                    builder.addPropertyValue("connectionProperties", config.getDruid().getConnectionProperties());
                }
            }

            builder.setInitMethodName("init");
            builder.setDestroyMethodName("close");
            return builder.getBeanDefinition();
        }

        private AbstractBeanDefinition createSqlSessionFactoryBeanDef(String dataSourceBeanName, DataSourceProperties config) {
            // 【核心修复】使用 RootBeanDefinition 替代 BeanDefinitionBuilder
            // 避免 Builder 读取可能存在的旧注解元数据
            RootBeanDefinition beanDef = new RootBeanDefinition(MybatisSqlSessionFactoryBean.class);
            MutablePropertyValues pvs = beanDef.getPropertyValues();

            pvs.add("dataSource", new RuntimeBeanReference(dataSourceBeanName));

            DataSourceProperties.MybatisPlusConfig mybatis = config.getMybatisPlus();
            if (mybatis != null) {
                if (mybatis.getMapperLocations() != null && mybatis.getMapperLocations().length > 0) {
                    try {
                        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                        List<org.springframework.core.io.Resource> resources = new ArrayList<>();
                        for (String location : mybatis.getMapperLocations()) {
                            try {
                                org.springframework.core.io.Resource[] mapperResources = resolver.getResources(location);
                                Collections.addAll(resources, mapperResources);
                            } catch (Exception e) {
                                log.warn("加载 Mapper 路径失败 [{}]: {}", location, e.getMessage());
                            }
                        }
                        if (!resources.isEmpty()) {
                            pvs.add("mapperLocations", resources.toArray(new org.springframework.core.io.Resource[0]));
                        }
                    } catch (Exception e) {
                        log.warn("Mapper 文件解析器初始化失败", e);
                    }
                }

                if (StringUtils.hasText(mybatis.getTypeAliasesPackage())) {
                    pvs.add("typeAliasesPackage", mybatis.getTypeAliasesPackage());
                }

                if (StringUtils.hasText(mybatis.getConfigLocation())) {
                    pvs.add("configLocation", mybatis.getConfigLocation());
                }
            }

            // 【移除】不再手动设置 factoryBeanObjectType
            // beanDef.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, ResolvableType.forClass(SqlSessionFactory.class));

            return beanDef;
        }
    }

}