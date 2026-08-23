# framework4j-web

&gt; Web 层契约落地：`ApiResponse` 信封 + `GlobalExceptionHandler` + `TraceContext` + `CachedBodyRequestWrapper`（多模块共用资产）

## 简介

`framework4j-web` 是 `framework4j` 的 Web 层契约模块，从 `framework4j-api` 拆分而来：

- **`framework4j-api`** 仅保留 `ApiCode` 错误码枚举（契约定义）
- **`framework4j-web`** 承载所有 Web 层落地实现

## 核心能力

| 类 | 职责 |
|---|---|
| `ApiResponse&lt;T&gt;` | 6 字段信封（code/message/data/error/trace_id/timestamp） |
| `ApiException` | 业务异常基类 |
| `ApiAssert` | 链式断言工具 |
| `TraceContext` | trace_id 读写（MDC + Micrometer Tracer 兜底） |
| `GlobalExceptionHandler` | `@RestControllerAdvice` 全局异常处理（业务异常 HTTP 200 / 系统异常 HTTP 500） |
| `CachedBodyRequestWrapper` | **解决 Spring `ContentCachingRequestWrapper` 不重放 InputStream 的隐藏 bug**（共用资产，供 signature / idempotency 模块使用） |

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-web</artifactId>
    <version>1.2.8</version>
</dependency>
```

### 2. 启用配置

```yaml
framework4j:
  web:
    enabled: true  # 默认开启
```

### 3. 使用 ApiResponse

```java
@RestController
public class OrderController {

    @GetMapping("/v1/orders/{id}")
    public ApiResponse<Order> getOrder(@PathVariable String id) {
        Order order = orderService.get(id);
        return ApiResponse.success(order);
    }

    @PostMapping("/v1/orders")
    public ApiResponse<Order> createOrder(@RequestBody CreateOrderRequest req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            return ApiResponse.fail(ApiCode.PARAM_MISSING, "items 不能为空");
        }
        return ApiResponse.success(orderService.create(req));
    }
}
```

### 4. 异常处理

业务代码抛 `ApiException`，`GlobalExceptionHandler` 自动转为信封：

```java
public Order getOrder(String id) {
    Order order = repo.findById(id);
    if (order == null) {
        throw new ApiException(ApiCode.NOT_FOUND, "订单不存在: " + id);
    }
    return order;
}
```

响应（HTTP 200 + 信封 code）：

```json
{
  "code": 10400,
  "message": "订单不存在: abc",
  "data": null,
  "error": null,
  "trace_id": "c0a8010116983728001",
  "timestamp": 1718660400000
}
```

## 模块依赖

```
framework4j-api  ── ApiCode（契约）
       ↓
framework4j-web  ── Web 层实现
       ↑
  signature / rate-limit / idempotency / accesstoken
```

## 自动装配

通过 `META-INF/spring/...AutoConfiguration.imports` 注册：

- `WebAutoConfiguration`（注册 `GlobalExceptionHandler` + `TraceConfig` + `WebConfig`）
- 通过 `framework4j.web.enabled=false` 关闭整个 web 模块（默认开启）

### Jackson 细粒度开关（v2.2+）

`WebConfig` 把 Jackson 定制拆成 3 个独立 customizer，各自可单独关闭（默认全开 = 向后兼容）：

| 属性 | 默认 | 说明 |
|---|---|---|
| `framework4j.web.enabled` | `true` | master 开关，关闭则整个 web 模块不装配 |
| `framework4j.web.jackson.snake-case` | `true` | 全局 snake_case 命名（对齐 mc-api-spec §5.1） |
| `framework4j.web.jackson.long-to-string` | `true` | `Long`/`long` → `String`（防 JS 精度丢失） |
| `framework4j.web.jackson.fail-on-unknown-properties` | `false` | 未知字段是否报错（默认 lenient） |

**典型场景**：保留 GlobalExceptionHandler + TraceContext，但保留驼峰命名：

```yaml
framework4j:
  web:
    enabled: true                    # 保留 GlobalExceptionHandler + TraceConfig
    jackson:
      snake-case: false              # 关 snake_case，保驼峰
      long-to-string: true           # 保 Long→String
```

&gt; v2.1 及之前的旧 prefix `framework4j.api.config.enabled` 已废弃。如果你在 v1.1.2 用过它关 Jackson，请迁移到 `framework4j.web.enabled` 或新的细粒度开关。

## 信封规范（对齐 mc-api-spec v1.6 §4）

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | 0 = 成功；非 0 = 错误码（5 位分段） |
| `message` | string | 用户可读消息 |
| `data` | T | 业务数据；**失败时必须 null**（10700 部分成功例外） |
| `error` | List&lt;ApiError&gt; | 字段级错误明细（参数校验场景） |
| `trace_id` | string | 链路追踪 ID（双通道：body + `X-Trace-Id` Header） |
| `timestamp` | long | 响应时间戳（毫秒） |

## IAE 异常分流（v2.2 修复 GitHub Issue #1）

`GlobalExceptionHandler.handleIllegalArgumentException` 按 message 特征分流，不再统一包成 `10106 BUSINESS_RULE_ERROR`：

| 触发场景 | 错误码 | 说明 |
|---|---|---|
| `NumberFormatException` 或 message 以 `For input string:` 开头 | **10102** `PARAM_FORMAT_ERROR` | 参数格式错（如 `@PathVariable Long` 收到非数字） |
| message 以 `Name for argument of type` 开头 | **10005** `MIDDLEWARE_ERROR` | 反射读不到 parameter name（缺 `-parameters` 编译选项）→ log.error 并提示管理员 |
| 其它 IAE | **10106** `BUSINESS_RULE_ERROR` | 保留 v2.1 行为：真业务校验失败 |

**HTTP 状态码**：业务异常统一 HTTP 200（符合 mc-api-spec §4），仅靠 code 区分。

**Breaking change 注意**：v1.x 期间 IAE 触发的响应可能从 `10106` 变为 `10102` 或 `10005`。客户端如硬编码 `if (code == 10106)` 处理参数错误，需同时处理新错误码。

## 相关文档

- `Java开发准则.md` §15 Web 层契约
- `mc-api-spec` v1.6 §4 响应信封规范
