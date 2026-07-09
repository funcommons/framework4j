# Java 开发准则

> 由 `framework4j` SDK 多轮代码审计（9 轮，共 87 项修复）提炼而成。
> 适用：Java 17 + Spring Boot 3.x 后端服务/SDK 开发。
> 优先级：**P0 严重（安全/正确性/可用性）** > **P1 重要（质量/可维护/性能/一致性）** > **P2 建议** > **P3 优化**。

---

## 0. 速查

| 你想 | 入口 |
|---|---|
| 写 Controller / Service / Mapper | §2 编码 |
| 用 Redis / Lua / 缓存 | §3 Redis |
| 写 JWT / Token / 拦截器 | §4 鉴权 |
| 用线程池 / 异步 / 定时 | §5 并发 |
| 配 Filter / Interceptor / Wrapper | §6 Web |
| 写配置 / 自动装配 / Bean | §7 Spring |
| 异常 / 响应 / 错误码 | §8 响应 |
| 处理依赖 / ObjectMapper / 序列化 | §9 序列化 |
| 性能 / 热路径 / 内存 | §10 性能 |
| 测试 / 边界 / 兜底 | §11 测试 |

---

## 1. 全局铁律（P0）

1. **业务异常 HTTP 200** — `code` 决定结果，禁 4xx/5xx 携带业务信封；**未捕获系统异常（NPE/OOM）必须 HTTP 500**，让监控从状态码维度看到 5xx 告警。
2. **JSON 统一 Jackson** — 禁 fastjson2（autotype RCE 风险）；全局 `SNAKE_CASE` + `Long→String` + `JavaTimeModule`。
3. **敏感项禁止入仓** — DB/Redis/MQ 密码、JWT 密钥、第三方 AK/SK → 必须 `${...}` 环境变量注入。
4. **越权检查必须服务端强制** — 禁信任前端传 `user_id`，必须从 `SecurityContext` / `TokenContext` 取。
5. **SQL 必须 `#{}` 参数化** — 禁 `${}` 拼接；排序字段动态化用白名单 `<choose>`。
6. **`@Transactional(rollbackFor = Exception.class)`** — 默认只回滚 `RuntimeException` 是常见坑。
7. **Entity 禁 `@Data`** — 用 `@Getter/@Setter`（防 MyBatis 懒加载栈溢出）；DTO/VO 推荐 `record`。
8. **线程池禁 `Executors.newXxx()`** — 必须 `ThreadPoolExecutor` 显式构造 + 业务命名 + `CallerRunsPolicy` + `setWaitForTasksToCompleteOnShutdown(true)`。
9. **`Idempotency-Key` 写操作必支持** — 客户端 UUID v4，服务端 48h 保留。
10. **资源必须 try-with-resources** — `Connection`、`RedisConnection`、`InputStream` 等，禁 `getConnection().ping()` 不关闭。

---

## 2. 编码

### 2.1 命名（P1）

- **类命名**（阿里约规）：Controller→`<Resource>Controller`、Service→`<Resource>Service`+`<Resource>ServiceImpl`、Mapper→`<Resource>Mapper`、Entity→`<Resource>DO`、DTO→`<Resource><Action>Request`/`<Action>Response`、VO→`<Resource><Action>VO`、枚举→`<Resource><Type>`（**禁 `Enum` 后缀**）。
- **常量**：UPPER_SNAKE_CASE；**包**：全小写；**方法/字段**：lowerCamelCase。
- **魔法值禁止** — 用常量或枚举，禁代码内裸 `"-1"`、`"OK:"`、`48`。
- **`long` 字面量加 `L`**：`827394182374921L`，禁裸 `827394182374921`（编译警告 + 易错）。

### 2.2 Boolean 陷阱（P1）

对外字段 `is_paid`，Java 成员变量 `paid`（**不加 `is` 前缀**，防 Jackson/Lombok 双 `is` 坑）；Jackson 全局 `SNAKE_CASE` 自动桥接。

### 2.3 集合 / 包装类（P1）

- 集合判空用 `CollectionUtils.isEmpty()`，禁 `list == null || list.size() == 0`。
- 包装类比较用 `Objects.equals(a, b)`，禁 `==`（Integer 缓存陷阱 -128~127）。
- 控制语句必加 `{}`，禁单行 `if (x) do();`。

### 2.4 注释（P2）

- 默认不写注释；只在 **WHY 非显而易见** 时写（隐藏约束、不变量、bug workaround、跨版本兼容）。
- **禁** 解释 WHAT（well-named identifier 已说明）、引用当前任务/调用方（"used by X"、"for Y flow" — 属 PR 描述，会随代码演进而腐烂）。
- **禁** 多段 docstring / 多行注释块（≤ 1 行短注释）。
- 注释中**禁** 含 `[*/\r\n]` 字面序列（会提前关闭 javadoc）。

