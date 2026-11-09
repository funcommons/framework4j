# framework4j-datetime

> `OffsetDateTime` / `LocalDateTime` 序列化、时间格式化拦截器、北京时间默认时区。

## 1. 概览

| 项 | 值 |
|---|---|
| 职责 | 全局时间序列化（ISO-8601 / 北京时间）、`TimeFormatInterceptor` 路径级格式化、`OffsetDateTime` ↔ `Long` 转换器 |
| 配置前缀 | `framework4j.datetime.enabled`（默认 `true`） |
| 必需依赖 | `spring-boot-starter`、`spring-boot-starter-web`（optional） |
| 可选依赖 | `framework4j-api`（仅 test scope） |
| 在 SDK 中的位置 | 基础层，独立于 `id` / `redis` / `datasource`，可单独引入 |

**核心原则**：服务端统一存 `OffsetDateTime`（UTC），API 响应按需输出 ISO-8601 字符串或时间戳（毫秒），前端不参与时区转换。

## 2. 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-datetime</artifactId>
    <version>1.2.8</version>
</dependency>
```

### 最小 application.yml

```yaml
framework4j:
  datetime:
    enabled: true  # 默认 true
```

### 最小代码示例

```java
public record OrderVO(
    String id,
    OffsetDateTime createdAt,   // 自动序列化为 "2026-07-04T15:30:00+08:00"
    LocalDateTime paidAt        // 自动序列化为 "2026-07-04T15:30:00"
) {}
```

无需任何注解，`DateTimeAutoConfiguration` 自动注册 Jackson 序列化器。

## 3. 配置参考

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `framework4j.datetime.enabled` | `boolean` | `true` | 是否启用本模块 |
| `framework4j.datetime.local-zone` | `String` | `Asia/Shanghai` | **v1.10（issue #30-②）**：本地格式分支（`@LocalTimeFormat` 态）换算的目标时区；非法值启动即失败 |

> **wire 契约（v1.10，issue #30）**：ISO 态输出<strong>定长</strong> `yyyy-MM-dd'T'HH:mm:ssXXX`
> （如 `2026-11-09T14:30:00+08:00`，秒段恒存在——1.9 及之前为变长 `toString()`，秒为 0 时省略秒段导致
> wire 形态漂移）；本地格式态输出 `yyyy-MM-dd HH:mm:ss`，<strong>先换算到 `local-zone` 墙钟</strong>
> （此前直接按值自带 offset 排墙钟，UTC 值错 8 小时）。历史上文档出现过的
> `framework4j.datetime.default-zone` / `use-iso-format` 均为不存在的键。

## 4. API 参考

### `DateTimeAutoConfiguration`

注册以下组件：
- `DynamicTimeFilter`（Jackson `OffsetDateTime` 序列化器）→ ISO 态定长 `yyyy-MM-dd'T'HH:mm:ssXXX` / 本地格式态 `yyyy-MM-dd HH:mm:ss`（按 `local-zone` 换算墙钟）
- `TimeFormatInterceptor`（MVC 拦截器）→ 检测 handler/方法/参数上的 `@LocalTimeFormat`，经 `TimeFormatStateHolder`（ThreadLocal）切换本请求的序列化分支
- `StringToOffsetDateTimeConverter` / `StringArrayToOffsetDateTimeCollectionConverter` → 入参 String → OffsetDateTime 转换

### `TimeFormatInterceptor`

`HandlerInterceptor`，按请求路径匹配时间格式化策略：

```java
@Configuration
public class MyTimeFormatConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TimeFormatInterceptor())
                .addPathPatterns("/v1/admin/**")
                .excludePathPatterns("/v1/admin/reports/**");
    }
}
```

### `@LocalTimeFormat`（注解）

标在 Controller 类 / 方法 / 参数上（v1.2.4 起 `@Target` 含 PARAMETER，参数位仅语义标记、
无运行时效果——解析能力来自全局转换器），经 `TimeFormatInterceptor` 检测后把本请求的
`OffsetDateTime` 序列化切到本地格式分支（按 `local-zone` 换算墙钟）：

```java
@LocalTimeFormat
@GetMapping("/v1/admin/reports")
public ApiResponse<ReportVO> report() { ... }
```

## 5. 示例

### 5.1 ISO-8601 默认输出

```java
@GetMapping("/v1/orders/{id}")
public ApiResponse<OrderVO> getOrder(@PathVariable String id) {
    OrderVO order = orderService.find(id);
    // createdAt: OffsetDateTime.now() → "2026-07-04T15:30:00+08:00"
    return ApiResponse.success(order, TraceContext.getTraceId());
}
```

### 5.2 本地格式输出（后台管理统一北京时间墙钟）

```java
// controller 方法级标注（也可类级）——本接口所有 OffsetDateTime 字段走本地格式分支
@LocalTimeFormat
@GetMapping("/v1/admin/orders")
public ApiResponse<List<OrderVO>> listOrders() { ... }
// createdAt: UTC offset 值也换算到 framework4j.datetime.local-zone（缺省 Asia/Shanghai）墙钟
// → "2026-11-09 22:00:00"
```

> 本模块没有「输出毫秒时间戳」模式（历史文档的 `use-iso-format` 是不存在的键）；需要时间戳
> 请在 VO 层自行转换（`value.toInstant().toEpochMilli()`）。

### 5.3 非 OffsetDateTime 类型

本模块只接管 `OffsetDateTime` 序列化；`LocalDate` / `LocalDateTime` 等由 Jackson
`JavaTimeModule`（Boot 缺省）按各自 ISO 格式输出。本模块无字段级自定义 pattern 注解——
历史文档的 `@TimeFormat` 不存在，实际注解是 `@LocalTimeFormat`
（`fun.commons.framework4j.datetime.LocalTimeFormat`，方法/类/参数级，开关整个请求的本地格式分支，见 5.2）。

## 6. 错误码

本模块不定义自己的错误码。时间格式解析失败时由 `GlobalExceptionHandler` 兜底返回 `10102 FORMAT_INVALID`。

## 7. FAQ

**Q1：为什么默认用 `OffsetDateTime` 而不是 `LocalDateTime`？**
A：`OffsetDateTime` 带时区信息，跨时区传输无歧义。`LocalDateTime` 不带时区，存数据库时依赖服务器时区设置，容易出 bug。

**Q2：前端需要时间戳怎么办？**
A：本模块无毫秒时间戳输出模式（历史文档的 `framework4j.datetime.use-iso-format` 是不存在的键）。需要时间戳请在 VO 层自行转换（`value.toInstant().toEpochMilli()`），Long 字段由 framework4j-web 全局 Long→String 防精度丢失。

**Q3：`TimeFormatInterceptor` 和 `@LocalTimeFormat` 的关系？**
A：`@LocalTimeFormat` 标注（类/方法/参数级），`TimeFormatInterceptor` 在请求入口检测该注解并设置 ThreadLocal 状态，`DynamicTimeFilter` 序列化时按状态切分支——三者是一条链路，不是两个独立开关。

**Q4：数据库存 `DATETIME` 还是 `TIMESTAMP`？**
A：推荐 `DATETIME` + MyBatis `OffsetDateTimeTypeHandler`。`TIMESTAMP` 受 MySQL `time_zone` 影响，跨地域部署易错。

**Q5：如何禁用本模块？**
A：`framework4j.datetime.enabled=false`。需要自行注册 `JavaTimeModule`，否则 Jackson 默认序列化 `OffsetDateTime` 会抛 `InvalidDefinitionException`。
