# fwk4j-id Skill
Snowflake 分布式 ID + OpenID 12 字符混淆。
## 快查
- Snowflake → `snowflake.nextId()`
- OpenID → `IdObfuscator.toOpenId(id)` / `fromOpenId(openId)`
- 带前缀 → `toOpenId(id, "ORD")`
- MyBatis → `@OpenId` 注解 + TypeHandler 自动