---

## 3. Redis

### 3.1 Lua 原子化（P0）

**任何 "GET + 判断 + SET" 序列必须用 Lua**，消除 TOCTOU 竞态。

```java
// ❌ TOCTOU 竞态
Boolean ok = redis.opsForValue().setIfAbsent(key, val, ttl);
if (!ok) { String old = redis.opsForValue().get(key); ... }

// ✅ Lua 原子
private static final DefaultRedisScript<String> GET_OR_SETNX = new DefaultRedisScript<>(
    "local v = redis.call('GET', KEYS[1]); " +
    "if v then return v end; " +
    "redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]); " +
    "return nil",
    String.class);
```

### 3.2 共享 Set TTL 单调延长（P0）

多个 token 撤销到同一 Set，**短 TTL 后写会覆盖长 TTL**，导致 jti 提前从黑名单消失 → 安全绕过。

```lua
-- 仅当当前 TTL < newTtl 或无 TTL 时才 expire
local ttl = redis.call('TTL', KEYS[1]);
if ttl < 0 or ttl < tonumber(ARGV[1]) then
  redis.call('EXPIRE', KEYS[1], ARGV[1]);
end
```

### 3.3 心跳 CAS 续期（P0）

租约心跳禁用 `set(key, val, ttl)` 无条件覆盖 — 节点暂停 >TTL 恢复后会覆盖他人租约。**禁用 `setIfAbsent` 续期**（NX 语义，key 已存在必返 false）。

```lua
-- ✅ Lua CAS：value 匹配才续期
if redis.call('GET', KEYS[1]) == ARGV[1] then
  return redis.call('EXPIRE', KEYS[1], ARGV[2]);
end
return 0
```

### 3.4 ObjectMapper 单例（P1）

`JsonRedisSerializer` / `TokenUtils` 等多处用 `ObjectMapper`，**禁各处 `new ObjectMapper()`**，共享 `static final` 单例。

### 3.5 Redis 连接关闭（P0）

```java
// ❌ 泄漏
return "PONG".equalsIgnoreCase(redis.getConnectionFactory().getConnection().ping());

// ✅ try-with-resources
try (var conn = redis.getConnectionFactory().getConnection()) {
    return "PONG".equalsIgnoreCase(conn.ping());
}
```

### 3.6 JsonRedisSerializer 白名单（P0）

`activateDefaultTyping` 必配 `BasicPolymorphicTypeValidator`，**禁用前缀匹配 `java.util.*` / `java.lang.*`**（gadget 链风险）。精确到具体类：

```java
.allowIfSubType(java.util.HashMap.class)
.allowIfSubType(java.util.ArrayList.class)
// ... 不再 allowIfSubType("java.util.")
.allowIfSubType(java.lang.String.class)
.allowIfSubType(java.lang.Long.class)
// ... 不再 allowIfSubType("java.lang.*")
```

### 3.7 多 DataSource 管理（P1）

- `MultiRedisManager.checkHealth` 异常吞掉返回 `false`；**禁把 `e.getMessage()` 暴露给 actuator health endpoint**（可能含连接串/主机名）。
- `stringRedisTemplate` 别名仅在 `templateType==STRING` 时注册，OBJECT 类型注册会破坏 Spring Boot 约定。
- `MultiRedisManager.destroy()` 迭代 keySet 前必须复制：`new ArrayList<>(map.keySet())`（`removeDatasource` 会修改 map）。

---

## 4. 鉴权 / Token

### 4.1 access / refresh 时长（P0）

- `access_token ≤ 2h`，`refresh_token ≤ 30d` 且**一次性**（jti 轮转）。
- refresh 重用检测：旧 refresh 被复用 → 写毒丸 + 撤销整族（family）。
- family 轮转次数硬上限（`maxRotations` 默认 20），达到强制重新登录。

### 4.2 refresh 轮转 Lua 原子化（P0）

**"标记旧 jti consumed + 写新 jti + 设 expire" 必须同一 Lua**，禁两步非原子写：

```lua
-- 标记 consumed
data.consumed = true
redis.call('HSET', KEYS[1], ARGV[1], cjson.encode(data))
-- 同事务写新 jti（防崩溃窗口）
if ARGV[3] and ARGV[4] then
  redis.call('HSET', KEYS[1], ARGV[3], ARGV[4])
  redis.call('EXPIRE', KEYS[1], ARGV[5])
end
```

### 4.3 旧 access token 撤销（P1）

refresh 后**必须撤销旧 access jti**（加入撤销 Set），防止旧 access 在自然过期前继续可用。

### 4.4 statsKey TTL 与主 key 同步（P1）

