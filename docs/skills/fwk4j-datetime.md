
# framework4j-datetime 时间处理

## OffsetDateTime 序列化（全局自动）

Jackson 自动配置：
- `OffsetDateTime` → ISO-8601 字符串
- `Long` → `String`（防 JS 精度丢失）

## @LocalTimeFormat 注解

```java
@GetMapping("/report")
public ApiResponse<Report> getReport(
    @LocalTimeFormat @RequestParam OffsetDateTime start) { ... }
```

支持多种输入：
- `2024-12-10T14:30:45+08:00`
- `2024-12-10 14:30:45`
- 时间戳（毫秒）

## 配置

```yaml
framework4j:
  datetime:
    enabled: true
```

## 引入

```xml
<dependency>
    <groupId>com.github.funcommons.framework4j</groupId>
    <artifactId>framework4j-datetime</artifactId>
    <version>v1.0.0</version>
</dependency>
```
