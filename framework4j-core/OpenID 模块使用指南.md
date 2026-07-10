# OpenID 模块使用指南

> **前端友好 | 安全混淆 | 自动转换** - 解决分布式ID暴露给前端的所有痛点

**当前版本**: v1.0.0-SNAPSHOT

---

## 📚 目录

- [1. 快速开始](#1-快速开始)
- [2. 核心特性](#2-核心特性)
- [3. 支持的数据类型](#3-支持的数据类型)
- [4. 使用方式](#4-使用方式)
- [5. 配置说明](#5-配置说明)
- [6. 最佳实践](#6-最佳实践)
- [7. 故障排查](#7-故障排查)
- [8. 高级用法](#8-高级用法)

---

## 1. 快速开始

### 1.1 依赖配置

确保项目中包含以下依赖：

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.ldx2t</groupId>
    <artifactId>ldx2t-commons-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 1.2 启用OpenID支持

在`application.yml`中启用OpenID功能：

```yaml
ldx2t:
  commons:
    openid:
      fastjson2:
        enabled: true  # 启用FastJson2支持
      jackson:
        enabled: true  # 启用Jackson支持
      swagger:
        enabled: true  # 启用Swagger文档适配
```

### 1.3 第一个示例

```java
import com.ldx2t.commons.openid.annotation.OpenId;

@RestController
@RequestMapping("/api/users")
public class UserController {

    // 在控制器参数中使用 - Long类型
    @GetMapping("/{id}")
    public UserVO getUser(@OpenId @PathVariable Long id) {
        // id参数会被自动从OpenID字符串转换为Long
        User user = userService.findById(id);
        return UserVO.from(user);
    }

    // 在控制器参数中使用 - Integer类型
    @GetMapping("/orders/{orderId}")
    public OrderVO getOrder(@OpenId @PathVariable Integer orderId) {
        // orderId参数会被自动从OpenID字符串转换为Integer
        Order order = orderService.findById(orderId);
        return OrderVO.from(order);
    }

    @PostMapping
    public UserVO createUser(@RequestBody UserCreateDTO dto) {
        User user = userService.save(dto);
        return UserVO.from(user); // 序列化时ID会被转换为OpenID字符串
    }
}
```

---

## 2. 核心特性

### 2.1 注解驱动

```java
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface OpenId {
}
```

### 2.2 自动转换机制

| 场景 | 输入 | 输出 | 说明 |
|------|------|------|------|
| **HTTP请求参数 (Long)** | `"YeirYkxHuQ"` | `123456789L` | String → Long |
| **HTTP请求参数 (Integer)** | `"YeirYkxHuQ"` | `123456789` | String → Integer |
| **JSON序列化 (Long)** | `123456789L` | `"YeirYkxHuQ"` | Long → String |
| **JSON序列化 (Integer)** | `123456789` | `"YeirYkxHuQ"` | Integer → String |
| **集合类型** | `List<Long>` | `List<String>` | 批量转换 |
| **数组类型** | `long[]`, `int[]` | `String[]` | 数组转换 |
| **兼容纯数字** | `"123456789"` | `123456789L` / `123456789` | 向后兼容 |

### 2.3 支持的功能

✅ **Spring MVC参数绑定** - @RequestParam、@PathVariable自动转换
✅ **JSON序列化** - FastJson2、Jackson双支持
✅ **Swagger文档** - 自动修正API文档类型定义
✅ **多种数据类型** - Long/long、Integer/int全支持
✅ **集合类型** - List、Set自动批量转换
✅ **数组类型** - long[]、Long[]、int[]、Integer[]全支持
✅ **业务前缀** - 支持`"USER_"`、`"ORDER_"`等前缀
✅ **向后兼容** - 自动识别纯数字输入
✅ **类型安全** - 自动范围检查和负数验证

---

## 3. 支持的数据类型

### 3.1 基本数据类型

OpenID 注解支持以下基本数据类型的自动转换：

| 数据类型 | 说明 | 取值范围 | 示例 |
|---------|------|---------|------|
| `Long` | 包装类型 | -2^63 ~ 2^63-1 | `@OpenId private Long userId;` |
| `long` | 基本类型 | -2^63 ~ 2^63-1 | `@OpenId @PathVariable long id` |
| `Integer` | 包装类型 | -2^31 ~ 2^31-1 | `@OpenId private Integer orderId;` |
| `int` | 基本类型 | -2^31 ~ 2^31-1 | `@OpenId @PathVariable int id` |

> **⚠️ 重要约束**: 所有ID值必须为**非负数**(≥ 0),负数将会抛出异常

### 3.2 集合数据类型

支持以下集合类型的批量自动转换：

| 集合类型 | 说明 | 示例 |
|---------|------|------|
| `List<Long>` | Long类型列表 | `@OpenId private List<Long> userIds;` |
| `List<Integer>` | Integer类型列表 | `@OpenId private List<Integer> orderIds;` |
| `Set<Long>` | Long类型集合 | `@OpenId private Set<Long> productIds;` |
| `Set<Integer>` | Integer类型集合 | `@OpenId private Set<Integer> categoryIds;` |

**转换示例:**

```java
// 输入 (Java对象)
@OpenId
private List<Long> userIds = Arrays.asList(123L, 456L, 789L);

// 输出 (JSON)
{
  "userIds": ["YeirYkxHuQ", "AbCdEfGhIj", "XyZ123456"]
}
```

### 3.3 数组数据类型

支持以下数组类型的批量自动转换：

| 数组类型 | 说明 | 示例 |
|---------|------|------|
| `long[]` | long基本类型数组 | `@OpenId private long[] userIds;` |
| `Long[]` | Long包装类型数组 | `@OpenId private Long[] userIds;` |
| `int[]` | int基本类型数组 | `@OpenId private int[] orderIds;` |
| `Integer[]` | Integer包装类型数组 | `@OpenId private Integer[] orderIds;` |

**转换示例:**

```java
// 输入 (Java对象)
@OpenId
private long[] productIds = {123L, 456L, 789L};

// 输出 (JSON)
{
  "productIds": ["YeirYkxHuQ", "AbCdEfGhIj", "XyZ123456"]
}
```

### 3.4 类型转换规则

#### 3.4.1 序列化转换 (Java → JSON)

```java
// 单一值
Long id = 123456789L;           → "YeirYkxHuQ"
Integer orderId = 999;          → "Xy7Z9aBc"

// 集合类型
List<Long> ids = [123L, 456L];  → ["YeirYkxHuQ", "AbCdEfGhIj"]
Set<Integer> orders = {1, 2};   → ["A1B2C3", "D4E5F6"]

// 数组类型
long[] ids = {123L, 456L};      → ["YeirYkxHuQ", "AbCdEfGhIj"]
```

#### 3.4.2 反序列化转换 (JSON → Java)

```java
// 单一值
"YeirYkxHuQ" → Long id = 123456789L
"Xy7Z9aBc"   → Integer orderId = 999

// 兼容纯数字输入(向后兼容)
"123456789"  → Long id = 123456789L
"999"        → Integer orderId = 999
```

### 3.5 类型安全保证

#### 3.5.1 Integer范围检查

当目标类型为 Integer 时,会自动进行范围检查:

```java
// ✅ 正常转换
Long value = 100L;
Integer result = convert(value);  // 100

// ❌ 抛出异常 - 值超出Integer范围
Long value = 3000000000L;  // > Integer.MAX_VALUE
Integer result = convert(value);  // ParseException: Value too large for Integer
```

#### 3.5.2 负数验证

所有ID值都会进行负数检查:

```java
// ❌ 抛出异常 - 不允许负数
@GetMapping("/{id}")
public UserVO getUser(@OpenId @PathVariable Long id) {
    // 如果前端传递负数,会抛出异常
}

// 错误信息: "ID cannot be negative: -123"
```

#### 3.5.3 空值处理

null值会被正常处理,不进行转换:

```java
@OpenId
private Long userId = null;  // 序列化后仍为 null

@OpenId
private List<Long> ids = null;  // 序列化后仍为 null
```

---

## 4. 使用方式

### 4.1 控制器层使用

#### 4.1.1 路径参数转换

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping("/{orderId}")
    public OrderVO getOrder(@OpenId @PathVariable Long orderId) {
        // 前端请求：/api/orders/YeirYkxHuQ
        // 控制器收到：orderId = 123456789L
        return orderService.findById(orderId);
    }

    @GetMapping("/user/{userId}")
    public List<OrderVO> getUserOrders(@OpenId @PathVariable Long userId) {
        // 前端请求：/api/orders/user/JgWGVqQJpf
        // 控制器收到：userId = 987654321L
        return orderService.findByUserId(userId);
    }
}
```

#### 4.1.2 请求参数转换

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @GetMapping("/search")
    public List<ProductVO> searchProducts(
        @OpenId @RequestParam(required = false) Long categoryId,
        @RequestParam String keyword) {

        // 前端请求：/api/products/search?categoryId=Xy7Z9aBc&keyword=手机
        // 控制器收到：categoryId = 555555L
        return productService.search(categoryId, keyword);
    }
}
```

### 4.2 实体类使用

#### 4.2.1 基本字段标注

```java
public class UserVO {
    @OpenId
    private Long id;

    @OpenId
    private Long departmentId;

    private String name;
    private String email;

    // Getters and Setters
}
```

#### 4.2.2 集合类型字段

```java
public class OrderVO {
    @OpenId
    private Long id;

    @OpenId
    private Long userId;

    // List类型 - 批量转换
    @OpenId
    private List<Long> productIds;

    // Set类型 - 批量转换
    @OpenId
    private Set<Integer> categoryIds;

    private List<OrderItemVO> items;
}
```

#### 4.2.3 数组类型字段

```java
public class ProductVO {
    @OpenId
    private Integer id;

    // 基本类型数组
    @OpenId
    private long[] relatedProductIds;

    // 包装类型数组
    @OpenId
    private Integer[] tagIds;

    private String name;
    private BigDecimal price;
}
```

### 4.3 MyBatis Plus集成

```java
@Data
@TableName("users")
public class UserEntity {
    @TableId(type = IdType.ASSIGN_ID)
    @OpenId
    private String id;  // 数据库存储OpenID字符串

    @TableField(typeHandler = OpenIdTypeHandler.class)
    private String departmentId;  // 外键字段使用类型处理器

    private String name;
}
```

---

## 5. 配置说明

### 5.1 基础配置

```yaml
ldx2t:
  commons:
    openid:
      # FastJson2 配置
      fastjson2:
        enabled: true           # 是否启用FastJson2支持
        default-format: true    # 是否使用默认格式化器

      # Jackson 配置
      jackson:
        enabled: true           # 是否启用Jackson支持
        auto-detect: true       # 是否自动检测Jackson环境

      # Swagger 配置
      swagger:
        enabled: true           # 是否启用Swagger文档适配
        auto-schema: true       # 是否自动修改Schema定义
        example-value: "Xy7Z9aBc..."  # Swagger示例值
```

### 5.2 JSON框架选择

```yaml
# 如果只使用FastJson2
ldx2t:
  commons:
    openid:
      fastjson2:
        enabled: true
      jackson:
        enabled: false

# 如果只使用Jackson
ldx2t:
  commons:
    openid:
      fastjson2:
        enabled: false
      jackson:
        enabled: true

# 如果同时使用（推荐）
ldx2t:
  commons:
    openid:
      fastjson2:
        enabled: true
      jackson:
        enabled: true
```

### 5.3 集成配置示例

```java
@Configuration
public class WebMvcConfig {

    @Bean
    public OpenIdFormatterFactory openIdFormatterFactory() {
        return new OpenIdFormatterFactory();
    }

    @Bean
    public OpenIdAnnotationFilter openIdAnnotationFilter() {
        return new OpenIdAnnotationFilter();
    }
}
```

---

## 6. 最佳实践

### 6.1 使用建议

#### ✅ 推荐做法

```java
// 1. 在API入口处统一使用@OpenId
@RestController
public class UserController {
    @GetMapping("/{id}")
    public UserVO getUser(@OpenId @PathVariable Long id) { ✅
        return userService.findById(id);
    }
}

// 2. 在响应VO中标注需要混淆的字段
public class UserVO {
    @OpenId
    private Long id;                    ✅ 敏感ID字段

    private String name;                ✅ 普通字段无需标注
}
```

#### ❌ 避免的做法

```java
// 1. 不要在内部服务调用中使用@OpenId
@Service
public class UserService {
    public User findUser(@OpenId Long id) { ❌ 内部服务直接使用Long
        return userRepository.findById(id);
    }
}

// 2. 不要在非API层使用@OpenId
public class UserEntity {
    @OpenId
    private Long id; ❌ 数据库层使用原始Long类型
}
```

### 6.2 前端集成指南

#### 6.2.1 JavaScript处理

```javascript
// 前端收到的是字符串类型OpenID
const userId = "YeirYkxHuQ";  // 不是数字！

// 发送请求时直接使用字符串
fetch(`/api/users/${userId}`)
    .then(response => response.json())
    .then(user => {
        console.log(user.id);  // "Xy7Z9aBc..." - 字符串
        console.log(typeof user.id);  // "string"
    });
```

#### 6.2.2 TypeScript类型定义

```typescript
interface User {
    id: string;        // OpenID 字符串
    departmentId: string;
    name: string;
}

interface CreateUserRequest {
    name: string;
    departmentId: string;  // 前端发送OpenID字符串
}
```

### 6.3 性能优化

```yaml
# 生产环境建议配置
ldx2t:
  commons:
    openid:
      fastjson2:
        enabled: true
        cache-enabled: true  # 启用转换结果缓存

      jackson:
        enabled: false      # 优先使用FastJson2提高性能
```

### 6.4 安全考虑

1. **定期更换混淆算法**：在生产环境中定期更新混淆盐值
2. **访问日志记录**：记录包含OpenID的API访问日志
3. **前端验证**：前端验证OpenID格式但不进行业务逻辑判断

---

## 7. 故障排查

### 7.1 常见问题

#### 问题1：参数转换失败

**症状**：
```
2025-12-06 10:00:00 ERROR [] c.l.c.o.f.OpenIdFormatterFactory - Invalid OpenID: INVALID_VALUE
```

**解决方案**：
1. 检查前端发送的OpenID格式是否正确
2. 确认是否启用了对应的JSON框架支持
3. 验证OpenID是否被意外修改

#### 问题2：Swagger文档类型错误

**症状**：
```json
{
  "parameters": [
    {
      "name": "id",
      "schema": {
        "type": "integer",  // 应该是 "string"
        "format": "int64"
      }
    }
  ]
}
```

**解决方案**：
```yaml
ldx2t:
  commons:
    openid:
      swagger:
        enabled: true  # 确保启用Swagger适配
```

#### 问题3：JSON序列化不生效

**症状**：
```json
{
  "id": 123456789,  // 应该是 "YeirYkxHuQ"
  "name": "张三"
}
```

**解决方案**：
```java
// 检查JSON序列化配置
@RestController
public class UserController {

    // 方式1：手动指定过滤器
    @GetMapping("/user/{id}")
    public UserVO getUser(@PathVariable Long id) {
        UserVO user = userService.findById(id);
        return user; // 确保配置了OpenIdAnnotationFilter
    }
}
```

### 7.2 调试技巧

#### 7.2.1 启用详细日志

```yaml
logging:
  level:
    com.ldx2t.commons.openid: DEBUG
    com.ldx2t.commons.id: DEBUG
```

#### 7.2.2 调试输出

```java
// 手动测试OpenID转换
@Component
public class OpenIdDebugComponent {

    public void debugConversion() {
        long originalId = 123456789L;
        String openId = IdObfuscator.toOpenId(originalId);
        System.out.println("Original: " + originalId + " -> OpenID: " + openId);

        long restoredId = IdObfuscator.fromOpenId(openId);
        System.out.println("OpenID: " + openId + " -> Restored: " + restoredId);
    }
}
```

---

## 8. 高级用法

### 8.1 自定义业务前缀

```java
public class CustomOpenIdAnnotationFilter extends OpenIdAnnotationFilter {

    @Override
    public Object process(BeanContext context, Object object, String name, Object value) {
        // 自定义前缀逻辑
        if (value instanceof Long && isOrderIdField(context)) {
            long id = (Long) value;
            return "ORDER_" + IdObfuscator.toOpenId(id);
        }

        return super.process(context, object, name, value);
    }

    private boolean isOrderIdField(BeanContext context) {
        return "orderId".equals(context.getName()) ||
               context.getName().endsWith("OrderId");
    }
}
```

### 8.2 自定义验证规则

```java
@Component
public class CustomOpenIdValidator {

    public boolean isValidOpenId(String openId) {
        // 自定义验证逻辑
        if (openId == null || openId.isEmpty()) {
            return false;
        }

        // 检查格式、长度、业务规则等
        return openId.matches("^[A-Za-z0-9]{10,20}$");
    }
}
```

### 8.3 批量转换优化

```java
@Service
public class BatchConversionService {

    // List<Long> 批量转换
    public List<String> batchToOpenId(List<Long> ids) {
        return ids.stream()
            .map(IdObfuscator::toOpenId)
            .collect(Collectors.toList());
    }

    // Set<Integer> 批量转换
    public Set<String> batchIntegerToOpenId(Set<Integer> ids) {
        return ids.stream()
            .map(id -> IdObfuscator.toOpenId(id.longValue()))
            .collect(Collectors.toSet());
    }

    // 数组批量转换
    public String[] batchArrayToOpenId(long[] ids) {
        return Arrays.stream(ids)
            .mapToObj(IdObfuscator::toOpenId)
            .toArray(String[]::new);
    }

    // 从OpenID批量恢复
    public List<Long> batchFromOpenId(List<String> openIds) {
        return openIds.stream()
            .map(openId -> {
                try {
                    return IdObfuscator.fromOpenId(openId);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid OpenID: " + openId, e);
                }
            })
            .collect(Collectors.toList());
    }
}
```

### 8.4 集合类型完整示例

#### 8.4.1 Controller层使用集合类型

```java
@RestController
@RequestMapping("/api/batch")
public class BatchOperationController {

    // 批量查询用户 - List类型
    @PostMapping("/users")
    public List<UserVO> batchGetUsers(@RequestBody @OpenId List<Long> userIds) {
        // userIds自动从List<String>转换为List<Long>
        return userService.findByIds(userIds);
    }

    // 批量查询订单 - Set类型
    @PostMapping("/orders")
    public Set<OrderVO> batchGetOrders(@RequestBody @OpenId Set<Integer> orderIds) {
        // orderIds自动从Set<String>转换为Set<Integer>
        return orderService.findByIds(orderIds);
    }

    // 批量查询产品 - 数组类型
    @PostMapping("/products")
    public ProductVO[] batchGetProducts(@RequestBody @OpenId long[] productIds) {
        // productIds自动从String[]转换为long[]
        return productService.findByIds(productIds);
    }
}
```

#### 8.4.2 响应对象使用集合类型

```java
public class UserDetailVO {
    @OpenId
    private Long userId;

    private String username;

    // 用户的好友ID列表 - List<Long>
    @OpenId
    private List<Long> friendIds;

    // 用户加入的群组ID集合 - Set<Integer>
    @OpenId
    private Set<Integer> groupIds;

    // 用户收藏的文章ID数组 - long[]
    @OpenId
    private long[] favoriteArticleIds;

    // 序列化后:
    // {
    //   "userId": "YeirYkxHuQ",
    //   "username": "张三",
    //   "friendIds": ["AbCdEf", "XyZ123"],
    //   "groupIds": ["Gh789", "Ij456"],
    //   "favoriteArticleIds": ["Kl012", "Mn345"]
    // }
}
```

#### 8.4.3 请求对象使用集合类型

```java
public class BatchDeleteRequest {
    // 批量删除的用户ID - List<Long>
    @OpenId
    private List<Long> userIds;

    // 批量删除的订单ID - Set<Integer>
    @OpenId
    private Set<Integer> orderIds;

    private String reason;

    // 从前端接收:
    // {
    //   "userIds": ["YeirYkxHuQ", "AbCdEf"],
    //   "orderIds": ["XyZ123", "Gh789"],
    //   "reason": "用户注销"
    // }
    // 自动转换为:
    // userIds: [123456789L, 987654321L]
    // orderIds: [111, 222]
}
```

### 8.5 类型混合使用示例

```java
@RestController
@RequestMapping("/api/complex")
public class ComplexOperationController {

    @PostMapping("/transfer")
    public TransferResultVO transferData(
        @OpenId @RequestParam Long fromUserId,          // 单个Long
        @OpenId @RequestParam Integer toGroupId,         // 单个Integer
        @RequestBody @OpenId List<Long> itemIds) {      // List<Long>

        // fromUserId: Long类型自动转换
        // toGroupId: Integer类型自动转换
        // itemIds: List<Long>批量转换
        return dataService.transfer(fromUserId, toGroupId, itemIds);
    }
}

public class TransferResultVO {
    @OpenId
    private Long sourceUserId;              // 单个Long

    @OpenId
    private Integer targetGroupId;           // 单个Integer

    @OpenId
    private List<Long> successIds;          // List<Long>

    @OpenId
    private Set<Integer> failedIds;         // Set<Integer>

    @OpenId
    private long[] pendingIds;              // long[]

    private String message;
}
```

### 8.6 性能优化建议

#### 8.6.1 大批量数据处理

```java
@Service
public class LargeBatchService {

    // 优化1: 分批处理
    public List<String> convertLargeBatch(List<Long> ids) {
        int batchSize = 1000;
        return IntStream.range(0, (ids.size() + batchSize - 1) / batchSize)
            .mapToObj(i -> ids.subList(
                i * batchSize,
                Math.min((i + 1) * batchSize, ids.size())
            ))
            .flatMap(batch -> batch.stream()
                .map(IdObfuscator::toOpenId))
            .collect(Collectors.toList());
    }

    // 优化2: 并行处理
    public List<String> convertParallel(List<Long> ids) {
        return ids.parallelStream()
            .map(IdObfuscator::toOpenId)
            .collect(Collectors.toList());
    }
}
```

#### 8.6.2 缓存优化

```java
@Service
public class CachedConversionService {

    private final LoadingCache<Long, String> conversionCache = Caffeine.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build(IdObfuscator::toOpenId);

    public String toOpenIdCached(Long id) {
        return conversionCache.get(id);
    }

    public List<String> batchToOpenIdCached(List<Long> ids) {
        return ids.stream()
            .map(this::toOpenIdCached)
            .collect(Collectors.toList());
    }
}
```

---

## 📞 技术支持

如有问题，请联系：

- **维护团队**: 基础架构组
- **文档版本**: v1.0.0
- **实现版本**: v1.0.0-SNAPSHOT

---

**相关文档**:
- [分布式ID SDK使用指南](ldx2t-commons%20分布式%20ID%20SDK%20使用指南.md)
- [API统一响应与错误码SDK使用指南](API%20统一响应与错误码%20SDK%20(v1.0.0)%20使用指南.md)
- [分布式ID安全混淆技术方案](分布式%20ID%20安全混淆%20(OpenID)%20技术方案.md)