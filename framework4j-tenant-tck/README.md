# framework4j-tenant-tck 租户合规测试集

机器可执行的租户接入合规验收（中间件中台租户设计 §10）：消费项目引依赖 → 继承
`TenantComplianceSuite` → 实现 `TenantComplianceContext` → **跑绿即合规**。

> 对应 GitHub Issue #22（F2：TokenGo 接入可用性确认）。

## 引入（test scope）

```groovy
// Gradle
testImplementation('com.github.funcommons.framework4j:framework4j-tenant-tck:1.9.0')
```

```xml
<!-- Maven -->
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-tenant-tck</artifactId>
    <version>1.10.1</version>
    <scope>test</scope>
</dependency>
```

> 注意：本模块是**普通 jar（compile 依赖随包传递）**而非 Maven test-jar classifier——
> `TenantComplianceSuite` 基类必须出现在消费项目的测试编译期。TCK 会传递引入
> `framework4j-tenant`、`spring-boot-starter-test`、`spring-jdbc`、mybatis-plus starter、assertj。

## 用法（三步）

```java
@SpringBootTest          // 你的应用测试配置（可运行上下文 + DataSource/JdbcTemplate）
class MyTenantComplianceTest extends TenantComplianceSuite {

    @Override
    public TenantComplianceContext complianceContext() {
        return new TenantComplianceContext() {
            @Override public String tenantTable() { return "platform_tenant"; }
            @Override public List<String> businessTables() { return List.of("biz_order", "biz_item"); }
            // T4-T8 需要：端点与凭据 fixture（返回 null 则对应断言跳过）
            @Override public String authEndpoint() { return "/auth/tenant/login"; }
            @Override public String platformTokenForTenant() { return ...; }
            // 可选覆写：assertBruteForceProtection() / assertResetSecretRevokesAndGrace() 等
        };
    }
}
```

## 断言范围

| # | 断言 | 前置条件 |
|---|---|---|
| T1 | 租户表列契约（17 列全量 + **无 tenant_id 列**） | 上下文可运行 |
| T2 | 每张业务表有 `tenant_id` 列且存在 `tenant_id` 领先的索引 | **真实 PostgreSQL**（查 `pg_indexes`） |
| T3 | email 部分唯一索引（`is_deleted=0 AND email IS NOT NULL`） | **真实 PostgreSQL** |
| T4 | 平台域拒绝租户 token（403） | 覆写 fixture + 断言钩子 |
| T5 | 租户域拒绝平台 token（403） | 同上 |
| T6 | 暴力破解锁定 | 同上 |
| T7 | 重置密钥撤销会话 + 宽限窗口 | 同上 |
| T8 | `X-User-Id` 头不可伪造身份 | 同上 |

## 已知边界（接入前必读）

1. **T1-T3 需真实 PostgreSQL**：索引断言用 `pg_indexes`，H2 内存库跑不了（PG 兼容模式也不行）。
   CI 建议起 Postgres service 或 Testcontainers。
2. **T4-T8 默认是空钩子**：`TenantComplianceContext` 对应 accessor 返回 null 或不覆写 `assert*`
   时该断言静默跳过——「跑绿」不等于「T4-T8 已验」，消费方必须自行覆写才能算数。
3. **需要可运行的 Spring 上下文**：套件是 `@SpringBootTest` 基类，你的测试类负责装配
   应用上下文（DataSource + tenant 模块自动配置激活 `framework4j.tenant.enabled=true`）。
4. 模块自身**零自测**（构建即验证编译）；真正的验收发生在消费项目的测试执行里。
   仓库内首个真实消费者：TokenGo（F2 接入中）。
