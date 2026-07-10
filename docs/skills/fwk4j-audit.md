
# framework4j-audit 审计日志

## 注解

```java
@Auditable(action = "DELETE_ORDER", targetType = "order", targetIdSpel = "#orderId")
public void deleteOrder(String orderId) { ... }

@Auditable(action = "CREATE_ORDER", targetType = "order", targetIdSpel = "#req.id", logArgs = true)
public Order createOrder(@RequestBody CreateOrderRequest req) { ... }
```

## Hash Chain 防篡改

```
hash = SHA256(prev_hash || TreeMap(action, targetType, targetId, actor, result, timestamp, args))
```

- 篡改任一条记录 → 后续 hash 校验失败
- `computeNextSnapshot` 原子返回 (prevHash, hash)
- sink 失败 CAS 回滚

## AuditSink 扩展（生产用）

```java
@Component
public class DbAuditSink implements AuditSink {
    @Override
    public void write(AuditRecord record) {
        auditMapper.insert(record);  // append-only
    }
    @Override
    public String loadLastHash() { return auditMapper.selectLastHash(); }
}
```

## 安全责任

> `actor` 取自 `X-User-Id`、`ip` 取自 `X-Forwarded-For`
> **必须由网关在入口覆写后才可信**

## 配置

```yaml
framework4j:
  audit:
    enabled: true
    hash-chain-enabled: true
    hash-algorithm: SHA-256
```

## 引入

```xml
<dependency>
    <groupId>com.github.funcommons.framework4j</groupId>
    <artifactId>framework4j-audit</artifactId>
    <version>v1.0.0</version>
</dependency>
```
