# framework4j-id

> 雪花 ID 生成（`SnowflakeDistributor`）+ OpenID 12 字符混淆（防遍历攻击）。

## 1. 概览

| 项 | 值 |
|---|---|
| 职责 | 分布式 ID 生成（雪花算法）/ `WorkerIdStrategy`（Redis 租约 / IP 哈希）/ OpenID 混淆（`Long` ↔ 12 字符串）/ MyBatis `IdentifierGenerator` 自动注册 |
| 配置前缀 | `framework4j.id.*`、`framework4j.openid.*` |
| 必需依赖 | `spring-boot-starter`、`mybatis-plus-spring-boot3-starter`（optional） |
| 可选依赖 | `framework4j-redis`（用 `RedisWorkerIdStrategy` 时必需）、`framework4j-api`（仅 test） |
| 在 SDK 中的位置 | 基础层，独立于 `datetime` / `redis` / `datasource` |

**核心原则**：内部用 `Long` 主键（雪花 ID，64 位），对外暴露 12 字符 OpenID 字符串（防遍历 + 防猜测）。前端永远拿不到原始 `Long`。

## 2. 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-id</artifactId>
    <version>1.2.7</version>
</dependency>
```

### 最小 application.yml

```yaml
framework4j:
  id:
    enabled: true
    worker-id-strategy: redis  # 或 ip-hash（无 Redis 时用）
    mybatis:
      enabled: true  # 自动注册 IdentifierGenerator
  openid:
    enabled: true
    salt: "${OPENID_SALT}"  # 必填，环境变量注入
```

### 最小代码示例

```java
@TableName("users")
public class UserDO {
    @TableId(type = IdType.ASSIGN_ID)  // MyBatis Plus 自动用雪花 ID
    private Long id;
    // ...
}

// Controller 永远返回 OpenID 字符串
public record UserVO(
    @OpenId Long id,           // 序列化为 12 字符串
    String name
) {}