```java
// ❌ statsKey TTL = 主 key 剩余 TTL（如 5s），主 key 续期后 statsKey 先过期 → 计数器归零 → 突破 maxUsage
redisTemplate.expire(statsKey, ttl, TimeUnit.SECONDS);
// ✅ 续期主 key 后同步续期 statsKey
redisTemplate.expire(redisKey, renewIncrement, TimeUnit.SECONDS);
redisTemplate.expire(statsKey, renewIncrement, TimeUnit.SECONDS);
```

### 4.5 throwCustom 后 return false（P1）

`AuthExceptionFactory.throwCustom(annotation, code, msg)` 声明 `throws Exception`，但编译器不保证一定抛。若自定义异常构造吞异常，会继续执行后续代码导致 NPE。**每个 `throwCustom(...)` 后必加 `return false;`** 防御性收尾。

### 4.6 Token 拦截器路由（P1）

`@RequiresToken(type = "access"|"refresh")` 按物理类型路由到不同 `ValidationStrategy`：
- `type == "access"` → 现有 access 路径（验签 → 撤销 → 类型匹配 → Redis → nonce → 限次 → 续期）
- `type == "refresh"` → refresh 路径（毒丸 → family hash → consumed 校验）

**禁用 access token 走 refresh 路由**（路由前必须校验 token 实际 `type` 字段）。

### 4.7 refresh token nonce 冗余（P2）

refresh token 不校验 nonce（仅 access 校验），`createToken` 时 nonce 参数传 `null`，禁传 `UUID.randomUUID()`（误导语义 + 浪费随机数）。

---

## 5. 并发

### 5.1 ThreadLocal 资源（P0）

`Mac` / `MessageDigest` 非线程安全 + `getInstance` 有 JCA 查找开销，用 `ThreadLocal` 缓存：

```java
private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() ->
    MessageDigest.getInstance("SHA-256"));
// 用完 reset()（防残留上次状态）
md.reset();
```

### 5.2 ScheduledExecutor 生命周期（P0）

- `ScheduledExecutorService` 必须是**实例字段**（非 static），避免多 Spring 上下文共享。
- 实现 `DisposableBean` / `@PreDestroy` 优雅关闭：`shutdownNow()` + `awaitTermination(2s)`。
- 持有 `ScheduledFuture<?>` 句柄，release/destroy 时取消。

### 5.3 ThreadLocal 清理（P1）

- `ThreadLocal.clear()` 是 Java 16+（虽然 Java 17 支持，但保守用 `remove()` 兼容 Java 1.4+）。
- 用完必 `finally remove()`，防线程池复用导致内存泄漏 + 状态串读。

### 5.4 并发 Map 迭代（P1）

`ConcurrentHashMap.keySet()` 迭代时若同时 `remove(key)`，必须先复制：

```java
// ❌ ConcurrentModificationException 风险
map.keySet().forEach(k -> removeDatasource(k));
// ✅
new ArrayList<>(map.keySet()).forEach(this::removeDatasource);
```

### 5.5 @Scheduled 多实例（P1）

多实例部署必须 ShedLock 防 repeated execution，禁裸 `@Scheduled`。

### 5.6 @Async 异常（P1）

异步异常必须配 `AsyncUncaughtExceptionHandler`，否则静默丢失。

---

## 6. Web

### 6.1 ContentCachingRequestWrapper 不重放流（P0）

Spring 的 `ContentCachingRequestWrapper` 仅缓存 content，**不重放 InputStream**。拦截器 `readAllBytes()` 后 Controller `@RequestBody` 拿空 body。

**自定义 wrapper 重放**：

```java
public class CachedBodyRequestWrapper extends ContentCachingRequestWrapper {
    private byte[] cachedBody;
    public void cacheBody() throws IOException {
        if (cachedBody != null) return;
        super.getInputStream().readAllBytes();
        cachedBody = getContentAsByteArray();
    }
    @Override
    public ServletInputStream getInputStream() {
        if (cachedBody == null) return super.getInputStream();
        return new ServletInputStream() { /* ByteArrayInputStream */ };
    }
}
```

Filter 预读 `cacheBody()`，拦截器用 `getContentAsByteArray()` 拿 hash，Controller 从重放流读 `@RequestBody`。

### 6.2 回放分支响应体（P0）

`ContentCachingResponseWrapper` 写入只进缓冲区，必须 `copyBodyToResponse()` 刷到真实 response。**禁用 `ATTR_REPLAY_WRITTEN` 标记跳过 copy**（会导致客户端收到空响应体）。

### 6.3 失败响应不缓存（P1）

`IdempotencyInterceptor.afterCompletion` 对非 2xx / 异常**不写入 marker，直接 `delete(redisKey)`**，让客户端可用同 `Idempotency-Key` 重试。原实现写 `ERR:status` marker（TTL 48h），重试被 409 永久阻塞。

