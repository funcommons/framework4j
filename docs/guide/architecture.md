# 架构总览

## 17 模块一览

| 分类 | 模块 | 能力 | 配置前缀 |
|---|---|---|---|
| **契约** | `framework4j-api` | ApiCode 错误码枚举 | — |
| **契约** | `framework4j-web` | ApiResponse 信封 + GlobalExceptionHandler + trace_id 链路 + CachedBodyRequestWrapper | `framework4j.web.*` + `framework4j.api.trace.*` |
| **数据** | `framework4j-redis` | 多 Redis + @RedisOn 注解注入 | `framework4j.redis.*` |
| **数据** | `framework4j-datasource` | 多 DataSource + Druid + @DataSourceOn | `framework4j.datasource.*` |
| **数据** | `framework4j-sql-tracing` | SQL trace_id 注入（Druid Filter + 3 模式） | `framework4j.datasource.sql-tracing.*` |
| **数据** | `framework4j-id` | Snowflake + OpenID 混淆 | `framework4j.id.*` + `framework4j.openid.*` |
| **数据** | `framework4j-datetime` | OffsetDateTime 定长序列化 + 动态格式 | `framework4j.datetime.*` |
| **安全** | `framework4j-accesstoken` | JWT + Redis 双验 + Refresh 家族 + 角色鉴权 | `framework4j.access-token.*` |
| **安全** | `framework4j-signature` | HMAC-SHA256 签名防重放 | `framework4j.signature.*` |
| **安全** | `framework4j-audit` | 审计日志 + Hash Chain | `framework4j.audit.*` |
| **安全** | `framework4j-sensitive` | 脱敏 + AES-256-GCM 加密 | `framework4j.sensitive.*` |
| **流量** | `framework4j-rate-limit` | Lua 滑动窗口限流 | `framework4j.rate-limit.*` |
| **流量** | `framework4j-idempotency` | Idempotency-Key 防重复 | `framework4j.idempotency.*` |
| **流量** | `framework4j-cache` | Caffeine L1 + Redis L2 多级缓存 | `framework4j.cache.*` |
| **集成** | `framework4j-transport` | RestTemplate / WebClient 传输抽象 | `framework4j.transport.*` |
| **集成** | `framework4j-tracelog` | 动态日志 Redis 采集 + 动态提权 | `framework4j.tracelog.*` |
| **聚合** | `framework4j-all` | 一行依赖引入全部 16 starter | — |
| **示例** | `framework4j-demo` | 全链路示例 | — |
| **可选域** | `framework4j-tenant`（不在 all 内） | 多租户横切面 + tenant-tck 合规测试集 | `framework4j.tenant.*` |

## 模块依赖关系

```
framework4j-api（ApiCode 契约）
    ↓
framework4j-web（ApiResponse / GlobalExceptionHandler / trace_id 链路）
    ↑                    ↑              ↑
    │                    │              │
signature  rate-limit  cache  audit  sensitive  idempotency  accesstoken  tracelog
    │                    │
    └──── framework4j-redis（MultiRedisManager）─────┘
              ↑
    framework4j-all（聚合，不含 tenant）
```

## 激活语义（v1.8 起，issue #26）

| 语义 | 模块 |
|---|---|
| **默认激活**（设 false 可关） | web / datetime / sql-tracing / idempotency / signature / rate-limit / cache / audit / sensitive / transport |
| **显式启用**（缺省不激活） | redis / datasource / accesstoken / tracelog / **id + openid（v1.8 起）** / tenant |

## 核心设计原则

1. **Lua 原子化** — 所有 Redis GET+SET 序列走 Lua（消除 TOCTOU）
2. **ThreadLocal 资源复用** — Mac / MessageDigest / Cipher 用 ThreadLocal
3. **共享 Bean 单例** — 每模块抽共享 StringRedisTemplate
4. **双信任 token** — 服务端只信 Redis 会话（可撤销/热更），JWT payload 嵌套 claims 仅供持有方解码
5. **生产级安全深度** — 9 轮审计 + 8 轮新模块优化 = 145+ 项修复
