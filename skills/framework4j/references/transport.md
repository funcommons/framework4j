
# framework4j-transport HTTP 传输

S2S 服务间调用的传输抽象。上层 Client 只依赖 `HttpTransport` 接口，
替换实现（RestTemplate/WebClient/装饰器）不改业务代码。

## 注入使用

```java
@RequiredArgsConstructor
public class MyRemoteClient {
    private final HttpTransport transport;

    public Object call(String url, Object body) {
        return transport.post(url, body, Map.of("X-Trace-Id", traceId));
    }
}
```

## 内置实现

| 实现 | 定位 | 装配 |
|---|---|---|
| `RestTemplateHttpTransport` | 同步 + 重试（默认 3 次/500ms） | 自动（classpath 有 RestTemplate） |
| `WebClientHttpTransport` | 响应式非阻塞 | 手动（业务方声明 WebClient Bean） |

## 替换 / 装饰

```java
// 换 WebClient
@Bean
public HttpTransport httpTransport(WebClient webClient) {
    return new WebClientHttpTransport(webClient);
}

// 装饰注入 S2S JWT + 签名
@Bean @Primary
public HttpTransport authedTransport(RestTemplate rt, ...) {
    return new AuthenticatedHttpTransport(new RestTemplateHttpTransport(rt), ...);
}
```

## 配置

```yaml
framework4j:
  transport:
    enabled: true   # 默认 true
```

默认实现 `@ConditionalOnMissingBean(HttpTransport.class)`，业务方声明即覆盖。
兜底 RestTemplate 无超时 —— 生产建议业务方声明带超时的覆盖。