### 6.4 bodyHash 兜底 fail-secure（P1）

`IdempotencyBodyCacheFilter` 未生效时（wrapper 找不到），**禁用 `"*"` 放行**（同 key 不同 body 命中回放绕过 body 校验）。应抛 IOException 拒绝请求。

### 6.5 buildMethodKey 含 parameterTypes（P1）

```java
// ❌ 重载方法共享缓存，@LocalTimeFormat 状态错乱
return beanType.getName() + "#" + method.getName();
// ✅
return beanType.getName()
    + "#" + method.getName()
    + "(" + Arrays.toString(method.getParameterTypes()) + ")";
```

### 6.6 ApiAssert 正则缓存（P1）

`Pattern.matches(regex, text)` 每次重新编译正则。用 `ConcurrentHashMap<String, Pattern>` 缓存：

```java
private static final ConcurrentHashMap<String, Pattern> REGEX_CACHE = new ConcurrentHashMap<>();
private static Pattern compileRegex(String regex) {
    Pattern cached = REGEX_CACHE.get(regex);
    if (cached != null) return cached;
    Pattern compiled = Pattern.compile(regex);
    Pattern prev = REGEX_CACHE.putIfAbsent(regex, compiled);
    return prev != null ? prev : compiled;
}
```

---

## 7. Spring

### 7.1 BeanPostProcessor 顶级类（P1）

**禁用内部静态类**做 `BeanPostProcessor`，必须是顶级类（Spring 早期初始化阶段内部类可能不被识别）。

### 7.2 共享 StringRedisTemplate Bean（P1）

同一模块多个 Bean（`AccessTokenGenerator` / `RefreshTokenService` / `TokenInterceptor`）需要同一 `StringRedisTemplate`，**抽一个单例 Bean 注入**，禁各自调 `multiRedisManager.getStringRedisTemplate(name)`：

```java
@Bean
@ConditionalOnMissingBean(name = "accessTokenStringRedisTemplate")
public StringRedisTemplate accessTokenStringRedisTemplate(...) {
    return redisManager.getStringRedisTemplate(properties.getRedisName());
}
```

### 7.3 AutoConfiguration 开关（P1）

每个 starter 通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册。**核心模块默认 `enabled=true`**，redis/datasource/access-token **opt-in `true`**。

### 7.4 @ConfigurationProperties（P1）

- `@Component` + `@ConfigurationProperties` 或 `@EnableConfigurationProperties(XxxProperties.class)`。
- **禁用 `@Value` 散落注入**。
- 敏感项必须 `${...}` 环境变量。

### 7.5 @ConditionalOnMissingBean 优先级（P2）

业务可覆盖默认 Bean 时，必加 `@ConditionalOnMissingBean`，让用户自定义 Bean 优先生效。

### 7.6 destroyMethod="destroy"（P2）

`@Bean(destroyMethod = "destroy")` 让 Spring 管理 bean 生命周期；或实现 `DisposableBean`。禁裸 `Runtime.getRuntime().addShutdownHook`。

---

## 8. 响应

### 8.1 ApiResponse 信封 6 字段（P0）

```java
public record ApiResponse<T>(
    int code, String message, T data,
    List<ApiError> error,
    @JsonProperty("trace_id") String traceId,
    long timestamp
);
```

- **失败时 `data` 必须 `null`**（唯一例外 10700 部分成功）。
- `trace_id` 双通道：body 必返 + 响应头 `X-Trace-Id` 必返。
- 业务异常 HTTP 200 + 信封 `code`；系统异常 HTTP 500。

### 8.2 ApiCode 错误码（P1）

5 位数字分段：`0` 成功 / `101xx` 参数 / `102xx` 认证 / `103xx` 权限 / `104xx` 资源 / `105xx` 限流重试 / `107xx` 部分成功 / `109xx` 系统。**禁与既有码冲突**。

### 8.3 全局异常处理器（P0）

```java
@ExceptionHandler(Exception.class)  // 兜底
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)  // 系统异常返 500
public ApiResponse<?> handleException(Exception e) {
    log.error("[系统异常] 兜底捕获，实际异常类型: {}", e.getClass().getName(), e);
    return ApiResponse.fail(ApiCode.SYSTEM_BUSY, "系统繁忙，请稍后重试");
}
```

业务异常仍走 HTTP 200 + `code`，仅未知系统异常（NPE/OOM）走 500 让监控告警。

### 8.4 ApiException.getMessage() 禁无意义 override（P3）

```java
// ❌ 无意义
@Override public String getMessage() { return super.getMessage(); }
// ✅ 直接继承，不 override
```

