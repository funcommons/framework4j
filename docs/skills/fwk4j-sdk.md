
# framework4j SDK 使用指南

## 0. 用户速查

| 你想 | 入口 |
|---|---|
| 引入依赖 / 快速开始 | §1 引入方式 |
| 接口需要登录才能访问 | §2 AccessToken 鉴权 |
| 开放 API 签名防重放 | §3 接口签名 |
| 接口限流 | §4 限流 |
| 缓存（防穿透/击穿/雪崩） | §5 多级缓存 |
| 关键操作审计 | §6 审计日志 |
| 手机号/身份证脱敏 | §7 字段脱敏 |
| DB 字段加密存储 | §8 字段加密 |
| 防止重复提交 | §9 幂等键 |
| 生成分布式 ID | §10 分布式 ID |
| 多 Redis 数据源 | §11 多 Redis |
| 多 DataSource | §12 多 DataSource |
| 统一 API 响应格式 | §13 ApiResponse |
| Token 过期通知客户端 | §14 Token 续期 |
| 管理员踢用户下线 | §15 强制下线 |
| 退出本规范 | 「退出 fwk4j-sdk」 |

## 1. 引入方式

### JitPack（推荐）

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<!-- 全量引入 -->
<dependency>
    <groupId>com.github.funcommons.framework4j</groupId>
    <artifactId>framework4j-all</artifactId>
    <version>v1.1.1</version>
</dependency>

<!-- 或按需引入单模块 -->
<dependency>
    <groupId>com.github.funcommons.framework4j</groupId>
    <artifactId>framework4j-signature</artifactId>
    <version>v1.1.1</version>
</dependency>
```

### 最小配置

```yaml
spring:
  application:
    name: my-app  # 必填（Redis key 前缀 + JWT iss）

framework4j:
  redis:
    enabled: true
    datasources:
      default:
        host: localhost
        port: 6379
```

---

## 2. AccessToken 鉴权

### 生成 Token

```java
@Autowired
private AccessTokenGenerator generator;

// 生成 access token
String token = generator.generateToken("WEB", Map.of("uid", "u-123"));

// 生成 access + refresh pair
TokenPair pair = refreshTokenService.generateTokenPair(Map.of("uid", "u-123"));
```

### 保护接口

```java
@RequiresToken("WEB")  // 校验 access token
@GetMapping("/v1/users/{id}")
public ApiResponse<User> getUser(@PathVariable String id) { ... }
```

### Refresh 轮转

```java
@PostMapping("/v1/auth/refresh")
public ApiResponse<?> refresh(@RequestBody RefreshRequest req) {
    TokenPair pair = refreshTokenService.refreshAccessToken(req.getRefreshToken());
    return ApiResponse.success(pair);
}
```

### 配置

```yaml
framework4j:
  access-token:
    enabled: true
    redis-name: default
    secret-key: ${JWT_SECRET}       # 必须 ≥ 32 字符
    hash-salt: ${HASH_SALT}
    policies:
      WEB:
        key: [uid]
        expire-time: 7200            # 2 小时
        auto-renew: true
        renew-increment: 1800        # 续期 30 分钟
```

---

## 3. 接口签名（HMAC-SHA256）

### 签名算法

```
签名串 = METHOD\nPATH\nTIMESTAMP\nNONCE\nBODY_MD5
签名值 = BASE64(HMAC_SHA256(secret, 签名串))
```

### 请求 Header

| Header | 说明 |
|---|---|
| `X-Access-Key` | 应用标识 |
| `X-Timestamp` | Unix 毫秒（±5min） |
| `X-Nonce` | UUID v4（10min 一次性） |
| `X-Signature` | BASE64 签名值 |

### 保护接口

```java
@RequiresSignature
@PostMapping("/v1/api/orders")
public ApiResponse<?> createOrder(@RequestBody OrderRequest req) { ... }
```

### 配置

```yaml
framework4j:
  signature:
    enabled: true
    path-patterns: ["/v1/api/**"]
    timestamp-tolerance-ms: 300000
    nonce-ttl-seconds: 600
```

---

## 4. 限流

### 注解式

```java
@RateLimit(limit = 100, window = "1m", scope = "user")
@PostMapping("/v1/sms/send")
public ApiResponse<?> sendSms(@RequestBody SmsRequest req) { ... }
```

### scope 维度

| scope | key 格式 | 适用 |
|---|---|---|
| `ip`（默认） | `ratelimit:ip:{ip}:{path}` | 公网 IP |
| `user` | `ratelimit:user:{uid}:{path}` | 已登录用户 |
| `global` | `ratelimit:global:global:{path}` | 全局限流 |

### 白名单（健康检查/内部服务豁免）

```yaml
framework4j:
  rate-limit:
    whitelist-paths: ["/actuator/**", "/health/**"]
    whitelist-ips: ["10.0.0.1"]
```

---

## 5. 多级缓存

### 编程式

```java
@Autowired
private CacheService cacheService;

