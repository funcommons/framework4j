# framework4j-tenant 模块设计方案

> **版本** v1.2 · **更新** 2026-08-28 · **状态** 定稿(7 步全绿,56 测试) · **上游文档** [中间件中台租户设计 v2.1](./tenant-中间件中台租户设计.md) · **参考实现** benefit4j(6+1 项安全修复已验证)
> v1.2:实施完成 —— D-1 改定 B 方案(映射项目表,表名守项目简码规范)落地;七步全绿;
> 双仓库联动 P1(framework4j v1.5.0)待打 tag。
> v1.1:D-1 改定 B 方案(映射项目现有表,表名={项目简码前缀}tenant,遵守「所有表以项目简码_开头」规范;实体子类 SPI + DDL 初始化器替代框架自有表,benefit4j 零数据迁移)。

---

## 1. 定位与边界

**framework4j-tenant** = 多租户中间件的**租户横切面** Spring Boot Starter。

| 管(横切面) | 不管(业务自理) |
|---|---|
| 租户主表 + 四类配置(§3.1) | 各中间件业务表(自带 tenant_id 列即可) |
| 三域身份守卫(平台/租户双面) | 业务 runtime 逻辑 |
| client_credentials 认证(防爆破/平台合成/宽限期) | 业务错误码/响应封装细节 |
| 密钥生命周期(reset/撤销会话/双版本) | 邮件/短信等通知渠道 |
| 注册码通道(发码/原子扣减/吊销) | OEM 前端(→ fc-web-sdk) |
| RLS 助手(策略迁移 + 连接层 SET) | 业务侧对账/计量 |

**依赖的 framework4j 模块**:accesstoken(会话/key 结构)、id(OpenID)、web(ApiResponse)、redis、audit、sensitive(AES-GCM 加密 TypeHandler)、datasource(MyBatis Plus)。

## 2. 关键设计决策

### D-1 租户表:映射项目现有表,表名 = `{项目简码前缀}tenant`(B 方案,v1.1 定稿)

**规范前提**:各项目所有表以项目简码_ 开头(benefit4j: `ubm{a|p|x}_*`)——租户表也不例外,
框架**不引入**自有命名空间(不建 `f4j_tenant`),映射到项目现有表:

```yaml
framework4j:
  tenant:
    table-prefix: ubma_        # 项目简码域前缀 → 租户表 = ubma_tenant
```

| 手段 | 判定 |
|---|---|
| **实体子类 SPI(2 文件/项目)** ✅ | 项目写 `@TableName("ubma_tenant") class BenefitTenant extends TenantEntity {}` + `interface BenefitTenantMapper extends BaseMapper<BenefitTenant> {}` 并注册为 `TenantSchema` —— 显式、零魔法、零 SQL 解析开销、不动全局 table-prefix |
| DynamicTableNameInnerInterceptor | 零项目代码,但全局 SQL 解析开销 + 隐式替换魔法(误伤同名逻辑表风险),弃 |
| 全局 mybatis-plus table-prefix | 会拼接在项目全部显式 @TableName 前,破坏现有实体,禁用 |

**结构契约不因 B 方案放松**:`TenantEntity` 基类字段(= 文档 §3.1 列,契约层冻结)由框架定义;
**DDL 由框架保证**——启动初始化器按配置表名执行幂等 DDL(CREATE IF NOT EXISTS + 缺列补列,
`ddl-mode: AUTO`),Flyway 项目可切 `PROVIDED`(框架输出 SQL 模板,项目迁移工具自行管理);
**tck 结构断言参数化表名**,列漂移照样被验收抓住。

### D-2 三域守卫:注解 + 拦截器(替代各项目手工 @ModelAttribute)

```java
@PlatformDomain                    // 平台域: 仅平台身份(tenant_id==0)可达
@RestController @RequestMapping("/benefit/api/v1/platform/tenants")
public class TenantController { ... }

@TenantDomain                      // 租户域: 仅真实租户身份(tenant_id>0)可达
@RestController @RequestMapping("/benefit/api/v1/runtime/**")
public class XxxRuntimeController { ... }
```