---

## 9. 序列化

### 9.1 全局 Jackson 配置（P1）

```java
@Bean
public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
    return builder -> {
        builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        builder.serializerByType(Long.class, ToStringSerializer.instance);
        builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        builder.modulesToInstall(new JavaTimeModule());
        builder.failOnUnknownProperties(false);
    };
}
```

### 9.2 Long ID → String（P0）

主键 / 雪花 ID / 金额一律 String（防 JS 精度丢失）。Controller 字段类型 `String`，禁 `Long` 出现在 Controller。

### 9.3 金额（P1）

`BigDecimal.setScale(2, RoundingMode.HALF_UP).toPlainString()`，禁 `double` / `float`。

### 9.4 StdSerializer 注册类型（P0）

```java
// ❌ Jackson 不知 handledType，addSerializer 失败
public class OpenIdJsonSerializer extends JsonSerializer<Long> { ... }
objectMapper.addSerializer(new OpenIdJsonSerializer());

// ✅ StdSerializer 显式类型
public class OpenIdJsonSerializer extends StdSerializer<Long> {
    public OpenIdJsonSerializer() { super(Long.class); }
    ...
}
```

### 9.5 OpenID 混淆（P1）

- 算法：乘法散列（Knuth）+ Base62 + XOR + 乱序字符集 + 校验位。
- **字符集固化字符串**，禁用 `new Random(seed).shuffle()`（跨 JDK 版本不保证稳定，会导致所有历史 OpenID 无法解码 → 数据丢失级事故）。
- 反向索引表 `int[128]` O(1) 查找，替代 `indexOf` O(n)。
- 非法字符抛 `IllegalArgumentException`，禁让 `sum` 可能为负导致 `StringIndexOutOfBoundsException`。

---

## 10. 性能

### 10.1 HEX 查表替代 String.format（P1）

```java
// ❌ String.format 每次创建 Formatter
sb.append(String.format("%02x", b & 0xff));

// ✅ HEX 查表（吞吐 5-10x）
private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
int v = b & 0xff;
sb.append(HEX_CHARS[v >>> 4]).append(HEX_CHARS[v & 0x0f]);
```

### 10.2 正则预编译（P1）

`Pattern.matches(regex, text)` 每次重新编译。热路径用 `static final Pattern` 或 `ConcurrentHashMap` 缓存。

### 10.3 StringBuilder 预分配容量（P2）

```java
// ❌ 默认 16，多次扩容
StringBuilder sb = new StringBuilder();
// ✅ 已知长度预分配
StringBuilder sb = new StringBuilder(64);  // SHA-256 = 32 字节 = 64 hex
```

### 10.4 批量上限（P1）

- Mapper `foreach` / `insertBatch` ≤ 1000。
- 同步业务批量 ≤ 100；>100 必须异步 Job（返 `job_id` + `poll_url`）。

### 10.5 SQL 注释 trace_id（P2）

MyBatis Interceptor 写 `/*traceid=xxx*/` 前缀，但**白名单过滤**特殊字符（`/*`、`*/`、`\r`、`\n`、`;` 等），防 SQL 注入。

### 10.6 禁 N+1（P1）

循环内查 DB 必须批量；`map.keySet().forEach` 查每个 key 违反。

---

## 11. 测试

### 11.1 Lua 脚本测试 mock（P1）

```java
// 原 setIfAbsent 测试 mock 失效，新 Lua 用 execute：
when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(null);
```

### 11.2 MockHttpServletRequest + ContentCachingRequestWrapper（P1）

`MockHttpServletRequest.setContent()` 的流可重读，掩盖了 `ContentCachingRequestWrapper` 不重放流的 bug。**必须用 `CachedBodyRequestWrapper` + `cacheBody()`** 才能模拟真实容器行为。

### 11.3 异常类型断言（P1）

修代码改变异常类型时同步更新测试断言（如 `IllegalArgumentException` → `IllegalStateException`）。

### 11.4 测试分组（P2）

`unit/` / `functional/` / `integration/` / `performance/`，按 `-Dtest='**/functional/*Test'` 跑。

### 11.5 Redis 依赖（P2）

- `framework4j-redis` / `framework4j-accesstoken` / 部分 `framework4j-id` 测试需要 `localhost:6379`。
- `framework4j-accesstoken` 性能测试用 `it.ozimov:embedded-redis` 内嵌 Redis。
- 无 Redis 时优先 `framework4j-api` 测试（IP-hash `WorkerIdStrategy`）。

---

## 12. SDK 白名单（P0）

