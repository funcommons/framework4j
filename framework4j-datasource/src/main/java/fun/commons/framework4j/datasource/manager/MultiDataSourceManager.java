package fun.commons.framework4j.datasource.manager;

import com.alibaba.druid.pool.DruidDataSource;
import fun.commons.framework4j.datasource.exception.DataSourceException;
import fun.commons.framework4j.datasource.health.HealthCheckResult;
import fun.commons.framework4j.datasource.properties.DataSourceProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多数据源管理器
 *
 * @since 1.0.0
 */
@Slf4j
public class MultiDataSourceManager implements ApplicationContextAware {

    private final Map<String, DataSource> localDataSources = new ConcurrentHashMap<>();
    private final Map<String, SqlSessionFactory> localFactories = new ConcurrentHashMap<>();
    private final Map<String, SqlSessionTemplate> localTemplates = new ConcurrentHashMap<>();
    private final Map<String, PlatformTransactionManager> localTransactionManagers = new ConcurrentHashMap<>();
    private final Map<String, org.springframework.jdbc.core.JdbcTemplate> localJdbcTemplates = new ConcurrentHashMap<>();

    private ApplicationContext applicationContext;
    private final Set<String> springDataSourceNames = ConcurrentHashMap.newKeySet();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 设置 Spring 容器中的数据源名称列表
     * <p>
     * 对应配置类中的 property 注入: managerBuilder.addPropertyValue("springDataSourceNames", registeredNames);
     */
    public void setSpringDataSourceNames(Collection<String> names) {
        this.springDataSourceNames.clear();
        if (names != null) {
            this.springDataSourceNames.addAll(names);
        }
    }

    public void registerDataSource(String name, DataSource dataSource) {
        if (name != null && dataSource != null) localDataSources.put(name, dataSource);
    }

    public void registerSqlSessionFactory(String name, SqlSessionFactory factory) {
        if (name != null && factory != null) localFactories.put(name, factory);
    }

    public void registerSqlSessionTemplate(String name, SqlSessionTemplate template) {
        if (name != null && template != null) localTemplates.put(name, template);
    }

    public void registerTransactionManager(String name, PlatformTransactionManager manager) {
        if (name != null && manager != null) localTransactionManagers.put(name, manager);
    }

    public void registerJdbcTemplate(String name, org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        if (name != null && jdbcTemplate != null) localJdbcTemplates.put(name, jdbcTemplate);
    }

    // ========== 快捷获取默认数据源 ==========

    public DataSource getDefaultDataSource() {
        return getDataSource("default");
    }

    public SqlSessionFactory getDefaultSqlSessionFactory() {
        return getSqlSessionFactory("default");
    }

    public SqlSessionTemplate getDefaultSqlSessionTemplate() {
        return getSqlSessionTemplate("default");
    }

    public PlatformTransactionManager getDefaultTransactionManager() {
        return getTransactionManager("default");
    }

    public org.springframework.jdbc.core.JdbcTemplate getDefaultJdbcTemplate() {
        return getJdbcTemplate("default");
    }

    // ========== 获取指定数据源 ==========

    public DataSource getDataSource(String name) {
        if (localDataSources.containsKey(name)) return localDataSources.get(name);

        if (applicationContext != null) {
            // 1. 直接查 Bean (支持 Spring Alias)
            if (applicationContext.containsBean(name)) {
                if (applicationContext.isTypeMatch(name, DataSource.class)) {
                    return applicationContext.getBean(name, DataSource.class);
                }
            }

            // 2. 查 {name}DataSource
            String beanName = name + "DataSource";
            if (applicationContext.containsBean(beanName)) {
                return applicationContext.getBean(beanName, DataSource.class);
            }

            // 3. 降级 default
            if ("default".equals(name) && applicationContext.containsBean("dataSource")) {
                return applicationContext.getBean("dataSource", DataSource.class);
            }
        }
        throw new DataSourceException("数据源 [" + name + "] 不存在");
    }

    public SqlSessionFactory getSqlSessionFactory(String name) {
        if (localFactories.containsKey(name)) return localFactories.get(name);

        if (applicationContext != null) {
            String beanName = name + "SqlSessionFactory";
            if (applicationContext.containsBean(beanName)) {
                return applicationContext.getBean(beanName, SqlSessionFactory.class);
            }
            if (applicationContext.containsBean(name) && applicationContext.isTypeMatch(name, SqlSessionFactory.class)) {
                return applicationContext.getBean(name, SqlSessionFactory.class);
            }
        }
        throw new DataSourceException("SqlSessionFactory [" + name + "] 不存在");
    }

