# framework4j-all

> 聚合 starter — 一行依赖引入全部 16 个模块

## 引入方式

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-all</artifactId>
    <version>1.2.0</version>
</dependency>
```

## 包含的模块（16 个）

| 模块 | 能力 |
|---|---|
| `framework4j-api` | ApiCode 错误码枚举（契约层） |
| `framework4j-web` | ApiResponse 信封 + GlobalExceptionHandler + TraceContext + CachedBodyRequestWrapper |
| `framework4j-datetime` | OffsetDateTime 序列化 + 时间格式拦截器 |
| `framework4j-id` | Snowflake 分布式 ID + OpenID 混淆 |
| `framework4j-redis` | 多 Redis 数据源 + `@RedisOn` 注解注入 |
| `framework4j-datasource` | 多 DataSource + `@DataSourceOn` 注解注入 |
| `framework4j-sql-tracing` | SQL trace_id 注入（Druid Filter） |
| `framework4j-accesstoken` | JWT + Redis 双验 + Refresh 家族轮转 |
| `framework4j-idempotency` | Idempotency-Key 防重复提交 |
| `framework4j-signature` | HMAC-SHA256 接口签名防重放 |
| `framework4j-rate-limit` | 分布式限流（Lua 滑动窗口 + 固定窗口） |
| `framework4j-cache` | 多级缓存（Caffeine L1 + Redis L2 + 单飞防击穿） |
| `framework4j-audit` | 审计日志（`@Auditable` AOP + Hash Chain） |
| `framework4j-sensitive` | 字段脱敏 + AES-256-GCM 加密 TypeHandler |

## 与单独引入对比

| 方式 | 优点 | 缺点 |
|---|---|---|
| `framework4j-all`（推荐） | 一行搞定，零配置门槛 | 引入全部依赖（jar 较大） |
| 单独引入所需模块 | 精简依赖，按需装配 | 需手动管理依赖关系 |

## 最小配置

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

其余模块默认 `enabled=true`（opt-in），按需关闭：
```yaml
framework4j:
  signature:
    enabled: false  # 不需要签名校验
  audit:
    enabled: false  # 不需要审计
```
