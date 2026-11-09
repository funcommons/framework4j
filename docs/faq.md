# 常见问题

## Q: 升级 v1.8+ 后 id / OpenID 功能静默失效

**A**: v1.8 起 `framework4j.id.enabled` / `framework4j.openid.enabled` 改为**显式启用**（缺省不激活，issue #26）——此前「引依赖即激活」。升级后未显式配置 `true` 的应用模块不装配。注意子开关（`mybatis.enabled` 等）缺省跟随父开关。另外配置键是 `framework4j.id.worker.strategy`（redis|ip），历史文档的 `worker-id-strategy` / `ip-hash` 是不存在的键。

## Q: 升级 v1.10 后接口返回的时间字符串变了

**A**: v1.10 修复了两类 wire 契约缺陷（issue #30）：ISO 态从变长 `toString()`（秒为 0 时省略秒段，`14:30+08:00` ↔ `14:30:05+08:00` 漂移）改为**定长** `yyyy-MM-dd'T'HH:mm:ssXXX`；本地格式分支（`@LocalTimeFormat` 态）此前按值自带 offset 排墙钟（UTC 值错 8 小时），现在先换算到 `framework4j.datetime.local-zone`（缺省 Asia/Shanghai）。按 ISO 弹性解析的消费方无感；对字符串做定长切分的消费方此前本就随数据随机崩，定长后恒定。

## Q: 升级 v1.10 后 SQL 注入防火墙（wall）不生效了

**A**: v1.10 起 `druid.filters` 缺省 `stat,wall,slf4j` → **`stat,slf4j`**（issue #28）：wall 的 `comment not allow` 拦截注释 SQL——与同仓 sql-tracing 的 `/*traceid=...*/` 前缀互斥，也拦 Flyway 带注释迁移。需要 wall 请显式选配 `filters: stat,wall,slf4j`，并用 wall 的 `commentAllow` 豁免 `/*traceid=` 前缀实现两者兼得。

## Q: MyBatis-Plus 分页插件不生效（多数据源场景）

**A**: v1.10 前框架注册的 `MybatisPlusInterceptor` 从未 wire 进框架 SqlSessionFactory（惰性 bean）——分页从未真正生效（issue #29）。v1.10 起按类型注入：用户自定义 `MybatisPlusInterceptor` bean 时自动接管。GlobalConfig/TypeHandler 定制走 `datasources.<name>.mybatis-plus.config-location` 或自建工厂，详见 datasource 模块文档 §3.1。

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
