# framework4j-transport

&gt; HTTP 传输抽象 —— S2S 服务间调用（含重试）

## 简介

服务间调用（S2S）需要一个可替换的 HTTP 传输层：默认 RestTemplate 同步 + 重试，
可换 WebClient 响应式 / gRPC / 带鉴权装饰器。`HttpTransport` 接口把「传输协议」
从上层 Client 解耦 —— 上层只依赖接口，替换实现不改业务代码。

常配合 [framework4j-accesstoken](./accesstoken.md)（S2S JWT）与
[framework4j-signature](./signature.md)（HMAC 签名）组成完整 S2S 调用链。

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-transport</artifactId>
    <version>1.2.9</version>
</dependency>
```

`framework4j-all` 已聚合传递，用 all 无需单独引。

### 2. 自动装配

引入即自动装配默认 `RestTemplateHttpTransport`（RestTemplate 同步 + 重试 3 次/500ms）：

```yaml
framework4j:
  transport:
    enabled: true   # 默认 true
```

### 3. 注入使用

```java
@RequiredArgsConstructor
public class MyRemoteClient {
    private final HttpTransport transport;

    public Object call(String url, Object body) {
        return transport.post(url, body, Map.of("X-Trace-Id", "abc"));
    }
}
```

## 接口

```java
public interface HttpTransport {
    Object post(String url, Object body, Map<String, String> headers);
    Object get(String url, Map<String, String> headers);
    Object put(String url, Object body, Map<String, String> headers);
    Object delete(String url, Map<String, String> headers);
}
```

## 内置实现

| 实现 | 定位 | 装配 |
|---|---|---|
| `RestTemplateHttpTransport` | 同步 + 重试（默认 3 次/500ms） | **自动**（classpath 有 RestTemplate） |
| `WebClientHttpTransport` | 响应式非阻塞（高 QPS） | **手动**（WebClient 需配 baseUrl，业务方显式声明 Bean） |

### 重试与熔断

`RestTemplateHttpTransport` 内置网络抖动重试（默认 3 次，500ms 间隔，重试耗尽抛异常）。
熔断不内置 —— 由业务方自配（如 Resilience4j circuitbreaker）。

### 替换为 WebClient

```java
@Bean
public HttpTransport httpTransport(WebClient webClient) {
    return new WebClientHttpTransport(webClient);  // 业务方声明后, 默认 RestTemplate 实现自动让位
}
```

### 装饰器（S2S 鉴权 + 签名）

业务方可写装饰器包装 `RestTemplateHttpTransport`，注入 S2S JWT / HMAC 签名头：

```java
public class AuthenticatedHttpTransport implements HttpTransport {
    private final HttpTransport delegate;
    // post/get/put/delete 前自动注入 Authorization + X-Signature 等头
}
```

`@Bean` 声明装饰器后（或 `@Primary`），默认实现让位。

## 关键设计

### 自动装配

- 默认 `RestTemplateHttpTransport` 用 `@ConditionalOnMissingBean(HttpTransport.class)`，业务方声明任意 HttpTransport 即覆盖。
- 兜底 `RestTemplate`（业务方未声明时）用 `@ConditionalOnMissingBean(RestTemplate.class)`，无超时配置 —— **生产建议业务方声明带超时的 RestTemplate 覆盖**。
- 开关 `framework4j.transport.enabled`（默认 true）。

### 版本

- 引入：v1.2.9（自 benefit4j 双模式 remote 抽离回贡献）

## 相关文档

- [AccessToken 鉴权](./accesstoken.md) — S2S JWT
- [接口签名](./signature.md) — HMAC 签名防重放
- [配置参考](../config/reference.md)
