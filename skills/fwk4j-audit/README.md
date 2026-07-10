# fwk4j-audit Skill
审计日志 — @Auditable AOP + Hash Chain 防篡改。
## 快查
- 注解 → `@Auditable(action, targetType, targetIdSpel)`
- 防篡改 → Hash Chain 自动计算
- 持久化 → 实现 `AuditSink`
- 安全 → actor/ip Header 必须网关覆写
