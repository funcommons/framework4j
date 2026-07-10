# fwk4j-idempotency Skill
幂等键防重复提交。
## 快查
- 使用 → 客户端加 `Idempotency-Key: <UUID v4>` Header
- 防重 → Redis Lua SETNX 48h
- 重试 → Controller 异常时删 key（允许重试）
