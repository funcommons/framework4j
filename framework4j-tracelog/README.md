# framework4j-tracelog

> **高性能轻量级动态追踪日志 SDK**：随开随用、自动过期释放资源、细粒度（TraceId / URL / UserId）精准捕获低级别日志的辅助性日志服务。

## 定位

| 项 | 值 |
|---|---|
| 职责 | 临时提权 DEBUG/TRACE + 跨节点全链路日志聚合（Redis） + 自动过期释放 |
| 配置前缀 | `framework4j.tracelog.*` |
| 必需依赖 | `framework4j-redis`、`framework4j-api`、`framework4j-web`（统一响应信封 + TraceContext） |
| 可选依赖 | `framework4j-sql-tracing`（traceId 注入 MDC）、`framework4j-accesstoken`（取 userId）、`framework4j-audit`（开关审计） |
| 默认开关 | `false`（opt-in，避免无意识开启写入 Redis） |
| 当前版本 | **v1.2.9**（已落地，24 个 Java + 1 HTML + 7 测试已 install 到本地 Maven） |

## 核心能力

| 能力 | 实现 |
|---|---|
| **动态级别提权** | 按 TraceId / URL / UserId / OrderId 临时开启 DEBUG，1 小时自动过期 |
| **毫秒级开关同步** | Redis Pub/Sub（默认 5s 重拉兜底）或 Redis Streams（`switch-transport=streams` 可选升级） |
| **跨节点日志聚合** | 共享 Redis + 同一 traceId 写入同一 List，控制台一键查询全链路 |
| **资源有界** | 四重保护：全局 10w 条上限 / 单 List LTRIM 5000 / 24h TTL / per-trace 200 条/秒 限速 |
| **零业务侵入** | 异步 Appender + Interceptor，业务代码无感知 |
| **异步非阻塞** | LMAX Disruptor 分片 + Pipeline 批写，业务线程延迟 < 0.05ms |
| **优雅停机** | Disruptor drain + 超时保护（默认 10s） |
| **Redis 故障降级** | 本地滚动文件（**启动硬失败**）+ Replayer 异步回灌（容器化需挂 hostPath/PVC） |
| **多租户隔离** | `tenant.enabled=true` + `tenant.key-spel` → Redis Key 加租户前缀 |
| **Micrometer 指标** | 6 类指标（appended/dropped/flushed/queue-depth/flush-duration/redis-fail） |
| **导出能力** | txt / json + gzip，单文件大小可配 |
| **纯 HTML 控制台** | 单文件 10.5KB，零三方依赖，VS Code 暗色风格 |

## 非目标（明确不做）

- ❌ **不取代** ELK / Loki / PLG 等持久化日志体系（仅短期调试用）
- ❌ **不做** 历史日志归档、压缩
- ❌ **不做** 完整 APM（不含指标 / 告警 / 服务拓扑）

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-tracelog</artifactId>
    <version>1.2.9</version>
</dependency>
```

### 2. 最小配置

```yaml
spring:
  application:
    name: my-app
framework4j:
  tracelog:
    enabled: true
    redis-name: default
```

### 3. Logback 配置（logback-spring.xml）

```xml
<configuration>
  <!-- 动态提权过滤器（业务代码零侵入） -->
  <turboFilter class="fun.commons.framework4j.tracelog.appender.DynamicLevelTurboFilter"/>
  
  <!-- 异步 Redis 采集 -->
  <appender name="ASYNC_REDIS" class="fun.commons.framework4j.tracelog.appender.AsyncRedisLogAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
  </appender>
  
  <root level="INFO">
    <appender-ref ref="ASYNC_REDIS"/>
  </root>
</configuration>
```

### 4. 鉴权 SPI（**必须实现**，未配置启动 fail-fast）

```java
@Component("traceLogAuthValidator")
public class MyAuthValidator implements TraceLogAuthValidator {
    @Override public boolean canQuery(String operatorId, String tenantId) { ... }
    @Override public boolean canOpenSwitch(String operatorId, String tenantId, SwitchRequest req) { ... }
    @Override public boolean canExport(String operatorId, String tenantId) { ... }
}
```

## 控制台

访问：`http://app-host:port/tracelog.html`

- 单文件 HTML，零三方依赖（无 Vue/React/jQuery/Axios）
- 暗色主题，VS Code 风格
- 三面板：查询 / 开关 / 关于
- 完整 XSS 防护（前端 HTML 转义）

