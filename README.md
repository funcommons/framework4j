# framework4j

> 企业级 Spring Boot 3.5 / Java 17 SDK — 覆盖鉴权、缓存、Redis、数据源、分布式 ID、幂等、限流、签名、审计、脱敏等企业后端 80%+ 基础设施场景。

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](./LICENSE)

## 模块总览（17 个）

| 模块 | 用途 | 配置前缀 |
|---|---|---|
| `framework4j-api` | ApiCode 错误码枚举（契约层） | - |
| `framework4j-web` | ApiResponse 信封 + GlobalExceptionHandler + TraceContext + CachedBodyRequestWrapper | `framework4j.web.*` |
| `framework4j-datetime` | OffsetDateTime 序列化 + 时间格式拦截器 | `framework4j.datetime.*` |
| `framework4j-id` | Snowflake 分布式 ID + OpenID 混淆（12 位 + 校验位） | `framework4j.id.*` |
| `framework4j-redis` | 多 Redis 数据源管理 + `@RedisOn` 注解注入 | `framework4j.redis.*` |
| `framework4j-datasource` | 多 DataSource + Druid + `@DataSourceOn` 注解注入 | `framework4j.datasource.*` |
| `framework4j-sql-tracing` | SQL trace_id 注入（Druid Filter + 3 模式） | `framework4j.datasource.sql-tracing.*` |
| `framework4j-accesstoken` | JWT + Redis 双校验 Token + Refresh 家族轮转 + `roles`/`anyRole` 角色鉴权 | `framework4j.access-token.*` |
| `framework4j-idempotency` | Idempotency-Key 防重复提交（Redis 48h） | `framework4j.idempotency.*` |
| `framework4j-signature` | HMAC-SHA256 接口签名防重放（4 Header + nonce） | `framework4j.signature.*` |
| `framework4j-rate-limit` | 分布式限流（Lua 滑动窗口 + 响应头三件套） | `framework4j.rate-limit.*` |
| `framework4j-cache` | 多级缓存（Caffeine L1 + Redis L2 + 单飞防击穿） | `framework4j.cache.*` |
| `framework4j-audit` | 审计日志（`@Auditable` AOP + Hash Chain 防篡改） | `framework4j.audit.*` |
| `framework4j-sensitive` | 字段脱敏（Jackson）+ AES-256-GCM 加密（MyBatis TypeHandler） | `framework4j.sensitive.*` |
| `framework4j-transport` | HTTP 传输抽象（RestTemplate / WebClient 切换 + 内置重试） | `framework4j.transport.*` |
| `framework4j-tracelog` | 运行链路日志（logback appender + 采样限速 + 查询 API + 敏感字段脱敏） | `framework4j.tracelog.*` |
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
    <version>v1.5.0</version>
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
    <version>1.5.0</version>
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
| **v1.5.0** | **新增 framework4j-tenant / framework4j-tenant-tck**:多租户中间件租户横切面(契约层=中间件中台租户设计 v2.1)——TenantEntity 基类+实体子类 SPI(表名={项目简码前缀}tenant)+ DDL 初始化器(AUTO 幂等建表/缺列补列,PROVIDED 模板)/ @PlatformDomain+@TenantDomain 双面守卫 MVC 自动注册 / TenantAuthTemplate+内置端点(平台合成租户、防爆破 5次15min、密钥宽限期双版本)/ TenantSecretService(reset+撤销存量会话)+ RegistrationKeyService(注册码通道,Redis 原子扣减)/ UserIdContext(X-User-Id 永不鉴权)+ TenantIdentity(claim 优先+默认租户回落,单租户模式 default-tenant-id)/ RlsAssistant(OFF/POLICY/FULL);platform.tenant-id 平台身份取值可配(默认 0);60+ 测试全绿。tenant-tck = 设计 §10 的机器可执行版(结构 T1-T3 + 行为 T4-T8) |
| **v1.4.2** | **framework4j-transport 修复 GitHub Issue #18**：`framework4jHttpTransport(RestTemplate)` 形参按类型注入，业务方声明 ≥2 个 `RestTemplate` Bean 时 `NoUniqueBeanDefinitionException` 启动失败（即使未使用 HttpTransport 也触发，模块自 1.2.9 引入即有此缺陷）。修复：形参改 `ObjectProvider`——新增 `framework4j.transport.rest-template-bean-name` 显式指定复用的 Bean（按名取用，配错启动报错）；未配置时唯一候选自动复用（业务 0 个→框架兜底 / 1 个→复用业务实例 / 多个但标 `@Primary`→复用主 Bean），歧义时**降级内置默认实例 + WARN 列出候选 Bean 名**，不再崩溃。注：issue 中建议的 `@Qualifier("framework4jRestTemplate")` 方案本身不成立——业务方声明 ≥1 个 RestTemplate 时该 Bean 因 `@ConditionalOnMissingBean` 让位不存在，按名注入必失败。存量 0/1 个 RestTemplate 场景行为逐一不变。模块首套测试（6 用例，ApplicationContextRunner） |
| **v1.4.1** | **framework4j-accesstoken 角色鉴权（GitHub Issue #16 方案 A）+ path-patterns 语义修正（Issue #17）**。`@RequiresToken` 新增 `roles()`（全匹配 AND）/ `anyRole()`（任一匹配 OR）声明式角色校验：角色从 **Redis claims** 的 `roles` 键读取（非 JWT payload），新增 `AccessTokenGenerator#updateClaims(tokenType, uid, claims)` 让角色变更（升权/降权）**全端实时生效**——无需重签 token / 重登（TTL 以 SET KEEPTTL 原样保留）；校验失败返回 `10300 FORBIDDEN`（与 10200 未认证区分）；fail-closed：存量老 token 无 `roles` claim 时新加角色校验的端点返回 403，重登或 `updateClaims` 后恢复；`type=refresh` 端点不做角色校验；两属性空默认值，存量注解零影响。**行为变更**：`path-patterns` 显式空列表从"拦截 `/**`"（Spring `addPathPatterns(空)` 语义，反直觉）改为**跳过拦截器注册 + WARN**；默认 `/**` 与非空列表不变，关闭模块请用 `enabled: false`。新增 13 个测试（角色校验 / updateClaims 实时生效与 TTL 保留 / 空列表注册锁定），模块 97 tests 全绿 |
| **v1.3.0 – v1.4.0** | 新增 `framework4j-tracelog` 模块（运行链路日志：logback appender + 采样限速 + 查询 API；v1.4.0 补敏感字段脱敏 + 4 个运行链路集成测试；v1.3.1–v1.3.3 修复接入阻断与运行链路 bug、web 模块 MDC traceId 兜底过滤器，详见 git log） |
| **v1.2.8** | **framework4j-accesstoken 严重修复**（下游 benefit4j "claims → TokenContext 链路问题"报告，实为误诊）：`AccessTokenAutoConfiguration` 补 `@Import(AccessTokenWebMvcConfig)` —— 与 idempotency v1.2.5 同构的孤儿注册类问题：`TokenInterceptor` Bean 创建了但永不进 MVC 链，`@RequiresToken` 不生效、`TokenContext` 永不填充（所有 `getClaim` 返回 null，不止 app_id）。claims → Redis → TokenContext 链路本身无故障（`WebIntegrationTest` 早已证明，只是测试自建了注册）。附带修复续期潜伏 bug：`renewIncrement != null` 恒真（`asLong` 缺省返装箱 0），`autoRenew` 未配 `renew-increment` 的策略每次校验 `expire(key, 0)` **删除 token 元数据**（第二次请求必 10201），改为 `> 0` 判定。新增注册锁定测试；模块 84 tests 全绿。⚠️ 下游注意：升级后 `@RequiresToken` 开始真正生效（此前鉴权形同虚设）；自建拦截器注册请拆除 |
| **v1.2.7** | framework4j-idempotency 修复下游 benefit4j 排查报告 bug2（"第一次请求必 409"）：① **重入守卫** —— 同一请求第二次进入 `preHandle`（典型：v1.2.5 框架注册 + 下游自建 workaround 注册未拆除，拦截器跑两遍）时，检测到本请求已通过 SETNX 直接放行，不再读到自己刚写的 PENDING 标记而 409 自己；② **PENDING 并发态区分** —— 同 key 前一请求仍在处理中的 409 消息改为"请稍后重试"并打 WARN，区别于已缓存响应的普通重复提交。新增 2 个锁定测试（重入不触 Redis + PENDING 消息），模块 42 tests 全绿。⚠️ 下游注意：升级 v1.2.5+ 后自建拦截器注册 workaround 应拆除（重入守卫是防御，不是保留 workaround 的理由） |
| **v1.2.6** | framework4j-id 内部重构（行为零变化）：`@OpenId` 值转换层统一为 `OpenIdLongCodec`（Long 枢轴命名对齐 + 负数错误消息恢复 `ID cannot be negative`）。⚠️ **breaking**：退役并删除 public 类 `fun.commons.framework4j.openid.util.OpenIdTypeUtils`（v2.1 遗留静态工具，容器处理已由 Jackson 序列化/反序列化器承担）——直接引用它的下游需迁移到 `@OpenId` 注解 / `IdObfuscator`；已知下游（MMagiX/benefit4j）均走注解，不受影响。净删 768 行，framework4j-id 337 tests 全绿 |
| **v1.2.5** | 两个下游排查报告确认的 bug 修复。**framework4j-idempotency**：`IdempotencyAutoConfiguration` 补 `@Import(IdempotencyWebMvcConfig)` —— v2.1 从 @Component 迁移到 @Bean 时注册类被漏挂，拦截器 Bean 创建了但永不进 MVC 拦截链，幂等校验形同虚设（新增容器级注册锁定测试）。**framework4j-sql-tracing**：`SqlTracingBeanPostProcessor` 配置查找改为 per-datasource 优先 + 全局 `framework4j.datasource.sql-tracing.*` 兜底 —— 此前只查 per-datasource，只配全局时主开关亮了但 filter 静默不注入；跳过注入现在有明确日志（WARN/INFO），bind 异常不再吞掉。⚠️ 下游注意：依赖幂等拦截的接口升级后**开始真正拦截**（重复 `Idempotency-Key` 会返回缓存响应）；sql-tracing 只配了全局 prefix 的应用升级后 SQL 会开始带 trace_id 注释 |
| **v1.2.4** | framework4j-datetime 修复 GitHub Issue #8：`@LocalTimeFormat` 的 `@Target` 补充 `PARAMETER`（此前按文档示例标在 `@RequestParam` 参数上编译失败，MMagiX audit 9 处被卡）。语义澄清：参数级注解仅为语义标记，**入参多格式解析（时间戳/ISO/空格）由全局 `StringToOffsetDateTimeConverter` 负责、无需注解**；出参格式由方法/类级注解控制（`yyyy-MM-dd HH:mm:ss` GMT+8）。新增注解契约测试 + 技能文档同步。⚠️ 下游注意：升级后可移除 `@DateTimeFormat` workaround（它会顶掉全局多格式转换器），裸 `@RequestParam OffsetDateTime` 即可 |
| **v1.2.3** | **framework4j-id `@OpenId` 重大更新**。🔴 **critical 修复**：OpenId Jackson 模块改用 `BeanPostProcessor` 直接 `mapper.registerModule` 注册，修复与 framework4j-web Long→String 的 `modulesToInstall` 互冲——此前 `@OpenId` 序列化/反序列化在容器级**静默失效**（对应 `OpenIdWebIntegrationTest` 长期 `@Disabled`），存量项目建议升级。**R1/R2/R3**：`@RequestBody` 中 `@OpenId` 字段（`Long` / `List<Long>` / `Set<Long>` / `Long[]` / `long[]` / 嵌套 record）自动反混淆；序列化侧 `@OpenId List<Long>` 输出混淆串数组。**R4 三开关**：`support-integer` / `support-string`（默认 false，opt-in `Integer`/`String` 及其集合，String 以 Long 为枢轴双向转）/ `accept-numeric-fallback`（默认 true，关掉拒绝裸数字、迁移后收口反枚举）+ 子开关 `request-body-deserializer`（默认 true）。**R6**：fail-fast 增加 `@RequestBody` DTO 字段误用扫描（`@OpenId` 标在未受理类型上启动报错）+ 消息全限定类名。**R8**：README `@OpenId` 落地迁移指南。⚠️ **breaking**：path/query 入参的 `@OpenId Integer` 从"默认支持"改为需 `support-integer=true`（对齐开关；序列化侧本就不支持 Integer）。R7（`strict`）未做（数字透传已保护真数字 Long 字段） |
| **v1.2.1** | Spring Boot 3.2 → 3.5.16；Druid 1.2.20 → 1.2.28（切换 `druid-spring-boot-3-starter` artifact）；Redisson 3.25.0 → 4.6.1；MyBatis Plus 3.5.14 → 3.5.15；PostgreSQL JDBC 42.7.1 → 42.7.11（修 CVE-2026-42198）；Lombok 1.18.30 → 1.18.46（JDK 21 兼容）；H2 / Mockito / ByteBuddy / JUnit / commons / jacoco 等同步升级；修复 Lettuce poolConfig 泛型不兼容（Spring Data Redis 3.5 收紧 `GenericObjectPoolConfig` 类型参数） |
| ~~v1.2.0~~ | **已撤回**：framework4j-redis 编译失败（Lettuce poolConfig 泛型收紧未适配），JitPack 仅发布 7 个不依赖 redis 的子模块。tag 已删除，请使用 v1.2.1 |
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
