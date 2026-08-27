# framework4j-transport

HTTP 传输抽象（`@since 1.2.9`）：跨进程 HTTP 调用的统一门面 + 内置重试，供业务方与其他模块复用。`framework4j-all` 已聚合。

## 接口

```java
public interface HttpTransport {
    Object post(String url, Object body, Map<String, String> headers);
    Object get(String url, Map<String, String> headers);
    Object put(String url, Object body, Map<String, String> headers);
    Object delete(String url, Map<String, String> headers);
}
```

内置实现：

| 实现 | 说明 |
|---|---|
| `RestTemplateHttpTransport` | 默认自动装配。同步 RestTemplate + 重试（默认 3 次 / 500ms 间隔），重试耗尽抛 RuntimeException（业务方 catch）；熔断由业务方自配（如 Resilience4j） |
| `WebClientHttpTransport` | WebFlux 备选实现，业务方自行声明 Bean 即可替换（`@ConditionalOnMissingBean(HttpTransport.class)` 自动让位） |

## 配置

| 属性 | 默认 | 说明 |
|---|---|---|
| `framework4j.transport.enabled` | `true` | 关闭自动装配（不注册任何 Bean） |
| `framework4j.transport.rest-template-bean-name` | 空 | **v1.4.2（Issue #18）**：显式指定 HttpTransport 复用的 RestTemplate Bean 名。业务方声明多个 RestTemplate 时按类型注入存在歧义，配置本属性按名取用（Bean 不存在 / 类型不符时启动报错，错误信息即配置错误本身） |

## HttpTransport 复用哪个 RestTemplate？（v1.4.2 语义）

| 业务方 RestTemplate Bean | 解析结果 |
|---|---|
| 0 个 | 框架兜底实例（无超时配置，生产建议业务方声明带超时的 Bean 覆盖） |
| 1 个 | 复用该业务实例（框架兜底让位） |
| ≥2 个，其中 1 个 `@Primary` | 复用主 Bean |
| ≥2 个，配置了 `rest-template-bean-name` | 复用指定 Bean |
| **≥2 个，未配置且无 `@Primary`** | **降级内置默认实例 + WARN 列出候选 Bean 名**（v1.4.2 之前是 `NoUniqueBeanDefinitionException` 启动失败，即使业务方未使用 HttpTransport 也会触发） |

## 测试

`TransportAutoConfigurationTest`（6 个用例）锁定上表全部语义与 `enabled=false` 关闭途径。