@GetMapping("/v1/users/{open_id}")
public ApiResponse<UserVO> getUser(@PathVariable("open_id") String openId) {
    Long id = IdObfuscator.fromOpenId(openId);  // 12 字符串 → Long
    return ApiResponse.success(userService.find(id), TraceContext.getTraceId());
}
```

## 3. 配置参考

### `framework4j.id.*`

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `framework4j.id.enabled` | `boolean` | `true` | 是否启用 |
| `framework4j.id.worker-id-strategy` | `String` | `redis` | `redis`（租约）/ `ip-hash`（无 Redis 兜底） |
| `framework4j.id.epoch` | `long` | `1704067200000`（2024-01-01 UTC+8） | 雪花纪元 |
| `framework4j.id.mybatis.enabled` | `boolean` | `true` | 自动注册 MyBatis Plus `IdentifierGenerator` |
| `framework4j.id.redis.lease-seconds` | `int` | `30` | WorkerId 租约时长 |
| `framework4j.id.redis.renew-interval` | `int` | `10` | 续约间隔 |

### `framework4j.openid.*`

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `framework4j.openid.enabled` | `boolean` | `true` | 是否启用 |
| `framework4j.openid.salt` | `String` | 必填 | HMAC 盐值，环境变量注入 |
| `framework4j.openid.length` | `int` | `12` | 输出字符长度（11 数据 + 1 校验位） |
| `framework4j.openid.alphabet` | `String` | `0123456789ABCDEFGHJKMNPQRSTVWXYZ` | 字符表（去除易混字符） |
| `framework4j.openid.fail-fast` | `boolean` | `true` | v2.2 启动期校验：检测到 `@OpenId @PathVariable` 缺 `-parameters` 立即启动失败（loud > silent）。v1.3 追加：`@RequestBody` DTO 上 `@OpenId` 标在未受理类型（如开关关时的 `Integer`、`Map`）也启动失败 |
| `framework4j.openid.request-body-deserializer` | `boolean` | `true` | v1.3 `@RequestBody` 中 `@OpenId` 反序列化器子开关（特殊 DTO 走自定义反序列化时关） |
| `framework4j.openid.support-integer` | `boolean` | `false` | v1.3 `@OpenId` 受理 `Integer/int`（含集合/数组），三通道（序列化 / 反序列化 / path-query 入参）补齐对称 |
| `framework4j.openid.support-string` | `boolean` | `false` | v1.3 `@OpenId` 受理 `String`（含 `List<String>` 等），以 Long 为枢轴双向转：字段持数字串、线上混淆串。**迁移 String 入参的最轻路径** |
| `framework4j.openid.accept-numeric-fallback` | `boolean` | `true` | v1.3 关掉则所有 `@OpenId` 字段拒绝纯数字、只吃合法混淆串（迁移完成后强制收口，反枚举） |

## 4. API 参考

### `SnowflakeDistributor`

```java
public class SnowflakeDistributor {
    public long nextId();              // 同步生成
    public long[] nextIds(int count);  // 批量
    public long getWorkerId();
    public long getEpoch();
}
```

### `WorkerIdStrategy`（接口）

```java
public interface WorkerIdStrategy {
    long getWorkerId();        // 0 ~ 1023
    void renew();              // 续约（Redis 模式）
}
```

实现：
- `RedisWorkerIdStrategy`：租约模式，多实例不冲突（推荐生产）
- `IpHashWorkerIdStrategy`：IP 哈希取模，无 Redis 兜底（适合开发）

### `IdObfuscator`

```java
public class IdObfuscator {
    public static String toOpenId(Long id);    // Long → 12 字符串
    public static Long fromOpenId(String s);    // 12 字符串 → Long
    public static boolean isValid(String s);    // 校验位验证
    public static String toOpenId(long id, String prefix);  // 带业务前缀
}
```

> v2.2 修正：原 README 写 `OpenID.encode/decode`（全大写类名 + encode/decode 方法名），与代码不一致。实际是 `IdObfuscator.toOpenId/fromOpenId`。若需 `OpenID` 门面类请提 issue。

### `@OpenId`（注解）

标注在 `Long` 字段上，Jackson 序列化时自动转 OpenID 字符串：

```java
public record OrderVO(
    @OpenId Long id,
    @OpenId Long userId,
    String amount
) {}
```

> **v2.2 实现机制**：`@OpenId` 注解本身不带 `@JsonSerialize`（旧版本带，会导致 `framework4j.openid.enabled=false` 时序列化器仍生效，因为 Jackson 字段级注解走静态反射，绕过 Spring 容器）。当前实现是 `OpenIdAutoConfiguration` 通过 `OpenIdBeanSerializerModifier` 动态扫描 `@OpenId` 字段并应用序列化器。关闭 `framework4j.openid.enabled=false` 时，modifier 不注册，`@OpenId Long` 字段按普通 Long 序列化（走 framework4j-web 的 Long→String 输出数字字符串）。
>
> **入参还原**（`OpenIdFormatterFactory`）同样受开关控制 —— 关闭后 `@OpenId Long` 入参不再自动从 12 字符串还原，需消费方自行处理。
>
> **v1.3 请求体反序列化 + 注册机制修复**：`@RequestBody` JSON 中的 `@OpenId Long` / `List<Long>` / 嵌套 record 字段现在自动反混淆（`OpenIdBeanDeserializerModifier`，Jackson per-bean 递归，嵌套字段天然生效，无需 `@OpenIdRecursive`）。序列化侧 modifier 同步类型感知，`@OpenId List<Long>` 输出混淆串数组。**关键**：OpenId Jackson 模块改用 `BeanPostProcessor` 在 `ObjectMapper` 初始化后直接 `registerModule` 注册——旧写法走 `Jackson2ObjectMapperBuilderCustomizer.modulesToInstall`，与 framework4j-web 的 Long→String（同样 `modulesToInstall`）互冲，会让 OpenId 模块成"孤儿"（`setupModule` 不调用，序列化/反序列化全失效）。`Integer`/`String`/集合等受理范围由 `support-integer`/`support-string` 开关控制（默认仅 `Long`/`long`）。

### `@OpenId @PathVariable` 入参还原（v2.2 修复 GitHub Issue #1）

v2.1 及之前的版本：`@OpenId @PathVariable Long id` 依赖 Spring `MethodParameter.getParameterName()` 反射，要求消费方 Maven 编译加 `<parameters>true</parameters>`，否则在收到非数字 OpenID 字符串路径请求时**静默失败**（被 framework4j-web 包成 HTTP 200 + `code=10106 BUSINESS_RULE_ERROR`）。

v2.2 修复：新增 `OpenIdPathVariableArgumentResolver`，直接从 `HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE` 读 path 变量值，跳过 parameter name 反射。该 resolver 通过 BeanPostProcessor 前置注入到 `RequestMappingHandlerAdapter`，确保优先于内置 `PathVariableMethodArgumentResolver` 生效。

```java
// ✓ 不再要求消费方加 -parameters
@GetMapping("/v1/users/{id}")
public ApiResponse<UserVO> getUser(@OpenId @PathVariable("id") Long id) { ... }

