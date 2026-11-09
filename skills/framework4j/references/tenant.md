# framework4j-tenant 多租户中间件（opt-in，不在 framework4j-all 内）

> 设计文档：`docs/modules/tenant-中间件中台租户设计.md`；配置全表见 `docs/config/reference.md`

## 定位

跨切面租户 starter：租户表（`{table-prefix}tenant`，实体 SPI + DDL 初始化 AUTO/PROVIDED）、
双域守卫（`@PlatformDomain` / `@TenantDomain`，自动 MVC 注册）、内置 auth 端点
（`TenantAuthTemplate`：平台合成租户 / 暴力锁定 / 密钥宽限窗口）、密钥重置 + 会话撤销、
`RegistrationKeyService` 渠道、`UserIdContext` + `TenantIdentity`（claim 优先 +
`default-tenant-id` 单租户兜底）、`RlsAssistant`（OFF/POLICY/FULL）。

## 引入（opt-in）

```xml
<dependency>
    <groupId>com.github.funcommons.framework4j</groupId>
    <artifactId>framework4j-tenant</artifactId>
    <version>v1.10.0</version>
</dependency>
```

## 核心 API

```java
// 内置 auth 端点签发 token（tenant_id 进业务 claims）
// framework4j.tenant.auth.embed-claims=true（默认）时 claims 嵌套写入 JWT payload——
// 前端解码 payload.claims.tenant_id 自助判身份域（issue #23，v1.7.0）

// 身份获取
TenantIdentity identity = ...;   // claim 优先，缺省回落 default-tenant-id（默认 0）
UserIdContext.getUserId();       // 请求线程用户 ID

// 域守卫（Controller 标注，框架自动注册拦截）
@PlatformDomain   // 平台管理面（仅平台身份可过）
@TenantDomain     // 租户业务面（租户身份可过）
```

## 会话治理

- `TenantSecretService` 重置密钥（旧钥宽限窗口）
- `TenantSessionRevoker` 撤销会话（平台踢租户）
- 暴力破解：连续失败锁定 + 解锁窗口

## 合规验收（tenant-tck）

```groovy
testImplementation('com.github.funcommons.framework4j:framework4j-tenant-tck:v1.8.0')
```

继承 `TenantComplianceSuite` 实现 `TenantComplianceContext`（租户表名/业务表清单/端点与凭据 fixture），
跑绿即合规。注意：T1-T3 结构断言需真实 PostgreSQL（查 `pg_indexes`）；T4-T8 行为断言需消费方
覆写 `assert*` 钩子并提供 endpoint/token fixture。详见 `framework4j-tenant-tck/README.md`。

## 配置（framework4j.tenant.*，常用）

```yaml
framework4j:
  tenant:
    enabled: true
    platform:
      tenant-id: 0
    auth:
      embed-claims: true     # token 自包含（独立于 access-token 模块的 embed-claims）
    default-tenant-id: 0     # 单租户应用兜底
    rls:
      mode: OFF              # OFF | POLICY | FULL
```
