# fwk4j-signature Skill

HMAC-SHA256 接口签名防重放。

## 快查
- 保护接口 → `@RequiresSignature`
- 签名算法 → `HMAC_SHA256(METHOD\nPATH\nTS\nNONCE\nBODY_MD5)`
- 密钥查询 → 实现 `SecretProvider`
- 四个 Header → `X-Access-Key` / `X-Timestamp` / `X-Nonce` / `X-Signature`
