
# framework4j-id 分布式 ID

## Snowflake

```java
@Autowired private SnowflakeDistributor snowflake;

long id = snowflake.nextId();  // → 892310293123123（全局唯一 + 单调递增）
```

## OpenID 混淆（12 字符 + 校验位）

```java
long original = 123456789L;
String openId = IdObfuscator.toOpenId(original);         // → "DxjWpoSI9f6Q"
long restored = IdObfuscator.fromOpenId(openId);         // → 123456789
String prefixed = IdObfuscator.toOpenId(original, "ORD"); // → "ORD_DxjWpoSI9f6Q"
```

- 连续 ID → 离散 OpenID（防爬/防暴露业务量）
- 12 字符（11 数据 + 1 校验位）
- 固化字符集（防 JDK 版本差异）
- MyBatis TypeHandler + Jackson Serializer 全链路适配

## @OpenId 注解（v1.3：双向自动转换，业务侧零手工调用）

- 标 DTO/VO 字段 `@OpenId Long id` → 出参自动混淆串；**`@RequestBody` 入参自动反混淆**（含 `List<Long>` / `Set<Long>` / `Long[]` / 嵌套 record，无需 `@OpenIdRecursive`）
- 标参数 `@OpenId @PathVariable/@RequestParam Long id` → 混淆串/数字串自动还原
- 开关（`framework4j.openid.*`）：`support-integer` / `support-string`（默认 false）、`accept-numeric-fallback`（默认 true，关掉只吃混淆串）、`request-body-deserializer`（默认 true）
- 业务代码不写手工 `IdObfuscator.fromOpenId/toOpenId`；`@OpenId` 只标字段/标量参数，**不要**标在 `@RequestBody` 整个对象上（无效）

## WorkerIdStrategy

| 策略 | 说明 |
|---|---|
| `redis`（默认） | Redis 租约（0-1023 slot，心跳 CAS 续期） |
| `ip-hash` | IP 哈希（无 Redis 场景） |

## 配置

```yaml
framework4j:
  id:
    enabled: true
    worker-id-strategy: redis  # 或 ip-hash
    mybatis:
      enabled: true            # 自动注册 MyBatis Plus IdentifierGenerator
```

## 引入

```xml
<dependency>
    <groupId>com.github.funcommons.framework4j</groupId>
    <artifactId>framework4j-id</artifactId>
    <version>v1.2.5</version>
</dependency>
```
