
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
    <version>v1.1.1</version>
</dependency>
```
