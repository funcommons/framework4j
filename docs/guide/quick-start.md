# 快速开始

## 1. 引入依赖

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-all</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

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

## 4. 运行 Demo

```bash
# 启动 demo 项目
mvn -pl framework4j-demo spring-boot:run

# 浏览器打开测试报告
open http://localhost:8080/v1/demo/report

# 浏览器打开配置工具
open http://localhost:8080/config-tool.html
```

## 下一步

- [架构总览](./architecture.md) — 16 模块关系图
- [模块指南](../modules/api.md) — 各模块详细使用
- [配置工具](../config/generator.md) — 在线生成 application.yml
