
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

## TraceContext

```java
String traceId = TraceContext.getTraceId();  // 从 MDC / Micrometer Tracer 取
```

响应头自动返 `X-Trace-Id`。

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
    <version>v1.0.0</version>
</dependency>
```

## 配置

```yaml
framework4j:
  web:
    enabled: true  # 默认开启
```
