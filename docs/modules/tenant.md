# framework4j-tenant

> 多租户中间件租户横切面 Starter —— 租户表 SPI、三域守卫、认证端点、密钥生命周期、注册码、RLS 助手。

## 1. 概览

| 项 | 值 |
|---|---|
| 职责 | `TenantEntity` 基类(契约冻结)+ `TenantSchema` SPI + DDL 初始化器 / `@PlatformDomain` `@TenantDomain` 双面守卫 / `TenantAuthTemplate` + 内置端点 / `TenantSecretService` + `TenantSessionRevoker` / `RegistrationKeyService` / `UserIdContext` / `RlsAssistant` |
| 配置前缀 | `framework4j.tenant.*`(kebab-case) |
| 必需依赖 | `framework4j-accesstoken`(会话/JWT)、`framework4j-sensitive`(AES-GCM)、`framework4j-id`(OpenID)、`framework4j-datasource`(MyBatis Plus)、`framework4j-web`(ApiResponse) |
| 上游契约 | [中间件中台租户设计 v2.1](./tenant-中间件中台租户设计.md)(契约层冻结) |
| 参考实现 | benefit4j(6+1 项安全修复已验证) |

**核心原则**:租户是隔离边界(数据/幂等/限流/对账/计费五位一体);表 id 即租户 id;平台身份(tenant_id=0)是管理面不是记账主体。

## 2. 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-tenant</artifactId>
    <version>1.5.0</version>
</dependency>
<!-- 自动引入 accesstoken/sensitive/id/datasource/web 等 -->
```

### 最小 application.yml

```yaml
spring:
  application:
    name: my-app

framework4j:
  tenant:
    enabled: true
    table-prefix: ubma_            # 租户表 = ubma_tenant(项目简码规范)
    ddl-mode: AUTO                 # AUTO 幂等建表 / PROVIDED 输出 SQL 模板
```

### 最小代码示例(实体子类 SPI,每项目 2 文件)

```java
// 文件 1: 实体子类(表名 = {table-prefix}tenant)
@TableName(value = "ubma_tenant", autoResultMap = true)
public class BenefitTenant extends TenantEntity {}

// 文件 2: Mapper(框架泛型解析注入)
public interface BenefitTenantMapper extends BaseMapper<BenefitTenant> {}

// 文件 3: 注册 SPI(任一 @Configuration)
@Bean
TenantSchema tenantSchema() {
    return () -> BenefitTenant.class;
}
```

## 3. 配置参考

| 配置 | 默认 | 说明 |
|---|---|---|
| `framework4j.tenant.enabled` | `false` | 总开关(含 DDL/端点注册,须显式开启) |
| `framework4j.tenant.table-prefix` | —(必填) | 项目简码_,如 `ubma_`;租户表 = `{table-prefix}tenant` |
| `framework4j.tenant.ddl-mode` | `AUTO` | AUTO 幂等建表 / PROVIDED 由项目迁移工具管理 |
| `framework4j.tenant.auth.enabled` | `true` | 内置认证端点开关 |
| `framework4j.tenant.auth.path` | `/api/v1/auth/token` | 认证端点路径 |
| `framework4j.tenant.auth.max-fail` | `5` | 连续失败锁定阈值 |
| `framework4j.tenant.auth.lock-minutes` | `15` | 锁定时长(分钟) |
| `framework4j.tenant.auth.token-type` | `TENANT` | 签发型别(存量项目可配 `APP` 兼容) |
| `framework4j.tenant.auth.expire-seconds` | `28800` | 8h;上限 43200(12h,§5.2) |
| `framework4j.tenant.platform.client-id/secret` | `PLATFORM`/空 | 平台合成租户(tenant_id=0)凭据 |
| `framework4j.tenant.secret.grace-hours` | `24` | 密钥轮换宽限期(小时) |
| `framework4j.tenant.registration-key.enabled` | `false` | 注册码通道(§6.2,按需开) |
| `framework4j.tenant.rls.mode` | `OFF` | OFF / POLICY(就位不 FORCE) / FULL(强制) |

## 4. API 参考

### 三域守卫(注解)

```java
@PlatformDomain                    // 仅平台身份(tenant_id==0)可达
@RestController @RequestMapping("/platform/api/v1/tenants")
public class PlatformController { ... }

@TenantDomain                      // 仅真实租户身份(tenant_id>0)可达
@RestController @RequestMapping("/api/v1/runtime/**")
public class RuntimeController { ... }
```

### 认证(内置端点)

```http
POST /api/v1/auth/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
&client_id={租户 OpenID 或原始 id}
&client_secret={租户密钥明文}
```

响应: `{ "code": 0, "data": { "access_token": "eyJ...", "token_type": "Bearer", "expires_in": 28800 } }`

### 密钥生命周期

```java
@Autowired
TenantSecretService secretService;

// 重置(平台域操作): 旧钥入宽限期,新钥明文只显一次,存量会话撤销
ApiResponse<Map<String, Object>> resp = secretService.reset(tenantId);
```

### 注册码通道(可选)

```java
@Autowired
RegistrationKeyService regKeyService;

// 平台域发码(次数/有效期/预绑配置档)
regKeyService.issue("大客户A", 1, 24, Map.of("billing", true), Map.of("quotaDefault", 1000));

// 开放域凭码注册(凭码即 ACTIVE,密钥明文只显一次)
regKeyService.register("RK-xxx", "租户名", "contact@example.com");
```

## 5. 测试

```bash
mvn -pl framework4j-tenant test
```

56 个测试:配置契约(6)+ 实体契约(5)+ DDL 生成(3)+ DDL 初始化(6)+ 守卫矩阵(7)+ 认证矩阵(8)+ 密钥/注册码(6)+ UserId(3)+ RLS(4)+ MyBatis 全链(2)+ 装配(4)。

## 6. 合规验收(tenant-tck)

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-tenant-tck</artifactId>
    <version>1.5.0</version>
    <scope>test</scope>
</dependency>
```

```java
@SpringBootTest
class MyTenantComplianceTest extends TenantComplianceSuite {
    @Override
    public TenantComplianceContext complianceContext() {
        return new TenantComplianceContext() {
            public String tenantTable() { return "ubma_tenant"; }
            public List<String> businessTables() { return List.of("ubmx_account", "ubmx_posting"); }
            // 行为断言端点(可选,提供才跑 T4-T8)
            public String authEndpoint() { return "/api/v1/auth/token"; }
        };
    }
}
```

跑绿即合规(§10 checklist 的机器版)。