// ⚠️ 未显式 name + 缺 -parameters 仍会失败，但 v2.2 启动期 fail-fast 会立刻报清楚
@GetMapping("/v1/users/{id}")
public ApiResponse<UserVO> getUser(@OpenId @PathVariable Long id) { ... }
```

启动期 fail-fast 校验（`framework4j.openid.fail-fast=true`，默认开）：扫描所有 controller，发现 `@OpenId @PathVariable` 无法解析参数名时立即 fail，提示加 `<parameters>true</parameters>` 或显式 `@PathVariable("name")`。把 silent production failure 变成 loud startup failure。

### `OpenIdTypeHandler`（MyBatis）

数据库字段是 12 字符串，Java 实体是 `Long`：

```java
@TableName("orders")
public class OrderDO {
    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = OpenIdTypeHandler.class)
    private Long id;  // DB 存 "XK7M3N9PQRST"，Java 是 Long
}
```

## 5. 示例

### 5.1 主键生成

```java
@Service
public class OrderService {
    @Resource
    private SnowflakeDistributor snowflake;
    
    public Long createOrder(CreateOrderRequest req) {
        Long id = snowflake.nextId();
        // INSERT INTO orders (id, ...) VALUES (id, ...)
        return id;
    }
}
```

### 5.2 OpenID 全链路

```java
// Controller 入参：12 字符串
@GetMapping("/v1/users/{open_id}")
public ApiResponse<UserVO> getUser(@PathVariable("open_id") String openId) {
    Long id = IdObfuscator.fromOpenId(openId);  // 12 字符串 → Long
    UserDO user = userService.find(id);
    UserVO vo = new UserVO(user.getId(), user.getName());  // @OpenId 自动编码
    return ApiResponse.success(vo, TraceContext.getTraceId());
}
```

### 5.3 多实例部署（Redis WorkerId）

```yaml
framework4j:
  id:
    worker-id-strategy: redis
    redis:
      lease-seconds: 30
      renew-interval: 10
