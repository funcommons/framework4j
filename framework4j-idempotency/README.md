# framework4j-idempotency

> `Idempotency-Key` 拦截器：Redis 48h 保留，防重放 / 防重复提交。

## 1. 概览

| 项 | 值 |
|---|---|
| 职责 | `IdempotencyInterceptor`（拦截写操作）/ `IdempotencyProperties`（配置）/ SHA-256 请求体哈希 / Redis 原子 SETNX 防重放 |
| 配置前缀 | `framework4j.idempotency.*` |
| 必需依赖 | `framework4j-redis`、`framework4j-api`、`spring-boot-starter-web`、`jackson-databind` |
| 可选依赖 | — |
| 在 SDK 中的位置 | 安全层，独立于 `accesstoken`，可单独引入 |

**核心原则**（mc-api-spec §5 铁律 8）：所有写操作支持 `Idempotency-Key` Header（客户端 UUID v4）；服务端 48h 内同 key + 同 Body 返回首次结果；同 key + 不同 Body 返 `10501 DUPLICATE_SUBMIT`。

## 2. 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-idempotency</artifactId>
    <version>1.2.7</version>
</dependency>
```

### 最小 application.yml

```yaml
spring:
  application:
    name: my-app

framework4j:
  idempotency:
    enabled: true
    redis-name: default
    ttl-seconds: 172800  # 48h
    header-name: Idempotency-Key
    path-patterns:       # 默认 [/api/**]
      - /v1/orders
      - /v1/payments/**
    exclude-path-patterns: []
```

> **注册方式（v1.2.5+）**：拦截器由 `IdempotencyAutoConfiguration` 通过
> `@Import(IdempotencyWebMvcConfig)` 自动注册进 MVC 拦截链，**消费方不要自行注册
> `IdempotencyInterceptor`** —— 重复注册会导致同一请求跑两遍（v1.2.7 已加重入守卫防御，
> 但正确做法仍是只保留框架注册）。

### 最小代码示例

```java
// 消费者应用：发请求时带 Idempotency-Key
@PostMapping("/v1/orders")
public ApiResponse<OrderVO> createOrder(
        @RequestHeader("Idempotency-Key") String key,
        @RequestBody CreateOrderRequest req) {
    // 拦截器已自动校验：同 key + 同 Body 返回首次结果
    return ApiResponse.success(orderService.create(req), TraceContext.getTraceId());
}
```

客户端：

```bash
curl -X POST https://api.example.com/v1/orders \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -H "Content-Type: application/json" \
  -d '{"address_id":"123","items":[{"sku_id":"A","quantity":2}]}'
```

第二次相同请求（48h 内）返回首次的完整响应，不会创建第二个订单。

## 3. 配置参考

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `framework4j.idempotency.enabled` | `boolean` | `false` | 是否启用（opt-in） |
| `framework4j.idempotency.redis-name` | `String` | `default` | 用 `framework4j-redis` 的哪个数据源 |
| `framework4j.idempotency.ttl-seconds` | `long` | `172800`（48h） | Idempotency-Key 保留时长 |
| `framework4j.idempotency.header-name` | `String` | `Idempotency-Key` | Header 名（可自定义） |
| `framework4j.idempotency.key-prefix` | `String` | `idem` | Redis key 前缀（完整格式 `{prefix}:{requestURI}:{uuid}`） |
| `framework4j.idempotency.body-hash-required` | `boolean` | `true` | 是否校验 body hash（防同 key 不同 body 绕过；要求 `IdempotencyBodyCacheFilter` 生效） |
| `framework4j.idempotency.path-patterns` | `List<String>` | `[/api/**]` | 拦截路径（Ant 风格） |
| `framework4j.idempotency.exclude-path-patterns` | `List<String>` | `[]` | 排除路径 |

## 4. API 参考

### `IdempotencyInterceptor`

`HandlerInterceptor`，拦截 `path-patterns` 配置的路径（仅写方法，按序）：

1. **方法过滤**（v2.2）：仅 POST/PUT/PATCH/DELETE 生效，GET/HEAD/OPTIONS 直接放行
2. 读 `Idempotency-Key` Header，无则放行（不强制）；**必须是 UUID v4**，否则 400 `10102`
3. 计算 Body 的 SHA-256 哈希（需 `IdempotencyBodyCacheFilter` 已缓存 body）
4. **Lua 原子 `GET + SETNX`**（v2.1，消除 TOCTOU 竞态）：
   key = `{keyPrefix}:{requestURI}:{uuid}`，value = `{hash}|PENDING`，TTL 48h
   - 返回 nil（首次设置成功）→ 放行
   - 已存在 + hash 不匹配 → **409** + `10501 DUPLICATE_SUBMIT`
   - 已存在 + `OK:{body}` → **重放**：直接返回首次响应（HTTP 200，不触 Controller）
   - 已存在 + `PENDING`（前一请求仍在处理）→ **409**「前一请求仍在处理中，请稍后重试」（v1.2.7 区分）
5. `afterCompletion` 回写：2xx → `hash|OK:{响应体}` 存 Redis；非 2xx/异常 → **删除 key** 允许同 key 重试（v2.1）
6. **重入守卫**（v1.2.7）：同一请求已通过 SETNX 则重入直接放行，防御拦截器重复注册

### `IdempotencyProperties`

```java
@Data
public class IdempotencyProperties {
    private boolean enabled = false;
    private String headerName = "Idempotency-Key";
    private String keyPrefix = "idem";
    private long ttlSeconds = 172800;
    private boolean bodyHashRequired = true;
    private String redisName = "default";
    private List<String> pathPatterns = List.of("/api/**");
    private List<String> excludePathPatterns = List.of();
}
```

## 5. 示例

### 5.1 订单创建（防重复提交）

```java
@RestController
public class OrderController {
    @PostMapping("/v1/orders")
    @Idempotent  // 自定义注解（可选），或用 paths 配置
    public ApiResponse<OrderVO> createOrder(@RequestBody CreateOrderRequest req) {
        return ApiResponse.success(orderService.create(req), TraceContext.getTraceId());
    }
}
```

```yaml
framework4j:
  idempotency:
    path-patterns:
      - /v1/orders
      - /v1/payments
```

### 5.2 客户端配合

```javascript
// 前端 axios 拦截器
axios.interceptors.request.use(config => {
    if (['post', 'put', 'patch', 'delete'].includes(config.method)) {
        config.headers['Idempotency-Key'] = crypto.randomUUID();
    }
    return config;
});
```

### 5.3 重放场景

```bash
KEY=550e8400-e29b-41d4-a716-446655440000   # 必须 UUID v4，否则 400

# 第一次：创建订单
curl -X POST /v1/orders -H "Idempotency-Key: $KEY" -d '{"items":[...]}'
# → 200 OK, {"order_id":"OD001",...}

# 第二次：相同 key + 相同 Body（48h 内）
curl -X POST /v1/orders -H "Idempotency-Key: $KEY" -d '{"items":[...]}'
# → 200 OK, {"order_id":"OD001",...}  # 重放首次结果，不创建新订单

# 变体：相同 key + 不同 Body
curl -X POST /v1/orders -H "Idempotency-Key: $KEY" -d '{"items":[不同]}'
# → 409, {"code":10501,"message":"请勿重复提交"}

# 变体：相同 key 的前一请求仍在处理中（PENDING，v1.2.7）
# → 409, {"code":10501,"message":"相同 Idempotency-Key 的前一请求仍在处理中，请稍后重试"}

# 变体：第一次请求失败（非 2xx / 异常）→ key 被删除，同 key 可重试
```

> **测试提示**：每次测试请用**新生成的 UUID**。key 有 48h TTL，复用固定 UUID 会命中
> 上一轮的缓存值（不同 body 时返回 409），造成"首次请求就 409"的假象。

## 6. 错误码

| HTTP | Code | 名称 | 触发场景 |
|---|---|---|---|
| 400 | `10102` | `PARAM_FORMAT_ERROR` | `Idempotency-Key` 不是 UUID v4 |
| 409 | `10501` | `DUPLICATE_SUBMIT` | 同 key + 不同 Body，或同 key 前一请求仍在处理中（PENDING） |

## 7. FAQ

**Q1：GET 请求需要 `Idempotency-Key` 吗？**
A：不需要。GET 天然幂等（无副作用）。本模块只拦截写操作（POST / PUT / PATCH / DELETE）。`paths` 配置时只配写端点。

**Q2：Redis 挂了怎么办？**
A：当前实现是 **fail-closed**：幂等检查的 Redis 调用异常会向上抛出，请求失败（避免失去幂等保证时静默放行造成重复提交）。回写阶段的异常则被捕获降级（只丢幂等缓存，不影响响应）。如业务可接受重复提交换可用性，可临时 `enabled: false`。Redis 建议主从 + 哨兵。

**Q3：48h 后再请求会怎样？**
A：Redis key 已过期，按首次请求处理。如果业务需要更长保留期，调大 `ttl-seconds`（但占用 Redis 内存）。

**Q4：Body 哈希用什么算法？**
A：SHA-256（JDK `MessageDigest`）。Body 直接哈希，不解析 JSON。即使字段顺序不同，序列化后的字节流相同就视为同 Body（前端应保证序列化稳定）。

**Q5：响应怎么缓存？**
A：`IdempotencyBodyCacheFilter` 用 `ContentCachingResponseWrapper` 包装响应，`afterCompletion` 从缓冲区复制响应体到 Redis。重放时直接从 Redis 读 JSON 写回。响应体大小无硬限制，但建议 < 64KB（大响应不适合幂等缓存）。

**Q6：「首次请求就 409」怎么排查？**
A：真首次请求返回 409 只有两条路径：
1. **拦截器被重复注册**（自建 `WebMvcConfigurer` + 框架注册并存）—— 同一请求跑两遍，第二遍读到自己刚写的 PENDING 而 409。v1.2.5+ 框架已自动注册，**拆除自建注册**即可（v1.2.7 重入守卫已防御，但拆掉才是正解）。
2. **复用了固定 UUID + 48h TTL 残留** —— 上一轮的 `idem:{uri}:{uuid}` 还在 Redis 里，本轮 body 不同就 409。每次测试换新 UUID，或测试前清 `idem:*`。
