package fun.commons.framework4j.datasource.properties;

import fun.commons.framework4j.datasource.tracing.SqlTracingProperties;
import lombok.Data;
import java.util.List;

/**
 * 数据源属性配置类
 * <p>
 * 对应配置文件结构:
 * framework4j:
 *   datasource:
 *     datasources:
 *       key:
 *         url: ...
 *         druid: ...
 *         mybatis-plus: ...
 *         sql-tracing: ...
 *
 * @since 1.0.0
 */
@Data
public class DataSourceProperties {
    /**
     * JDBC URL
     */
    private String url;

    /**
     * 数据库用户名
     */
    private String username;

    /**
     * 数据库密码
     */
    private String password;

    /**
     * 驱动类名 (可选, 自动检测)
     */
    private String driverClassName;

    /**
     * 别名列表 (v2.0 新增)
     */
    private List<String> aliases;

    /**
     * Druid 连接池配置
     */
    private DruidConfig druid = new DruidConfig();

    /**
     * MyBatis Plus 配置
     */
    private MybatisPlusConfig mybatisPlus = new MybatisPlusConfig();

    /**
     * MyBatis Plus 内置插件配置
     */
    private MybatisPlusPluginsConfig mybatisPlusPlugins = new MybatisPlusPluginsConfig();

    /**
     * SQL 追踪配置
     */
    private SqlTracingProperties sqlTracing = new SqlTracingProperties();

    /**
     * Druid 连接池详细配置
     */
    @Data
    public static class DruidConfig {
        private int initialSize = 5;
        private int minIdle = 5;
        private int maxActive = 20;
        private long maxWait = 60000;
        private boolean testWhileIdle = true;
        private boolean testOnBorrow = false;
        private boolean testOnReturn = false;
        private String validationQuery = "SELECT 1";
        private long timeBetweenEvictionRunsMillis = 60000;
        private long minEvictableIdleTimeMillis = 300000;
        private String filters = "stat,wall,slf4j";
        private String connectionProperties;
    }

    /**
     * MyBatis Plus 详细配置
     */
    @Data
    public static class MybatisPlusConfig {
        /**
         * Mapper XML 文件位置
         * e.g. classpath*:/mapper/*.xml
         */
        private String[] mapperLocations;

        /**
         * 实体类包路径
         * e.g. com.example.entity
         */
        private String typeAliasesPackage;

        /**
         * MyBatis 配置文件位置 (修复报错的关键字段)
         * e.g. classpath:mybatis-config.xml
         */
        private String configLocation;
    }

    /**
     * MyBatis Plus 内置插件配置
     */
    @Data
    public static class MybatisPlusPluginsConfig {
        /** 总开关，默认 true */
        private boolean enabled = true;

        /** 分页插件，默认 true */
        private boolean pagination = true;
        /** 分页 DbType，默认 null（自动检测） */
        private String dbType;

        /** 乐观锁插件，默认 false（需 Entity @Version） */
        private boolean optimisticLock = false;

        /** 防全表更新/删除插件，默认 true */
        private boolean blockAttack = true;

        /** 多租户插件，默认 false（需租户上下文） */
        private boolean dataPermission = false;
        /** 多租户字段名 */
        private String tenantColumn = "tenant_id";
        /** 多租户忽略表 */
        private String[] tenantIgnoreTables;
    }
}