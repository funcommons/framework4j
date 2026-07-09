# framework4j-demo

> 全模块集成示例 — signature + rate-limit + cache + audit + sensitive 全链路

## 启动

```bash
# 前置：Redis on localhost:6379
mvn -pl framework4j-demo spring-boot:run
```

## 接口列表

| 接口 | 方法 | 签名 | 限流 | 审计 | 缓存 | 脱敏 |
|---|---|---|---|---|---|---|
| `/v1/api/users/{id}` | GET | ✅ | ✅ 10/min IP | ✅ | ✅ | ✅ |
| `/v1/public/health` | GET | ❌ | ✅ 但白名单豁免 | ❌ | ❌ | ❌ |

## curl 示例

### 1. 无签名 → 401

```bash
curl -v http://localhost:8080/v1/api/users/u-1
# HTTP 401 + {"code":10101,"message":"签名头缺失"}
```

### 2. 正确签名 → 200（含脱敏 + X-Token-Expire-At）

```bash
# 生成签名（Python）
python3 -c "
import hmac, hashlib, base64, time, uuid
secret = 'test-secret'  # 实际应与 SecretProvider 一致
method, path = 'GET', '/v1/api/users/u-1'
ts = str(int(time.time()*1000))
nonce = str(uuid.uuid4())
body_md5 = hashlib.md5(b'').hexdigest()  # GET 无 body
string_to_sign = f'{method}\n{path}\n{ts}\n{nonce}\n{body_md5}'
sig = base64.b64encode(hmac.new(secret.encode(), string_to_sign.encode(), hashlib.sha256).digest()).decode()
print(f'curl -H \"X-Access-Key: test-app\" -H \"X-Timestamp: {ts}\" -H \"X-Nonce: {nonce}\" -H \"X-Signature: {sig}\" http://localhost:8080/v1/api/users/u-1')
"
```

响应：
```json
{
  "code": 0,
  "data": {
    "id": "u-1",
    "phone": "138****5678",
    "email": "a***@example.com",
    "realName": "张**"
  }
}
```

### 3. 超限流 → 429

连续发 11 次请求（limit=10/min）：
```bash
# 第 11 次：
# HTTP 429 + {"code":10500} + Retry-After: 60
```

### 4. 健康检查（白名单豁免限流）

```bash
curl http://localhost:8080/v1/public/health
# {"code":0,"data":"OK"}  — 不受限流
```

## 配置说明

见 `src/main/resources/application.yml`。关键项：
- `framework4j.signature.path-patterns: ["/v1/api/**"]` — 签名校验路径
- `framework4j.rate-limit.default-limit: 10` — 每分钟 10 次
- `framework4j.rate-limit.whitelist-paths: ["/actuator/**"]` — 白名单豁免
- `framework4j.sensitive.encryption-key: demo-aes-key` — AES 加密密钥
