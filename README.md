# framework4j

> 企业级 Spring Boot 3.5 / Java 17 SDK — 覆盖鉴权、缓存、Redis、数据源、分布式 ID、幂等、限流、签名、审计、脱敏等企业后端 80%+ 基础设施场景。

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](./LICENSE)

## 模块总览（16 个）

| 模块 | 用途 | 配置前缀 |
|---|---|---|
| `framework4j-api` | ApiCode 错误码枚举（契约层） | - |
| `framework4j-web` | ApiResponse 信封 + GlobalExceptionHandler + TraceContext + CachedBodyRequestWrapper | `framework4j.web.*` |
| `framework4j-datetime` | OffsetDateTime 序列化 + 时间格式拦截器 | `framework4j.datetime.*` |
| `framework4j-id` | Snowflake 分布式 ID + OpenID 混淆（12 位 + 校验位） | `framework4j.id.*` |
| `framework4j-redis` | 多 Redis 数据源管理 + `@RedisOn` 注解注入 | `framework4j.redis.*` |
| `framework4j-datasource` | 多 DataSource + Druid + `@DataSourceOn` 注解注入 | `framework4j.datasource.*` |
| `framework4j-sql-tracing` | SQL trace_id 注入（Druid Filter + 3 模式） | `framework4j.datasource.sql-tracing.*` |
| `framework4j-accesstoken` | JWT + Redis 双校验 Token + Refresh 家族轮转 | `framework4j.access-token.*` |
| `framework4j-idempotency` | Idempotency-Key 防重复提交（Redis 48h） | `framework4j.idempotency.*` |
| `framework4j-signature` | HMAC-SHA256 接口签名防重放（4 Header + nonce） | `framework4j.signature.*` |
| `framework4j-rate-limit` | 分布式限流（Lua 滑动窗口 + 响应头三件套） | `framework4j.rate-limit.*` |
| `framework4j-cache` | 多级缓存（Caffeine L1 + Redis L2 + 单飞防击穿） | `framework4j.cache.*` |
| `framework4j-audit` | 审计日志（`@Auditable` AOP + Hash Chain 防篡改） | `framework4j.audit.*` |
| `framework4j-sensitive` | 字段脱敏（Jackson）+ AES-256-GCM 加密（MyBatis TypeHandler） | `framework4j.sensitive.*` |
| `framework4j-all` | 聚合所有模块（一行依赖全栈集成） | - |

## 快速开始

### 1. 引入依赖

#### 方式一：JitPack（推荐，无需手动 install）

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.funcommons.framework4j</groupId>
    <artifactId>framework4j-all</artifactId>
    <version>v1.2.0</version>
</dependency>
```

#### 方式二：本地 install

```bash
git clone https://github.com/funcommons/framework4j.git
cd framework4j
mvn -DskipTests install
```

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-all</artifactId>
    <version>1.2.0</version>
</dependency>
```

### 2. 配置 application.yml

```yaml
spring:
  application:
    name: my-app          # 必填，用作 Redis key 前缀

framework4j:
  redis:
    enabled: true
    datasources:
      default:
        host: localhost
        port: 6379

  access-token:
    enabled: true
    secret-key: ${JWT_SECRET}    # 环境变量注入
    hash-salt: ${HASH_SALT}

  signature:
    enabled: true
    path-patterns: ["/v1/api/**"]

  rate-limit:
    enabled: true
    default-limit: 100
    default-window: "1m"

  cache:
    enabled: true
    default-ttl-seconds: 3600

  audit:
    enabled: true
    hash-chain-enabled: true

  sensitive:
    enabled: true
    encryption-key: ${AES_KEY}   # 生产环境从 KMS 取
```

### 3. 使用

```java
@RestController
public class OrderController {

    @Autowired
    private OrderService orderService;

    @RateLimit(limit = 100, window = "1m", scope = "user")
    @RequiresSignature
    @Auditable(action = "CREATE_ORDER", targetType = "order", targetIdSpel = "#req.orderId")
    @PostMapping("/v1/api/orders")
    public ApiResponse<Order> createOrder(@RequestBody CreateOrderRequest req) {
        return ApiResponse.success(orderService.create(req));
    }
}
```

## 核心设计原则

1. **Lua 原子化** — 所有 Redis GET+SET 序列走 Lua（消除 TOCTOU）
2. **ThreadLocal 资源复用** — Mac / MessageDigest / Cipher 用 ThreadLocal（§5.1）
3. **共享 Bean 单例** — 每模块抽共享 StringRedisTemplate（§7.2）
4. **CachedBodyRequestWrapper** — 解决 Spring ContentCachingRequestWrapper 不重放流的隐藏 bug
5. **生产级安全深度** — 9 轮审计 + 8 轮新模块优化 = 145 项 P0/P1 修复沉淀

## 文档

- [Java开发准则.md](./Java开发准则.md) — 21 章完整规范（P0-P3 分级）
- 各模块 `README.md` — 快速开始 + 配置示例 + Python 客户端示例
- `framework4j-cache/DESIGN.md` — 缓存设计文档

## 版本历史

| 版本 | 关键变更 |
|---|---|
| **v1.2.0** | Spring Boot 3.2 → 3.5.16；Druid 1.2.20 → 1.2.28（切换 `druid-spring-boot-3-starter` artifact）；Redisson 3.25.0 → 4.6.1；MyBatis Plus 3.5.14 → 3.5.15；PostgreSQL JDBC 42.7.1 → 42.7.11（修 CVE-2026-42198）；Lombok 1.18.30 → 1.18.46（JDK 21 兼容）；H2 / Mockito / ByteBuddy / JUnit / commons / jacoco 等同步升级 |
| v1.1.3 | 修复 GitHub Issue #1：`@OpenId @PathVariable` 静默失败 + IAE 异常分流（10102/10005/10106）+ 启动期 fail-fast 校验 + WebConfig Jackson 三开关 |
| v1.1.2 | jsqlparser 5.x 版本冲突修复（`optional=true`）+ 5 层依赖防御（checklist + Enforcer + compat-test） |
| v1.1.1 | MyBatis Plus 内置插件 + 文档重组 + P0 安全修复 |
| v1.1.0 | +6 新模块（cache / audit / sensitive / signature / rate-limit / idempotency）+ demo + docs + skills |

## 构建

```bash
# 编译（跳过测试）
mvn -DskipTests install

# 全量测试（排除性能测试）
mvn test

# 含性能测试
mvn test -Dgroups=performance

# 覆盖率检查（≥ 60%）
mvn verify
```

## 前置条件

- Java 17+
- Redis（`framework4j-redis` / `framework4j-accesstoken` / `framework4j-signature` / `framework4j-rate-limit` / `framework4j-cache` 测试需要）
- PostgreSQL（`framework4j-datasource` PostgreSQL profile 测试需要）

## License

[Apache License 2.0](./LICENSE)