拦截器扫描 handler 所属 controller 的注解统一校验,401(型别)/403(身份)语义与文档一致。**注解只定规则,拦截器按 path 注册才生效**(benefit4j 踩坑:注解≠自动触发)。

### D-3 认证端点:框架内置可关

模块自动注册 `POST {auth-path}`(默认 `/api/v1/auth/token`,可配),含防爆破/平台合成租户/宽限期双版本全套;`enabled=false` 时项目可自带端点、逻辑委托 `TenantAuthTemplate`。内置端点默认放行于 access-token 的 exclude-path(自动配置代填)。

### D-4 会话撤销:复用 accesstoken 的 key 结构

`TenantSessionRevoker` 按 `TokenKeyBuilder.accessMetadata(appName, type, hash)` 删 APP/OPS 两型 key(benefit4j 已验证的算法,源码同仓零猜测)。

## 3. 模块组成(七件套)

| # | 组件 | 内容 | benefit4j 对应(迁移源) |
|---|---|---|---|
| 1 | `TenantEntity` 基类 + `TenantSchema` SPI + DDL 初始化器 | 表名={简码前缀}tenant(项目现有表,零数据迁移);四类配置 JSONB;secret AES-GCM;DDL 幂等(AUTO/PROVIDED) | `ubma_tenant` + `UbmaTenantMapper`(直接映射,加两个适配文件) |
| 2 | `@PlatformDomain` / `@TenantDomain` + `DomainGuardInterceptor` | 双面守卫(认 0 / 拒 0),401/403 映射 | `PlatformIdentityGuard` / `TenantIdentityGuard` + 手工 @ModelAttribute |
| 3 | `TenantAuthTemplate` + 内置 `TenantAuthEndpoint` | client_credentials、防爆破(5 次/15min,429)、平台合成租户(id=0)、宽限期双版本比对 | `DefaultBenefitAuthService` |
| 4 | `TenantSecretService` + `TenantSessionRevoker` | reset(旧钥入 prev + 撤销全部会话)、明文只显一次、脱敏 | `DefaultBenefitPlatformService#postTenantsTenantIdSecret` + revoke |
| 5 | `RegistrationKeyService` + 开放域端点(可选) | 发码(次数/有效期/预绑配置档)、Redis 原子扣减、吊销、凭码即 ACTIVE | 无(新能力,文档 §6.2) |
| 6 | `UserIdContext` | `X-User-Id` 请求头解析(ThreadLocal,永不鉴权红线在 Javadoc+运行时断言) | 各 controller 手工取参 |
| 7 | `RlsAssistant` | 策略 SQL 生成(ENABLE 不 FORCE 起步)+ 连接层 `set_config` Filter(FULL 模式,配合 FORCE) | V1.4.1 迁移(手工 SQL) |

## 4. 配置面(全部有默认,零配置可用)

```yaml
framework4j:
  tenant:
    enabled: true
    table-prefix: ubma_             # 租户表 = {table-prefix}tenant(项目简码规范)
    ddl-mode: AUTO                  # AUTO 启动幂等建表 / PROVIDED 输出 SQL 模板由项目迁移工具管理
    auth:
      enabled: true                  # 内置认证端点
      path: /api/v1/auth/token
      max-fail: 5
      lock-minutes: 15
      token-type: TENANT             # 签发的 token 型别名(默认 TENANT,可配 APP 兼容存量)
      expire-seconds: 28800          # 8h(文档 §5.2 上限 12h)
    platform:
      client-id: ${PLATFORM_CLIENT_ID:PLATFORM}
      client-secret: ${PLATFORM_CLIENT_SECRET:}
      tenant-id: 0                   # 平台身份的 tenant_id 取值(默认 0;守卫/认证模板共用)
    secret:
      grace-hours: 24
    registration-key:
      enabled: false                 # 通道 B,按需开
      default-uses: 1
      default-ttl-hours: 24
    rls:
      mode: OFF                      # OFF / POLICY(就位不 FORCE) / FULL(连接层 SET + FORCE)
```

**兼容开关**(benefit4j 迁移期):`token-type: APP` 使签发 token 型别与存量一致,存量会话不失效。

## 5. tenant-tck(合规测试集,独立 test-jar)

文档 §10 checklist 的机器可执行版,任何项目(哪怕不用本模块)引依赖跑绿即合规:

- 结构断言:租户表列/唯一键、业务表 tenant_id+索引打头(扫 information_schema)
- 行为断言:双面守卫(平台 token↔租户域 403、租户 token↔平台域 403)、防爆破锁定、reset 撤销会话、宽限期新旧钥、X-User-Id 不鉴权
- 用法:`testImplementation('...:framework4j-tenant-tck')` + 继承 `TenantComplianceSuite` 提供端点映射

## 6. benefit4j 迁移路径(零 API 破坏)

| 阶段 | 动作 | 风险 |
|---|---|---|
| P1 | framework4j v1.5.0 发布模块(不含 benefit4j 改动) | 零 |
| P2 | benefit4j 升依赖,删自有 guard/auth/secret 实现,controller 换 `@PlatformDomain`/`@TenantDomain`;写 `BenefitTenant extends TenantEntity` + Mapper 两文件注册 SPI(`table-prefix: ubma_` 直接映射现有表,**零数据迁移**);`token-type: APP` 兼容存量 | 低:全量 IT+smoke 回归;无数据迁移 |
| P3 | 接入 tenant-tck;前端无感(token 透传) | 低 |
| P4 | 观察一个版本后删兼容开关(token-type 切 TENANT,存量 token 失效窗口公告) | 低 |

## 7. 实施计划(framework4j 仓,7 步,每步独立可交付)

| 步 | 内容 | 估时 | 验证 | 状态 |
|---|---|---|---|---|
| 1 | 模块骨架(pom/自动配置/Properties)+ 依赖版本对齐 | 0.5d | demo 启动 | ✅ `67e3fa3` |
| 2 | 租户表实体 SPI + DDL 初始化器(AUTO 幂等建表/补列,PROVIDED 模板) | 1d | 模块 IT(建表/幂等/补列) | ✅ `a6484e7` |
| 3 | @PlatformDomain/@TenantDomain 双守卫注解+拦截器+MVC 自动注册 | 0.5d | IT(双面 403/放行) | ✅ `0c82f49` |
| 4 | TenantAuthTemplate+内置端点(防爆破/合成租户/宽限期/policy 代填) | 1d | IT(认证矩阵 8 用例) | ✅ `6021ec5` |
| 5 | TenantSecretService(reset 撤销会话)+ TenantSessionRevoker + RegistrationKeyService | 1d | IT(6 用例) | ✅ `c8dbdbb` |
| 6 | UserIdContext + RlsAssistant(OFF/POLICY/FULL) | 0.5d | IT(7 用例) | ✅ `532c740` |
| 7 | tenant-tck test-jar(结构 T1-T3 可跑,行为 T4-T8 项目触发)+ README 全绿 | 1d | tck 编译过 + benefit4j 试接入 | ✅ `0b7f097` |
| — | **合计** | **~5.5d** | 打 tag v1.5.0 → JitPack | **7/7 完成,56 测试全绿** |

> **P1 待办**:framework4j 打 tag `v1.5.0`(含 tenant 模块)→ benefit4j 升依赖接入(P2)。

## 8. 风险与对策

| 风险 | 对策 |
|---|---|
| 双仓库联动(benefit4j 依赖未发布版本) | 严格按 P1 先发布后接入;本地 install 联调时注意 `-am` 与本地库旧 jar 陷阱(已记入记忆) |
| B 方案列结构漂移(项目魔改租户表) | TenantEntity 基类 + DDL 初始化器(缺列补列)+ tck 结构断言(参数化表名)三重守护 |
| 各项目存量 token 型别不一 | `token-type` 兼容开关 + P4 观察期切换 |
| 模块演进与文档契约层脱钩 | 模块 CHANGELOG 引用文档条款号(§x.y);tck 断言与 §10 checklist 一一对应 |

## 9. 与既有体系的关系

```
中间件中台租户设计 v2.1(契约层冻结 —— SSOT)
   ├── framework4j-tenant      后端实现方(本方案)
   ├── fc-web-sdk              前端实现方(useEmbedToken/useEmbedParams/四入口骨架 —— 后续沉淀)
   └── tenant-tck              双向验收(§10 的机器版)
```