- **禁 fastjson2**（autotype RCE）。
- **禁 `<repositories>`** 在业务 POM（统一走内部 Nexus）。
- **禁协议**（GPL/AGPL/SSPL）；允许 Apache/MIT/BSD。
- 业务 POM **禁写 `<version>`**（由父 POM / BOM 统一）。
- 测试依赖必须 `<scope>test</scope>`。
- BOM 清单：`spring-boot-dependencies` / `mybatis-plus-bom` / `redisson-bom` / `opentelemetry-bom` / `micrometer-bom`。

---

## 13. 安全 P0 必查 6 项

| # | 检查项 | 检查方式 |
|---|---|---|
| 1 | 密码 bcrypt（cost ≥ 12） | grep `BCryptPasswordEncoder`、`MD5`、`SHA1` |
| 2 | JWT 密钥环境变量注入 | grep `secret` / `signing-key` 配置，禁硬编码 |
| 3 | SQL 用 `#{}` 不用 `${}` | grep Mapper XML |
| 4 | 越权检查从 SecurityContext 取 user_id | grep `@PathVariable.*user_id` |
| 5 | 文件上传 Magic Number 校验 | grep `MultipartFile`，检查 Tika 探测 |
| 6 | 敏感字段加密存储 | 查 DDL，身份证/银行卡走加密 TypeHandler |

P1：refresh 一次性 / 签名 constant time 比较 / 审计表 append-only / OAuth state 校验 / Spring Security STATELESS / 安全响应头（HSTS/CSP/X-Frame-Options）/ SSRF 校验 / JsonRedisSerializer 精确白名单。

---

## 15. Web 层契约（framework4j-web）

> v2.1 拆分：`framework4j-api` 仅留 `ApiCode`，Web 实现（ApiResponse / GlobalExceptionHandler / TraceContext / CachedBodyRequestWrapper）独立为 `framework4j-web`。

### 15.1 ApiResponse 信封（P0）

```java
// fun.commons.framework4j.web.ApiResponse
public class ApiResponse<T> {
    int code;            // 0 = 成功
    String message;
    T data;              // 失败时必须 null（10700 部分成功例外）
    List<ApiError> error;
    @JsonProperty("trace_id") String traceId;  // 双通道：body + X-Trace-Id Header
    long timestamp;
}
```

- **6 字段必返**（即使 data/error 为 null）。
- **失败时 data 必须 null**（除 10700）。
- **trace_id 自动从 `TraceContext.getTraceId()` 取**（MDC + Micrometer Tracer 兜底）。

### 15.2 GlobalExceptionHandler 兜底（P0）

- 业务异常（`ApiException`）→ HTTP **200** + 信封 `code`
- 未知系统异常（NPE/OOM）→ HTTP **500** + 信封 10001（让监控可见）
- 参数校验失败 → 10100 + `error[]` 含字段错误
- DB 异常（DuplicateKey / BadSqlGrammar / DataIntegrityViolation）→ 自动映射到 104xx

### 15.3 CachedBodyRequestWrapper 共用资产（P0）

- 位于 `fun.commons.framework4j.web.cache.CachedBodyRequestWrapper`
- **解决 Spring `ContentCachingRequestWrapper` 不重放 InputStream 的隐藏 bug**
- signature / idempotency 模块共用：body MD5 / body hash
- Filter 需在拦截器之前 `cacheBody()`（详见 §6.1）

### 15.4 模块依赖关系

```
framework4j-api  ── ApiCode（契约）
       ↓
framework4j-web  ── ApiResponse / GlobalExceptionHandler / TraceContext / CachedBodyRequestWrapper
       ↑                    ↑
       │                    │
framework4j-signature   framework4j-rate-limit
framework4j-idempotency framework4j-accesstoken
```

---

## 16. HMAC 签名规范（framework4j-signature）

> mc-java-security 铁律 6 强制要求。适用于开放 API / 三方对接场景。

### 16.1 签名算法（P0）

```
签名串 = HTTP_METHOD + "\n" + PATH + "\n" + TIMESTAMP + "\n" + NONCE + "\n" + BODY_MD5
签名值 = BASE64(HMAC_SHA256(secret, 签名串))
```

### 16.2 请求 Header 四件套

| Header | 必填 | 说明 |
|---|---|---|
| `X-Access-Key` | 是 | 应用标识（查 secret 用） |
| `X-Timestamp` | 是 | Unix 毫秒，服务端 ±5min 容忍 |
| `X-Nonce` | 是 | UUID v4，一次性（Redis SETNX 10min） |
| `X-Signature` | 是 | BASE64 签名值 |

### 16.3 服务端五步校验（P0）

