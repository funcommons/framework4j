
# framework4j-web Web 层契约

## ApiResponse 6 字段信封

```java
@GetMapping("/v1/orders/{id}")
public ApiResponse<Order> getOrder(@PathVariable String id) {
    return ApiResponse.success(orderService.get(id));
}
// → {"code":0,"message":"操作成功","data":{...},"error":null,"trace_id":"xxx","timestamp":1718660400000}
```

失败时 data 必须 null：
```java
return ApiResponse.fail(ApiCode.NOT_FOUND, "订单不存在");
```

## GlobalExceptionHandler（自动注册）

- 业务异常（ApiException）→ HTTP 200 + 信封 code
- 参数校验失败 → 10100 + error[] 字段级错误
- 未知系统异常（NPE）→ HTTP 500 + 10001
- **无需手写**，AutoConfiguration 自动装配

## TraceContext 与 trace_id 链路（v1.8 契约，mc-monitor 对齐）

```java
String traceId = TraceContext.getTraceId();  // MDC（可配置 key）→ Micrometer Tracer → null
```

`traceIdMdcFilter`（最高优先级，`/*`）自动完成（无需任何代码）：

1. **入站采纳**：上游/网关传入的 `X-Trace-Id` 经 sanitize（trim 后 16~64 位、`[A-Za-z0-9._-]` 白名单）合法即采纳，跨服务链路不断裂；非法/缺失才本地生成 UUID
2. **响应头回显**：进 chain 之前回写 `X-Trace-Id`——提前提交响应/抛异常路径也带
3. Micrometer Tracing 已写 MDC 时不覆盖不清理，仅补响应头

```yaml
framework4j:
  api:
    trace:
      enabled: true          # 默认开
      header-name: X-Trace-Id  # 入站采纳 + 回显的 header 名
      mdc-key: traceId         # MDC key 可配置；tracelog 采集侧同步生效
```

## Jackson 全局配置（自动生效）

- `SNAKE_CASE` 命名
- `Long → String`（防 JS 精度丢失）
- `JavaTimeModule`（OffsetDateTime 序列化）
- `FAIL_ON_UNKNOWN_PROPERTIES = false`

## CachedBodyRequestWrapper

解决 Spring `ContentCachingRequestWrapper` 不重放 InputStream 的 bug。signature / idempotency 模块共用。

## 引入

```xml
<dependency>
    <groupId>com.github.funcommons.framework4j</groupId>
    <artifactId>framework4j-web</artifactId>
    <version>v1.9.0</version>
</dependency>
```

## 配置

```yaml
framework4j:
  web:
    enabled: true  # 默认开启
```
