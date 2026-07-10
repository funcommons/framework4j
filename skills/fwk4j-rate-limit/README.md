# fwk4j-rate-limit Skill

Lua 滑动窗口 + 固定窗口分布式限流。

## 快查
- 注解限流 → `@RateLimit(limit=100, window="1m", scope="user")`
- 白名单 → `whitelist-paths` / `whitelist-ips`
- 被限流响应 → HTTP 429 + `Retry-After` + `X-RateLimit-*`
- scope → `ip` / `user` / `app` / `global`