```java
// fun.commons.framework4j.signature.service.SignatureService#validate
1) Header 齐全 → 缺失抛 10101 PARAM_MISSING
2) timestamp ±5min → 过期/超前抛 10102 PARAM_FORMAT_ERROR
3) nonce 一次性（Lua 原子 SETNX EX 600s）→ 重复抛 10302 SIGNATURE_ERROR
4) 查 secret → 找不到抛 10200 UNAUTHORIZED
5) HMAC 常量时间比较（MessageDigest.isEqual）→ 不匹配抛 10302 SIGNATURE_ERROR
```

### 16.4 ThreadLocal<Mac>（P0）

```java
// fun.commons.framework4j.signature.util.MacUtil
private static final ThreadLocal<Mac> MAC_CACHE = ThreadLocal.withInitial(...);

public static byte[] hmacSha256(byte[] key, byte[] data) {
    Mac mac = MAC_CACHE.get();
    try {
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    } finally {
        mac.reset();  // 关键：防残留状态
    }
}
```

**禁用 `commons-codec HmacUtils`**，统一 JDK `Mac` + ThreadLocal。

### 16.5 配置示例

```yaml
framework4j:
  signature:
    enabled: true
    timestamp-tolerance-ms: 300000       # ±5min
    nonce-ttl-seconds: 600                # 10min
    path-patterns: ["/v1/api/**"]
    exclude-path-patterns: ["/v1/auth/**"]
    nonce-key-prefix: "signature:nonce"
    redis-name: "default"
```

### 16.6 SecretProvider 扩展点

```java
// 默认 InMemorySecretProvider（开发/测试用）
// 生产应实现：
public class DbSecretProvider implements SecretProvider {
    public String getSecret(String accessKey) {
        // 从 DB / 配置中心 / KMS 查询
    }
}
```

---

## 17. 限流规范（framework4j-rate-limit）

> mc-api-spec §8.5 强制要求。

### 17.1 算法选型（P0）

| 算法 | 场景 | 实现 |
|---|---|---|
| **sliding_window**（默认） | 平滑限流、精确 | Lua ZSET + `ZREMRANGEBYSCORE` + `ZCARD` + `ZADD` + `PEXPIRE` |
| token_bucket | 允许突发 | Redisson `RRateLimiter` |

### 17.2 响应头三件套（P0）

被限流时**必须**返回：
```
HTTP/1.1 429 Too Many Requests
Retry-After: 30                           # 距下次可请求的秒数
X-RateLimit-Limit: 100                    # 窗口内配额上限
X-RateLimit-Remaining: 0                  # 剩余配额
X-RateLimit-Reset: 1718660460             # 重置时间（Unix 秒）

{
  "code": 10500,
  "message": "请求过于频繁，请 30 秒后重试",
  "data": null,
  ...
}
```

### 17.3 scope 维度（P1）

| scope | key 维度 | 适用 |
|---|---|---|
| `ip`（默认） | `ratelimit:ip:{ip}:{path}` | 公网 IP 限流 |
| `user` | `ratelimit:user:{uid}:{path}` | 已登录用户限流（从 `X-User-Id` 取） |
| `app` | `ratelimit:app:{accessKey}:{path}` | 开放 API 三方限流 |
| `global` | `ratelimit:global:global:{path}` | 全局共享 key |

**X-Forwarded-For 优先**（取第一个 IP），其次 `X-Real-IP`，最后 `remoteAddr`。

### 17.4 注解使用

```java
@RestController
public class OrderController {

    @RateLimit(limit = 100, window = "1m", scope = "ip")
    @PostMapping("/v1/orders")
    public ApiResponse<?> createOrder(...) { ... }

    @RateLimit(limit = 10, window = "1s", scope = "user", algorithm = "token_bucket")
    @PostMapping("/v1/sms/send")
    public ApiResponse<?> sendSms(...) { ... }
}
```

### 17.5 Lua 原子化（P0）

```lua
-- fun.commons.framework4j.ratelimit.lua.RateLimitLuaScripts#SLIDING_WINDOW
local now = tonumber(ARGV[3])
local cutoff = now - tonumber(ARGV[1])
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, cutoff)        -- 清旧记录
local count = redis.call('ZCARD', KEYS[1])                -- 统计当前
if count < tonumber(ARGV[2]) then
  redis.call('ZADD', KEYS[1], now, now .. '-' .. math.random())
  redis.call('PEXPIRE', KEYS[1], ARGV[1])
  return {1, count + 1, now + window, limit}              -- 放行
else
  return {0, count, resetAt, limit}                       -- 限流
end
```

### 17.6 Redis 故障兜底（P1）

Redis 异常时**放行**（避免限流故障拖垮业务）：
```java
} catch (Exception e) {
    log.warn("[RateLimit] Redis sliding_window failed: {}", e.getMessage());
    return new AcquireResult(true, 0, limit, now + windowMs);  // 兜底放行
}
```

### 17.7 配置示例

