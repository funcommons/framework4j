# fwk4j-web Skill

Web 层契约 — ApiResponse + GlobalExceptionHandler + TraceContext + CachedBodyRequestWrapper。

## 快查
- 统一响应 → `ApiResponse.success(data)` / `ApiResponse.fail(ApiCode.XXX)`
- 异常处理 → 自动（GlobalExceptionHandler）
- trace_id → `TraceContext.getTraceId()` + 响应头 `X-Trace-Id`
- Jackson 配置 → 自动（snake_case + Long→String）
