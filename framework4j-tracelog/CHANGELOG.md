# Changelog

本模块遵循 [Semantic Versioning](https://semver.org/)，与 framework4j 全 reactor 统一版本。

## [1.4.0] - 2026-08-26

### Added

- **敏感字段脱敏**（`SensitiveLogMasker`）：采集进 Redis 前按 key 匹配脱敏，值替换 `******`。
  覆盖两种形态——日志体内 JSON 字段（`"password":"x"`，含 message 里转义嵌套的
  `\"password\":\"x\"`）与 message 内 kv（`password=x` / `token: x`）。Worker 线程执行，
  业务线程零开销；`collection.mask-sensitive` 默认开启，`collection.mask-keys` 可配
  （默认 password/passwd/pwd/token/access_token/refresh_token/authorization/secret/
  api_key/apikey/cookie/set-cookie）。与 framework4j-sensitive 的按值格式脱敏互补，
  零跨模块依赖。
- **4 个运行链路集成测试**（`TraceLogRuntimeIntegrationTest`，真实 Redis）：
  多节点写同一 traceId 聚合 + 队列仅 1 条 / resync Redis→cache diff 精准失效 /
  Streams XADD→消���者组→缓存更新→key 删除失效全生命周期 / 停机 drain 未满批日志
  全部落 Redis（flushed == appended）。

## [1.3.3] - 2026-08-26

### Fixed

- **多节点重复入队全局队列**（集成测试锁定）：`flushBatch` 原先 pipeline 盲发 SETNX + Lua，
  SETNX 返回值无从消费，Lua 每次都 RPUSH —— 多节点写同一 traceId 会重复入队
  `trace_global_queue`。修复：SETNX 判定移入 Lua 脚本内部（`KEYS[3]`），原子判定且不加 RTT。
  （`TraceLogStoreIntegrationTest#setnxFirstTime`：5 次重复 flush 队列长度必须 = 1）
- **周期重拉瞬时空窗**：`SwitchResyncScheduler` 原先 `clear() + 重放`，每 5s 制造一次
  缓存空窗，提权请求周期性 miss。修复：`SwitchRuleCache#replaceAll` diff 合并
  （新规则先 put、仅精准 invalidate 失效项）。并发读 0 miss 有单测锁定
  （`SwitchRuleCacheReplaceAllTest#noMissWindowUnderConcurrentRead`，8 读线程 × 50 轮替换）。
- **控制台/导出字段空白**：查询与 txt 导出只认文档 schema 字段名（`logger`/`thread`/`tsIso`），
  而 LogstashEncoder 默认输出 `logger_name`/`thread_name`/`@timestamp` —— 控制台时间/线程/类名
  全空。修复：读侧双字段名兼容 + `@timestamp` ISO 解析补齐毫秒 ts。
- **控制台 404**：静态资源实际路径 `/tracelog/index.html`，文档承诺的 `/tracelog.html` 无路由。
  修复：`TraceLogWebMvcConfig#addViewControllers` 按 `console.path` forward 转发。

### Changed

- 集成测试 Redis 来源升级：嵌入式 Redis（16380）启动失败时自动回退本机 Redis（localhost:6379），
  连接 **db 15** 并用 `flushDb`（只清测试库，绝不触碰业务库 0）。本机有 Redis 时
  5 个集成用例从"跳过"变"真正执行"。

### Verified（端到端冒烟，demo + docker redis:7.2）

启动装配 / traceId 聚合 / 带横线 UUID 查询 / 开关提权（DEBUG×2+TRACE×1 采集，对照 INFO×2）/
控制台 200 / gzip 导出 / 应用重启后开关持久生效 —— 7/7 通过（详见技术方案 §十三）。

测试：**45 通过 / 0 失败 / 0 跳过**（单元 40 + 集成 5）。

## [1.3.2] - 2026-08-25

### Fixed（benefit4j 实测发现）

- `TraceLogSwitchInterceptor` 注册路径反了：只在 `/api/logs/**` 生效，提权标记注入不到业务
  请求 → 改拦 `/**`（排除 tracelog 自身 API/静态页/actuator）
- `SwitchRule.type` 大小写不归一：开关存 `URL` 匹配侧查 `url` 永不命中 → 构造/setter 统一小写
- `AsyncRedisLogAppender.flushIfDue` 无调用点：攒不满批日志永不落 Redis → ScheduledExecutor
  按 flush-interval 驱动 + 停机 drain 后残留 flush
- `TraceLogStore.buildLogKey` 不归一化 traceId：写入 32-hex、查询带横线 UUID 永远查不到
- `SwitchResyncScheduler` @Scheduled SpEL 配置键 `switch` → `sync`

### Changed

- TurboFilter / Appender 改为 `TraceLogBeansConfig` 编程式注册（logback-spring.xml 声明会因
  无无参构造启动失败）
- framework4j-web 新增 `TraceIdMdcAutoConfiguration`：无 Micrometer Tracing 时请求入口生成
  UUID 写入 MDC
- `StringRedisTemplate` Bean 改名 `traceLogStringRedisTemplate`（避免与 Spring Boot 同名 Bean 冲突）

## [1.3.1] - 2026-08-24

### Fixed

- v1.3.0 两个接入阻断 bug（详见 git log `6eb21fa`）

## [1.3.0] - 2026-08-24

### Added

- 首个完整实现：动态提权（TurboFilter）/ 分片 Disruptor 异步采集 / Redis 聚合 + Lua 容量管控 /
  Pub/Sub + Streams 开关同步 / 查询-开关-导出 API / 纯 HTML 控制台 / 多租户 / Micrometer 指标

## [1.2.9] - 2026-08-24

### Added

- 模块骨架 + 设计文档（技术方案 v1.0 → v1.1 评审整合）
