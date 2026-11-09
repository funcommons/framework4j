# framework4j SDK（统一技能 · 单源 · 按需加载）

> [!TIP] 安装本 Skill（AI 编码工具接入）
>
> 把下面的提示词发给你的 AI 编码工具（Claude Code / Cursor / Cline 等）即可安装：
>
> ```text
> 下载并安装 SKILL 到项目中 https://funcommons.github.io/framework4j/skills/fwk4j-skill.zip
> ```
>
> 或手动下载 [fwk4j-skill.zip](/skills/fwk4j-skill.zip)，解压到工具的技能目录
> （Claude Code：`~/.claude/skills/` 或项目 `.claude/skills/`），目录名保持 `framework4j`。


Spring Boot 3.5 / Java 17 企业 SDK，16 个独立 starter + 可选租户模块。
本文件是**唯一常驻入口**；模块细节在 `references/`，**命中场景时才读取对应文件，禁止一次性全读**。

## 1. 架包下载

### 方式一：JitPack（免安装仓库，推荐第三方）

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<!-- 全家桶（16 starter 聚合，不含 tenant） -->
<dependency>
    <groupId>com.github.funcommons.framework4j</groupId>
    <artifactId>framework4j-all</artifactId>
    <version>v1.9.0</version>
</dependency>
<!-- 或按需单个 starter：把 artifactId 换成下表任意一个 -->
```

### 方式二：源码本地 install（groupId 变为 `fun.commons`）

```bash
git clone https://github.com/funcommons/framework4j.git && cd framework4j
mvn -DskipTests install
```

### 模块速查（artifactId → 文档）

| artifactId | 用途 | 激活 | 文档（按需读取） |
|---|---|---|---|
| framework4j-api | ApiCode 错误码契约 | 随 web | `references/api.md` |
| framework4j-web | ApiResponse 信封 + 全异常 HTTP 200 + trace_id 链路 | 默认 | `references/web.md` |
| framework4j-datetime | OffsetDateTime 序列化 + 时间转换器 | 默认 | `references/datetime.md` |
| framework4j-id | Snowflake + OpenID 混淆 | **显式开（v1.8）** | `references/id.md` |
| framework4j-redis | 多 Redis + @RedisOn | 显式开 | `references/redis.md` |
| framework4j-datasource | 多 DataSource + @DataSourceOn | 显式开 | `references/datasource.md` |
| framework4j-sql-tracing | SQL 语句 trace_id 前缀 | 默认 | `references/sql-tracing.md` |
| framework4j-accesstoken | JWT + Refresh family + 角色鉴权 | 显式开 | `references/accesstoken.md` |
| framework4j-idempotency | Idempotency-Key 幂等 | 默认 | `references/idempotency.md` |
| framework4j-signature | HMAC 签名防重放 | 默认 | `references/signature.md` |
| framework4j-rate-limit | Lua 滑动窗口限流 | 默认 | `references/rate-limit.md` |
| framework4j-cache | 多级缓存（L1+L2+单飞） | 默认 | `references/cache.md` |
| framework4j-audit | @Auditable + Hash Chain | 默认 | `references/audit.md` |
| framework4j-sensitive | 脱敏 + AES-256-GCM 加密 | 默认 | `references/sensitive.md` |
| framework4j-transport | RestTemplate/WebClient 切换 | 默认 | `references/transport.md` |
| framework4j-tracelog | 运行时日志 Redis 采集 + 动态提权 | 显式开 | `references/tracelog.md` |
| framework4j-tenant | 多租户（不在 all 内） | 显式开 | `references/tenant.md` |

「显式开」= 需在 application.yml 设 `framework4j.<feature>.enabled=true`（v1.8 起 id/openid 同样；
其余默认激活，设 false 可关）。每个模块的完整配置键见 `references/<module>.md` 与 docs 站
`docs/config/reference.md`。

## 2. 文档按需加载（本节即机制）

- **先读本文件判断场景 → 只读命中的 `references/<module>.md`**（每文件 ≤100 行精华）。
- 跨模块问题（如「登录+签名+审计完整链路」）读涉及的多个文件，各自独立。
- 在线文档（离线降级）：`https://github.com/funcommons/framework4j/tree/main/docs`。
- 设计文档不在 references 内：深入实现细节看仓库 `docs/modules/` 与各模块目录。

## 3. 跨模块硬规范（任何模块组合都适用）

1. **响应信封**：Controller 返回 `ApiResponse<T>`（code/message/data/error/trace_id/timestamp）；
   失败 `data` 必须 null。错误码 5 位分段（ABCCC，格式见 `references/api.md`）。
2. **HTTP 200 契约**：`GlobalExceptionHandler` 把一切异常翻译成信封并返回 HTTP 200
   （未知系统异常除外 → 500/10001）。业务代码 throw 即可，无需 try/catch 包装。
3. **JSON**：全局 snake_case + `Long → String`（防 JS 精度丢失）。VO 字段命名用驼峰，线上自动转。
4. **`spring.application.name` 必配**：accesstoken / id(redis 策略) / tracelog 的 Redis key 前缀依赖它。
5. **配置键**：一律 `framework4j.<module>.*`；唯一 kebab-case 例外是 `framework4j.access-token.*`
   （不是 `accessToken`）。
6. **v1.8 显式启用**：`framework4j.id.enabled` / `framework4j.openid.enabled` 缺省不激活（issue #26）；
   redis/datasource/accesstoken/tenant/tracelog 历史上就是显式开。
7. **token 双信任**：服务端只信 Redis 会话 claims（可撤销/热更 `updateClaims`）；
   JWT payload 的嵌套 `claims` 键仅供持有方自助解码（`embed-claims` 开关）。
8. **trace_id 链路**：`X-Trace-Id` 头入站自动采纳（16~64 位白名单校验）、响应自动回显、
   MDC key 经 `framework4j.api.trace.mdc-key` 可配（`references/web.md` §Trace）。

## 4. 新项目接入 checklist

1. 引 `framework4j-all`（或按表挑 starter）；配 `spring.application.name`
2. 起 Redis（localhost:6379 或多源 `framework4j.redis.datasources.*`）；开需要显式启用的模块
3. 需要鉴权 → `framework4j.accesstoken.secret-key`（≥32 字符）+ policies（`references/accesstoken.md`）
4. Controller 返回 `ApiResponse`；错误码复用 `ApiCode`，不够再扩枚举（ABCCC 格式）
5. 日志 pattern 加 `%X{traceId:-}`；监控契约不同则改 `mdc-key`
6. 遵守所在仓库的 mc-java-spec / mc-api-spec 规范（本 skill 只管 SDK 行为）
