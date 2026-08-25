# 配置参考

## 全模块配置速查

### framework4j.web

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否启用 Web 层契约 |

### framework4j.access-token

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `false` | 是否启用（opt-in） |
| `redis-name` | `default` | Redis 数据源名 |
| `secret-key` | — | JWT 签名密钥（≥32 字符） |
| `hash-salt` | — | Redis key hash 盐 |
| `expire-time` | `3600` | 默认过期秒数 |

### framework4j.signature

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否启用 |
| `timestamp-tolerance-ms` | `300000` | 时间戳容忍（±5min） |
| `nonce-ttl-seconds` | `600` | nonce 防重放 TTL |
| `redis-name` | `default` | Redis 数据源名 |

### framework4j.rate-limit

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否启用 |
| `default-limit` | `100` | 窗口内最大请求数 |
| `default-window` | `1m` | 窗口大小 |
| `default-scope` | `ip` | ip/user/app/global |
| `whitelist-paths` | `[/actuator/**]` | 白名单路径 |
| `whitelist-ips` | `[127.0.0.1]` | 白名单 IP |

### framework4j.cache

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否启用 |
| `default-ttl-seconds` | `3600` | 默认 TTL |
| `null-ttl-seconds` | `30` | 空值缓存 TTL |
| `l1.enabled` | `true` | 是否启用 L1 Caffeine |
| `l1.max-size` | `10000` | 每 prefix 最大条目 |
| `single-flight.enabled` | `true` | 是否启用单飞防击穿 |

### framework4j.audit

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否启用 |
| `hash-chain-enabled` | `true` | 是否启用 Hash Chain |
| `hash-algorithm` | `SHA-256` | Hash 算法 |

### framework4j.sensitive

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否启用 |
| `encryption-key` | — | AES-256-GCM 密钥（必须配置，否则 TypeHandler 不注册） |

### framework4j.transport

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否启用 HTTP 传输抽象（默认装配 RestTemplateHttpTransport） |

### framework4j.tracelog

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `false` | 是否启用动态追踪日志（opt-in，避免无意识写 Redis） |
| `redis-name` | `default` | 日志存储与开关同步用的 Redis 数据源 |
| `storage.key-prefix` | `trace_log` | 日志 List Key 前缀（`{prefix}:{traceId}`） |
| `storage.global-max-traces` | `100000` | 全局 trace 数上限 |
| `storage.trace-ttl-seconds` | `86400` | 单 trace 日志保留时长（24h） |
| `storage.single-trace-max-logs` | `5000` | 单 trace 日志条数上限（LTRIM 裁剪） |
| `collection.flush-batch-size` | `500` | Pipeline 批写大小 |
| `collection.flush-interval-ms` | `500` | 批写间隔 |
| `collection.rate-limit-per-trace-per-second` | `200` | 单 trace 采集限速 |
| `collection.fallback-dir` | `/var/log/framework4j-tracelog/fallback` | Redis 故障降级目录（启动硬失败，容器需挂载） |
| `sync.channel` | `channel:log_switch` | 开关广播频道（Pub/Sub） |
| `sync.transport` | `pubsub` | 开关同步方式（`pubsub` / `streams`） |
| `sync.max-ttl-seconds` | `3600` | 开关 TTL 上限 |
| `elevation.allowed-packages` | `com.yourcompany` | 提权作用域包路径白名单 |
| `elevation.default-level` | `DEBUG` | 提权默认级别 |
| `api.require-auth` | `true` | 控制台 API 是否鉴权（true 时必须实现 TraceLogAuthValidator，否则启动 fail-fast） |
| `api.max-return-logs` | `1000` | 单次查询最大返回条数 |
| `export.enabled` | `true` | 是否启用导出 |
| `console.enabled` | `true` | 是否暴露控制台页面（`/tracelog/index.html`） |

### framework4j.idempotency

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否启用 |
| `ttl-seconds` | `172800` | Idempotency-Key 保留时长（48h） |