User user = cacheService.get("user", id, 3600,
    () -> userMapper.selectById(id), User.class);
cacheService.put("user", id, 3600, user);
cacheService.evict("user", id);
```

### 注解式

```java
@CacheableGet(prefix = "user", key = "#id", nullTtl = 5)
public User getUser(String id) { ... }

@CacheablePut(prefix = "user", key = "#id")
public User updateUser(String id, String name) { ... }

@CacheableEvict(prefix = "user", key = "#id")
public void deleteUser(String id) { ... }
```

### 批量预热

```java
@PostConstruct
void warmup() {
    cacheService.warmup("user", hotIds, 3600,
        id -> userMapper.selectById(id), User.class);
}
```

---

## 6. 审计日志

### 注解

```java
@Auditable(action = "DELETE_ORDER", targetType = "order", targetIdSpel = "#orderId")
public void deleteOrder(String orderId) { ... }
```

### 安全责任

> `actor` 取自 `X-User-Id`、`ip` 取自 `X-Forwarded-For`。
> **必须由网关在入口覆写后才可信。**

---

## 7. 字段脱敏

### Jackson 自动脱敏

```java
public class UserVO {
    @Sensitive(SensitiveRule.PHONE)     private String phone;    // 138****5678
    @Sensitive(SensitiveRule.ID_CARD)   private String idCard;   // 110101********1234
    @Sensitive(SensitiveRule.EMAIL)     private String email;    // a***@example.com
    @Sensitive(SensitiveRule.NAME)      private String name;     // 张**
    @Sensitive(value = SensitiveRule.CUSTOM, pattern = "2,2,4")
    private String orderNo;                                     // AB****GH
}
```

---

## 8. 字段加密（DB 存储层）

### MyBatis TypeHandler

```java
@TableName(autoResultMap = true)
public class UserDO {
    @TableField(typeHandler = EncryptedFieldTypeHandler.class)
    private String idCard;  // DB 存密文，Java 层是明文
}
```

### 配置

```yaml
framework4j:
  sensitive:
    enabled: true
    encryption-key: ${AES_KEY}  # 生产从 KMS 取
```

---

## 9. 幂等键

客户端在写操作请求头加 `Idempotency-Key: <UUID v4>`，服务端自动防重。

```yaml
framework4j:
  idempotency:
    enabled: true
    ttl-seconds: 172800  # 48h
```

---

## 10. 分布式 ID

### Snowflake

```java
@Autowired
private SnowflakeDistributor snowflake;

long id = snowflake.nextId();  // → 892310293123123
```

### OpenID 混淆

```java
String openId = IdObfuscator.toOpenId(123456789L);      // → "DxjWpoSI9f6Q"
long original = IdObfuscator.fromOpenId(openId);         // → 123456789
String prefixed = IdObfuscator.toOpenId(123456789L, "ORD"); // → "ORD_DxjWpoSI9f6Q"
```

---

## 11. 多 Redis 数据源

### 配置

```yaml
framework4j:
  redis:
    datasources:
      default: { host: localhost, port: 6379 }
      cache:  { host: cache.redis.com, database: 1 }
```

### 注解注入

```java
@RedisOn("cache")
private StringRedisTemplate cacheTemplate;
```

---

## 12. 多 DataSource

### 配置

```yaml
framework4j:
  datasource:
    datasources:
      default: { url: jdbc:postgresql://localhost/mydb }
      business: { url: jdbc:postgresql://localhost/business }
```

### 注解注入

```java
@DataSourceOn("business")
private DataSource businessDs;
```

---

## 13. ApiResponse

```java
@GetMapping("/v1/orders/{id}")
public ApiResponse<Order> getOrder(@PathVariable String id) {
    Order order = orderService.get(id);
    return ApiResponse.success(order);
}
// → {"code":0,"message":"操作成功","data":{...},"trace_id":"xxx","timestamp":1718660400000}
```

---

## 14. Token 续期通知

每次 access token 校验通过后，响应头自动返回剩余有效期：

```
X-Token-Expire-At: 5400
```

客户端在剩余 < 5min 时主动调 `/v1/auth/refresh`。

---

## 15. 强制下线

```java
// 管理员踢出用户的所有设备
int revoked = generator.revokeByUser("WEB", "user-123");
// → 删除该用户所有 session + jti 加入撤销 Set
```

---

## 全链路组合示例

```java
@RestController
@RequestMapping("/v1/api")
public class OrderController {

    @RequiresSignature                          // 签名校验
    @RateLimit(limit = 100, window = "1m")      // 限流
    @Auditable(action = "CREATE_ORDER",         // 审计
               targetType = "order",
               targetIdSpel = "#req.orderId")
    @PostMapping("/orders")
    public ApiResponse<OrderVO> createOrder(@RequestBody CreateOrderRequest req) {
        Order order = orderService.create(req);
        return ApiResponse.success(toVO(order)); // VO 中 @Sensitive 自动脱敏
    }
}
```
