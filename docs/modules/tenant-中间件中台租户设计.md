# 中间件中台租户设计(通用方案)

> **版本** v2.1 · **更新** 2026-08-28 · **状态** 定稿(长期标准,变更受 §1.0 演进规则约束) · **来源** benefit4j 实战抽象(见 §9 对照) · **实现** framework4j-tenant 模块(契约层代码化,7 步完成,56 测试全绿)
> v2.1:**§6.2 重新设计 —— 场景修正为本方案是中间层(内部/半开放生态,不对外运营)**:
> 自助注册从「邮箱验证+captcha+SANDBOX 分级」(v1.2 抄 SaaS 模式,前提不成立)改为
> **注册码模式**——信任由发码动作线下前置,凭码即 ACTIVE,配额档预绑在码上;新增
> **信任分级 L1 平台密钥/L2 注册码/L3 通道 A**;D14 取代 D9/D10;PENDING/SANDBOX
> 标注为对外 SaaS 场景预留(枚举保留,演进规则不删值)。
> v2.0(10 年标准终审):新增 **§1.0 稳定性承诺与演进规则**(契约/参数/适配三层模型——文档
> 主体不变的结构性保证)、**§6.3 租户注销与数据处置**(SUSPEND 冷静期→CLOSED,OpenID 永不
> 复用,生命周期闭环)、**§11 关键设计决策记录**(D1~D13 ADR 式取舍汇总,含被否替代方案);
> 状态机补 CLOSED;时点声明(§9 大厂对照为 2026-08 调研快照);修路径不一致一处。
> v1.4:安全合规复审(mc-java-security / mc-web-security 全条目过审)——修 嵌入基础级 token
> localStorage→sessionStorage(原与 Web 铁律 1 及 §8#11 自相矛盾)、补外观参数白名单校验
> (防 query 注入 DOM/store)、适用范围声明不覆盖项(密码/文件上传/OAuth2 用户登录)、
> 审计补最小字段集。
> v1.3:新增 **§7 前端控制台与页面嵌入**——四入口(app/page × 平台/租户)、外观参数与 OEM
> 按 host 白标联动、认证两级(URL token / postMessage 握手,token 暴露面 7→1)、frame-ancestors
> 白名单;原 §7/8/9 顺延为 §8/9/10。
> v1.2:新增**双通道接入**——§6.0 租户生命周期状态机(PENDING/SANDBOX/ACTIVE/SUSPEND)+
> §6.2 线上自助注册(开放域发密钥,Stripe test-mode / AWS sandbox / OpenAI free-tier 分级开通
> 模式,配额代替审批);§3.1 表结构补 email/channel/status 扩展;安全清单补自助注册防线。
> v1.1:按安全规范(mc-java-security / mc-web-security)修订——user_id 移出 JWT 改请求头、
> claims 补 iss、JWT/AES 密钥管理、平台域强校验落地手段、M2M token 时效论证、RLS 双保险、
> 审计 hash chain、常量时间签名比较、控制台前端 sessionStorage。
>
> **适用** 任何需要多租户化的中间件/中台类项目(权益、计费、消息、任务调度、网关等)。
> **不覆盖**(非本方案范围,落地时按 mc-java-security 对应场景执行): 用户密码体系(场景四)、文件上传(场景六)、OAuth2 第三方用户登录(场景八)——本方案是 M2B 租户模型,无终端用户会话。
> **约定** `xmp` = 中间件简码(示例),落地时替换为本项目简码(benefit4j 即 `benefit`);`tenant_id` 一律 **BIGINT 雪花**。

## 目录

1. 设计目标(§1.0 稳定性承诺与演进规则 / §1.1 四个基本问题)
2. 核心概念与术语
3. 数据模型(3.1 租户主表 / 3.2 业务表规范)
4. 接口三域
5. 凭据与认证(5.1 三级凭据 / 5.2 换 token / 5.3 平台域强校验 / 5.4 X-User-Id / 5.5 密钥管理)
6. 租户接入流程(6.0 状态机 / 6.1 运营创建 / 6.2 自助注册 / 6.3 注销与数据处置)
7. 前端控制台与页面嵌入(四入口 / 外观参数 / 认证两级 / 跨域安全 / OEM 联动)
8. 安全设计清单(13 项)
9. benefit4j 参考实现对照
10. 落地检查清单
11. 关键设计决策记录(D1~D14)

---

## 1. 设计目标

### 1.0 稳定性承诺与演进规则(本文档的 10 年契约)

本文档按**长期标准(目标生命周期 10 年)**维护,靠以下分层结构实现「主体不变」:

| 层 | 内容 | 稳定性 |
|---|---|---|
| **契约层**(本文档 §2~§7) | 租户模型(表 id 即租户)、三域接口、凭据与信任分级、claim 命名、状态机、X-User-Id 语义、嵌入四入口 | **冻结**:字段只加不删不改名,语义只收紧不放松 |
| **参数层**(文中所有具体数值) | token TTL 8h、验证 token 10min、PENDING 回收 24h、限流阈值、AES-GCM、SHA-256 | **默认值,全部可配**;调整参数不算改文档 |
| **适配层**(具体构件名) | PostgreSQL/RLS、Redis、JWT、AES-256-GCM、HMAC-SHA256、邮箱验证 | **可等价替换**(换 DB/换算法/换验证通道),替换时以附录记录,契约层不动 |

**演进规则**(违反任何一条即算破坏性变更,须升主版本号):

1. 表字段、API 路径、claim 名、请求头名、状态机状态:**只加不改不删**
2. 枚举(channel/status/brand 等)只允许**追加**值,不允许改语义或删值
3. 唯一键、幂等语义(`UNIQUE(tenant_id, 业务键)`)、平台域强校验(§5.3)、X-User-Id 三条红线(§5.4):**不可松动**
4. 废弃流程:先标注 deprecated(保留 ≥2 个大版本)→ 文档记录替代方案 → 才可移除
5. 大厂对照与实现参考(§9)是**定稿时点(2026-08)的调研快照**,不构成契约;原则层的有效性不依赖任何厂商的当前行为

**规模假设**:雪花 BIGINT(63 位可用)支持万亿级租户号段;单库 RLS/索引模型在 10⁴ 租户 × 10⁹ 行级验证可行,超出走分库(按 tenant_id 哈希分片,契约层不变)。

### 1.1 四个基本问题

一份可直接复制的租户化中间件蓝本,回答四个问题:

1. **数据怎么隔离** —— 每张业务表带 `tenant_id`,查询强制过滤
2. **接口怎么分域** —— 平台域 / 租户域 / 开放域,三域三种鉴权
3. **凭据怎么流转** —— 平台密钥、租户密钥、JWT(AccessToken)三级凭据
4. **租户怎么接入** —— 创建 → 配置 → 换 token → 调用,四步闭环

## 2. 核心概念与术语

| 概念 | 说明 |
|---|---|
| **租户 Tenant** | 接入方(一个业务系统)。是**隔离边界**:数据权限 / 幂等命名空间 / 限流计量 / 对账切片 / 计费单元,五位一体 |
| **平台 Platform** | 中间件的运营方(自己)。通过平台密钥管理租户与全局配置 |
| **用户 User** | 租户侧的终端用户。**仅透传标识,不建档、不校验**(见 §5.4) |

**命名规约**(踩坑后沉淀,务必遵守):

- `tenant_id` 一律 BIGINT 雪花(引用 `xmp_tenants.id`);若未来出现三方字符串租户标识,另命名 `tenantid`,**别混用**
- 对外暴露用 OpenID(Base62 + 校验位,如 `jZyCTw8xIjz4`),内部 BIGINT id 不泄露
- 全链路 claim / 参数 / 列名统一叫 `tenant_id`(配置、代码、文档三方一致;benefit4j 曾因 it/app 配置键不一致被迫双键兼容)

## 3. 数据模型

### 3.1 租户主表 `xmp_tenants` —— 全库唯一没有 tenant_id 的表

**表 id 即租户 id**,它自己是租户的本体,不隔离自己:

```sql
CREATE TABLE xmp_tenants (
    id              BIGINT PRIMARY KEY,                 -- = tenant_id(雪花)
    name            VARCHAR(64) NOT NULL,               -- 租户名
    email           VARCHAR(128),                       -- 联系邮箱(可选,凭码注册时可留;无验证流程)
    channel         VARCHAR(16) NOT NULL DEFAULT 'OPS'  -- 来源: OPS 运营创建 | SELF 自助注册
                    CHECK (channel IN ('OPS','SELF')),
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'   -- 生命周期状态机,见 §6.0
                    CHECK (status IN ('PENDING','SANDBOX','ACTIVE','SUSPEND','CLOSED')),
    tenant_secret   VARCHAR(256) NOT NULL,              -- 安全类:租户密钥(AES-256-GCM 加密存储)
    privileges      JSONB NOT NULL DEFAULT '{}',        -- 权限类:功能开关 {"billing":true,"webhook":false}
    config          JSONB NOT NULL DEFAULT '{}',        -- 配置类:参数与默认值 {"quotaDefault":1000}
    oem             JSONB NOT NULL DEFAULT '{}',        -- OEM 类:主题/名称/logo {"theme":"dark","title":"XX 控制台"}
    ext             JSONB NOT NULL DEFAULT '{}',        -- 预留扩展(验证token、注册IP等)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted      SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_xmp_tenants_ext ON xmp_tenants USING GIN (ext);
CREATE UNIQUE INDEX uk_xmp_tenants_email ON xmp_tenants(email) WHERE is_deleted = 0 AND email IS NOT NULL;
```

> 受限密钥(restricted key,Stripe/Cloudflare 模式:scope 收敛的多把 key、可选 TTL + IP 白名单)
> 与双环境密钥(同租户 test/live 两套,Stripe 模式)为**后续候选能力**,落地时以 `xmp_tenant_keys`
> 子表扩展,本表结构不动。

**四类配置进一张表的设计依据**(benefit4j 验证):

| 类 | 内容 | 读写特征 | 形态 |
|---|---|---|---|
| 安全类 | tenant_secret、密钥版本 | 极低频写,换 token 时读 | 独立列(需加密/精确更新) |
| 权限类 | 功能开关 | 低频写,请求链路上读 | JSONB(开关矩阵,无索引需求) |
| 配置类 | 参数、默认值 | 低频写,高频读(带缓存) | JSONB |
| OEM 类 | 主题、名称、logo | 极低频 | JSONB |

> 若某类配置膨胀到需要按 key 索引/审计变更历史,再拆独立表(`xmp_tenant_config` 等)——JSONB 分组列是起步最优,不为未来过度设计。所有 JSONB 列必须 `NOT NULL DEFAULT '{}'` + 评估 GIN 需求(有查询路径才建)。

### 3.2 业务表规范

所有 `xmp_*` 业务表(租户主表除外):

```sql
    tenant_id   BIGINT NOT NULL,          -- 第一等公民列,紧跟 id
    -- 索引/唯一键一律以 tenant_id 打头:
    --   普通:  idx_{表}_{列}
    --   唯一:  UNIQUE (tenant_id, {业务键})     ← 幂等命名空间,跨租户允许同号
```

**代码规约**(数据隔离的生命线):

1. `tenant_id` **只从 token claim 取**,请求 body 里的同名字段一律忽略(防越权注入)
2. 查询收口:tenant_id 过滤在 Service/Query 层单点收口,不允许散落各 Controller
3. 用集成测试守护:租户 A 的数据对租户 B 的 token 不可见(每次涉及新查询路径时补一条)
4. 遵循禁外键铁律:一致性应用层保证,`tenant_id` 是逻辑外键

## 4. 接口三域

| 域 | 前缀 | 鉴权 | 用途 |
|---|---|---|---|
| **平台域** | `/xmp/platform/api/v1/*` | **平台密钥**(PLATFORM 型 token) | 租户 CRUD、密钥重置、功能开关/参数/OEM 下发、跨租户查询、对账运维 |
| **租户域** | `/xmp/api/v1/admin/*`(管理面)<br>`/xmp/api/v1/runtime/*`(运行面) | **租户 AccessToken**(TENANT 型 JWT) | 租户自助管理自己的资源;业务运行时读写 |
| **开放域**(可选) | `/xmp/open/api/v1/*` | 无(按 IP/全局限流 + captcha) | ① 公开数据(产品介绍、公开榜单),**严禁**租户私有数据;② **凭注册码自助注册**(§6.2,唯一允许的写操作;码由平台域签发) |

**三域铁律**:

- **token 型别硬隔离**:平台 token 打租户域 → 401「令牌类型不匹配」,反向同。运营页(平台域)与接入方(租户域)互不可越权
- runtime 面(资金/配额等敏感写操作)可叠加 **HMAC 请求签名**(时间戳 + nonce 防重放),签名密钥注册前全拒(安全默认)
- admin 与 runtime 分开的意义:同凭据不同授权面(可对 admin 面单独限流/审计)

## 5. 凭据与认证

### 5.1 三级凭据

| 凭据 | 归属 | 形态 | 用途 |
|---|---|---|---|
| 平台密钥 | 中间件运营方 | 环境变量/配置中心(`PLATFORM_CLIENT_ID/SECRET`),**不进库表** | 换 PLATFORM token → 平台域 |
| 租户密钥 | 每个租户 | `xmp_tenants.tenant_secret`(AES-256-GCM 加密存储) | 换 TENANT token → 租户域 |
| 用户 ID | 租户的终端用户 | 请求头 `X-User-Id`(字符串,每请求携带,原样透传) | 审计/埋点/限流维度,不做鉴权 |

### 5.2 换取 AccessToken(client_credentials)

```http
POST /xmp/api/v1/auth/token        # 免 token 放行路径(凭据即认证),但限流+审计
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
&client_id={租户 OpenID 或原始 id}
&client_secret={租户密钥明文}
```

响应:

```json
{ "code": 0, "data": {
    "access_token": "eyJhbGciOi...",   // JWT
    "token_type": "Bearer",
    "expires_in": 28800                 // 默认 8h(可配,见下「时效论证」)
} }
```

**JWT claims**(必含 `exp/iat/iss/sub/jti`,安全规范铁律):

```json
{ "sub": "TENANT", "type": "TENANT", "iss": "xmp-backend",
  "tenant_id": "1234567890123456789",   // 数字字符串,解析侧兼容 Number/String
  "iat": 1787700000, "exp": 1787728800, "jti": "uuid" }
```

> **user_id 不进 JWT**(v1.1 修订):用户标识是**请求级**数据,token 是**会话级**凭据——
> token 被服务端缓存复用会给不同用户串号。用户标识走请求头 `X-User-Id` 每次携带,见 §5.4。
> (Stripe 的 `Stripe-Account` 请求头即此模式)

**时效论证**(M2M ≠ 用户态):`access_token ≤ 2h` 铁律针对用户会话;M2M 服务级 token 对齐
AWS STS 口径(1h~12h),本方案默认 **8h、上限 12h**,runtime 等高敏面可配更短。**M2M 无
refresh_token**——续期语义 = 用密钥重新 client_credentials(密钥即长期凭据,token 即临时凭据,
两层分离)。安全兜底不靠短 TTL,靠**可撤销**:token 在 Redis 有会话记录(key 含**应用名前缀**,
多服务共享时应用名必须一致——benefit4j 踩坑),支持滑动续期(auto-renew + increment)、黑名单
失效、以及**密钥重置时批量撤销该租户全部存量会话**(§5.5)。

### 5.3 平台 token(合成租户)与平台域强校验

平台密钥不属于任何库表租户,换 token 时使用**合成租户**(`tenant_id = 0`):

- `tenant_id == 0` 即「平台身份」,仅平台域接口放行
- 平台域查询跨租户数据时不带 tenant_id 过滤(与 §3.2 并冲突的豁免点由下一行保证)

**强约束(v1.1 升级为 P0)**:平台域的可达性必须由**服务端强制**的二选一保证:

| 方案 | 做法 | 适用 |
|---|---|---|
| A. 独立型别(推荐) | 平台 token `type=PLATFORM`,租户 token `type=TENANT`;拦截器按型别硬隔离,互打 401 | 新建中间件,一步到位 |
| B. 单型别 + 零值校验 | 沿用单一 token 型别,但平台域入口**统一校验 claim `tenant_id == 0`**,非 0 一律 403 | 既有系统最小改造(benefit4j 现状采用) |

> ❌ 反例(benefit4j 踩坑):型别隔离只做了一半(APP vs OPS),平台域与租户域同型别且入口不校验
> 零值——**任何租户 token 都能调平台域(管理所有租户、重置任意密钥)**。此为本方案明令禁止的形态。

### 5.4 用户 ID 的语义(必填,不校验,不进 token)

中间件**不建用户档案、不做用户鉴权**。用户标识以请求头携带:

```http
GET /xmp/api/v1/runtime/xxx
Authorization: Bearer {tenant_access_token}     ← 会话级凭据(租户身份)
X-User-Id: u_9f8e7d                              ← 请求级标签(终端用户标识,字符串,必填)
```

存在意义:

- 幂等键的组成部分(同租户同用户同业务号 = 同一笔)
- 审计与限流的细分维度(按租户+用户两级限流)
- 数据归属展示(流水按用户过滤)

三条红线:

1. **必填但永不鉴权**——服务端不得基于 `X-User-Id` 做任何权限判断,权限边界**永远且仅是** `tenant_id`
2. **不进 JWT**——token 是会话级(8h),用户是请求级;进 token 会被服务端 token 缓存复用导致串号
3. **原样透传不校验**——用户的真实性由租户自己的认证体系保证(各租户用户体系异构,校验不了也不该校验)

### 5.5 密钥与签名密钥管理

**租户密钥生命周期**:

| 阶段 | 操作 | 约束 |
|---|---|---|
| 创建租户 | 生成 tenant_secret | 明文**仅返回一次**(创建响应),库内密文存储 |
| 日常 | 换 token 时解密比对 | 列表接口返回脱敏 `••••` |
| 泄露/轮换 | `POST /platform/tenants/{id}/reset-secret` | 新明文仅显一次;**同时批量撤销该租户全部存量会话**(Redis 按 tenant_id 扫描);支持双密钥版本过渡(旧密钥宽限期 N 小时,版本号区分) |
| 停用 | 租户 status=SUSPEND | 换 token 立即拒绝,存量会话即时失效 |

**存储形态二选一**(按是否有「揭示明文」需求):

- **可逆加密 AES-256-GCM**(本方案默认,benefit4j 采用):支持运维重置后的明文揭示端点;密钥本身走 **KMS / 环境变量注入,禁硬编码、禁进代码库**
- **不可逆 hash**(更安全,若无揭示需求):存储 `HMAC-SHA256(kdf_secret, tenant_secret)`,比对时重算——密钥泄露也拖不出明文

**JWT 签名密钥**:≥256 位,环境变量注入(如 `JWT_SECRET`),生产禁默认值;轮换时双密钥并行验证宽限期。

## 6. 租户接入流程(双通道)

### 6.0 租户生命周期状态机

```
                 通道A: 运营创建(直接 ACTIVE)
   ┌────────────────────────────────────────────┐
   │                                            ▼
 凭注册码(§6.2)/ 运营创建(§6.1)──► ACTIVE ◄──► SUSPEND(停用/注销冷静期)
                                        │            │
                                        │   ── 复活(冷静期内)
                                        └── 注销(冷静期30天)──► CLOSED(§6.3)
   (PENDING/SANDBOX 为对外 SaaS 场景预留,内部形态不经过,见下注)
```

- **SANDBOX**:注册即得,零审批。约束 = 更严限流 + 低配额 + 部分 runtime 能力关闭(按 privileges 开关)——**用配额代替审批做风控**(Stripe test mode / AWS sandbox / OpenAI free tier 同款)
- **ACTIVE(PRODUCTION)**:补验证后升级;运营创建的大客户跳过 SANDBOX 直接 ACTIVE
- **PENDING**:自助注册后邮箱验证前,不可换 token
- **CLOSED**:注销终态(§6.3),OpenID 永久退役

### 6.1 通道 A · 平台运营创建(大客户 / 内部系统)

```
 平台运营方                 xmp 中台                    租户(接入方)系统
     │                        │                              │
     │ ① 创建租户(平台密钥)     │                              │
     ├───────────────────────►│ 生成 id + tenant_secret       │
     │◄───────────────────────┤ (明文仅此一次)                │
     │ ② 配置开关/参数/OEM      │                              │
     ├───────────────────────►│                              │
     │◄───────────────────────┤                              │
     │        ③ 线下发 secret(安全渠道,自此中台不再出明文)     │
     │───────────────────────────────────────────────────────►│
     │                        │  ④ 换 token(secret) │
     │                        │◄─────────────────────────────┤
     │                        │  access_token(8h)            │
     │                        ├─────────────────────────────►│
     │                        │  ⑤ Bearer token 调租户域      │(管理/运行)
     │                        │◄─────────────────────────────┤
```

### 6.2 通道 B · 凭注册码自助注册(密钥线上发放)

> **v2.1 场景修正**: 本方案面向**中间层**(内部/半开放生态),不对外运营 —— 没有公网垃圾注册面,
> 邮箱验证/captcha/SANDBOX 分级那套 SaaS 自助注册前提不成立(v1.2 设计,已被 D14 取代)。
> 信任由「**发码**」这个线下动作前置:平台运营方定向发注册码,消费方凭码线上自助开户领密钥。

**信任分级**(平台运营方对消费方的授权策略):

| 级 | 信任度 | 发放物 | 语义 |
|---|---|---|---|
| L1 | 100% 信任(与平台同安全域,如兄弟团队/同一责任主体) | **平台密钥** | 等于平台身份,全域权限 —— 发放即审计;仅限与平台共担安全责任的主体 |
| L2 | 信任(常规接入方) | **注册码** | 定向、限量、可撤销的开户凭证 —— 本节主流程 |
| L3 | 不信任/临时/观察 | 不发码,走通道 A | 运营创建 + 观察期,资料与用途人工审核 |

```
 平台运营方                xmp 中台                     消费 APP(接入方)
     │ ① 生成注册码(平台域,平台密钥)                    │
     ├───────────────────────►│ 码属性: 可用次数(默认1)/ │
     │◄───────────────────────┤ 有效期/预绑配置档         │
     │   {registration_key}   │ (privileges/config 模板) │
     │                        │                          │
     │ ② 线下安全渠道发码( IM/密码管/当面 )                │
     │───────────────────────────────────────────────────►│
     │                        │ ③ POST /xmp/open/api/v1/tenants/register
     │                        │    {registration_key, name}
     │                        │◄─────────────────────────┤
     │                        │ 校验: 码存在/未用尽/未过期 │
     │                        │ (Redis 原子扣减次数)      │
     │                        │ 创建租户 status=ACTIVE,   │
     │                        │ 注入码上预绑的配置档       │
     │                        │─────────────────────────►│
     │                        │   响应(仅此一次):          │
     │                        │   {tenant_open_id,        │
     │                        │    tenant_secret}         │
     │                        │    ← 明文只在此响应,不落日志│
     │                        │                          │
     │                        │ ④ 用 secret 换 token(同 §5.2)→ 正常调租户域 │
```

**通道 B 铁律(v2.1)**:

1. **码即信任**:注册码由平台域生成(平台密钥鉴权),属性 = 可用次数(默认 1,一次性)/有效期(默认 24h)/预绑配置档(privileges+config 模板,发码时定档,**配额在码上**而非状态分级)
2. **凭码即 ACTIVE**:信任已由发码前置,注册成功直接 ACTIVE,无 PENDING/SANDBOX 中间态(该两态保留为对外 SaaS 场景预留,见 §6.0 注)
3. **原子扣减**:注册码校验与次数扣减必须原子(Redis Lua / `DECR` 后判负回滚),防并发重放薅码
4. **明文只显一次**:secret 仅在注册响应返回一次,不落日志;注册码本身也按敏感凭据管理(发码审计、可吊销)
5. **兜底防线**:开放域仍需 IP 级限流 + 全流程审计(发码/用码/发放 secret 的 IP+UA)—— 码泄露的爆炸半径 = 剩余次数 × 有效期,吊销即时止损

### 6.3 租户注销与数据处置(生命周期的最后一环)

> 10 年生命周期的系统必须回答「租户走了怎么办」;缺此节的多租户设计都是不完整的。

**注销流程**(平台域操作,须二次确认 + 审计):

```
SUSPEND(冷静期,默认 30 天,可配)
  ├─ 撤销全部存量会话;换 token 拒绝;runtime 拒绝写(读可留)
  ├─ 租户可自助申请数据导出(平台域按租户切片导出,§8 #12)
  └─ 冷静期内可复活(恢复 ACTIVE,数据原样)
        │ 冷静期满(无复活)
        ▼
CLOSED(注销终态)
  ├─ 软删:is_deleted=1(主表行保留,供审计追溯与 OpenID 防重用)
  ├─ 业务数据处理按租户约定的保留期(默认 90 天,可配):到期物理删除或脱敏归档
  ├─ OpenID 永久退役(不复用,防新租户继承旧身份)
  └─ 注销证明入审计(hash chain,不可篡改)
```

**规则**:

1. **OpenID 与 tenant_id 永不复用**——新租户新号;幂等键命名空间永久退役
2. 资金/账本类数据默认**归档不删**(合规要求,保留期按业务监管口径);非账本数据按约定可物理删
3. 有对账未平(差错池非空)的租户**禁止进入 CLOSED**——先清账
4. 注销全程状态机化、可审计、可追溯;「删除」永远不是 API 的直接效果,而是状态机 + 定时处置

**接入 checklist**(租户视角,两通道通用):

**接入 checklist**(租户视角):

1. 拿到 `tenant_id(OpenID)` + `tenant_secret`(一次性明文),安全存储(接 KMS/环境变量,勿进代码库)
2. 实现 token 获取与过期刷新(expires_in 提前 5min 续)
3. 每次业务调用带 `X-User-Id` 请求头(终端用户标识,字符串,必填)
4. 业务号(ext_order_id 等)保证**租户内唯一**并永久不复用(幂等键 = tenant_id + 业务号;失败重试请**换新号**,同号重放返回首次结果)
5. 4xx 语义对齐:`401` 凭据/型别问题、`409` 幂等冲突、`402/403` 额度/权限、`429` 限流

## 7. 前端控制台与页面嵌入(OEM 白标)

中间件自带运营控制台(平台端)与租户控制台(租户端);租户可把控制台页面 **iframe 嵌入自己的系统**,并按 `xmp_tenants.oem` 配置白标化。本节为通用嵌入方案(benefit4j 已全量实现,见 §9 对照)。

### 7.1 嵌入形态:两布局 × 两视角 = 四入口

| 布局 | 路由前缀(租户端) | 形态 | 适用 |
|---|---|---|---|
| **完整 app** | `/xmp/tenant/app/*` | 带侧栏/导航 | 深度操作,用户在嵌入视图内可跳转 |
| **单页 page** | `/xmp/tenant/page/*` | 无 header/aside,纯内容 | iframe 嵌单个看板/列表 |

平台端同理(`/xmp/platform/app|page/*`)。**View 组件完全复用,仅外层 layout 不同**——单页模式 = 同一组页面 + 不同壳,零组件复制;`/page/*` 与 `/app/*` 走同一登录守卫(受保护路由,无特权差异)。

### 7.2 外观参数与 OEM 联动

URL query(两布局通用):

| 参数 | 值 | 作用 |
|---|---|---|
| `brand` | 品牌 id 枚举 | 色调品牌(对接 `oem.theme` / 品牌表) |
| `mode` | `light`/`dark` | 明暗 |
| `language` | `zh-CN`/`en-US` 等 | 强制 locale |

**优先级链**:`query > postMessage > OEM(按 host 匹配 xmp_tenants.oem) > localStorage 持久化 > 内置默认`。

- OEM 配置(§3.1 `oem` JSONB)按**宿主域名**匹配——同一套控制台,租户 A 域名下自动呈现租户 A 的品牌/标题/logo,即白标
- **嵌入态不污染持久化**:query 触发的外观变更只写内存不写 localStorage(否则 iframe 嵌一次就污染用户直访时的偏好)
- **参数白名单校验**(防 query 注入):`brand/mode/language` 的值必须先过枚举白名单,非法值静默丢弃走默认——这些值最终写入 DOM 属性(`data-theme`)与全局 store,是 XSS/属性注入的入口,禁原样透传

### 7.3 认证两级(第三方自选,中台同时支持)

| | 基础级:URL 带 token | 推荐级:postMessage 握手 |
|---|---|---|
| token 传递 | `?access_token=...` | 握手后内存持有 |
| token 暴露面 | **7 个**(URL/访问日志/Referer/历史/书签/代理/浏览器扩展) | **1 个**(iframe 内存) |
| 持久化 | 写 **sessionStorage**(iframe 会话内 SPA 跳转复用;iframe 刷新后由 URL 重新解析——URL 仍带 token) | 不写,关 tab 即销毁(内存) |
| 集成复杂度 | 最低(拼 URL) | 中(双方各 ~30 行 JS) |
| 适用 | 内网/低敏/快速验证 | 公网/高敏/生产 |

**推荐级握手时序**:

```
 父页(租户系统)                iframe(中台控制台)
     │ ① <iframe src="https://xmp.example.com/xmp/tenant/page/dashboard?brand=acme">
     │ ─────────────────────────────►│ URL 不含 token
     │                    ② postMessage({type:'XMP_READY'})  iframe 就绪通告
     │ ◄─────────────────────────────│
     │ ③ 向自己后端取短期 token(≤1h,后端用租户密钥换)
     │ ④ postMessage({type:'XMP_TOKEN', token}, targetOrigin)
     │ ─────────────────────────────►│ 校验 origin 白名单后入内存
     │                    ⑤ token 过期前父页主动续发新 token
```

要点:iframe 校验 `event.origin` 必须命中租户域名白名单(`xmp_tenants.oem.hosts`);父页 `targetOrigin` 写明确origin 禁 `*`;token 全程不走网络、不落 URL。

### 7.4 跨域与安全要求

| 维度 | 要求 |
|---|---|
| **frame 嵌入白名单** | 后端响应 `Content-Security-Policy: frame-ancestors <租户域> <自家域>`(禁全局 `X-Frame-Options: ALLOWALL`);按租户 OEM hosts 下发 |
| CORS | `Access-Control-Allow-Origin: <租户域>`;token 在 Header 无 cookie,`credentials=false` |
| 基础级泄漏缓解 | iframe 加 `referrerpolicy="no-referrer"`;网关访问日志过滤 `access_token=`;token TTL ≤1h;监控(Sentry)beforeSend 过滤 query |
| 存储隔离 | 跨域 localStorage 互不可见——嵌入态 token 推荐只持内存(推荐级天然满足) |
| 短期 token | 嵌入专用 token 由**租户后端**用密钥代换(§5.2),TTL ≤1h,与控制台登录 token 同型别同权限 |

### 7.5 与租户模型的联动总览

```
xmp_tenants.oem (JSONB)          嵌入 URL query
  ├─ theme/brand   ◄───────────  ?brand=acme&mode=dark&language=zh-CN(query 优先)
  ├─ title/logo    ────────────► 控制台标题/logo/浏览器 favicon
  └─ hosts[]       ────────────► frame-ancestors 白名单 + postMessage origin 校验
```

OEM 是租户表四类配置中「对外呈现」的一类;嵌入方案是它的**运行时出口**——配置驱动、零代码白标。

## 8. 安全设计清单

| # | 项 | 方案 |
|---|---|---|
| 1 | 数据隔离 | §3.2 四规约(token 取值 / 查询收口 / 测试守护 / 逻辑外键) |
| 2 | 隔离双保险 | 资金/账本类表启用 **PG RLS**:`policy (tenant_id = current_setting('app.tenant_id')::bigint)`,连接层按请求 SET——应用层忘加过滤时 DB 层兜底漏不出数据(Salesforce 引擎强制的 PG 对应物) |
| 3 | 越权防御 | 平台域强校验(§5.3 A/B 二选一,明令禁止同型别+不校验零值的形态)+ body 的 tenant_id 一律忽略 + `X-User-Id` 永不鉴权 |
| 4 | 密钥存储 | AES-256-GCM 密文(或无揭示需求时不可逆 hash)+ 明文只显一次 + 脱敏返回 + 轮换版本化 + reset 撤销存量会话;加密密钥与 JWT secret 走 KMS/env,禁硬编码 |
| 5 | 敏感操作 | runtime 面 HMAC 签名:HMAC-SHA256(secret, method+path+timestamp+nonce+body_md5),时间戳 ±5min、nonce Redis 10min 防重放,比对用 **`MessageDigest.isEqual` 常量时间比较**(禁 `equals`);签名密钥未注册=全拒(安全默认);错误码区分 缺失/过期/重复/无效 四态 |
| 6 | 限流 | 按租户维度(claim tenant_id)滑动窗口;开放域按 IP;换 token 端点单独限流(防密钥爆破) |
| 7 | 换 token 防爆破 | 失败 N 次锁定(租户级 Redis 计数),审计全部失败尝试 |
| 8 | 幂等 | UNIQUE(tenant_id, 业务键) 是真闸;Redis 幂等层只是快路 |
| 9 | 审计 | 平台域全部写操作 @Auditable 落审计表:**append-only(DB 权限 REVOKE UPDATE/DELETE)+ 每日 hash chain 防篡改**;最小字段集:`trace_id / 操作者(tenant_id+user_id) / action / target_type / target_id / before_value / after_value(JSONB) / result / ip / user_agent / created_at`;密钥操作与自助注册全流程必审计 |
| 10 | SQL | 一律参数化占位符,禁字符串拼接(禁 `${}`) |
| 11 | 运营控制台前端 | token 存 **sessionStorage**(禁 localStorage);路由守卫双层;按钮显隐是体验层,后端独立校验;JWT Header 模式无 CSRF 面 |
| 12 | 对账 | 平台域提供按租户切片的对账/导出接口(租户数据自主可核) |
| 13 | 自助注册防线 | §6.2(注册码模式):码原子扣减防重放 + 一次性 secret 不落日志 + 发码/用码全审计 + 开放域 IP 限流;码泄露爆炸半径 = 剩余次数 × 有效期,吊销即时止损 |

## 9. benefit4j 参考实现对照

本方案全部条目在 [benefit4j](../)(用户权益中台)有生产级实现与测试守护,落地时可直接参考:

| 本方案(xmp) | benefit4j 实现 |
|---|---|
| `xmp_tenants` 四类配置一表 | `ubma_tenant`(name/status/tenant_secret 加密/ext);开关/参数在业务表列(can_* 矩阵) |
| 双通道接入(§6) | benefit4j 仅通道 A(运营创建);**通道 B 注册码模式为后续项**(信任分级:兄弟团队可暂用平台密钥直发=L1) |
| `tenant_id` BIGINT + 唯一键打头 | 全部 `ubma_*/ubmx_*` 表;`UNIQUE(tenant_id, ext_order_id)` 幂等闸 |
| 三域接口 | `/benefit/api/v1/platform/*`(APP 型,平台登录)| `/benefit/api/v1/tenant/*` + `/runtime/*`(APP 型)| ops 运维通道(OPS 型,运维签发) |
| 平台域强校验(§5.3 方案 B) | **已落地(2026-08-28)**: `PlatformIdentityGuard`(两平台域 controller @ModelAttribute 入口校验 tenant_id==0,非 0 → 403)+ smoke 租户 token 双端点 403 回归 |
| client_credentials 换 token | `POST /benefit/api/v1/auth/token`,claims `tenant_id`,2h(本方案默认放宽到 8h,M2M 口径) |
| user_id 请求头透传不鉴权 | benefit4j 用请求参数(userid)——与 `X-User-Id` 请求头语义等价 ✓;`userid`(字符串三方 ID)与 `user_id`(bigint 雪花)是**两个概念**,别合并 |
| 合成平台租户 tenant_id=0 | `DefaultBenefitAuthService#syntheticPlatformTenant` |
| OpenID 不泄内部 id | `IdObfuscator` Base62 + 校验位 |
| 密钥明文只显一次 | `reset-secret` / `secret` 端点,列表脱敏 `••••` |
| HMAC 签名 + 全拒默认 | framework4j-signature,`/runtime/**` + `/assets/runtime/**` |
| 按租户限流 | framework4j-rate-limit,default-scope 按租户 claim |
| 审计 | framework4j-audit `@Auditable` → `ubmp_audit_log` |
| 租户注销链路(§6.3) | benefit4j 未实现(仅 SUSPEND 停用);**注销冷静期/CLOSED/OpenID 防重用为待落地项** |
| 密钥轮换宽限期(§5.5) | **已落地**: `tenant_secret_prev` 双版本列,默认 24h 两把皆可换 token,过期懒校验 |
| reset 撤销存量会话(§5.5) | **已落地**: 按 framework4j 会话 key 结构删 APP/OPS 全部 key |
| 换 token 防爆破(§8#7) | **已落地**: 连续失败 5 次锁 15min(429),成功清零 |
| RLS 双保险(§8#2) | **策略已就位**(ENABLE 不 FORCE): 账本六表 tenant_isolation 策略;FORCE 待连接层切非 owner 角色 |
| 控制台 token sessionStorage(§8#11) | **已落地**: token/tenant_id/secret/expires 四 key 全迁 sessionStorage(2026-08-28) |
| **前端嵌入(§7 全套)** | [嵌入接入指南](./嵌入接入指南.md):`/benefit/{platform\|tenant}/{app\|page}/*` 四入口、`useEmbedParams`(brand/mode/language)、`useEmbedToken`(postMessage 握手)、OEM 按 host 白标、`PageLayout` 单页壳;嵌入文档页 `EmbedDocs.vue` |

**benefit4j 踩过并已写进本方案的坑**:

1. 登录端点只发一种 token 型别 → 运营页 401(平台运营页面与租户接入方需要**各自可达的 token 通道**,见 §5.3)
2. it/app 两处配置 claim 键名不一致(`appid` vs `app_id`)→ 被迫双键兼容;通用方案统一 `tenant_id`
3. Redis 会话 key 含应用名 → 测试与生产应用名不一致时会话互不可见
4. 术语分裂(app vs tenant 并存)→ 2026-08-28 完成 app→tenant 彻改,本方案即为该结果的抽象

## 10. 落地检查清单

新中间件项目按序勾选:

- [ ] `xmp_tenants` 建表(id 即租户 id,四类配置列,AES-GCM secret 或 hash)
- [ ] 业务表 `tenant_id NOT NULL` + 唯一键打头,查询收口 + 越权测试
- [ ] (资金/账本表)PG RLS 双保险 + 连接层 SET tenant_id
- [ ] 三域路径 + 平台域强校验(§5.3 方案 A 独立型别 或 B 零值校验)
- [ ] `POST /api/v1/auth/token`(client_credentials;claims 含 iss/jti;默认 8h 上限 12h;M2M 无 refresh)
- [ ] `X-User-Id` 请求头(必填、透传、永不鉴权、不进 JWT)
- [ ] 密钥:明文一次 + 脱敏 + reset-secret(撤销存量会话)+ 轮换版本宽限期;加密密钥/JWT secret 走 KMS/env
- [ ] OpenID 编解码(内部 id 不出网)
- [ ] 限流(租户维度)+ 换 token 防爆破锁定 + 审计(append-only + hash chain)+ 幂等唯一键
- [ ] (可选)开放域:确认无私有数据泄露路径、永不写
- [ ] (可选)runtime 签名:常量时间比较、四态错误码、密钥未注册 = 全拒
- [ ] 运营控制台前端:token sessionStorage、路由守卫、后端独立校验
- [ ] (可选,需被嵌入)前端嵌入四入口(app/page × 平台/租户)+ 外观参数 + OEM 按 host 白标;认证两级自选,生产用 postMessage 握手;frame-ancestors 白名单按租户 oem.hosts 下发
- [ ] (可选)凭注册码自助注册:平台域发码(次数/有效期/预绑配置档)+ 开放域 register 原子扣减 + 一次性 secret 发放 + 码可吊销;信任分级 L1 平台密钥/L2 注册码/L3 通道 A
- [ ] 租户注销链路:SUSPEND 冷静期 → CLOSED,OpenID 永不复用,对账未平禁注销(§6.3)
- [ ] 文档:接入指南(§6 checklist)+ 配置键全链命名一致

## 11. 关键设计决策记录(ADR 摘要)

评审高分文档须回答「为什么不是别的」——所有关键取舍汇总于此,细节见对应章节:

| # | 决策 | 替代方案(被否原因) | 依据 |
|---|---|---|---|
| D1 | **表 id 即租户 id**,租户表自身无 tenant_id | 加自指 tenant_id(冗余且语义歧义) | §3.1 租户是本体不隔离自己 |
| D2 | **四类配置一张表**(安全列 + 三类 JSONB) | 每类一张子表(起步过度设计);全塞 ext(安全列需加密/精确更新,不可混) | §3.1 读写特征表;膨胀再拆 |
| D3 | **token 默认 8h、无 refresh_token** | 2h+refresh(用户态铁律,对 M2M 过敏);永久 token(不可撤销) | §5.2 M2M≠用户态论证;密钥即长期凭据,token 即临时凭据,撤销兜底 |
| D4 | **user_id 走 X-User-Id 请求头,不进 JWT** | 进 claim(token 会话级 vs 用户请求级,缓存复用串号);不传(审计/限流/幂等需要) | §5.4;Stripe-Account 同模式 |
| D5 | **user_id 必填但永不鉴权** | 校验归属(中间件无用户档案,租户用户体系异构,校验不了也轮不到) | §5.4 三条红线;权限边界永远仅 tenant_id |
| D6 | **平台域强校验 A/B 二选一,禁止同型别裸奔** | 单型别+不校验(= benefit4j 越权缺口,任何租户 token 可管理全部租户) | §5.3 P0;反例即踩坑实录 |
| D7 | **合成平台租户 tenant_id=0** | 平台凭据入租户表(平台不是租户,污染模型);独立认证体系(多一套机制) | §5.3 平台密钥不进库表 |
| D8 | **secret 可逆加密(AES-256-GCM)为默认** | 一律不可逆 hash(本项目有明文揭示端点需求);明文(违规) | §5.5 二选一按需求定 |
| D9 | **SANDBOX 分级开通,配额代替审批** | 注册即全量(滥用面);人工审批(不可扩展,接入摩擦) | §6.2 v1.2;**v2.1 被 D14 取代**(前提不成立于内部中间件) |
| D10 | **验证通过前不发密钥** | 注册即返回(垃圾注册批量薅密钥) | §6.2 v1.2;**v2.1 被 D14 取代** |
| D11 | **嵌入认证两级自选**,生产推荐 postMessage** | 只留 URL token(暴露面 7 个:URL/日志/Referer/历史/书签/代理/扩展);只留 postMessage(内网快速验证不便) | §7.3 对照表 |
| D12 | **OpenID 永不复用(含注销后)** | 复用号段(新租户继承旧身份与幂等历史,审计断裂) | §6.3 |
| D13 | **CLOSED 前置冷静期 + 对账未平禁注销** | 直接删(误操作不可逆;资金域悬账) | §6.3 |
| D14 | **通道 B = 注册码模式,凭码即 ACTIVE**(取代 D9/D10 的邮箱验证+SANDBOX 分级) | 邮箱验证/captcha(对外 SaaS 前提,内部中间件无公网注册面);平台密钥直发所有人(越权面失控) | §6.2 v2.1;信任由发码动作前置(线下),线上只做原子校验;信任分级 L1 平台密钥/L2 注册码/L3 通道 A |

**10 年稳定性视角的兜底判断**:本方案所有决策均满足「原则层不依赖特定厂商行为、参数层全部可配、适配层可等价替换」(§1.0 三层模型)——这是文档敢承诺长期有效的结构性原因,而非依赖某家大厂 2026 年的做法不变。
