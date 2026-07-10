# fwk4j-sensitive Skill
脱敏（Jackson）+ 加密（AES-256-GCM TypeHandler）。
## 快查
- 脱敏 → `@Sensitive(SensitiveRule.PHONE)` / CUSTOM pattern
- 加密 → `@TableField(typeHandler=EncryptedFieldTypeHandler.class)`
- 安全 → decrypt 失败返 null + warn（不返回明文）
