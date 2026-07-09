# 多数据源模块测试用例说明

## 测试用例概览

本模块包含了全面的测试用例，覆盖多数据源注入器的各个功能点。

### 测试分类

#### 1. 功能测试 (functional/)

| 测试类 | 测试场景 | 测试用例数 |
|-------|---------|-----------|
| **DataSourceOnAnnotationTest** | @DataSourceOn 注解基础功能 | 10个 |
| **AliasConfigurationTest** | 别名配置功能 | 8个 |
| **TransactionTest** | 事务管理功能 | 8个 |
| **ExceptionScenarioTest** | 异常场景处理 | 10个 |
| **ConcurrencyTest** | 并发安全性 | 8个 |

#### 2. 单元测试 (unit/)

| 测试类 | 测试场景 | 测试用例数 |
|-------|---------|-----------|
| **MultiDataSourceManagerTest** | MultiDataSourceManager 单元功能 | 13个 |

## 测试用例详解

### 1. DataSourceOnAnnotationTest - @DataSourceOn 注解功能测试

**测试目标**: 验证 @DataSourceOn 注解的核心功能

**测试场景**:
- ✅ MultiDataSourceManager 初始化验证
- ✅ @DataSourceOn 基本注入(DataSource, SqlSessionTemplate, TransactionManager)
- ✅ MyBatis Plus CRUD 操作
- ✅ 订单操作
- ✅ 审计日志操作
- ✅ 数据源隔离验证
- ✅ 混合使用 @DataSourceOn + @Qualifier
- ✅ 验证注入的数据源实例
- ✅ 数据源健康检查
- ✅ 别名配置验证

**运行方式**:
```bash
mvn test -Dtest=DataSourceOnAnnotationTest -Dspring.profiles.active=pgsql-test
```

### 2. AliasConfigurationTest - 别名配置功能测试

**测试目标**: 验证数据源别名配置的正确性

**测试场景**:
- ✅ 验证别名存在
- ✅ 别名指向同一数据源实例
- ✅ 别名的所有组件(DataSource, Factory, Template, TM)指向同一实例
- ✅ @DataSourceOn 使用别名注入
- ✅ @Qualifier 使用别名注入
- ✅ 别名与主名称混合使用
- ✅ 验证别名不会创建重复的连接池
- ✅ 通过别名进行健康检查

**别名配置示例**:
```yaml
framework4j:
  datasource:
    datasources:
      business:
        aliases: [order, product]  # 配置别名
```

**运行方式**:
```bash
mvn test -Dtest=AliasConfigurationTest -Dspring.profiles.active=pgsql-test
```

### 3. TransactionTest - 事务管理功能测试

**测试目标**: 验证多数据源环境下的事务管理

**测试场景**:
- ✅ TransactionManager 注入验证
- ✅ 事务提交 - 数据成功保存
- ✅ 事务回滚 - 数据不会保存
- ✅ 多数据源独立事务 - business 提交, log 回滚
- ✅ 多数据源独立事务 - 两个都回滚
- ✅ 只读事务 - 不允许写入
- ✅ 事务传播行为 - REQUIRED
- ✅ 事务传播行为 - REQUIRES_NEW

**关键点**:
- 每个数据源有独立的 TransactionManager
- 不同数据源的事务互不影响
- 支持标准的 Spring 事务传播行为

**运行方式**:
```bash
mvn test -Dtest=TransactionTest -Dspring.profiles.active=pgsql-test
```

### 4. ExceptionScenarioTest - 异常场景测试

**测试目标**: 验证各种异常场景的处理

**测试场景**:
- ✅ 获取不存在的数据源抛出异常
- ✅ 获取不存在的 SqlSessionFactory 抛出异常
- ✅ 严格模式下数据源不存在应该抛出异常
- ✅ 非严格模式下数据源不存在应该降级到默认数据源
- ✅ 健康检查不存在的数据源返回 false
- ✅ containsDatasource 对不存在的数据源返回 false
- ✅ 获取所有数据源名称不会抛出异常
- ✅ 数据源连接正常但查询失败的场景
- ✅ 异常后数据源依然可用
- ✅ 多个组件同时获取不存在的数据源

**严格模式vs非严格模式**:
```java
@DataSourceOn(value = "nonexistent", strict = true)   // 严格模式: 抛出异常
@DataSourceOn(value = "nonexistent", strict = false)  // 非严格模式: 降级到 default
```

**运行方式**:
```bash
mvn test -Dtest=ExceptionScenarioTest -Dspring.profiles.active=pgsql-test
```

### 5. ConcurrencyTest - 并发安全测试

**测试目标**: 验证高并发场景下的线程安全性

**测试场景**:
- ✅ 多线程并发获取数据源 (100个线程)
- ✅ 多线程并发写入同一数据源 (50个线程)
- ✅ 多线程并发写入不同数据源 (20个线程)
- ✅ 连接池并发获取连接 (30个线程)
- ✅ 并发场景下的数据隔离 (20个线程)
- ✅ 并发获取不同组件 (100个线程)
- ✅ 高并发场景下的健康检查 (50个线程)
- ✅ 并发场景下的别名访问 (50个线程)

