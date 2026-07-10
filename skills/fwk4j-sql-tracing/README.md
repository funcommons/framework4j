# fwk4j-sql-tracing Skill
Druid Filter 注入 trace_id 到 SQL 注释。
## 快查
- 效果 → `/*traceid=xxx*/ SELECT ...`
- 模式 → `DISABLED` / `WRITE_ONLY` / `ALL`
- trace_id 来源 → MDC（Micrometer Tracer 自动写入）