    public SqlSessionTemplate getSqlSessionTemplate(String name) {
        if (localTemplates.containsKey(name)) return localTemplates.get(name);

        if (applicationContext != null) {
            String beanName = name + "SqlSessionTemplate";
            if (applicationContext.containsBean(beanName)) {
                return applicationContext.getBean(beanName, SqlSessionTemplate.class);
            }
            if (applicationContext.containsBean(name) && applicationContext.isTypeMatch(name, SqlSessionTemplate.class)) {
                return applicationContext.getBean(name, SqlSessionTemplate.class);
            }
        }
        throw new DataSourceException("SqlSessionTemplate [" + name + "] 不存在");
    }

    public PlatformTransactionManager getTransactionManager(String name) {
        if (localTransactionManagers.containsKey(name)) return localTransactionManagers.get(name);

        if (applicationContext != null) {
            String beanName = name + "TransactionManager";
            if (applicationContext.containsBean(beanName)) {
                return applicationContext.getBean(beanName, PlatformTransactionManager.class);
            }
            if (applicationContext.containsBean(name) && applicationContext.isTypeMatch(name, PlatformTransactionManager.class)) {
                return applicationContext.getBean(name, PlatformTransactionManager.class);
            }
        }
        throw new DataSourceException("TransactionManager [" + name + "] 不存在");
    }

    public org.springframework.jdbc.core.JdbcTemplate getJdbcTemplate(String name) {
        if (localJdbcTemplates.containsKey(name)) return localJdbcTemplates.get(name);

        if (applicationContext != null) {
            String beanName = name + "JdbcTemplate";
            if (applicationContext.containsBean(beanName)) {
                return applicationContext.getBean(beanName, org.springframework.jdbc.core.JdbcTemplate.class);
            }
            if (applicationContext.containsBean(name) && applicationContext.isTypeMatch(name, org.springframework.jdbc.core.JdbcTemplate.class)) {
                return applicationContext.getBean(name, org.springframework.jdbc.core.JdbcTemplate.class);
            }
        }
        throw new DataSourceException("JdbcTemplate [" + name + "] 不存在");
    }

    // ========== 数据源管理 ==========

    public boolean containsDatasource(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        if (localDataSources.containsKey(name)) return true;
        if (applicationContext != null) {
            if (springDataSourceNames.contains(name)) return true;
            if (applicationContext.containsBean(name) || applicationContext.containsBean(name + "DataSource")) {
                springDataSourceNames.add(name);
                return true;
            }
        }
        return false;
    }

    public List<String> getAllDatasourceNames() {
        Set<String> names = new HashSet<>(localDataSources.keySet());
        if (applicationContext != null) names.addAll(springDataSourceNames);
        return new ArrayList<>(names);
    }

