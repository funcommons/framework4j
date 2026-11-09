# 快速开始

## 1. 引入依赖

### 方式一：JitPack（推荐，免本地 install）

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.funcommons.framework4j</groupId>
    <artifactId>framework4j-all</artifactId>
    <version>v1.10.0</version>
</dependency>
```

### 方式二：本地 install（groupId 为 `fun.commons`）

```bash
git clone https://github.com/funcommons/framework4j.git && cd framework4j
mvn -DskipTests install
```

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-all</artifactId>
    <version>1.10.0</version>
</dependency>
```

> 只需要部分能力？按模块单独引入（artifactId 见 [模块总览](/modules/api)）——`framework4j-all`
> 不含 tenant（多租户是 opt-in 领域模块）。

## 2. 最小配置

```yaml
spring:
  application:
    name: my-app  # 必填（Redis key 前缀 + JWT iss）

framework4j:
  redis:
    enabled: true
    datasources:
      default:
        host: localhost
        port: 6379

  # v1.8 起显式启用（缺省不激活，issue #26）
  id:
    enabled: true
    worker:
      strategy: redis      # Redis 不可达自动降级 ip（fallback-to-ip 默认 true）
  openid:
    enabled: true
```

## 3. 使用注解

```java
@RestController
public class OrderController {

    @RateLimit(limit = 100, window = "1m", scope = "user")
    @RequiresSignature
    @Auditable(action = "CREATE_ORDER", targetType = "order", targetIdSpel = "#req.id")
    @PostMapping("/v1/api/orders")
    public ApiResponse<Order> createOrder(@RequestBody CreateOrderRequest req) {
        return ApiResponse.success(orderService.create(req));
    }
}
```

Controller 返回 `ApiResponse<T>` 即得统一信封（含 trace_id）；业务代码直接 throw，
`GlobalExceptionHandler` 统一翻译为 HTTP 200 + 业务码。

## 4. 运行 Demo

```bash
# 启动 demo 项目（需本地 Redis localhost:6379）
mvn -pl framework4j-demo spring-boot:run
```

## 下一步

- [架构总览](/guide/architecture) — 模块分层与装配关系
- [配置参考](/config/reference) — 全模块配置键速查
- 各模块文档见左侧「模块」导航；AI 编码工具可直接引入 [skills/framework4j](https://github.com/funcommons/framework4j/tree/main/skills/framework4j) 统一技能
