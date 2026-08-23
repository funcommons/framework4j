
# framework4j-idempotency 幂等键

## 使用方式

客户端写操作请求头加 `Idempotency-Key: <UUID v4>`，服务端自动防重。

## 行为

| 场景 | 结果 |
|---|---|
| 首次请求 | 放行 + 写入 PENDING |
| 同 key + 同 body + 已完成 | 回放缓存响应（不触 Controller） |
| 同 key + 不同 body | 409 DUPLICATE_SUBMIT |
| 同 key 前一请求仍在处理（PENDING） | 409「请稍后重试」（v1.2.7 区分） |
| 非 UUID v4 | 400 PARAM_FORMAT_ERROR |
| Controller 异常 / 非 2xx | 删除 key（允许重试） |

仅写方法生效（POST/PUT/PATCH/DELETE）。拦截器由框架自动注册（v1.2.5+），
**不要自行注册**；「首次请求就 409」优先排查重复注册与固定 UUID 复用（48h TTL 残留）。

## 配置

```yaml
framework4j:
  idempotency:
    enabled: true
    ttl-seconds: 172800  # 48h
```

## 引入

```xml
<dependency>
    <groupId>com.github.funcommons.framework4j</groupId>
    <artifactId>framework4j-idempotency</artifactId>
    <version>v1.2.8</version>
</dependency>
```
