# 架构总览

## 16 模块一览

| 分类 | 模块 | 能力 | 配置前缀 |
|---|---|---|---|
| **契约** | `framework4j-api` | ApiCode 错误码枚举 | — |
| **契约** | `framework4j-web` | ApiResponse 信封 + GlobalExceptionHandler + CachedBodyRequestWrapper | `framework4j.web.*` |
| **数据** | `framework4j-redis` | 多 Redis + @RedisOn 注解注入 | `framework4j.redis.*` |
| **数据** | `framework4j-datasource` | 多 DataSource + @DataSourceOn | `framework4j.datasource.*` |
| **数据** | `framework4j-sql-tracing` | SQL trace_id 注入（Druid Filter） | `framework4j.datasource.sql-tracing.*` |
| **数据** | `framework4j-id` | Snowflake + OpenID 混淆 | `framework4j.id.*` |
| **数据** | `framework4j-datetime` | OffsetDateTime 序列化 | `framework4j.datetime.*` |
| **安全** | `framework4j-accesstoken` | JWT + Redis 双验 + Refresh 家族 | `framework4j.access-token.*` |
| **安全** | `framework4j-signature` | HMAC-SHA256 签名防重放 | `framework4j.signature.*` |
| **安全** | `framework4j-audit` | 审计日志 + Hash Chain | `framework4j.audit.*` |
| **安全** | `framework4j-sensitive` | 脱敏 + AES-256-GCM 加密 | `framework4j.sensitive.*` |
| **流量** | `framework4j-rate-limit` | Lua 滑动窗口限流 | `framework4j.rate-limit.*` |
| **流量** | `framework4j-idempotency` | Idempotency-Key 防重复 | `framework4j.idempotency.*` |
| **流量** | `framework4j-cache` | Caffeine L1 + Redis L2 多级缓存 | `framework4j.cache.*` |
| **聚合** | `framework4j-all` | 一行依赖引入全部 | — |
| **示例** | `framework4j-demo` | 全链路示例 + 测试报告 + 配置工具 | — |

## 模块依赖关系

```
framework4j-api（ApiCode 契约）
    ↓
framework4j-web（ApiResponse / GlobalExceptionHandler / TraceContext）
    ↑                    ↑              ↑
    │                    │              │
signature  rate-limit  cache  audit  sensitive  idempotency  accesstoken
    │                    │
    └──── framework4j-redis（MultiRedisManager）─────┘
              ↑
    framework4j-all（聚合）
```

## 核心设计原则

1. **Lua 原子化** — 所有 Redis GET+SET 序列走 Lua（消除 TOCTOU）
2. **ThreadLocal 资源复用** — Mac / MessageDigest / Cipher 用 ThreadLocal
3. **共享 Bean 单例** — 每模块抽共享 StringRedisTemplate
4. **生产级安全深度** — 9 轮审计 + 8 轮新模块优化 = 145+ 项修复
