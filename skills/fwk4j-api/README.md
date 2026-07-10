# fwk4j-api Skill

API 契约层 — 仅含 ApiCode 错误码枚举。

## 快查
- 错误码段位表 → SKILL.md
- `ApiResponse.fail(ApiCode.XXX)` → 需要 `framework4j-web`
- `throw new ApiException(ApiCode.XXX)` → 需要 `framework4j-web`
- `ApiCode.fromCode(int)` → 反查枚举