    /**
     * 动态添加数据源
     * <p>
     * 修复: DataSourceProperties 中没有 name 字段, 需要显式传入 name
     *
     * @param name   数据源名称
     * @param config 数据源配置
     */
    public void addDataSource(String name, DataSourceProperties config) {
        if (!StringUtils.hasText(name) || config == null) {
            log.warn("添加数据源失败: 参数为空");
            return;
        }

        DruidDataSource dataSource = createDruidDataSource(config);

        // 动态创建 MyBatis 相关组件（失败时回滚 DataSource，保证状态一致性）
        try {
            org.mybatis.spring.SqlSessionFactoryBean factoryBean = new org.mybatis.spring.SqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);

            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            factoryBean.setConfiguration(configuration);

            SqlSessionFactory factory = factoryBean.getObject();
            SqlSessionTemplate template = new SqlSessionTemplate(factory);
            PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate = new org.springframework.jdbc.core.JdbcTemplate(dataSource);

            // 全部组件创建成功后，原子注册
            localDataSources.put(name, dataSource);
            localFactories.put(name, factory);
            localTemplates.put(name, template);
            localTransactionManagers.put(name, transactionManager);
            localJdbcTemplates.put(name, jdbcTemplate);

            log.info("动态添加数据源 [{}] 及 MyBatis 组件成功", name);

        } catch (Exception e) {
            // 回滚：关闭已创建的 DataSource，避免连接泄漏
            try {
                dataSource.close();
            } catch (Exception closeEx) {
                log.warn("回滚时关闭 DataSource [{}] 失败: {}", name, closeEx.getMessage());
            }
            log.error("创建动态数据源 [{}] 的 MyBatis 组件失败，已回滚", name, e);
            throw new fun.commons.framework4j.datasource.exception.DataSourceException(
                    "动态添加数据源 [" + name + "] 失败: " + e.getMessage(), e);
        }
    }

    public void removeDataSource(String name) {
        if (localDataSources.containsKey(name)) {
            DataSource ds = localDataSources.remove(name);
            if (ds instanceof DruidDataSource) {
                ((DruidDataSource) ds).close();
            }
        }
        localFactories.remove(name);
        localTemplates.remove(name);
        localTransactionManagers.remove(name);
        localJdbcTemplates.remove(name);
        log.info("移除数据源: {}", name);
    }

    public boolean checkHealth(String name) {
        try {
            DataSource ds = getDataSource(name);
            try (Connection conn = ds.getConnection()) {
                return conn.isValid(3);
            }
        } catch (Exception e) {
            log.warn("数据源 [{}] 健康检查失败: {}", name, e.getMessage());
            return false;
        }
    }

    /**
     * 详细健康检查
     * 返回包含响应时间、连接池状态等详细信息的健康检查结果
     *
     * @param name 数据源名称
     * @return 健康检查结果
     */
    public HealthCheckResult checkHealthDetailed(String name) {
        long startTime = System.currentTimeMillis();
        String errorMessage = null;
        boolean healthy = false;
        HealthCheckResult.PoolStatus poolStatus = null;
        String datasourceType = "Unknown";

        try {
            DataSource ds = getDataSource(name);
            datasourceType = ds.getClass().getSimpleName();

            try (Connection conn = ds.getConnection()) {
                healthy = conn.isValid(3);

                // 如果是Druid数据源，获取连接池状态
                if (ds instanceof DruidDataSource druidDataSource) {
                    poolStatus = new HealthCheckResult.PoolStatus();
                    int active = druidDataSource.getActiveCount();
                    int pooling = druidDataSource.getPoolingCount();
                    // v2.1 修复：Druid 的 getPoolingCount() 是空闲池大小，getActiveCount() 是借出活跃数。
                    // 原实现 idle = pooling - active 会负数，total = pooling 漏算活跃连接。
                    poolStatus.setActiveConnections(active);
                    poolStatus.setIdleConnections(pooling);
                    poolStatus.setTotalConnections(active + pooling);
                    poolStatus.setMaxConnections(druidDataSource.getMaxActive());
                    poolStatus.setUtilizationRate(poolStatus.calculateUtilizationRate());
                }
            }
        } catch (Exception e) {
            // v2.1 P1 修复：errorMessage 不返回 e.getMessage()，可能含 DB 连接串/主机名/账号等敏感信息
            // （actuator health endpoint 会暴露给上层）。详细堆栈记日志，返回固定文本。
            errorMessage = "数据源健康检查失败: " + e.getClass().getSimpleName();
            log.warn("数据源 [{}] 详细健康检查失败: {}", name, e.getMessage(), e);
        }

        long responseTime = System.currentTimeMillis() - startTime;

        if (healthy) {
            return HealthCheckResult.healthy(name, responseTime, datasourceType, poolStatus);
        } else {
            return HealthCheckResult.unhealthy(name, responseTime, errorMessage, datasourceType);
        }
    }

    /**
     * 批量详细健康检查
     *
     * @param datasourceNames 数据源名称列表
     * @return 健康检查结果映射
     */
    public Map<String, HealthCheckResult> checkHealthBatchDetailed(List<String> datasourceNames) {
        Map<String, HealthCheckResult> results = new HashMap<>();

        if (datasourceNames == null || datasourceNames.isEmpty()) {
            // 如果没有指定数据源，检查所有数据源
            Set<String> allNames = new HashSet<>();
            allNames.addAll(localDataSources.keySet());
            allNames.addAll(springDataSourceNames);
            datasourceNames = new ArrayList<>(allNames);
        }

        for (String name : datasourceNames) {
            results.put(name, checkHealthDetailed(name));
        }

        return results;
    }

    public void destroy() {
        // v2.1 修复：复制 keySet 后迭代（removeDataSource 会修改 map），避免 ConcurrentModificationException
        new java.util.ArrayList<>(localDataSources.keySet()).forEach(this::removeDataSource);
    }

   
    // ========== 私有方法 ==========

    private DruidDataSource createDruidDataSource(DataSourceProperties config) {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl(config.getUrl());
        dataSource.setUsername(config.getUsername());
        dataSource.setPassword(config.getPassword());
        dataSource.setDriverClassName(config.getDriverClassName());

        DataSourceProperties.DruidConfig druid = config.getDruid();
        if (druid == null) {
            druid = new DataSourceProperties.DruidConfig();
        }

        dataSource.setInitialSize(druid.getInitialSize());
        dataSource.setMinIdle(druid.getMinIdle());
        dataSource.setMaxActive(druid.getMaxActive());
        dataSource.setMaxWait(druid.getMaxWait());
        dataSource.setTestWhileIdle(druid.isTestWhileIdle());
        dataSource.setTestOnBorrow(druid.isTestOnBorrow());
        dataSource.setTestOnReturn(druid.isTestOnReturn());
        dataSource.setValidationQuery(druid.getValidationQuery());
        dataSource.setTimeBetweenEvictionRunsMillis(druid.getTimeBetweenEvictionRunsMillis());
        dataSource.setMinEvictableIdleTimeMillis(druid.getMinEvictableIdleTimeMillis());

        try {
            if (StringUtils.hasText(druid.getFilters())) {
                dataSource.setFilters(druid.getFilters());
            }
        } catch (Exception e) {
            log.warn("设置 Druid 过滤器失败: {}", e.getMessage());
        }

        return dataSource;
    }
}