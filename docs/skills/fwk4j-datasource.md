
# framework4j-datasource 多 DataSource

## 配置

```yaml
framework4j:
  datasource:
    enabled: true
    datasources:
      default:                        # 主库（写）
        url: jdbc:postgresql://localhost/mydb
        username: postgres
        password: ${DB_PASSWORD}
        initial-size: 5
        max-active: 20
      business:                       # 业务库
        url: jdbc:postgresql://localhost/business
      log:                            # 日志库
        url: jdbc:postgresql://localhost/logs
```

## @DataSourceOn 注解注入

```java
@Service
public class OrderService {
    @DataSourceOn("business")
    private DataSource businessDs;

    @DataSourceOn(value = "log", strict = false)  // 缺失则 fallback default
    private DataSource logDs;
}
```

## 引入

```xml
<dependency>
    <groupId>com.github.funcommons.framework4j</groupId>
    <artifactId>framework4j-datasource</artifactId>
    <version>v1.0.0</version>
</dependency>
```