```

10 个实例同时启动，每个租约到不同的 `workerId`（0-9），保证 ID 不冲突。

## 6. 错误码

| Code | 名称 | 触发场景 |
|---|---|---|
| `10900` | `INTERNAL_ERROR` | Redis 连接失败导致 WorkerId 获取失败 |
| `10102` | `FORMAT_INVALID` | OpenID 格式不对（长度 / 校验位 / 字符表） |

## 7. FAQ

**Q1：为什么不用 UUID？**
A：UUID 128 位、无序、索引性能差。雪花 ID 64 位、时间有序、适合做 MySQL 主键。OpenID 12 字符串仅用于对外暴露，数据库仍存 `Long`。

**Q2：OpenID 能被逆向回 Long 吗？**
A：不能直接逆向（HMAC-SHA256 单向）。但同 salt 下，同一个 `Long` 永远映射到同一个 OpenID（确定性），所以应用层 `IdObfuscator.fromOpenId(openId)` 能还原。salt 泄露 = OpenID 失效，必须环境变量注入。

**Q3：雪花 ID 会重复吗？**
A：`RedisWorkerIdStrategy` 模式下不会（每个实例独立 `workerId`）。`IpHashWorkerIdStrategy` 模式下，同 IP 多实例会冲突（仅适合开发）。生产必须用 Redis 模式。

**Q4：`@OpenId` 标注的字段在前端怎么用？**
A：前端永远拿 12 字符串，传回时也是 12 字符串。前端不需要解析。详见 mc-webui-spec 场景三。

**Q5：数据库存 `Long` 还是 OpenID 字符串？**
A：存 `Long`（雪花 ID）。OpenID 仅在 API 层暴露。`OpenIdTypeHandler` 用于历史数据库已存字符串的场景。

## 8. @OpenId 落地迁移指南（v1.3）

`@OpenId` 的接受规则（v1.3 三开关）：

- **合法 12 字符混淆串**（含 `PREFIX_xxx`）→ 永远接受，反混淆。
- **纯数字 / 数字串** → 默认接受（兼容期），`accept-numeric-fallback=false` 时拒绝（迁移后收口）。
- **非法串** → 拒绝（抛 `MismatchedInputException` → framework4j-web `GlobalExceptionHandler` → HTTP 200 + `BODY_FORMAT_ERROR 10103`）。

> 关键事实：`@OpenId Long` 入参（path/query/body）**同时接受数字串和混淆串**。所以把老的 `String id + Long.parseLong` 改成 `@OpenId Long id` 是**向后兼容**的——老前端传数字 id 照样解析。

### 8.1 入参迁移对照表

| 场景 | 迁移前 | 迁移后（推荐） | 备注 |
|---|---|---|---|
| `@PathVariable` | `@PathVariable String id` + `Long.parseLong(id)` | `@OpenId @PathVariable("id") Long id` | 数字串+混淆串都吃，类型改 Long |
| `@RequestParam` | `@RequestParam String id` + `parseLong` | `@OpenId @RequestParam("id") Long id` | 同上 |
| `@RequestBody` | DTO `String id` + 业务层 `parseLong` | DTO `@OpenId Long id`（v1.3 框架自动还原） | v1.3 新能力，无需手工 `fromOpenId` |
| `List<Long>` 入参 | `List<String>` + 循环 `fromOpenId` | `@OpenId List<Long>`（v1.3） | 集合/数组逐元素还原 |
| **不想改类型** | `@PathVariable String id`（保留 String） | `@OpenId @PathVariable("id") String id` + `support-string=true` | 字段仍是 String（持数字串），`parseLong` 不动；最轻改动 |

> 最后一行的 `support-string=true` 是 R4 的"最轻迁移"：老 String 入参只加一个 `@OpenId` 注解、类型不变、`parseLong` 不动，但线上已混淆。代价是 String 字段契约"持数字串"（序列化遇非数字串会报错，严格）。

### 8.2 出参迁移对照表

| 场景 | 迁移前 | 迁移后 |
|---|---|---|
| VO 主键 | `String.valueOf(entity.getId())` 手工混淆 | `@OpenId Long id`（框架序列化为 12 字符串） |
| VO 集合 | 循环 `IdObfuscator.toOpenId` | `@OpenId List<Long> ids`（v1.3 输出混淆串数组） |
| audit 等手工调用 | `IdObfuscator.toOpenId(id, "USR")` | `@OpenId Long id`（注解代替手工） |

### 8.3 灰度策略（回滚路径）

```yaml
framework4j:
  openid:
    enabled: true                       # 总开关，false 全关（序列化+反序列化+入参还原）
    request-body-deserializer: true     # v1.3 请求体反序列化；出问题时可单独关，回退到手工 fromOpenId
    accept-numeric-fallback: true       # 兼容期：吃数字；全员迁完混淆串后改 false 强制收口
    support-integer: false              # 字典表（Integer）默认不混淆；需要时开
    support-string: false               # 保留 String 入参时开
```

- **兼容期**：`accept-numeric-fallback=true`，老前端传数字 id 不破，新前端逐步传混淆串。
- **收口期**：前端全量切混淆串后，`accept-numeric-fallback=false`，拒绝裸数字 id（反枚举）。
- **回滚**：任一通道出问题，单独关对应开关（`request-body-deserializer` / `enabled`），不影响其余。

### 8.4 fail-fast 兜底

启动期 `OpenIdFailFastValidator`（`fail-fast=true` 默认）会拦下两类迁移期常见错误，把 silent failure 变 loud：

1. `@OpenId @PathVariable` 缺 name 且未加 `-parameters` → 启动失败，提示加 `<parameters>true</parameters>` 或显式 `@PathVariable("name")`。
2. `@RequestBody` DTO（含嵌套）的 `@OpenId` 标在未受理类型上（如 `support-integer=false` 时的 `@OpenId Integer`、`@OpenId Map`）→ 启动失败，提示改 Long 或开对应开关。

