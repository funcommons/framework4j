# fwk4j-accesstoken Skill

JWT + Redis 双验 + Refresh 家族轮转。

## 快查
- 登录发 Token → `generateToken` / `generateTokenPair`
- 保护接口 → `@RequiresToken("WEB")`
- Refresh → `refreshAccessToken(refreshToken)`
- 踢人 → `revokeByUser(type, uid)`
- 续期通知 → `X-Token-Expire-At` 响应头（自动）
