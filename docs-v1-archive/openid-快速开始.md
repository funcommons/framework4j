# OpenID 快速开始

> **3分钟上手** - 让前端安全访问 Long 型 ID，只需一个注解

---

## 1. 引入依赖

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-all</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## 2. 启用配置

```yaml
framework4j:
  openid:
    fastjson2:
      enabled: true
    jackson:
      enabled: false  # 使用 Jackson 时改为 true
    swagger:
      enabled: true
```

---

## 3. 使用 @OpenId

### Controller

```java
import fun.commons.framework4j.openid.annotation.OpenId;

@RestController
@RequestMapping("/api/users")
public class UserController {

    // 路径参数
    @GetMapping("/{id}")
    public ApiResponse<UserVO> getUser(@OpenId @PathVariable Long id) {
        return ApiResponse.success(userService.findById(id));
    }

    // 请求参数
    @GetMapping("/search")
    public List<UserVO> search(@OpenId @RequestParam Long deptId) {
        return userService.search(deptId);
    }

    // 批量操作
    @PostMapping("/batch")
    public List<UserVO> batchGet(@RequestBody @OpenId List<Long> ids) {
        return userService.findByIds(ids);
    }
}
```

### 响应对象 (VO)

```java
import fun.commons.framework4j.openid.annotation.OpenId;

public class UserVO {
    @Schema(description = "用户ID")  // ⚠️ 不要写 "用户ID (OpenID)"
    @OpenId
    private Long id;

    @OpenId
    private Long deptId;

    private String username;

    @OpenId
    private List<Long> friendIds;  // 集合类型
}
```

### 请求对象 (DTO)

```java
import fun.commons.framework4j.openid.annotation.OpenId;

public class CreateOrderRequest {
    @OpenId
    private Long userId;

    @OpenId
    private List<Long> productIds;

    private Integer quantity;  // Integer 型无需 @OpenId
}
```

---

## 4. 使用规范

### ✅ 必须使用

```java
import fun.commons.framework4j.openid.annotation.OpenId;

// Controller 参数
@GetMapping("/{id}")
public UserVO get(@OpenId @PathVariable Long id) { }

// 响应 VO
public class UserVO {
    @OpenId
    private Long id;
}

// 请求 DTO
public class CreateUserRequest {
    @OpenId
    private Long deptId;
}
```

### ❌ 不要使用

```java
// Service 层
@Service
public class UserService {
    public User findById(Long id) {  // 不加
        return userMapper.selectById(id);
    }
}

// 实体类
@TableName("t_user")
public class User {
    @TableId
    private Long id;  // 不加
}

// Integer 型状态码
public class OrderVO {
    @OpenId
    private Long id;        // ✅

    private Integer status; // ✅ 不加
}
```

---

## 5. Swagger 文档

### 正确写法

```java
@Schema(description = "用户ID")  // ✅
@OpenId
private Long id;

@Schema(description = "部门ID", example = "YeirYkxHuQ")  // ✅
@OpenId
private Long deptId;
```

### 错误写法

```java
@Schema(description = "用户ID (OpenID)")  // ❌ 删除 "(OpenID)"
@OpenId
private Long id;
```

---

## 6. 前端对接

### TypeScript

```typescript
interface UserVO {
  id: string;          // ⚠️ OpenID 是字符串
  deptId: string;
  username: string;
  friendIds: string[]; // 字符串数组
}

interface CreateOrderRequest {
  userId: string;
  productIds: string[];
  quantity: number;
}
```

### API 调用

```typescript
// 查询
const userId = "YeirYkxHuQ";
const response = await http.get<UserVO>(`/api/users/${userId}`);

// 创建
const request: CreateOrderRequest = {
  userId: "AbCdEf",
  productIds: ["P1", "P2"],
  quantity: 2
};
await http.post('/api/orders', request);

// 批量
const userIds = ["YeirYkxHuQ", "AbCdEf"];
await http.post<UserVO[]>('/api/users/batch', userIds);
```

---

## 7. 转换效果

### 后端 → 前端

```java
// 后端对象
UserVO vo = new UserVO();
vo.setId(123456789L);
vo.setFriendIds(Arrays.asList(111L, 222L));
```

```json
// 前端 JSON
{
  "id": "YeirYkxHuQ",
  "friendIds": ["AbCdEf", "GhIjKl"]
}
```

### 前端 → 后端

```json
// 前端 JSON
{
  "userId": "YeirYkxHuQ",
  "productIds": ["P123", "P456"]
}
```

```java
// 后端对象
request.getUserId();       // 123456789L
request.getProductIds();   // [111L, 222L]
```

---

## 8. 核心规则

| 规则 | 说明 |
|------|------|
| ✅ 对外接口全部用 | Controller 参数和返回值 |
| ✅ VO/DTO 的 Long 型 ID | 前端可见的对象 |
| ❌ 内部 Service 不用 | 保持内部逻辑清晰 |
| ❌ 数据库 Entity 不用 | 使用原始 Long |
| ❌ Integer 型慎用 | 仅安全敏感场景 |
| ❌ 文档不写 "OpenID" | 对前端透明 |

---

**完整文档**: [OpenID 模块使用指南](OpenID%20模块使用指南.md)
