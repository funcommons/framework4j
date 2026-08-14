---
name: fwk4j-datetime
description: framework4j 时间处理（OffsetDateTime 序列化 + @LocalTimeFormat 拦截器 + 多格式输入转换 + Long→String）。触发词：OffsetDateTime、时间序列化、LocalTimeFormat、时间格式、时间转换、ISO-8601、时区、时间拦截器。
version: 1.0.0
enabled: true
metadata:
  type: module-spec
  category: backend-data
  tags: [datetime, jackson, offsetdatetime]
  language: zh-CN
  artifactId: framework4j-datetime
  config-prefix: framework4j.datetime
  examples:
    - "时间序列化格式"                # → ISO-8601 + Long→String
    - "前端传时间参数怎么接收"         # → 全局多格式转换器（无需注解）
    - "多格式时间输入"               # → 自动识别
---

# framework4j-datetime 时间处理

## OffsetDateTime 序列化（全局自动）

Jackson 自动配置：
- `OffsetDateTime` → ISO-8601 字符串
- `Long` → `String`（防 JS 精度丢失）

## @LocalTimeFormat 注解（v1.2.4：参数位置放行 + 语义澄清）

**入参：全局多格式，无需注解。** `StringToOffsetDateTimeConverter` 对**所有** `OffsetDateTime` 入参生效：

```java
@GetMapping("/report")
public ApiResponse<Report> getReport(
    @RequestParam OffsetDateTime start) { ... }   // 三格式通吃,不用标注解
```

- `2024-12-10T14:30:45+08:00`（ISO-8601）
- `2024-12-10 14:30:45`（空格分隔，GMT+8）
- 时间戳（毫秒 / 秒）

> `@LocalTimeFormat @RequestParam OffsetDateTime start` 也能编译（v1.2.4 起 `@Target` 含 PARAMETER，修 GitHub Issue #8），但**参数上的注解仅是语义标记，无运行时效果** —— 解析能力来自全局转换器，与注解无关。

**出参：格式由方法/类上的注解控制。**

```java
@LocalTimeFormat                  // 标方法或类 → 出参 "yyyy-MM-dd HH:mm:ss"（GMT+8，无时区）
@GetMapping("/report")
public ApiResponse<Report> getReport(...) { ... }
```

不标则出参默认 ISO-8601（带时区）。微服务内部 API 禁用（丢时区）；纯前端展示接口使用后在接口文档注明。

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
    <version>v1.2.4</version>
</dependency>
```
