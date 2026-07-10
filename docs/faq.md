# 常见问题

## Q: 启动报错 "spring.application.name 必须配置"

**A**: `framework4j.redis` / `framework4j.access-token` / `framework4j.signature` 等模块使用 `spring.application.name` 作为 Redis key 前缀。必须在 `application.yml` 配置：

```yaml
spring:
  application:
    name: my-app
```

## Q: Redis 连接失败

**A**: 确认 Redis 已启动（`redis-cli ping` 返回 PONG），且 `framework4j.redis.datasources.default.host/port` 配置正确。

## Q: 签名校验总失败

**A**:
1. 确认 `X-Access-Key` / `X-Timestamp` / `X-Nonce` / `X-Signature` 四个 Header 都已传
2. 服务端时间与客户端时间差 < 5min（`timestamp-tolerance-ms`）
3. 签名串构造顺序：`METHOD\nPATH\nTIMESTAMP\nNONCE\nBODY_MD5`
4. nonce 不能重复（10min 内一次性）

## Q: 限流误伤健康检查

**A**: 在 `application.yml` 加白名单：

```yaml
framework4j:
  rate-limit:
    whitelist-paths:
      - "/actuator/**"
      - "/health/**"
```

## Q: sensitive 的 encryption-key 没配会怎样

**A**: `EncryptedFieldTypeHandler` 不会注册，使用该 TypeHandler 的字段会按 String 透传（明文落库）。日志会输出 ERROR 提醒。生产环境必须配置。

## Q: 如何按用户踢人（强退）

**A**:
```java
int revoked = generator.revokeByUser("WEB", "user-123");
// 删除该用户所有 session + jti 加入撤销 Set
```

## Q: 缓存冷启动如何预热

**A**:
```java
@PostConstruct
public void warmup() {
    cacheService.warmup("user", hotUserIds, 3600,
        id -> userMapper.selectById(id), User.class);
}
```

## Q: 测试覆盖率怎么看

**A**:
```bash
mvn verify
# JaCoCo 门槛 ≥ 80%，不达标会 BUILD FAILURE
```
