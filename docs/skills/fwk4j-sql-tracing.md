
# framework4j-sql-tracing SQL 追踪

## 效果

```sql
/*traceid=abc123,topic=my-app*/ SELECT * FROM orders WHERE id = 1
```

在日志 / Druid 监控页 / 慢 SQL 报告中可直接关联到发起请求。

## 3 种模式

| 模式 | 行为 |
|---|---|
| `DISABLED` | 不注入 |
| `WRITE_ONLY` | 仅 INSERT/UPDATE/DELETE |
| `ALL`（默认） | 所有 SQL |

## 配置

```yaml
framework4j:
  datasource:
    sql-tracing:
      enabled: true
      mode: ALL
      topic: my-app
```

## 引入

```xml
<dependency>
    <groupId>com.github.funcommons.framework4j</groupId>
    <artifactId>framework4j-sql-tracing</artifactId>
    <version>v1.2.7</version>
</dependency>
```
