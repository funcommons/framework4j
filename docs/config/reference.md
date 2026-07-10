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

### framework4j.idempotency

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否启用 |
| `ttl-seconds` | `172800` | Idempotency-Key 保留时长（48h） |
