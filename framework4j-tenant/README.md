# framework4j-tenant

多租户中间件的**租户横切面** Spring Boot Starter —— 把「中间件中台租户设计」的契约层代码化:
下一个中间件引入一个依赖 + 少量配置即合规,安全横切面单点维护、修一次全体受益。

> **设计文档**(SSOT 链)
> - [framework4j-tenant 模块设计 v1.1](../../benefit4j/documents/framework4j-tenant模块设计.md)(本模块蓝图)
> - [中间件中台租户设计 v2.1](../../benefit4j/documents/中间件中台租户设计.md)(上游契约)
> - **参考实现**:benefit4j(6+1 项租户安全修复已验证)

## 当前状态:Step 1 骨架(v1.1 §7 实施计划 7 步)

| 步 | 内容 | 状态 |
|---|---|---|
| 1 | 模块骨架(pom/自动配置/Properties)+ 依赖版本对齐 | ✅ |
| 2 | 租户表实体 SPI + DDL 初始化器 | ✅ |
| 3 | @PlatformDomain/@TenantDomain 双面守卫 | ✅ |
| 4 | TenantAuthTemplate + 内置认证端点 | ✅ |
| 5 | SecretService + RegistrationKeyService | ✅ |
| 6 | UserIdContext + RlsAssistant | ✅ |
| 7 | tenant-tck 合规测试集 | ✅ |

## 快速开始

```yaml
framework4j:
  tenant:
    enabled: true            # 默认 false,含 DDL/端点注册,必须显式开启
    table-prefix: ubma_      # 租户表 = {table-prefix}tenant(项目简码表名规范)
    ddl-mode: AUTO           # AUTO 幂等建表 / PROVIDED 输出 SQL 模板
```

## 配置面(v1.1 §4,全部有默认)

| 配置 | 默认 | 说明 |
|---|---|---|
| `framework4j.tenant.enabled` | `false` | 总开关(显式开启) |
| `framework4j.tenant.table-prefix` | —(必填) | 项目简码_,如 `ubma_`;租户表 = `{table-prefix}tenant` |
| `framework4j.tenant.ddl-mode` | `AUTO` | AUTO 幂等建表 / PROVIDED 由项目迁移工具管理 |
| `framework4j.tenant.auth.enabled` | `true` | 内置认证端点开关 |
| `framework4j.tenant.auth.path` | `/api/v1/auth/token` | 认证端点路径 |
| `framework4j.tenant.auth.max-fail` | `5` | 连续失败锁定阈值 |
| `framework4j.tenant.auth.lock-minutes` | `15` | 锁定时长 |
| `framework4j.tenant.auth.token-type` | `TENANT` | 签发型别(存量项目可配 `APP` 兼容) |
| `framework4j.tenant.auth.expire-seconds` | `28800` | 8h;上限 43200(12h,§5.2) |
| `framework4j.tenant.platform.client-id/secret` | `PLATFORM`/空 | 平台合成租户(tenant_id=0)凭据 |
| `framework4j.tenant.secret.grace-hours` | `24` | 密钥轮换宽限期 |
| `framework4j.tenant.registration-key.*` | 关/1/24h | 注册码通道(§6.2,按需开) |
| `framework4j.tenant.rls.mode` | `OFF` | OFF / POLICY(就位不 FORCE)/ FULL(强制) |

注:本模块为领域专用(多租户中间件),**不进** `framework4j-all` 聚合,按需单独引入。
