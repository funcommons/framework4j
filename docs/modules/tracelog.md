# framework4j-tracelog

&gt; 动态追踪日志 —— 临时提权 DEBUG/TRACE + 跨节点全链路日志聚合（Redis）+ 自动过期释放

## 简介

线上排查偶发问题时，全量开 DEBUG 会打爆磁盘、拖垮性能。`framework4j-tracelog` 提供
「随开随用」的动态追踪：按 **TraceId / URL / UserId / OrderId** 精准对单次请求临时提权
DEBUG/TRACE，日志异步聚合到共享 Redis（多节点同一 traceId 写同一 List），开关 1 小时
自动过期释放资源，用完即走。

**定位是短期调试辅助**，不取代 ELK / Loki / PLG 等持久化日志体系。

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-tracelog</artifactId>
    <version>1.3.2</version>
</dependency>
```

`framework4j-all` 已聚合传递，用 all 无需单独引。

必需依赖：`framework4j-redis`（日志存储 + 开关同步）、`framework4j-web`（TraceContext 取 traceId）。

### 2. 最小配置

```yaml
spring:
  application:
    name: my-app
framework4j:
  tracelog:
    enabled: true          # 默认 false, opt-in
    redis-name: default
```

### 3. Logback 配置（logback-spring.xml）

> **不要在 logback-spring.xml 声明 TurboFilter / AsyncRedisLogAppender** ——
> 两者依赖 Spring Bean，由 `TraceLogBeansConfig` 编程式注册（Appender 自动挂 root）。
> logback 声明会因无无参构造抛 `NoSuchMethodException` 启动失败。

> 同时**不要把业务包 logger 设为 DEBUG** —— TurboFilter 在级别检查之前执行，
> 提权命中时 ACCEPT 直接放行（绕过级别）；logger 本身 DEBUG 会让未提权事件也全量输出。
> 业务包保持 INFO 即可：

```xml
<configuration>
  <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [traceId=%X{traceId:-}] - %msg%n</pattern>
    </encoder>
  </appender>
  <root level="INFO">
    <appender-ref ref="STDOUT"/>
  </root>
</configuration>
```

### 4. 鉴权 SPI（**必须实现**，`require-auth=true` 且未配置时启动 fail-fast）

```java
@Component("traceLogAuthValidator")
public class MyTraceLogAuthValidator implements TraceLogAuthValidator {
    @Override public boolean canQuery(String operatorId, String tenantId) { ... }
    @Override public boolean canOpenSwitch(String operatorId, String tenantId, SwitchRequest req) { ... }
    @Override public boolean canExport(String operatorId, String tenantId) { ... }
}
```

## 使用流程

```bash
# 1. 对某个 traceId 开 DEBUG（默认 1h 过期）
curl -X POST http://host/api/logs/switch \
  -H 'Content-Type: application/json' \
  -d '{"type":"TRACE_ID","value":"abc123","level":"DEBUG","ttlSeconds":3600}'

# 2. 触发业务请求（带该 traceId 或让框架生成后从响应头取）

# 3. 查询全链路日志（跨节点聚合）
curl http://host/api/logs/trace/abc123

# 4. 导出（txt / json + gzip）
curl -O http://host/api/logs/trace/abc123/export?format=json
```

或打开控制台 `http://host/tracelog/index.html`（单文件 HTML，零三方依赖，VS Code 暗色风格）。

## 核心能力

| 能力 | 实现 |
|---|---|
| 动态级别提权 | 按 TraceId / URL / UserId / OrderId，1h 自动过期（TTL 上限可配） |
| 开关毫秒级同步 | Redis Pub/Sub（默认）或 Streams（`sync.transport=streams`），5s 重拉兜底 |
| 跨节点日志聚合 | 共享 Redis，同一 traceId 写同一 List |
| 资源有界 | 全局 10w trace 上限 / 单 trace LTRIM 5000 / 24h TTL / per-trace 200 条/s 限速 |
| 异步非阻塞 | LMAX Disruptor 按 traceId hash 分片 + Pipeline 批写，业务线程延迟 &lt; 0.05ms |
| 优雅停机 | Disruptor drain + 超时保护（默认 10s） |
| Redis 故障降级 | 本地滚动文件（**启动硬失败**）+ Replayer 异步回灌（容器化需挂 hostPath/PVC） |
| 多租户隔离 | `tenant.enabled=true` + `tenant.key-spel` → Redis Key 加租户前缀 |
| Micrometer 指标 | appended / dropped / flushed / queue-depth / flush-duration / redis-fail |

## 关键设计

### 提权链路

```
开关 API → Redis SET log_switch:id:{type}:{value} (TTL) + Pub/Sub 广播
        → 各节点 SwitchRuleCache 更新
        → DynamicLevelTurboFilter 命中规则 → 该 trace 的 DEBUG/TRACE 放行
        → AsyncRedisLogAppender 采集 → RPUSH trace_log:{traceId} (Lua 容量裁剪)
```

- 提权作用域受 `elevation.allowed-packages` 限制，防止误提权第三方包打爆日志。
- `TraceIdNormalizer` 把 traceId 归一为 32-hex（OTel 格式），非标准 traceId 也能命中。

### 非目标（明确不做）

- ❌ 不取代 ELK / Loki 持久化日志体系（仅短期调试）
- ❌ 不做历史日志归档、压缩
- ❌ 不做完整 APM（无指标 / 告警 / 拓扑）

### 版本

- 引入：v1.3.0

## 相关文档

- [Redis 多数据源](./redis.md) — 日志存储与开关同步通道
- [SQL 追踪](./sql-tracing.md) — traceId 注入 MDC
- [AccessToken 鉴权](./accesstoken.md) — 按用户提权取 userId
- [审计日志](./audit.md) — 开关操作审计
- [配置参考](../config/reference.md)
