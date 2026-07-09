# framework4j-datasource

> 多 DataSource 注入器：Druid + MyBatis Plus + `@DataSourceOn` 注解（类级 / 字段级自动路由）。

## 1. 概览

| 项 | 值 |
|---|---|
| 职责 | `MultiDataSourceManager`（多数据源管理）/ `@DataSourceOn("name")` 注解处理器（类级 / 字段级）/ 动态添加 / 移除数据源 / 配合 `framework4j-sql-tracing` 注入 trace_id |
| 配置前缀 | `framework4j.datasource.*` |
| 必需依赖 | `spring-boot-starter-jdbc`、`druid-spring-boot-starter`、`mybatis-plus-spring-boot3-starter` |
| 可选依赖 | `framework4j-sql-tracing`（推荐一起用）、`framework4j-api`、`postgresql`（驱动） |
| 在 SDK 中的位置 | 数据访问层，独立于 `redis` / `accesstoken` |

**核心原则**：一个 `@DataSourceOn` 注解解决多数据源切换，无需 `@MapperScan` 分包。BeanPostProcessor 在 Bean 初始化前注入正确的 `DataSource`。

## 2. 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-datasource</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<!-- 推荐配合 sql-tracing -->
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-sql-tracing</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 最小 application.yml

```yaml
spring:
  application:
    name: my-app

framework4j:
  datasource:
    enabled: true
    datasources:
      default:
        url: jdbc:mysql://localhost:3306/main
        username: root
        password: ${DB_PASSWORD}
        driver-class-name: com.mysql.cj.jdbc.Driver
      order:
        url: jdbc:mysql://localhost:3306/order
        username: root
        password: ${DB_PASSWORD}
        driver-class-name: com.mysql.cj.jdbc.Driver
      log:
        url: jdbc:mysql://localhost:3306/log
        username: root
        password: ${DB_PASSWORD}
        driver-class-name: com.mysql.cj.jdbc.Driver
```

### 最小代码示例

```java
// 类级：整个 Service 用 order 数据源
@Service
@DataSourceOn("order")
public class OrderService {
    @Resource
    private OrderMapper orderMapper;  // 自动走 order 数据源
    
    public OrderDO find(Long id) {
        return orderMapper.selectById(id);
    }
}

// 字段级：单字段切换
@Service
public class LogService {
    @DataSourceOn("log")
    private JdbcTemplate logJdbc;
    
    public void write(String msg) {
        logJdbc.update("INSERT INTO logs (msg) VALUES (?)", msg);
    }
}
```

## 3. 配置参考

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `framework4j.datasource.enabled` | `boolean` | `false` | 是否启用本模块（opt-in） |
| `framework4j.datasource.primary` | `String` | `default` | 默认数据源名 |
| `framework4j.datasource.datasources` | `Map<String, DataSourceProperties>` | — | 多数据源配置 |
| `framework4j.datasource.datasources.<name>.url` | `String` | 必填 | JDBC URL |
| `framework4j.datasource.datasources.<name>.username` | `String` | 必填 | 用户名 |
| `framework4j.datasource.datasources.<name>.password` | `String` | 必填 | 密码（环境变量） |
| `framework4j.datasource.datasources.<name>.driver-class-name` | `String` | 自动推断 | JDBC 驱动类 |
| `framework4j.datasource.datasources.<name>.druid.initial-size` | `int` | `5` | 初始连接数 |
| `framework4j.datasource.datasources.<name>.druid.max-active` | `int` | `20` | 最大连接数 |
| `framework4j.datasource.datasources.<name>.druid.max-wait` | `long` | `5000` | 获取连接超时（ms） |
| `framework4j.datasource.datasources.<name>.aliases` | `List<String>` | — | 别名（同一数据源多名字） |
| `framework4j.datasource.datasources.<name>.sql-tracing.*` | — | — | 见 `framework4j-sql-tracing` |

## 4. API 参考

### `@DataSourceOn`（注解）

```java
@Target({TYPE, FIELD})
@Retention(RUNTIME)
public @interface DataSourceOn {
    String value();              // 数据源名
    boolean strict() default true;  // true: 不存在抛异常; false: 回退 default
}
```

**类级**：类中所有 `JdbcTemplate` / `DataSource` / `Mapper` 字段自动注入指定数据源。
**字段级**：仅该字段注入指定数据源（优先于类级）。

### `MultiDataSourceManager`