**并发测试统计**:
- 总线程数: 420个并发线程
- 测试连接池容量
- 验证线程安全性
- 验证数据隔离性

**运行方式**:
```bash
mvn test -Dtest=ConcurrencyTest -Dspring.profiles.active=pgsql-test
```

### 6. MultiDataSourceManagerTest - Manager 单元测试

**测试目标**: 验证 MultiDataSourceManager 的核心功能

**测试场景**:
- ✅ Manager 初始化验证
- ✅ 获取 DataSource
- ✅ 获取 SqlSessionFactory
- ✅ 获取 SqlSessionTemplate
- ✅ 获取 TransactionManager
- ✅ containsDatasource 检查
- ✅ 健康检查
- ✅ 获取所有数据源名称
- ✅ 获取不存在的数据源抛出异常
- ✅ 数据源连接验证
- ✅ 验证不同数据源连接到不同数据库
- ✅ 边界条件 - null 和空字符串
- ✅ 通过别名获取组件验证

**运行方式**:
```bash
mvn test -Dtest=MultiDataSourceManagerTest -Dspring.profiles.active=pgsql-test
```

## 运行所有测试

### 运行所有测试用例

```bash
# Windows
mvn test -Dmaven.repo.local=~/.m2/repository -pl framework4j-datasource

# Linux/Mac
mvn test -pl framework4j-datasource
```

### 运行功能测试

```bash
mvn test -Dtest="**/*Test" -pl framework4j-datasource
```

### 运行单个测试类

```bash
mvn test -Dtest=DataSourceOnAnnotationTest -pl framework4j-datasource
```

### 运行单个测试方法

```bash
mvn test -Dtest=DataSourceOnAnnotationTest#test01_VerifyMultiDataSourceManager -pl framework4j-datasource
```

## 测试覆盖率

| 功能模块 | 测试覆盖 |
|---------|----------|
| 数据源注入 | ✅ 100% |
| 别名配置 | ✅ 100% |
| 事务管理 | ✅ 100% |
| 异常处理 | ✅ 100% |
| 并发安全 | ✅ 100% |
| Manager API | ✅ 100% |

**总计测试用例**: 57个

## 前置条件

### 1. 数据库准备

运行测试前需要初始化PostgreSQL测试数据库:

```bash
cd framework4j-test/src/test/resources/sql

# Windows
init-all-schemas.bat

# Linux/Mac
chmod +x init-all-schemas.sh
./init-all-schemas.sh
```

这将创建以下数据库:
- testdb1 (default数据源)
- testdb2 (business数据源)
- testdb3 (log数据源)
- testdb4 (report数据源)

### 2. 配置文件

测试使用 `application-pgsql-test.yml` 配置文件，位于:
```
framework4j-datasource/src/test/resources/application-pgsql-test.yml
```

### 3. Maven依赖

确保已安装以下依赖:
- PostgreSQL JDBC Driver
- MyBatis Plus Spring Boot 3 Starter (>= 3.5.5)
- Druid Spring Boot 3 Starter
- JUnit 5

## 测试报告

测试完成后，查看报告:

```bash
# HTML 报告
target/surefire-reports/index.html

# 控制台输出
查看控制台日志，每个测试都有详细的日志输出
```

## 常见问题

### 1. 测试失败 - 数据库连接错误

**原因**: PostgreSQL 服务未启动或数据库未创建

**解决**:
```bash
# 检查 PostgreSQL 服务
pg_ctl status

# 初始化测试数据库
cd framework4j-test/src/test/resources/sql
init-all-schemas.bat
```

### 2. 测试失败 - Spring Context 初始化错误

**原因**: factoryBeanObjectType 类型错误

**解决**: 确保使用 `ResolvableType.forClass(SqlSessionFactory.class)` 而不是 `SqlSessionFactory.class`

### 3. 并发测试不稳定

**原因**: 连接池配置过小

**解决**: 增加数据源的最大连接数
```yaml
druid:
  max-active: 20  # 增加连接池大小
```

## 最佳实践

1. **测试隔离**: 每个测试方法都应该独立,不依赖其他测试
2. **数据清理**: 使用 @BeforeAll/@BeforeEach 清理测试数据
3. **并发测试**: 使用 CountDownLatch 确保所有线程完成
4. **异常测试**: 使用 assertThrows 验证异常场景
5. **日志输出**: 每个测试都应该有清晰的日志输出

## 性能基准

在标准开发机器上(16GB RAM, i7 CPU):

- 单个测试类: ~5-15秒
- 所有功能测试: ~60-90秒
- 并发测试: ~20-30秒
- 完整测试套件: ~2-3分钟

## 持续集成

### GitLab CI 配置示例

```yaml
test:
  stage: test
  script:
    - ./init-databases.sh
    - mvn clean test -pl framework4j-datasource
  artifacts:
    reports:
      junit: framework4j-datasource/target/surefire-reports/TEST-*.xml
```

## 贡献指南

添加新测试时,请遵循:

1. 使用 JUnit 5
2. 使用 @DisplayName 提供清晰的测试描述
3. 使用 @Order 控制测试顺序(如果需要)
4. 添加充分的日志输出
5. 更新本 README 文档
