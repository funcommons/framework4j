
# framework4j-tracelog 动态追踪日志

按 TraceId/URL/UserId 临时提权 DEBUG/TRACE，日志异步聚合到共享 Redis，
开关 1h 自动过期。短期调试辅助，不取代 ELK/Loki。

## 接入三步

```yaml
# 1. application.yml
framework4j:
  tracelog:
    enabled: true        # 默认 false, opt-in
    redis-name: default
    elevation:
      allowed-packages: [com.yourcompany]   # 提权作用域限制
```

```xml
<!-- 2. logback-spring.xml: 只需业务包 DEBUG (TurboFilter/Appender 由 BeansConfig
     编程式注册挂 root, 在 logback 声明会因无无参构造启动失败) -->
<logger name="com.yourcompany" level="DEBUG"/>  <!-- 平时被 TurboFilter 拦下 -->
```

```java
// 3. 鉴权 SPI 必须实现, 否则启动 fail-fast (require-auth=true 时)
@Component("traceLogAuthValidator")
public class MyValidator implements TraceLogAuthValidator { ... }
```

## 使用

```bash
# 开开关 (默认 1h 过期)
POST /api/logs/switch  {"type":"TRACE_ID","value":"abc123","level":"DEBUG","ttlSeconds":3600}
# 查全链路 (跨节点聚合)
GET  /api/logs/trace/{traceId}?level=DEBUG&keyword=xxx
# 导出 (txt/json+gzip)
GET  /api/logs/trace/{traceId}/export?format=json
# 控制台: /tracelog/index.html
```

## 资源保护（默认）

全局 10w trace / 单 trace LTRIM 5000 / 24h TTL / per-trace 200 条/s 限速 /
Disruptor 分片批写（业务线程 <0.05ms）/ 停机 drain 10s。

## 注意

- `sync` 段（非 `switch`，Java 关键字）配开关同步：`sync.channel` / `sync.transport=pubsub|streams`。
- Redis 故障降级写本地文件并**启动硬失败**，容器化需挂 hostPath/PVC。
- 查询/开关/导出 API 在 `/api/logs/**`，记得纳入鉴权拦截（accesstoken 默认 `/**` 已覆盖）。