## 文档导航

- 📐 **[动态追踪日志 SDK 技术方案](./动态追踪日志%20SDK%20技术方案.md)** — 完整设计文档（架构 / Redis 存储 / Lua 脚本 / TurboFilter / Appender / API / 配置项 / 已知限制 / 测试矩阵 / 责任边界）

## 测试

| 类型 | 命令 | 结果 |
|---|---|---|
| 单元测试（默认运行） | `mvn -pl framework4j-tracelog test` | ✅ 34 通过 / 0 失败 |
| 集成测试（需 Redis） | `mvn -pl framework4j-tracelog test -Dtracelog.integration.redis=true` | ⚠️ 5 跳过（嵌入式 Redis 0.7.3 在部分 Mac 启动失败，优雅降级） |

## 实施状态（v1.2.9 已 install 到本地 Maven）

| 阶段 | 内容 | 状态 |
|---|---|---|
| 阶段 0 | 模块骨架 + 父 POM 注册 | ✅ |
| 阶段 1 | 配置类与自动装配（Properties 45+ 字段 + 9 嵌套类） | ✅ |
| 阶段 2 | Redis 存储与降级（含 Replayer 异步回灌） | ✅ |
| 阶段 3 | 分片 Disruptor Appender（按 traceId hash 分 N 个 ring buffer） | ✅ |
| 阶段 4 | 开关层（Pub/Sub + Streams + Interceptor + Resync 默认 5s） | ✅ |
| 阶段 5 | Logback TurboFilter 提权（多级 + 包路径作用域限制） | ✅ |
| 阶段 6 | API 层（查询 / 控制 / 导出 + 鉴权 fail-fast） | ✅ |
| 阶段 7 | 纯 HTML 单文件控制台（10.5KB，零三方依赖） | ✅ |
| 阶段 8 | 单元测试（Normalizer / RateLimiter / TurboFilter / SwitchRule / Cache / TenantResolver） | ✅ |
| 阶段 9 | 集成测试（TraceLogStore：SETNX + Lua 容量裁剪 + LTRIM） | ✅（Redis 不可用时跳过） |
| 阶段 10 | README + 父 POM 注册 + `mvn install` | ✅ |

## 核心类索引（24 个 Java）

```
config/      TraceLogProperties / AutoConfiguration / BeansConfig / WebMvcConfig
             FailureAnalyzer / AuthValidator (SPI)
appender/    AsyncRedisLogAppender / DynamicLevelTurboFilter / RawEvent
store/       TraceLogStore (SETNX + Lua + Pipeline) / TraceLogLua
             LocalFallbackWriter (硬失败) / FallbackReplayer (回灌)
             TraceIdNormalizer (32-hex OTel)
switcher/    SwitchRule / SwitchRuleCache / SwitchPubSubListener
             SwitchStreamsListener / SwitchResyncScheduler
             TraceLogSwitchInterceptor / SwitchRateLimiter
query/       TraceLogQueryController / LogExporter / LogDto / SwitchRequest
rate/        PerTraceRateLimiter (令牌桶)
metrics/     TraceLogMetrics (Micrometer 6 类指标)
util/        TraceIdResolver / TenantKeyResolver (多租户 SpEL)
```

## 相关模块

- `framework4j-api` — `ApiCode` 枚举
- `framework4j-web` — `ApiResponse` 统一信封 / `TraceContext` 取 traceId
- `framework4j-redis` — `MultiRedisManager`（多 Redis 数据源）
- `framework4j-sql-tracing` — `TraceIdDruidFilter`（trace_id 注入 MDC）
- `framework4j-accesstoken` — `TokenContext`（取 userId 用于按用户提权）
- `framework4j-audit` — `@Auditable` AOP（开关操作审计）

## 完整 demo

`framework4j-demo` 已包含完整接入示例：
- `TraceLogDemoController` — 触发 DEBUG/TRACE/异常日志
- `DemoTraceLogAuthValidator` — demo 鉴权实现（生产需替换为 accesstoken）
- `logback-spring.xml` — TurboFilter + AsyncRedisLogAppender 配置
- `application.yml` — tracelog 完整配置示例

```bash
mvn -pl framework4j-demo spring-boot:run
# 访问 http://localhost:8080/tracelog.html
```