```java
public class MultiDataSourceManager {
    public DataSource getDataSource(String name);
    public DataSource getDefaultDataSource();
    public boolean containsDatasource(String name);
    public List<String> getAllDatasourceNames();
    
    // 动态添加 / 移除（运行时）
    public void addDataSource(DataSourceProperties config);
    public void removeDatasource(String name);
    
    // 健康检查
    public boolean checkHealth(String name);
}
```

### `DataSourceOnBeanPostProcessor`

`BeanPostProcessor`，扫描带 `@DataSourceOn` 的 Bean，在 `postProcessBeforeInitialization` 阶段注入正确的 `DataSource` 实例。`strict=false` 时回退到 `default` 数据源。

### `MyBatisPlusConfig`

自动注册：
- `MybatisPlusInterceptor`（分页 + 乐观锁）
- `SqlSessionFactory` 按 `@DataSourceOn` 路由
- `MapperScannerConfigurer`（扫描 `fun.commons.framework4j.*.mapper`）

## 5. 示例

### 5.1 读写分离

```yaml
framework4j:
  datasource:
    datasources:
      default:    # 主库（写）
        url: jdbc:mysql://master:3306/mydb
      read:       # 从库（读）
        url: jdbc:mysql://slave:3306/mydb
```

```java
@Service
public class UserService {
    @Resource
    private UserMapper userMapper;       // 默认走 default（主库）
    
    @DataSourceOn("read")
    private UserMapper userReadMapper;   // 走从库
    
    @Transactional
    public void createUser(UserDO user) {
        userMapper.insert(user);          // 主库
    }
    
    public UserDO findUser(Long id) {
        return userReadMapper.selectById(id);  // 从库
    }
}
```

### 5.2 动态添加数据源（多租户）

```java
@Service
public class TenantDataSourceService {
    @Resource
    private MultiDataSourceManager manager;
    
    public void registerTenant(String tenantId, String jdbcUrl) {
        DataSourceProperties config = new DataSourceProperties();
        config.setName(tenantId);
        config.setUrl(jdbcUrl);
        config.setUsername("root");
        config.setPassword(System.getenv("TENANT_DB_PWD"));
        manager.addDataSource(config);
        // 失败时自动回滚（已实现原子性）
    }
    
    public void removeTenant(String tenantId) {
        manager.removeDatasource(tenantId);
    }
}
```

### 5.3 别名 + `strict=false`

```yaml
framework4j:
  datasource:
    datasources:
      order:
        aliases: [order-read, order-replica]  # 别名
        url: jdbc:mysql://master:3306/order
      cache:
        url: jdbc:mysql://cache:3306/cache
```

```java
@Service
@DataSourceOn(value = "order-replica", strict = false)  // 别名 + 回退 default
public class OrderService { ... }
```

## 6. 错误码

| Code | 名称 | 触发场景 |
|---|---|---|
| `10900` | `INTERNAL_ERROR` | 数据源初始化失败（连接超时 / 密码错） |
| `10400` | `NOT_FOUND` | `@DataSourceOn("xxx")` 但 `xxx` 不存在且 `strict=true` |

## 7. FAQ

**Q1：`@DataSourceOn` 和 Spring `@Qualifier` 区别？**
A：`@DataSourceOn` 是 SDK 自定义注解，由 `DataSourceOnBeanPostProcessor` 处理，支持类级 + 字段级 + `strict` 回退。`@Qualifier` 是 Spring 原生，仅字段级，无回退机制。建议用 `@DataSourceOn`。

**Q2：多数据源下事务怎么处理？**
A：Spring 默认单数据源事务管理器。多数据源需用 `@Transactional(transactionManager = "xxxTransactionManager")`。或引入 `seata` 分布式事务（SDK 不内置）。

**Q3：动态添加数据源失败会回滚吗？**
A：会。`addDataSource` 内部用原子注册 + 失败回滚（销毁连接工厂 + 删除已注册 Bean）。

**Q4：MyBatis Plus 的 `@TableName` / `@TableId` 还能用吗？**
A：能。本模块只负责数据源切换，不干预 MyBatis Plus 的实体注解。

**Q5：`druid` 监控页能看每个数据源吗？**
A：能。`/druid/datasource.html` 列出所有数据源。每数据源独立配置 `stat-view-servlet` 白名单 + 凭证（见 mc-java-spec §4.4.4）。