```yaml
framework4j:
  rate-limit:
    enabled: true
    path-patterns: ["/v1/**"]
    exclude-path-patterns: ["/v1/auth/login"]
    redis-name: "default"
    default-limit: 100
    default-window: "1m"
    default-algorithm: "sliding_window"
    default-scope: "ip"
    include-headers: true
```

---

## 18. 多级缓存规范（framework4j-cache）

> v2.1 新增。补齐 Spring Cache 缺失的 TTL / 防穿透 / 防击穿 / 防雪崩能力。

### 18.1 二级缓存架构（P0）

```
应用 → L1 (Caffeine) → L2 (Redis) → 业务方法
       命中即返回         命中回填 L1    未命中走业务
```

### 18.2 三防能力（P0）

| 能力 | 实现 | 规则 |
|---|---|---|
| **防穿透** | 空值缓存标记 `__NULL__`（短 TTL 30s） | DB 返回 null 也缓存 |
| **防击穿** | Lua 分布式锁 + per-key `CompletableFuture` 单飞 | leader 回源，follower 等待唤醒 |
| **防雪崩** | TTL ±10% 随机抖动（`ThreadLocalRandom`） | 批量预热 key 不会同时过期 |

### 18.3 单飞关键设计（P0）

- `tryAcquireLeader` 返回 `LeaderContext(token, future)`，`releaseLeader` 仅 complete 自己的 future
- follower `waitForLeader` 复用同一 future 被 leader complete 唤醒
- follower 超时后二次抢锁（防 leader crash 后 follower 雪崩回源）
- **禁用** `ConcurrentHashMap.compute` 内嵌 `computeIfAbsent`（Java 17 抛 `IllegalStateException`）

### 18.4 编程式 vs 注解

```java
// 编程式
cacheService.get("user", id, 3600, () -> userMapper.selectById(id), User.class);
// 注解（AOP）
@CacheableGet(prefix = "user", key = "#id")
public User getUser(String id) { return userMapper.selectById(id); }
```

---

## 19. 审计日志规范（framework4j-audit）

> v2.1 新增。mc-java-security 铁律 10。

### 19.1 注解驱动

```java
@Auditable(action = "DELETE_ORDER", targetType = "order", targetIdSpel = "#orderId")
public void deleteOrder(String orderId) { ... }
```

### 19.2 Hash Chain 防篡改（P0）

```
hash = SHA256(prev_hash || TreeMap(action, targetType, targetId, actor, result, timestamp, args))
```

- `computeNextSnapshot` 原子返回 `(prevHash, hash)`
- `verify` 独立计算（不影响 lastHash）
- sink 失败 CAS 回滚；CAS 失败标记空洞
- **content 用 `TreeMap`**（HashMap 迭代序不稳定 → verify 跨 JVM 不可复现）

### 19.3 Actor / IP 安全责任（P0）

- `actor` 取自 `X-User-Id`、`ip` 取自 `X-Forwarded-For` — **必须由网关在入口覆写后才可信**

---

## 20. 字段脱敏与加密规范（framework4j-sensitive）

> v2.1 新增。mc-java-security 铁律 9。

### 20.1 双层防护（P0）

```
应用层（脱敏）@Sensitive(PHONE) → Jackson 序列化 "138****1234"
存储层（加密）EncryptedFieldTypeHandler → DB 存 AES-256-GCM 密文
```

### 20.2 脱敏规则（6 种）

| 规则 | 输入 | 输出 |
|---|---|---|
| `PHONE` | 13812345678 | 138****5678 |
| `ID_CARD` | 110101199001011234 | 110101********1234 |
| `BANK_CARD` | 6228123456785678 | 6228******5678 |
| `EMAIL` | alice@example.com | a***@example.com |
| `NAME` | 张三丰 | 张** |
| `ADDRESS` | 北京市朝阳区望京街1号 | 北京市朝阳区*** |

### 20.3 AES-256-GCM 加密（P0）

- 算法 `AES/GCM/NoPadding`，IV 随机 12 字节，Tag 128 位
- `ThreadLocal<Cipher>` 复用（§5.1）
- encryptionKey 缺失时 **ERROR 日志**（不静默，防明文落库）
- decrypt 失败返回 null + warn（**不返回明文**，GCM 验签失败不得回退）

---

## 21. 修订历史

| 版本 | 日期 | 变更 |
|---|---|---|
| 1.0 | 2026-07-06 | 初版，由 framework4j SDK 9 轮审计（87 项修复）提炼 |
| 1.1 | 2026-07-08 | 加 §15 Web / §16 HMAC 签名 / §17 限流（第一梯队 3 模块交付） |
| 1.2 | 2026-07-09 | 加 §18 多级缓存 / §19 审计日志 / §20 字段脱敏加密（第二梯队 3 模块交付） |
