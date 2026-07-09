# docs-v1-archive

> framework4j v1.x 时期的旧文档归档。**已废弃**，仅供参考。
>
> 当前文档请看各模块目录下的 `README.md`：
> - [`framework4j-api/README.md`](../framework4j-api/README.md)
> - [`framework4j-datetime/README.md`](../framework4j-datetime/README.md)
> - [`framework4j-id/README.md`](../framework4j-id/README.md)
> - [`framework4j-sql-tracing/README.md`](../framework4j-sql-tracing/README.md)
> - [`framework4j-datasource/README.md`](../framework4j-datasource/README.md)
> - [`framework4j-redis/README.md`](../framework4j-redis/README.md)
> - [`framework4j-accesstoken/README.md`](../framework4j-accesstoken/README.md)
> - [`framework4j-idempotency/README.md`](../framework4j-idempotency/README.md)

## 归档原因

v2.0.0 重构后：
- `framework4j-core` 拆为 `api` + `datetime` + `id` 三模块
- `framework4j-datasource` 拆出 `sql-tracing` 独立模块
- `accesstoken` 拆出 `RefreshTokenService` + `TokenKeyBuilder` + `AccessToken/RefreshValidationStrategy`
- fastjson2 全量迁移到 Jackson
- BeanPostProcessor 提为顶级类
- 错误码扩展（10210-10212 refresh 相关）

旧文档描述的类结构 / 配置 / 错误码已过时，但保留作历史参考。

## 文档清单

### accesstoken

| 文件 | 状态 |
|---|---|
| `accesstoken-测试文档.md` | 旧测试文档，新测试列表见模块 README §6 |
| `accesstoken-用户指南.md` | 旧用户指南，RefreshTokenService 未涵盖 |
| `accesstoken-快速开始.md` | 旧快速开始，pathPatterns 未涵盖 |

### api（原 core）

| 文件 | 状态 |
|---|---|
| `api-使用指南.md` | 旧 API 指南，fastjson2 相关已过时 |
| `api-错误码规范.md` | 旧错误码规范，42 项错误码已扩展 |
| `id-产品文档.md` | 已迁到 `framework4j-id/` |
| `id-使用指南.md` | 已迁到 `framework4j-id/` |
| `datetime-方案.md` | 已迁到 `framework4j-datetime/` |
| `datetime-规范.md` | 已迁到 `framework4j-datetime/` |
| `openid-快速开始.md` | 已迁到 `framework4j-id/` |
| `openid-使用指南.md` | 已迁到 `framework4j-id/` |
| `openid-技术方案.md` | 已迁到 `framework4j-id/` |
| `openid-扩展计划.md` | 已迁到 `framework4j-id/` |

### datasource

| 文件 | 状态 |
|---|---|
| `datasource-产品文档.md` | 旧产品文档，sql-tracing 未拆分 |
| `datasource-测试README.md` | 旧测试说明 |
| `sql-tracing-方案.md` | 已迁到 `framework4j-sql-tracing/` |
| `sql-tracing-疑难解答.md` | 已迁到 `framework4j-sql-tracing/` |

### redis

| 文件 | 状态 |
|---|---|
| `redis-产品文档.md` | 旧产品文档，fastjson2 → Jackson 未涵盖 |
| `redis-RedisOn注解.md` | 旧注解文档 |

### idempotency

| 文件 | 状态 |
|---|---|
| `idempotency-用户指南.md` | 旧用户指南，fastjson2 → Jackson 未涵盖 |

## 迁移对照表

| 旧文档章节 | 新文档位置 |
|---|---|
| OpenID 12 字符混淆 | `framework4j-id/README.md` §4 API 参考 |
| 分布式 ID / Snowflake | `framework4j-id/README.md` §4 API 参考 |
| 时间序列化 / TimeFormat | `framework4j-datetime/README.md` §4 API 参考 |
| SQL 追踪 / TraceId | `framework4j-sql-tracing/README.md` §1 概览 |
| @RedisOn 注解 | `framework4j-redis/README.md` §4 API 参考 |
| @DataSourceOn 注解 | `framework4j-datasource/README.md` §4 API 参考 |
| AccessToken refresh family | `framework4j-accesstoken/README.md` §5.3 |
| Idempotency-Key | `framework4j-idempotency/README.md` §2 快速开始 |

## 当前架构文档

- [架构规划文档 v2.0.md](../架构规划文档%20v2.0.md) — 10 模块清单 + 目录结构 + 依赖图 + 命名约定
- [第二期规划.md](../第二期规划.md) — v2.0 GA 路线图
- [CLAUDE.md](../CLAUDE.md) — AI 助手指南
