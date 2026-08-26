# 动态追踪日志 SDK 技术方案

> 模块代号：`framework4j-tracelog`
> 配置前缀：`framework4j.tracelog.*`
> 目标版本：v2.3.0
> 文档状态：**已实现**（v1.4.0，代码已落地并端到端冒烟通过 + 敏感字段脱敏，详见 §十三 运行链路验证）

---

## 一、背景与定位

### 1.1 问题域

微服务或分布式单体架构下，**开发调试 + 线上快速排障** 长期被两类痛点困扰：

| 痛点 | 表现 |
|---|---|
| **日志噪音 / 采样难** | INFO 全量打不现实；开 DEBUG 又担心线上 QPS 下沉；按业务 ID 临时开 DEBUG 又要重启 |
| **链路聚合弱** | 跨服务调用时，每个节点日志独立输出，单凭 TraceId grep 成本高；控制台只能跳板到 Kibana 拼凑 |
| **高风险调试能力缺失** | 缺一个"按 TraceId/UserId/URL 临时拉起全链路 DEBUG、1 小时自动失效"的安全工具 |

### 1.2 定位

> **随开随用、自动过期释放资源、细粒度（TraceId / URL / UserId）精准捕获低级别日志的辅助性动态日志服务。**

### 1.3 非目标（明确不做）

- ❌ **不取代** ELK / Loki / PLG 等持久化��志体系（仅短期调试用，不做审计溯源）
- ❌ **不做** 历史日志归档、压缩、转冷存储
- ❌ **不替代** TraceContext（traceId 的源头仍由 Micrometer Tracing 提供）
- ❌ **不构建** 完整 APM（不含指标/告警/服务拓扑）

### 1.4 核心优势

| 维度 | 优势 |
|---|---|
| **开箱成本** | 引入 starter + 一个 `logback-spring.xml` 引用 Appender，零业务代码侵入 |
| **资源可控** | 单链路 TTL 24h + 全局 10w 条上限 + 单 List 5000 条 LTRIM，永不撑爆 Redis |
| **粒度灵活** | 按 TraceId / URL / UserId / OrderId 任一维度"提权"开 DEBUG，1 小时自动失效 |
| **微服务天然聚合** | 借助 Micrometer Tracing 的 TraceId 透传 + 共享 Redis，跨节点日志自动聚合在同一 List |
| **毫秒级生效** | Pub/Sub 广播开关变更，毫秒级同步到所有微服务节点 |

---

## 二、总体设计

### 2.1 设计原则

1. **零侵入**：业务代码无需引入新注解，运行时由 Logback Appender + Interceptor 协同完成
2. **异步非阻塞**：日志落 Redis 全程异步（本地内存队列 + 批刷盘），业务线程不感知
3. **资源有界**：四道闸门（全局上限 / 单链路上限 / 单链表上限 / TTL）保证 Redis 不爆
4. **失败优雅降级**：Redis 不可用时本地缓冲继续累积（带最大内存上限），恢复后批量回灌
5. **共享 Redis 聚合**：天然支持多服务跨节点查询同一 TraceId 的全链路日志

### 2.2 核心架构总览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Microservice Node A                                 │
│                                                                             │
│  ┌─────────────────┐    ┌────────────────────┐    ┌────────────────────┐   │
│  │ Logback Logger  │───▶│ AsyncRedisAppender │───▶│ Local RingBuffer   │   │
│  │  (业务代码日志)  │    │ (提权过滤 + 序列化) │    │ (10K 条有界)       │   │
│  └─────────────────┘    └─────────┬──────────┘    └─────────┬──────────┘   │
│                                   │                           │             │
│                                   │                           │             │
│                                   ▼                           ▼             │
│                          ┌──────────────────────────────────────────────┐   │
│                          │  Async Batch Worker (后台单线程 + Pipeline)   │   │
│                          │  - 500ms / 500 条刷盘阈值                     │   │
│                          │  - Lua 全局容量管控                           │   │
│                          │  - LTRIM 单链表防爆                          │   │
│                          └────────────────────┬─────────────────────────┘   │
│                                               │                              │
└───────────────────────────────────────────────┼──────────────────────────────┘
                                                │
                          ┌─────────────────────▼────────────────────┐
                          │           Redis (共享 / 主从)             │
                          │                                           │
                          │  trace_log:{traceId}     → List<JSON>     │
                          │  trace_global_queue      → List<traceId>  │
                          │  log_switch:id:user:*   → String TTL 1h  │
                          │  channel:log_switch     → Pub/Sub       │
                          └──────────────────────────────────────────┘
                                                ▲
                                                │ (Broadcast)
                          ┌─────────────────────┴────────────────────┐
                          │   Microservice Node B / C / D ...        │
                          │   (同样挂载, 同 traceId 写入同一 List)     │
                          └──────────────────────────────────────────┘
                                                ▲
                                                │
                          ┌─────────────────────┴────────────────────┐
                          │  Console / Query API (任一节点)          │
                          │  - GET /api/logs/trace/{traceId}          │
                          │  - POST /api/logs/switch (提权开关)      │
                          └──────────────────────────────────────────┘
```

### 2.3 与 framework4j 现有模块的集成关系

```
framework4j-tracelog
  ├── 强依赖: framework4j-redis        (共享 Redis + MultiRedisManager)
  ├── 强依赖: framework4j-api          (ApiCode 枚举)
  ├── 强依赖: framework4j-web          (ApiResponse 统一信封 + TraceContext 取 traceId)
  ├── 可选依赖: framework4j-sql-tracing (Druid Filter 注入的 trace_id 已经在 MDC)
  └── 借助: Micrometer Tracer             (traceId 来源, SDK 不引入, 由消费方配置)
```

> 关键点：`framework4j-tracelog` **强依赖 framework4j-web**（因为需要 `ApiResponse` 统一响应格式）。
> `framework4j-tracelog` **不强制依赖** Micrometer Tracing，而是从 MDC 取 traceId。
> 已有 `framework4j-sql-tracing` 的项目天然兼容（其 `TraceIdDruidFilter` 默认已经把 traceId 写进 MDC）；
> 未引入 `framework4j-sql-tracing` 但配置了 Micrometer 的项目也能直接工作。

---

## 三、详细设计

### 3.1 微服务架构适配与全链路追踪

#### 3.1.1 TraceId 透传（基于 Micrometer Tracing）

在微服务环境中，请求会穿透多个节点。本方案的聚合基础依赖以下机制：

- **TraceId 来源**：`framework4j-api` 的 `TraceContext.getTraceId()` 优先；fallback 到 SLF4J MDC 的 `traceId` 键
- **透传机制**：Micrometer Tracing（OpenTelemetry 适配）通过 HTTP Header（`X-B3-TraceId` / `traceparent`）向下游透传
- **统一 Key**：所有服务打印日志时携带同一 traceId，写入 Redis 时用同一 Key，自然实现跨服务日志聚合

#### 3.1.2 中心化聚合

所有微服务节点连接**同一个 Redis 实例**。按 traceId 作为 Key 写入时，天然实现跨服务链路日志的聚合。
控制台只需查一个 traceId，就能拿到完整调用链上所有节点的全量日志。

#### 3.1.3 全局开关同步（Pub/Sub）

> 利用 Redis Pub/Sub 实现管控中心 → 所有节点的毫秒级配置同步。

```
Console (POST /api/logs/switch)
  │
  ├─▶ SET log_switch:id:user:{userId}  EX 3600
  │
  └─▶ PUBLISH channel:log_switch  payload:{type:"user", value:"{userId}", level:"DEBUG"}
         │
         ├─▶ Node A: RedisMessageListener → 本地内存 Cache 更新 → Interceptor 后续命中
         ├─▶ Node B: 同上
         └─▶ Node C: 同上
```

| 关键点 | 说明 |
|---|---|
| **广播频道** | `framework4j.tracelog.sync.channel`（可配置，默认 `channel:log_switch`） |
| **Payload** | `{type: "user"\|"url"\|"trace", value: "...", level: "DEBUG"}` |
| **本地缓存** | Caffeine `Cache<String, SwitchRule>`（容量 10w，TTL 同 Redis 一致） |
| **本地兜底** | 启动时一次性 `SCAN + MGET log_switch:*` 加载已存在的开关；后续只靠 Pub/Sub 增量 |
| **降级策略** | Redis Pub/Sub 连接断开时，**每 5s 重新全量拉取一次**（`framework4j.tracelog.sync.resync-interval-seconds`，默认 5s），窗口期变化最多延迟 5s 生效 |
| **零窗口重拉**（v1.3.3） | 重拉用 `SwitchRuleCache#replaceAll` **diff 合并**（新规则先 put、仅精准 invalidate 失效项），不 clear —— 周期重拉不再造成提权请求瞬时 miss（并发读 0 miss 有单测锁定） |
| **推荐升级** | 高可用场景建议改用 Redis Streams（`XADD` + 消费者组）取代 Pub/Sub，天然 ack + 持久化，详见 §3.1.4 |

#### 3.1.4 高可用开关同步（Redis Streams，可选升级）

> Pub/Sub 是 fire-and-forget，断连期间变更丢失**，即便 5s 重拉也有窗口期。零容忍场景建议用 Redis Streams。

```
控制台 → XADD framework4j:tracelog:switch-stream * type user value 10086 level DEBUG
                                          │
              ┌───────────────────────────┼───────────────────────────┐
              ▼                           ▼                           ▼
       Node A (消费者组)          Node B (消费者组)          Node C (消费者组)
       XREADGROUP > nodeA-id      XREADGROUP > nodeB-id      XREADGROUP > nodeC-id
       → 本地 Caffeine 更新       → 本地 Caffeine 更新       → 本地 Caffeine 更新
       XACK                       XACK                       XACK
```

| 优势 | 说明 |
|---|---|
| **不丢消息** | Streams 持久化，节点断连期间消息不丢，重连后从 `last-delivered-id` 继续 |
| **每个节点独立消费** | 消费者组 + 每个节点独立 consumer name，避免重复消费 |
| **自动截断** | `MAXLEN ~ 10000` 控制 stream 长度，避免内存无限增长 |

> 通过 `framework4j.tracelog.sync.transport = pubsub \| streams`（默认 `pubsub`）切换。

#### 3.1.5 TraceId 标准化（OTel 32-hex）

不同 Tracing 实现的 traceId 长度差异巨大：

| 来源 | 长度 | 示例 |
|---|---|---|
| W3C Trace Context (OTel) | 32 hex (128bit) | `4bf92f3577b34da6a3ce929d0e0e4736` |
| Zipkin B3 | 16 或 32 hex | `aaaabbbb-cccc-dddd` 或 `aaaabbbbccccddddeeeeffffgggghhhh` |
| 自定义 | 不定 | `t-1234567890` |

**统一规范**：
- `TraceIdResolver` 在首次记录时**统一格式化为 32 位小写 hex**（不足 32 位左补 0）
- 不匹配的 traceId 在首次记录时打 WARN（提示可能是非 OTel 链路）
- Redis Key 长度固定 32 字符，便于容量估算

### 3.2 存储设计与容量管控（基于 Redis + Lua）

#### 3.2.1 Redis Key 设计

| Key | 类型 | 说明 |
|---|---|---|
| `trace_log:{traceId}` | List | 单次请求的全量日志（JSON 字符串） |
| `trace_global_queue` | List | FIFO 队列，记录所有产生过日志的 traceId |
| `log_switch:{type}:{value}` | String | 临时提权开关（带 TTL） |
| `channel:log_switch` | Pub/Sub 频道 | 开关变更��播 |

#### 3.2.2 四重保护机制

| 保护 | 机制 | 默认值 | 触发条件 | 配置项 |
|---|---|---|---|---|
| **短期有效兜底** | `EXPIRE` | 86400s（24h） | 首次写入 `trace_log:{traceId}` 时设置 | `trace-ttl-seconds` |
| **全局容量限制** | Lua 脚本弹出队头 | 100000 条 | 全局队列超过上限时 | `global-max-traces` |
| **单链路防死循环** | `LTRIM` | 5000 条 | 每次 `RPUSH` 后立即裁剪 | `single-trace-max-logs` |
| **单 trace 写入限速** | Java 令牌桶 | 200 条/秒 | 防业务死循环 / 日志炸弹 | `rate-limit-per-trace-per-second` |

> **所有阈值均通过 `framework4j.tracelog.*` 配置项暴露，**Lua 脚本不接受硬编码常量。

#### 3.2.3 容量管控 Lua 脚本（首次写入时执行）

> v1.3.3：SETNX 判定移入脚本内部（KEYS[3]），原子判定 + 仍单次 pipeline 往返。

```lua
-- KEYS[1] = 当前 traceId 的 key (trace_log:{traceId})
-- KEYS[2] = 全局队列 key (trace_global_queue 或分片)
-- KEYS[3] = 分布式首次标记 key (trace_log:{traceId}:meta)
-- ARGV[1] = 全局最大容量 (来自配置 storage.global-max-traces, 默认 100000)
-- ARGV[2] = 过期秒数   (来自配置 storage.trace-ttl-seconds, 默认 86400)

redis.call('EXPIRE', KEYS[1], ARGV[2])

local first = redis.call('SET', KEYS[3], '1', 'EX', tonumber(ARGV[2]), 'NX')
if first then
    redis.call('RPUSH', KEYS[2], KEYS[1])

    local len = redis.call('LLEN', KEYS[2])
    if len > tonumber(ARGV[1]) then
        local oldest_key = redis.call('LPOP', KEYS[2])
        if oldest_key then
            redis.call('DEL', oldest_key)
        end
    end
end
return 1
```

> 关键设计：
> - `ARGV[1]` 和 `ARGV[2]` 均为启动时从 `framework4j.tracelog.*` 注入，**零硬编码**
> - `EXPIRE` 幂等（每次刷新 TTL），`RPUSH` 仅在 SET NX 成功（全局首次）时执行
> - 全局队列超过上限时弹出**最老的** traceId 并彻底删除其日志 List，避免 Redis 内存单调增长
> - `trace_global_queue` 自身不需要 TTL（其长度受 LLEN 限制）

#### 3.2.4 分布式"首次写入"判定（SETNX）

> **问题**：本地 Caffeine `traceIdCache` 仅本节点有效，多节点同时首次写同一 traceId 时，全局队列会被重复 RPUSH。
>
> **解决**：`SET trace_log:{traceId}:meta 1 EX {ttl} NX` 作为**跨节点**的分布式首次标记。

```
本节点 Caffeine 不命中（firstSeen）→ pipeline 内执行 Lua
   ├─ 脚本内 SET NX 成功 → 真首次 → RPUSH 全局队列 + 容量裁剪
   └─ 脚本内 SET NX 失败 → 其他节点已写过 → 跳过入队，仅保留 RPUSH 日志 + LTRIM
```

> **为什么 SETNX 必须在 Lua 内部**（v1.3.2 实测 bug → v1.3.3 修复）：
> 若在 pipeline 里单独发 SETNX 再盲发 Lua，SETNX 的返回值无从消费（pipeline 异步批量返回），
> Lua 每次仍会 RPUSH —— 多节点场景同一 traceId 重复入队（集成测试
> `TraceLogStoreIntegrationTest#setnxFirstTime` 锁定该行为：5 次重复 flush 队列长度必须 = 1）。

| 优势 | 说明 |
|---|---|
| **节省 Redis CPU** | 入队 + 裁剪仅在真正首次时执行，多节点不会重复 |
| **无锁竞争** | SET NX 单命令原子，无需事务 / WATCH |
| **TTL 自清理** | 标记 key 与 trace 同生命周期，无需单独清理 |
| **单往返** | 判定与入队在同一脚本内完成，pipeline 不加额外 RTT |

### 3.3 临时日志开关设计（最长 1 小时自动过期���

> 目的：不影响全局性能的前提下，针对特定维度临时开启 Debug/Trace 级别。

#### 3.3.1 开关存储与过期

后台开启时，往 Redis 写入带 TTL（最大 3600 秒）的 Key：

| 维度 | Key 格式 | 示例 |
|---|---|---|
| User | `log_switch:id:user:{userId}` | `log_switch:id:user:10086` |
| Trace | `log_switch:id:trace:{traceId}` | `log_switch:id:trace:abc123` |
| URL | `log_switch:id:url:{pattern}` | `log_switch:id:url:/v1/orders/**` |
| Order | `log_switch:id:order:{orderId}` | `log_switch:id:order:OD20260824001` |

> 强制约束：`TTL ≤ 3600 秒`（1 小时），防止恶意开启后忘记关闭导致 Redis 长期占用。

#### 3.3.2 请求拦截与 MDC 注入

`TraceLogSwitchInterceptor`（基于 Spring `HandlerInterceptor`）：

1. 拦截所有命中路径（默认 `/api/**`）
2. 从 `TokenContext` 或 Header 取 `userId`、`traceId`、请求 URL
3. 查询本地内存 Caffeine 缓存（Pub/Sub 已同步），命中后：
   - 计算目标级别（取所有命中规则中的最高级别）
   - 写入 MDC.put("DYNAMIC_LOG_LEVEL", "DEBUG")
   - 写入 MDC.put("DYNAMIC_LOG_DIMERS", "user:10086,trace:abc") 便于审计
4. **MDC 清理（关键）**：`preHandle` 使用 `MDCCloseable`，`afterCompletion` 必须 `try-finally` 关闭，杜绝线程复用导致的级别污染
   ```java
   MDCCloseable levelMdc = null;
   MDCCloseable dimersMdc = null;
   try {
       levelMdc = MDC.putCloseable("DYNAMIC_LOG_LEVEL", "DEBUG");
       dimersMdc = MDC.putCloseable("DYNAMIC_LOG_DIMERS", "user:10086,trace:abc");
       return true;
   } finally {
       // 注意: afterCompletion 里 close, preHandle 不提前 close
       // 此处仅展示 MDCCloseable 用法
   }
   // afterCompletion:
   if (levelMdc != null) levelMdc.close();
   if (dimersMdc != null) dimersMdc.close();
   ```

#### 3.3.3 Logback 动态提权（TurboFilter）

在 Logback 中配置自定义 `TurboFilter`：

```java
public class DynamicLevelTurboFilter extends TurboFilter {

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level,
                              String format, Object[] params, Throwable t) {
        // 1. 业务全局配置为 INFO/WARN/ERROR 时, 直接放行 (走原有逻辑)
        if (level.isGreaterOrEqual(logger.getEffectiveLevel())) {
            return FilterReply.NEUTRAL;
        }

        // 2. 当前请求线程 MDC 中存在提权标记
        String dynamicLevel = MDC.get("DYNAMIC_LOG_LEVEL");
        if (dynamicLevel == null) {
            return FilterReply.DENY;  // 未提权, 拦截
        }

        // 3. 比较提权级别与当前日志级别 (支持 DEBUG/TRACE 多级)
        Level targetLevel = Level.toLevel(dynamicLevel, Level.DEBUG);
        if (level.isGreaterOrEqual(targetLevel)) {
            return FilterReply.ACCEPT;  // 提权到 DEBUG 后, TRACE 也允许
        }
        return FilterReply.DENY;
    }
}
```

| 关键点 | 说明 |
|---|---|
| **作用** | 即便全局 Logback 配置是 INFO，提权请求的 DEBUG 日志也能被强制放行 |
| **多级支持** | 提权到 DEBUG 时，TRACE 也一并放行（`level.isGreaterOrEqual(targetLevel)`） |
| **风险控制** | 仅对命中规则的用户生效，影响面严格限定 |
| **MDC 隔离** | 用 `MDCCloseable` 包装，请求结束自动清理，杜绝线程复用导致的级别污染 |
| **作用域限制**（v2.1 增强）| 通过 `framework4j.tracelog.elevation-allowed-packages`（默认 `["com.yourcompany"]`）配置仅提权业务包路径，第三方库（HTTP client / ORM）保持 INFO |

### 3.4 高性能日志收集（异步 + 分片 + 批量写入）

> 防同步写 Redis 阻塞业务主线程，设计了「**轻量 append + 分片 Disruptor + 批 Pipeline**」可水平扩展架构。

#### 3.4.1 整体架构

```
[业务线程]                                              [Worker Pool]
ILoggingEvent                                           ┌──────────────────────┐
    │                                                   │ Shard-0 (Consumer)   │
    ▼                                                   │   LogstashEncoder    │
┌─────────────────────────┐                             │   Pipeline flush     │
│ AsyncRedisLogAppender   │  ── O(1) enqueue ──▶       │   rate-limit         │
│ (单实例, 业务线程直接调) │                             └──────────────────────┘
│                         │                             ┌──────────────────────┐
│ - 读 MDC.traceId        │  ── hash(traceId) ──▶       │ Shard-1 (Consumer)   │
│ - 轻量 RawEvent 入队    │      分片到 N 个 MPSC        │   ...                │
│ - 业务线程耗时 < 0.05ms │                             └──────────────────────┘
└─────────────────────────┘                             ...
                                                        ┌──────────────────────┐
                                                        │ Shard-N-1            │
                                                        └──────────────────────┘
                                                                  │
                                                                  ▼
                                                       Redis (Pipeline + Lua)
                                                                  │
                                                                  ▼
                                                  trace_log:{traceId}  +  trace_global_queue
```

**关键改造对比 v1.0 单线程方案**：

| 维度 | v1.0 单线程 | v2.0 分片架构 |
|---|---|---|
| 并发消费 | 1 线程 | `worker-count`（默认 `availableProcessors`） |
| 队列 | `ArrayBlockingQueue` | LMAX Disruptor（无锁 MPSC） |
| 同 traceId 顺序 | ✅（单线程天然有序） | ✅（按 traceId hash 分片，同 traceId 必落同一 worker） |
| 业务线程序列化 | ❌（同步 `LogstashEncoder`） | ✅（仅入队原始事件） |
| 序列化位置 | 业务线程 | Worker 线程 |
| Lua 首次判定 | 本地 Caffeine | Redis SETNX（分布式） |
| 优雅停机 | ❌ | ✅（drain 队列） |

#### 3.4.2 核心组件：`AsyncRedisLogAppender`（分片版）

```java
public class AsyncRedisLogAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private final TraceLogProperties props;
    private final StringRedisTemplate redisTemplate;
    private final TraceIdResolver traceIdResolver;
    private final TraceLogStore store;            // 封装 SETNX + Lua + Pipeline
    private final RateLimiter perTraceRateLimiter;

    // LMAX Disruptor（按 traceId hash 分片，多生产者单消费者）
    private final Disruptor<RawEvent>[] disruptors;  // N 个 ring buffer
    private final Worker[] workers;                  // N 个 worker

    @Override
    protected void append(ILoggingEvent event) {
        // 1. 解析 traceId（仅读 MDC, 极轻量, 不做 JSON 序列化）
        String rawTraceId = traceIdResolver.fromEvent(event);
        if (rawTraceId == null) return;

        // 2. 标准化 32-hex（OTel 格式）
        String traceId = traceIdResolver.normalize(rawTraceId);

        // 3. 单 trace 限速（200 条/秒, 可配置）
        if (!perTraceRateLimiter.tryAcquire(traceId)) {
            Metrics.counter("tracelog.dropped", "reason", "rate_limit").increment();
            return;
        }

        // 4. 按 traceId hash 选 ring buffer
        int shard = Math.abs(traceId.hashCode()) % disruptors.length;

        // 5. 仅入队原始事件（O(1), 业务线程立即返回）
        RawEvent raw = new RawEvent(traceId, event);
        long seq = disruptors[shard].ringBuffer.tryNext();
        try {
            disruptors[shard].ringBuffer.get(seq).set(raw);
        } finally {
            disruptors[shard].ringBuffer.publish(seq);
        }
    }

    @Override
    public void stop() {
        // Graceful shutdown: 等所有 ring buffer drain
        for (Disruptor<RawEvent> d : disruptors) {
            d.shutdown(Duration.ofSeconds(props.getShutdownDrainTimeoutSeconds()));
        }
        super.stop();
    }

    // Worker: 每个 shard 一个, 串行���费 + Pipeline 批写
    static class Worker implements WorkHandler<RawEvent> {
        private final int shard;
        private final List<RawEvent> batch = new ArrayList<>(props.getFlushBatchSize());

        @Override
        public void onEvent(RawEvent raw) throws Exception {
            batch.add(raw);
            if (batch.size() >= props.getFlushBatchSize()) {
                flush();
            }
        }

        // 由 Disruptor 周期触发 (YieldingWaitStrategy) 或 batchSize 触发
        void flush() {
            try {
                store.flushPipeline(batch);  // 见 §3.4.3
            } finally {
                batch.clear();
            }
        }
    }
}
```

#### 3.4.3 `TraceLogStore.flushPipeline()` —— SETNX + Lua + Pipeline

```java
public void flushPipeline(List<RawEvent> batch) {
    // 1. Worker 线程完成 LogstashEncoder 序列化（异步不阻塞业务线程）
    List<RawEventPayload> payloads = batch.stream()
            .map(this::serialize)  // LogstashEncoder 在 Worker 线程执行
            .toList();

    // 2. Pipeline 批写 Redis
    try {
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (RawEventPayload p : payloads) {
                byte[] logKey = ("trace_log:" + p.traceId()).getBytes();

                // 2.1 RPUSH 日志到 List
                connection.listCommands().rPush(logKey, p.json().getBytes());

                // 2.2 LTRIM 限制单 trace 最大条数 (来自配置 single-trace-max-logs)
                connection.listCommands().lTrim(logKey,
                    -props.getSingleTraceMaxLogs(), -1);

                // 2.3 仅首次执行 SETNX + Lua
                if (p.isFirstSeen()) {
                    // SETNX 分布式首次标记
                    connection.setCommands().set(
                        (logKey + ":meta").getBytes(),
                        "1".getBytes(),
                        Expiration.seconds(props.getTraceTtlSeconds()),
                        SetOption.SET_IF_ABSENT  // NX
                    );
                    // Lua: EXPIRE + RPUSH 到全局队列 + 容量裁剪
                    // ARGV 全从 props 注入, 零硬编码
                    connection.scriptingCommands().evalSha(
                        capacityScriptSha,
                        ReturnType.INTEGER, 2,
                        logKey,
                        props.getGlobalQueueKey().getBytes(),
                        String.valueOf(props.getGlobalMaxTraces()).getBytes(),
                        String.valueOf(props.getTraceTtlSeconds()).getBytes()
                    );
                }
            }
            return null;
        });
    } catch (Exception e) {
        // Redis 故障: 降级到本地滚动文件 (见 §3.4.5)
        fallbackWriter.writeBatch(payloads, e);
    }
}
```

#### 3.4.4 性能与稳定性指标

| 指标 | 目标 | 设计保证 |
|---|---|---|
| 业务线程延迟 | < 0.05ms | append() 仅 MDC 读取 + hash 选 shard + Disruptor tryNext |
| 业务线程吞吐 | > 100K evt/s/thread | Disruptor 无锁入队 |
| 刷盘延迟 | ≤ 500ms | YieldingWaitStrategy + 周期触发 flush |
| 单批写入 | `flush-batch-size`（默认 500） | batch size 触发 flush |
| Worker 数量 | `worker-count`（默认 `availableProcessors`） | Disruptor N 个独立 ring buffer |
| 队列容量 | `disruptor-buffer-size`（默认 65536） | Ring buffer 有界，防 OOM |
| Redis 异常 | 不丢日志 | 降级写本地滚动文件（容器化需 hostPath/PVC） |
| 重复 traceId | 砍掉 99%+ 状态写 | SETNX 分布式首次标记 |
| Graceful shutdown | drain 超时 = `shutdown-drain-timeout-seconds`（默认 10s） | JVM shutdown hook |

#### 3.4.5 本地降级策略（Redis 故障）

```
Redis 故障
    │
    ▼
本地目录: ${fallback.dir} (默认 /var/log/framework4j-tracelog/fallback, 必须 hostPath/PVC)
    │
    ├── tracelog-fallback-{node}-20260824-15.log   (滚动: 100MB 或 1h)
    ├── tracelog-fallback-{node}-20260824-16.log
    └── ...
        │
        ▼
后台 Replayer 线程（每 30s 探测）
    │
    ├─ Redis 已恢复 → 按时间序回灌（每批 500 条 LRANGE）
    │     - 检查时间戳戳, 跳过已过期的 (超过 trace-ttl-seconds 的不再回灌)
    │     - 顺序写入, 避免打乱同 traceId 时间序
    │     - 回灌成功后删除文件
    └─ Redis 仍故障 → 继续累积, 启动磁盘告警（> 1GB 时打 ERROR 日志 + Micrometer gauge）
```

> **重要**：容器化部署必须挂载 `hostPath` 或 `PVC` 到 `${fallback.dir}`，容器本地文件系统在 Pod 驱逐时会丢。

#### 3.4.6 关键设计要点

| 要点 | 说明 |
|---|---|
| **分片有序** | 按 traceId hash 分片，同 traceId 必落同一 worker → 同一 traceId 日志**严格时间序** |
| **业务线程零序列化** | append() 仅做 MDC 读 + hash，序列化移至 Worker 线程 |
| **Disruptor 无锁** | 相比 ArrayBlockingQueue，省去锁竞争，GC 压力接近零 |
| **Pipeline 批写** | 一次 IO 提交 500 条命令，极大减少 RTT |
| **Lua 零硬编码** | 所有阈值（容量、TTL）从 `framework4j.tracelog.*` 配置注入 |
| **SETNX 跨节点去重** | Lua 仅在真首次执行，节省 Redis CPU |
| **Graceful shutdown** | JVM shutdown hook + Disruptor drain + 超时保护 |
| **降级不丢日志** | Redis 故障 → 本地滚动文件 → 重连后异步回灌 |

### 3.5 前端控制台与 API 查询

#### 3.5.1 查询 API（强制要求 TraceId）

```java
@GetMapping("/api/logs/trace/{traceId}")
public ApiResponse<List<LogDto>> queryLogs(
        @PathVariable String traceId,
        @RequestParam(required = false) String level,    // DEBUG/INFO/WARN/ERROR/ALL
        @RequestParam(required = false) String keyword  // 关键字
) {
    // 0. 鉴权 + 多租户过滤 (见 §3.5.4)
    requireTraceLogPermission();   // 启动时校验, 未配置则 fail-fast
    String tenantKey = tenantKeyResolver.currentTenant();

    String redisKey = (tenantKey != null ? tenantKey + ":" : "") + "trace_log:" + traceId;

    // 1. 根据 traceId 查出全部链路日志
    List<String> rawLogs = redisTemplate.opsForList().range(redisKey, 0, -1);

    if (rawLogs == null || rawLogs.isEmpty()) {
        return ApiResponse.success(List.of(), TraceContext.getTraceId());
    }

    // 2. 在 Java 内存中完成二级条件过滤 (单 trace 几百条内, 效率极高)
    List<LogDto> result = rawLogs.stream()
            .map(this::parseJson)
            .filter(log -> matchLevel(log, level))
            .filter(log -> matchKeyword(log, keyword))
            .limit(props.getMaxReturnLogs())      // 默认 1000, 防大 trace 拖垮接口
            .toList();

    return ApiResponse.success(result, TraceContext.getTraceId());
}
```

#### 3.5.2 日志导出 API（v1.0 必备）

> 排障场景常需把全链路日志发给同事 / 存档，必须 v1.0 就支持。

```java
@GetMapping(value = "/api/logs/trace/{traceId}/export", produces = "application/octet-stream")
public void exportLogs(
        @PathVariable String traceId,
        @RequestParam(defaultValue = "txt") String format,    // txt | json
        HttpServletResponse response
) throws IOException {
    requireTraceLogPermission();

    String redisKey = "trace_log:" + traceId;
    List<String> rawLogs = redisTemplate.opsForList().range(redisKey, 0, -1);
    if (rawLogs == null || rawLogs.isEmpty()) {
        response.setStatus(404);
        return;
    }

    // 文件名: trace-{traceId}-{timestamp}.log[.gz]
    String fileName = String.format("trace-%s-%d.%s%s",
            traceId, System.currentTimeMillis(),
            "json".equals(format) ? "json" : "log",
            props.getExportCompress() ? ".gz" : "");

    response.setContentType("application/octet-stream");
    response.setHeader("Content-Disposition",
            "attachment; filename=\"" + fileName + "\"");

    try (OutputStream out = response.getOutputStream();
         OutputStream gzipOut = props.getExportCompress()
                 ? new GZIPOutputStream(out) : out) {

        if ("json".equals(format)) {
            // JSON Lines 格式: 每行一条日志 (便于后续工具解析)
            for (String raw : rawLogs) {
                gzipOut.write(raw.getBytes(StandardCharsets.UTF_8));
                gzipOut.write('\n');
            }
        } else {
            // 文本格式: 人类可读
            for (String raw : rawLogs) {
                LogDto dto = parseJson(raw);
                String line = String.format("[%s] [%s] [%s] [%s] %s%n",
                        dto.getTsIso(), dto.getLevel(), dto.getThread(),
                        dto.getLogger(), dto.getMessage());
                gzipOut.write(line.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    // 审计: 谁导出了哪条 trace
    auditService.log("EXPORT_TRACE_LOG", traceId, Map.of(
            "format", format, "size", rawLogs.size()));
}
```

| 项 | 默认 | 说明 |
|---|---|---|
| 单 trace 限速 | `export-rate-limit-per-minute = 5` | 防恶意批量导出 |
| 单文件大小 | `export-max-size-mb = 10` | 超限截断 + 告警 |
| 启用压缩 | `export-compress = true` | gzip 压缩，体积减少 ~80% |
| 审计 | 必接 `framework4j-audit` | 记录导出者 / 时间 / traceId |

#### 3.5.3 开关控制 API

```java
@PostMapping("/api/logs/switch")
public ApiResponse<Void> openSwitch(@Valid @RequestBody SwitchRequest req) {
    // 1. 鉴权 + 频控
    requireTraceLogPermission();
    switchRateLimiter.tryAcquire(req.getType() + ":" + req.getValue());  // 1 分钟/维度/1 次

    // 2. 强制约束: TTL ≤ 3600s
    long ttl = Math.min(req.getTtlSeconds(), 3600L);
    String key = "log_switch:id:" + req.getType() + ":" + req.getValue();
    redisTemplate.opsForValue().set(key, req.getLevel().name(), Duration.ofSeconds(ttl));

    // 3. Pub/Sub 广播 (让所有节点立即生效)
    String payload = JsonUtil.toJson(Map.of(
            "type", req.getType(),
            "value", req.getValue(),
            "level", req.getLevel().name()
        ));
    redisTemplate.convertAndSend(props.getSwitchChannel(), payload);

    // 4. 审计 (必接 framework4j-audit)
    auditService.log("OPEN_TRACE_SWITCH", key, Map.of(
            "level", req.getLevel(), "ttl", ttl));

    return ApiResponse.success();
}
```

#### 3.5.4 鉴权与多租户（强约束，**启动 fail-fast**）

> 这是 P0 安全门，框架层面 fail-fast，业务方未正确配置则**启动失败**。

| 约束 | 默认 | 启动校验 |
|---|---|---|
| **`api.require-auth = true`** | ✅ 开启 | 关闭 → WARN（生产环境建议开启） |
| **`api.auth-validator-bean`** | 必填 | 未配置 → **启动 fail-fast**（业务方必须实现 `TraceLogAuthValidator` 接口接入 accesstoken） |
| **`tenant.enabled = true`** | 由 `tenant-key-spel` 决定 | 开启后 Redis Key 加 `tenantKey:` 前缀；查询时强校验租户 |
| **`api.ip-whitelist`** | `[]` | 非空 → 仅白名单 IP 可访问控制 API |
| **`api.ip-blacklist`** | `[]` | 黑名单 IP 永久拒绝 |

```java
// 业务方必须实现的鉴权接口
public interface TraceLogAuthValidator {
    /** 查询接口: 返回是否有 trace:log:query 权限 */
    boolean canQuery(String operatorId, String tenantId);

    /** 控制接口: 返回是否有 trace:log:open 权限 */
    boolean canOpenSwitch(String operatorId, String tenantId, SwitchRequest req);

    /** 导出接口: 返回是否有 trace:log:export 权限 */
    boolean canExport(String operatorId, String tenantId);
}

// 示例: 接入 framework4j-accesstoken
@Component
public class AccessTokenTraceLogAuthValidator implements TraceLogAuthValidator {
    @Autowired private AccessTokenService accessTokenService;

    @Override
    public boolean canQuery(String operatorId, String tenantId) {
        // 从 TokenContext 取当前用户, 校验 RBAC 权限
        return accessTokenService.hasPermission(operatorId, "trace:log:query", tenantId);
    }
    // ...
}
```

#### 3.5.5 安全约束（汇总）

| 约束 | 说明 |
|---|---|
| **控制台鉴权** | 必接 `framework4j-accesstoken`（实现 `TraceLogAuthValidator`），未配置则启动 fail-fast |
| **TTL 上限** | API 层强制 `Math.min(ttl, 3600)`，防止恶意开通 |
| **维度频控** | 同一维度 1 分钟内只能开 1 次（防误操作刷屏） |
| **审计日志** | 开关开启/关闭/导出 必须审计（接入 `framework4j-audit`） |
| **IP 白名单** | `api.ip-whitelist`（仅内网可访问） |
| **多租户隔离** | `tenant.enabled=true` + `tenant-key-spel` → Redis Key 加租户前缀，跨租户查不到 |
| **敏感字段脱敏**（v1.1） | 接入 `framework4j-sensitive`，自动 mask `password` / `Authorization` 等 |

### 3.6 控制台前端（纯 HTML 单文件，零三方依赖）

> **设计原则**：单 HTML 文件 + inline CSS + vanilla JS（仅 `fetch` API），无任何三方库（无 Vue / React / jQuery / Axios / 任何 CDN）。
>
> 部署在 `src/main/resources/static/tracelog/index.html`，Spring Boot 自动 serve 在 `${console.path}`（默认 `/tracelog.html`）。

#### 3.6.1 页面布局

```
┌─────────────────────────────────────────────────────────────────┐
│  Trace Log Console                                  [登出]      │  ← 顶部（20px）
├─────────────────────────────────────────────────────────────────┤
│  [Tab: 查询]  [Tab: 开关]  [Tab: 实时]                          │  ← 标签切换
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  查询页:                                                        │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │  TraceId: [_______________________]                       │ │
│  │  级别:     [ALL ▼]   关键字: [_______]   [查询] [导出]    │ │
│  └───────────────────────────────────────────────────────────┘ │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │ [16:00:01.456] [http-nio-8080-exec-3] [INFO ] OrderService│ │
│  │   订单创建中 userId=10086 orderId=OD001                  │ │
│  │ [16:00:01.523] [http-nio-8080-exec-3] [DEBUG] OrderDao    │ │
│  │   INSERT INTO t_order ... (masked)                        │ │
│  │ ...                                                       │ │
│  └───────────────────────────────────────────────────────────┘ │
│                                                                 │
│  开关页:                                                        │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │  维度: [user ▼]  值: [_______]  级别: [DEBUG ▼]           │ │
│  │  TTL(s): [3600] (上限 3600)            [开启]              │ │
│  └───────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

#### 3.6.2 单文件 HTML 实现（节选）

> **约束**：单个 `.html` 文件，无外部 CSS / JS 文件，无 CDN 引用，无 npm 依赖，**直接双击就能打开**（生产环境走 `${console.path}`）。

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <title>Trace Log Console</title>
  <style>
    /* ========== inline CSS, 零外部依赖 ========== */
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, "Segoe UI", "Microsoft YaHei", monospace;
      background: #1e1e1e; color: #d4d4d4; font-size: 13px;
    }
    .header {
      background: #2d2d2d; padding: 8px 16px;
      border-bottom: 1px solid #3e3e3e;
      display: flex; justify-content: space-between; align-items: center;
    }
    .header h1 { font-size: 14px; font-weight: normal; }
    .tabs {
      background: #252526; padding: 0 16px;
      border-bottom: 1px solid #3e3e3e;
      display: flex; gap: 0;
    }
    .tab {
      padding: 8px 16px; cursor: pointer;
      border-bottom: 2px solid transparent;
      color: #858585;
    }
    .tab.active { color: #d4d4d4; border-bottom-color: #007acc; }
    .tab:hover { color: #d4d4d4; }
    .panel { display: none; padding: 16px; }
    .panel.active { display: block; }
    .toolbar {
      background: #2d2d2d; padding: 12px; margin-bottom: 12px;
      border-radius: 4px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap;
    }
    input, select, button {
      background: #3c3c3c; color: #d4d4d4; border: 1px solid #3e3e3e;
      padding: 4px 8px; border-radius: 2px; font-size: 13px;
    }
    input:focus, select:focus { outline: 1px solid #007acc; border-color: #007acc; }
    button {
      cursor: pointer; background: #0e639c; border-color: #0e639c;
    }
    button:hover { background: #1177bb; }
    button:disabled { background: #3c3c3c; cursor: not-allowed; }
    .logs {
      background: #1e1e1e; border: 1px solid #3e3e3e;
      border-radius: 4px; max-height: calc(100vh - 200px);
      overflow-y: auto; padding: 8px;
    }
    .log-line {
      padding: 2px 0; font-family: "Cascadia Code", "Consolas", monospace;
      white-space: pre-wrap; word-break: break-all;
      border-bottom: 1px solid #2d2d2d;
    }
    .log-line:hover { background: #2a2a2a; }
    .log-ts   { color: #858585; margin-right: 8px; }
    .log-level { display: inline-block; min-width: 50px; margin-right: 8px; font-weight: bold; }
    .log-level.DEBUG { color: #569cd6; }
    .log-level.INFO  { color: #4ec9b0; }
    .log-level.WARN  { color: #dcdcaa; }
    .log-level.ERROR { color: #f44747; }
    .log-logger { color: #9cdcfe; margin-right: 8px; }
    .log-msg    { color: #d4d4d4; }
    .log-trace  { color: #c586c0; font-size: 11px; }
    .status {
      padding: 4px 12px; font-size: 12px;
    }
    .status.ok   { color: #4ec9b0; }
    .status.err  { color: #f44747; }
  </style>
</head>
<body>
  <div class="header">
    <h1>📋 Trace Log Console</h1>
    <span id="userInfo" style="color:#858585;">未登录</span>
  </div>

  <div class="tabs">
    <div class="tab active" data-tab="query">🔍 查询</div>
    <div class="tab" data-tab="switch">⚙️ 开关</div>
    <div class="tab" data-tab="metrics">📊 指标</div>
  </div>

  <!-- 查询面板 -->
  <div class="panel active" id="panel-query">
    <div class="toolbar">
      <label>TraceId:</label>
      <input id="traceId" placeholder="32 位 hex" style="width:360px;">
      <label>级别:</label>
      <select id="level">
        <option value="">ALL</option>
        <option value="DEBUG">DEBUG</option>
        <option value="INFO">INFO</option>
        <option value="WARN">WARN</option>
        <option value="ERROR">ERROR</option>
      </select>
      <label>关键字:</label>
      <input id="keyword" placeholder="模糊匹配">
      <button id="btnQuery">查询</button>
      <button id="btnExport">导出</button>
      <span id="queryStatus" class="status"></span>
    </div>
    <div class="logs" id="logList">
      <div style="color:#858585; padding:20px; text-align:center;">
        输入 TraceId 开始查询
      </div>
    </div>
  </div>

  <!-- 开关面板 -->
  <div class="panel" id="panel-switch">
    <div class="toolbar">
      <label>维度:</label>
      <select id="switchType">
        <option value="user">User</option>
        <option value="trace">Trace</option>
        <option value="url">URL</option>
        <option value="order">Order</option>
      </select>
      <label>值:</label>
      <input id="switchValue" placeholder="userId / traceId / url pattern / orderId" style="width:240px;">
      <label>级别:</label>
      <select id="switchLevel">
        <option value="DEBUG">DEBUG</option>
        <option value="TRACE">TRACE</option>
      </select>
      <label>TTL(s):</label>
      <input id="switchTtl" type="number" value="3600" min="1" max="3600" style="width:80px;">
      <button id="btnSwitch">开启</button>
      <span id="switchStatus" class="status"></span>
    </div>
  </div>

  <script>
    // ========== vanilla JS, 仅 fetch API ==========
    const API_BASE = '';  // 同域部署, 相对路径

    // Tab 切换
    document.querySelectorAll('.tab').forEach(tab => {
      tab.addEventListener('click', () => {
        document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
        tab.classList.add('active');
        document.getElementById('panel-' + tab.dataset.tab).classList.add('active');
      });
    });

    // ========== 查询 ==========
    document.getElementById('btnQuery').addEventListener('click', async () => {
      const traceId = document.getElementById('traceId').value.trim();
      if (!traceId) return alert('TraceId 不能为空');

      const params = new URLSearchParams();
      const lv = document.getElementById('level').value;
      const kw = document.getElementById('keyword').value;
      if (lv) params.set('level', lv);
      if (kw) params.set('keyword', kw);

      const status = document.getElementById('queryStatus');
      status.textContent = '查询中...'; status.className = 'status';

      try {
        const res = await fetch(API_BASE + '/api/logs/trace/' + encodeURIComponent(traceId) + '?' + params);
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const json = await res.json();

        const list = document.getElementById('logList');
        if (!json.data || json.data.length === 0) {
          list.innerHTML = '<div style="color:#858585;padding:20px;text-align:center;">无日志</div>';
          status.textContent = '0 条'; status.className = 'status';
          return;
        }

        // 渲染 (innerHTML 转义防 XSS, 来自 framework4j 响应 JSON)
        list.innerHTML = json.data.map(log => {
          const msg = escapeHtml(log.message || '');
          return `<div class="log-line">
            <span class="log-ts">${log.tsIso || ''}</span>
            <span class="log-level ${log.level}">[${log.level}]</span>
            <span class="log-logger">${escapeHtml(log.logger || '')}</span>
            <span class="log-trace">[${escapeHtml(log.traceId || '')}]</span>
            <span class="log-msg">${msg}</span>
          </div>`;
        }).join('');
        status.textContent = `${json.data.length} 条`; status.className = 'status ok';
      } catch (e) {
        status.textContent = '失败: ' + e.message; status.className = 'status err';
      }
    });

    // ========== 开关 ==========
    document.getElementById('btnSwitch').addEventListener('click', async () => {
      const body = {
        type: document.getElementById('switchType').value,
        value: document.getElementById('switchValue').value.trim(),
        level: document.getElementById('switchLevel').value,
        ttlSeconds: parseInt(document.getElementById('switchTtl').value, 10) || 3600
      };
      if (!body.value) return alert('值不能为空');

      const status = document.getElementById('switchStatus');
      status.textContent = '开启中...'; status.className = 'status';

      try {
        const res = await fetch(API_BASE + '/api/logs/switch', {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify(body)
        });
        if (!res.ok) throw new Error('HTTP ' + res.status);
        status.textContent = '已开启 (TTL ' + body.ttlSeconds + 's)';
        status.className = 'status ok';
      } catch (e) {
        status.textContent = '失败: ' + e.message; status.className = 'status err';
      }
    });

    // ========== 工具: HTML 转义（防 XSS） ==========
    function escapeHtml(s) {
      return String(s).replace(/[&<>"']/g, c => ({
        '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'
      }[c]));
    }

    // Enter 快捷键
    document.getElementById('traceId').addEventListener('keydown', e => {
      if (e.key === 'Enter') document.getElementById('btnQuery').click();
    });
  </script>
</body>
</html>
```

#### 3.6.3 关键技术决策

| 决策 | 理由 |
|---|---|
| **单 HTML 文件** | 直接 `cp target/*.jar /path && java -jar`，无需 build；可单独取出 `tracelog.html` 部署到任何静态服务器 |
| **无外部 CSS / JS** | 零网络请求，断网也能用；零供应链安全风险；不受 CDN 失效影响 |
| **无 build 步骤** | 改完即生效，没有 webpack / vite 配置负担 |
| **VS Code 暗色主题** | 排障工具长时间使用，护眼；与终端配色一致 |
| **`fetch` 原生 API** | IE 完全淘汰（项目依赖 Spring Boot 3.5 = JDK 17，对应现代浏览器）；不引 Axios 省 14KB |
| **HTML 转义防 XSS** | 后端响应虽可信（自家 API），但客户端做转义是 default-secure；防止未来重构后端响应混入恶意数据 |
| **Tab 切换 vanilla** | 3 个面板，用 `display:none/block` 即可，无需 SPA 路由 |

#### 3.6.4 不做的事（明确边界）

- ❌ 不做实时日志推送（WebSocket / SSE）—— v2.4 才考虑，先支持刷新式查询
- ❌ 不做日志高亮 / 折叠 / 搜索 —— 用浏览器原生 `Ctrl+F`
- ❌ 不做图表（Chart.js 等）—— 指标走 Micrometer + Grafana，不在前端做
- ❌ 不做多语言切换 —— 内部工具，统一中文
- ❌ 不做暗/亮主题切换 —— 排障场景固定暗色
- ❌ 不做响应式 —— PC 端工具，不考虑手机访问

#### 3.6.5 与 framework4j-web 集成

```java
// TraceLogAutoConfiguration 中:
// Spring Boot 自动 serve classpath:/static/tracelog/index.html
// 通过配置 console.path = /tracelog.html 提供访问入口
// (框架默认配置, 无需业务方额外注册)
```

控制台访问路径：
- 默认：`http://app-host:port/tracelog.html`
- 通过 `framework4j.tracelog.console.path` 修改

---

## 四、配置参考（`framework4j.tracelog.*`）

### 4.1 开关与依赖

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `enabled` | `boolean` | `false` | 全局开关（opt-in） |
| `redis-name` | `String` | `default` | 用 `framework4j-redis` 的哪个数据源 |

### 4.2 存储与容量（**全部可配置，零硬编码**）

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `key-prefix` | `String` | `trace_log` | Redis Key 前缀（完整格式 `{prefix}:{traceId}`） |
| `global-queue-key` | `String` | `trace_global_queue` | 全局队列 Key（Cluster 下用 `{globalQueueKey}:{shard}` 分片） |
| `global-queue-shards` | `int` | `1` | Cluster 模式下分片数（>1 时启用 hashtag 分片） |
| `global-max-traces` | `int` | `100000` | 全局最大追踪数（Lua 容量阈值） |
| `trace-ttl-seconds` | `long` | `86400` | ��� trace 过期时间（24h） |
| `single-trace-max-logs` | `int` | `5000` | 单 trace 最大日志条数（LTRIM 阈值） |

### 4.3 异步采集（分片 + Disruptor）

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `worker-count` | `int` | `availableProcessors()` | Worker 线程数（Disruptor 实例数） |
| `disruptor-buffer-size` | `int` | `65536` | 每个 RingBuffer 容量（必须 2 的幂） |
| `flush-batch-size` | `int` | `500` | 单批刷盘大小（达到即刷） |
| `flush-interval-ms` | `long` | `500` | 单批最大等待时长（YieldingWaitStrategy 触发） |
| `rate-limit-per-trace-per-second` | `int` | `200` | 单 traceId 写入速率（令牌桶） |
| `dedup-cache-size` | `int` | `50000` | 本地 traceId 防抖缓存容量（fallback） |
| `dedup-cache-ttl-seconds` | `long` | `120` | 本地 traceId 防抖 TTL |
| `shutdown-drain-timeout-seconds` | `long` | `10` | Graceful shutdown drain 超时 |
| `fallback-dir` | `String` | `/var/log/framework4j-tracelog/fallback` | Redis 故障时本地降级目录（容器化需 hostPath/PVC） |
| `fallback-replay-interval-seconds` | `long` | `30` | Redis 恢复后回灌探测周期 |
| `mask-sensitive` | `boolean` | `true` | 采集进 Redis 前按 key 脱敏（Worker 线程执行） |
| `mask-keys` | `List<String>` | `password,passwd,pwd,token,access_token,refresh_token,authorization,secret,api_key,apikey,cookie,set-cookie` | 脱敏 key（不区分大小写；匹配 JSON 字段与 message 内 kv 形态） |

### 4.4 开关同步（Pub/Sub 或 Streams）

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `sync.channel` | `String` | `channel:log_switch` | Pub/Sub 频道名 |
| `sync.transport` | `enum` | `pubsub` | `pubsub`（默认）或 `streams` |
| `sync.max-ttl-seconds` | `long` | `3600` | 开关最长有效期 |
| `sync.resync-interval-seconds` | `long` | `5` | 重拉周期（**默认 5s**，可配置；diff 合并零窗口） |
| `sync.rule-cache-size` | `int` | `100000` | 本地 Caffeine 容量 |

### 4.5 提权（TurboFilter）

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `elevation-allowed-packages` | `List<String>` | `["com.yourcompany"]` | 仅提权业务包路径，第三方库保持 INFO |
| `elevation-default-level` | `enum` | `DEBUG` | 提权默认目标级别（DEBUG / TRACE） |

### 4.6 API（查询 / 控制 / 导出）

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `api.require-auth` | `boolean` | `true` | 是否强制鉴权（生产建议开启） |
| `api.auth-validator-bean` | `String` | 必填 | `TraceLogAuthValidator` Bean 名（未配置启动 fail-fast） |
| `api.query-path-patterns` | `List<String>` | `[/api/logs/trace/**]` | 查询 API 路径 |
| `api.switch-path-patterns` | `List<String>` | `[/api/logs/switch]` | 控制 API 路径 |
| `api.export-path-patterns` | `List<String>` | `[/api/logs/trace/*/export]` | 导出 API 路径 |
| `api.ip-whitelist` | `List<String>` | `[]` | IP 白名单（非空则仅白名单可访问） |
| `api.ip-blacklist` | `List<String>` | `[]` | IP 黑名单（永久拒绝） |
| `api.max-return-logs` | `int` | `1000` | 查询接口单次返回上限 |
| `api.switch-rate-limit-per-minute` | `int` | `1` | 同一维度每分钟开关次数 |

### 4.7 多租户

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `tenant.enabled` | `boolean` | `false` | 是否启用多租户隔离 |
| `tenant.key-spel` | `String` | `null` | 租户 Key 取值 SpEL（如 `#userInfo.tenantId`），启用时必填 |
| `tenant.header-name` | `String` | `X-Tenant-Id` | 租户 Header 名（HTTP 请求来源） |

### 4.8 导出

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `export.enabled` | `boolean` | `true` | 是否启用导出接口 |
| `export.compress` | `boolean` | `true` | 是否 gzip 压缩 |
| `export.max-size-mb` | `int` | `10` | 单 trace 最大导出大小 |
| `export.rate-limit-per-minute` | `int` | `5` | 单用户每分钟导出次数 |

### 4.9 控制台（前端）

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `console.enabled` | `boolean` | `true` | 是否暴露前端页面 |
| `console.path` | `String` | `/tracelog.html` | 前端页面访问路径 |
| `console.title` | `String` | `Trace Log Console` | 页面标题 |

---

## 五、模块结构与依赖

### 5.1 目录结构

```
framework4j-tracelog/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/fun/commons/framework4j/tracelog/
│   │   │   ├── config/
│   │   │   │   ├── TraceLogAutoConfiguration.java      # 主装配类
│   │   │   │   ├── TraceLogProperties.java             # 配置属性（全部 @ConfigurationProperties）
│   │   │   │   ├── TraceLogWebMvcConfig.java           # Interceptor 注册
│   │   │   │   ├── TraceLogFailureAnalyzer.java        # 启动 fail-fast 检查
│   │   │   │   └── TraceLogAuthValidator.java          # 鉴权 SPI 接口
│   │   │   ├── appender/
│   │   │   │   ├── AsyncRedisLogAppender.java          # Logback Appender（分片 Disruptor）
│   │   │   │   ├── DynamicLevelTurboFilter.java        # 提权 Filter
│   │   │   │   └── RawEvent.java                       # Disruptor 传输对象
│   │   │   ├── store/
│   │   │   │   ├── TraceLogStore.java                  # SETNX + Pipeline + Lua
│   │   │   │   ├── TraceLogCapacityLua.java            # Lua 脚本（参数化）
│   │   │   │   ├── LocalFallbackWriter.java            # 滚动文件 + Replayer
│   │   │   │   └── TraceIdNormalizer.java              # 32-hex 标准化
│   │   │   ├── switcher/
│   │   │   │   ├── SwitchRule.java                     # 规则定义
│   │   │   │   ├── SwitchRuleCache.java                # 本地 Caffeine
│   │   │   │   ├── SwitchPubSubListener.java           # Redis Pub/Sub 监听
│   │   │   │   ├── SwitchStreamsListener.java          # Redis Streams 监听（可选）
│   │   │   │   ├── SwitchResyncScheduler.java          # 断连重拉定时任务
│   │   │   │   └── TraceLogSwitchInterceptor.java      # 请求拦截 + MDCCloseable
│   │   │   ├── query/
│   │   │   │   ├── TraceLogQueryController.java        # 查询 / 控制 / 导出 API
│   │   │   │   ├── LogExporter.java                    # 导出实现
│   │   │   │   ├── LogDto.java                         # 响应 DTO
│   │   │   │   └── SwitchRequest.java                  # 开关请求 DTO
│   │   │   ├── rate/
│   │   │   │   ├── PerTraceRateLimiter.java            # 单 trace 令牌桶
│   │   │   │   └── SwitchRateLimiter.java              # 开关频控
│   │   │   ├── lifecycle/
│   │   │   │   └── GracefulShutdownHook.java          # JVM shutdown drain
│   │   │   ├── metrics/                                # v2.4
│   │   │   │   └── TraceLogMetrics.java                # Micrometer 指标绑定
│   │   │   └── util/
│   │   │       ├── TraceIdResolver.java                # 多源取 traceId
│   │   │       └── TenantKeyResolver.java              # 多租户 SpEL 解析（v1.2 新增）
│   │   └── resources/
│   │       ├── META-INF/spring/
│   │       │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   │       └── static/tracelog/
│   │           └── index.html                          # 控制台单文件（10.5KB，零三方依赖）
│   └── test/
│       └── java/fun/commons/framework4j/tracelog/
│           ├── unit/                                  # 6 个（Normalizer / RateLimiter / TurboFilter / SwitchRule / Cache / TenantResolver）
│           └── functional/                            # 1 个（TraceLogStore Integration，Redis 可用时启用）
├── 动态追踪日志 SDK 技术方案.md        ← 本文档（v1.2，已落地）
└── README.md
```

> **v1.2 实施增补**：
> - 新增 `store/FallbackReplayer.java`（Redis 恢复后异步回灌 fallback 文件）
> - 新增 `switcher/SwitchStreamsListener.java`（Redis Streams 替代 Pub/Sub，transport=streams 启用）
> - 新增 `metrics/TraceLogMetrics.java`（Micrometer 指标绑定，6 类指标）
> - 新增 `util/TenantKeyResolver.java`（多租户 SpEL，Redis Key 加租户前缀）
> - `store/LocalFallbackWriter.java` 改为启动硬失败（目录不可写抛异常）
> - `switcher/SwitchRuleCache.java` 新增 `valuesOf(type)` 迭代接口
> - 删除 `lifecycle/GracefulShutdownHook.java`（Disruptor 自带 shutdown，无需额外 Hook）
> - `config/TraceLogBeansConfig.java` 作为各层 Bean 工厂集中注册

### 5.2 Maven 依���关系

```xml
<!-- framework4j-tracelog/pom.xml 核心依赖（v1.2 实际） -->
<dependencies>
    <!-- 内部 SDK 依赖 -->
    <dependency>
        <groupId>fun.commons</groupId>
        <artifactId>framework4j-api</artifactId>
    </dependency>
    <dependency>
        <groupId>fun.commons</groupId>
        <artifactId>framework4j-web</artifactId>          <!-- v1.2 强依赖：ApiResponse + TraceContext -->
    </dependency>
    <dependency>
        <groupId>fun.commons</groupId>
        <artifactId>framework4j-redis</artifactId>
    </dependency>

    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- 第三方依赖 -->
    <dependency>
        <groupId>net.logstash.logback</groupId>
        <artifactId>logstash-logback-encoder</artifactId>
    </dependency>
    <dependency>
        <groupId>com.github.ben-manes.caffeine</groupId>
        <artifactId>caffeine</artifactId>
    </dependency>
    <dependency>
        <groupId>com.lmax</groupId>
        <artifactId>disruptor</artifactId>                 <!-- 版本由父 POM 管理 -->
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-core</artifactId>            <!-- v1.2 可选：消费方启用 actuator 时绑定 -->
        <optional>true</optional>
    </dependency>

    <!-- Lombok / 配置元数据 -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-configuration-processor</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- 测试 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>it.ozimov</groupId>
        <artifactId>embedded-redis</artifactId>            <!-- 可选：集成测试 -->
        <scope>test</scope>
        <optional>true</optional>
    </dependency>
</dependencies>
```

### 5.3 注册到父 POM

```xml
<!-- 根 pom.xml 的 <modules> 增加 -->
<module>framework4j-tracelog</module>

<!-- dependencyManagement 中增加 -->
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-tracelog</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 5.4 Logback 接入（消费方，v1.3.x 起**零声明**）

> **v1.3.x 重要变更**：TurboFilter 与 AsyncRedisLogAppender 由 `TraceLogBeansConfig` **编程式注册**
> （TurboFilter 加入 LoggerContext，Appender 自动挂到 root logger）。
> **不要在 logback-spring.xml 里声明它们** —— 两者依赖 Spring Bean
> （TraceLogStore / TraceLogProperties），logback 声明式实例化会因没有无参构造抛
> `NoSuchMethodException` 导致启动失败。

> 同时**不要把业务包 logger 设为 DEBUG** —— TurboFilter 在级别检查**之前**执行，
> 提权命中返回 ACCEPT 直接放行（绕过级别检查）；若 logger 本身 DEBUG，
> 未提权的 DEBUG 事件也会全量输出，失去动态提权意义。业务包保持 INFO 即可。

消费方的 `logback-spring.xml` 只需保留常规配置：

```xml
<!-- logback-spring.xml -->
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [traceId=%X{traceId:-}] - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="STDOUT"/>
        <!-- ASYNC_REDIS 与 DynamicLevelTurboFilter 由 TraceLogBeansConfig 自动注册, 此处无需声明 -->
    </root>
</configuration>
```

---

## 六、安全与风险控制

| 风险 | 缓解措施 |
|---|---|
| **Redis 内存爆** | 四重保护：全局上限 `global-max-traces`（默认 10w） / 单 List `single-trace-max-logs`（默认 5000） / TTL `trace-ttl-seconds`（默认 24h） / per-trace 速率限制（默认 200 条/秒）。所有阈值均可配置 |
| **控制台被滥用** | 强制鉴权（`api.require-auth=true`，未配置 `TraceLogAuthValidator` 启动 fail-fast）+ IP 白/黑名单 + 1h TTL + 审计 + 维度频控 |
| **敏感日志泄漏** | **v1.4.0 起内置 `SensitiveLogMasker`**：采集进 Redis 前按 key 匹配（password/token/cookie…默认 12 个，可配 `collection.mask-keys`），值替换 `******`；覆盖 JSON 字段（含 message 转义嵌套）与 kv 形态；Worker 线程执行业务零开销；与 `framework4j-sensitive` 的按值格式脱敏（手机号/身份证）互补；控制台 HTML 输出做 HTML 转义防 XSS |
| **Redis 单点** | 建议消费方用 Redis Cluster / 主从 + 哨兵；Cluster 下启用 `global-queue-shards` 分片避免 `trace_global_queue` 热点 |
| **网络分区下开关不同步** | 本地兜底（启动 SCAN）+ **`sync.resync-interval-seconds`**（默认 5s，可配置）周期重拉（diff 合并零窗口）；高可用场景建议升级到 Streams（§3.1.4） |
| **跨租户泄漏** | `tenant.enabled=true` + `tenant.key-spel` → Redis Key 加 `tenantKey:` 前缀；查询接口强校验租户 |
| **业务日志炸弹** | per-trace 速率限制（默认 200 条/秒）+ Java 令牌桶（防 Redis Lua 失效时被业务拖垮） |
| **容器化降级丢失** | **必须挂载 hostPath / PVC 到 `${fallback-dir}`**；容器本地文件系统在 Pod 驱逐时丢失 |
| **进程退出丢日志** | Graceful shutdown（`shutdown-drain-timeout-seconds` 默认 10s）drain 队列，超时打 ERROR 日志；K8s `terminationGracePeriodSeconds` 配 30s |
| **Pub/Sub 消息丢失** | 5s 重拉窗口期；高可用建议升级 Streams（天然持久化 + ack） |
| **traceId 格式混乱** | `TraceIdResolver` 统一 32-hex OTel 格式 |
| **Redis Cluster hashtag** | 文档明确：单 trace 操作同槽位 OK；`trace_global_queue` 需分片 |

---

## 七、后续规划（v2.3.0 之后）

| 版本 | 计划 |
|---|---|
| v2.3.0 | 基础能力（本设计文档范围），**控制台内嵌纯 HTML 单文件**（`/tracelog.html`） |
| v2.4.0 | 接入 Prometheus 指标：`tracelog_appended_total` / `tracelog_dropped_total` / `tracelog_redis_fail_total` / `tracelog_queue_depth` / `tracelog_flush_duration_seconds` |
| v2.4.0 | 敏感字段脱敏（接入 `framework4j-sensitive`，自动 mask `password` / `Authorization` / `Cookie`） |
| v2.5.0 | 跨链路关联（基于 SpanId，支持树形展开） |
| v2.5.0 | 自适应采样（高 QPS 接口自动降采样，DEBUG 默认 1% 采样，命中开关后 100%） |
| 长期 | 接入 OpenTelemetry Logs API（与 OTel 生态打通） |

---

## 八、日志 JSON Schema

> 采集到 Redis 的每条日志统一 JSON 格式，便于消费方（控制台 / 导出 / 第三方工具）解析。

### 8.1 标准字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `ts` | `long` | ✅ | 毫秒时间戳（`System.currentTimeMillis()`） |
| `tsIso` | `string` | ✅ | ISO-8601 UTC 时间（`Instant.toString()`），人类可读 |
| `level` | `string` | ✅ | `DEBUG` / `INFO` / `WARN` / `ERROR` / `TRACE` |
| `logger` | `string` | ✅ | Logger 名（如 `com.xx.OrderService`） |
| `thread` | `string` | ✅ | 线程名 |
| `traceId` | `string` | ✅ | 32 位小写 hex（OTel 标准化，见 §3.1.5） |
| `spanId` | `string` | ❌ | SpanId（v2.5 关联树） |
| `message` | `string` | ✅ | 格式化后的日志消息 |
| `mdc` | `object` | ❌ | MDC 全部键值对（除内部字段） |
| `exception` | `object` | ❌ | `{class, message, stacktrace[]}` |
| `marker` | `string` | ❌ | Logback Marker |
| `app` | `string` | ✅ | `spring.application.name`（消费方必填，便于多服务区分） |
| `host` | `string` | ✅ | 主机名（`InetAddress.getLocalHost().getHostName()`） |

### 8.2 示例

```json
{
  "ts": 1724496000000,
  "tsIso": "2026-08-24T15:00:00.000Z",
  "level": "DEBUG",
  "logger": "com.example.order.OrderService",
  "thread": "http-nio-8080-exec-3",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7",
  "message": "订单创建中 userId=10086 orderId=OD001 amount=199.00",
  "mdc": {
    "userId": "10086",
    "tenantId": "T001",
    "DYNAMIC_LOG_LEVEL": "DEBUG",
    "DYNAMIC_LOG_DIMERS": "user:10086"
  },
  "exception": null,
  "marker": null,
  "app": "order-service",
  "host": "order-pod-7d8f9c-xnz2q"
}
```

### 8.3 异常字段结构

```json
{
  "exception": {
    "class": "java.lang.IllegalStateException",
    "message": "订单已支付，不可重复扣款",
    "stacktrace": [
      "com.example.order.OrderService.pay(OrderService.java:128)",
      "com.example.order.OrderController.create(OrderController.java:45)",
      "sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)",
      "..."
    ]
  }
}
```

### 8.4 大小估算

- 单条日志（INFO）：~300 字节
- 含异常堆栈（10层）：~1.5 KB
- 全局队列 10w 条 * 1KB（保守）= ~100 MB（仅 traceId 索引）
- 实际日志 List 平均 ~50 条/trace × 100 KB/trace = **~10 GB 峰值 Redis 占用**

> **结论**：默认配置下 Redis 峰值 ~10GB，**强烈建议消费方监控 `redis.memory.used` 指标**。

---

## 九、已知限制

> 主动暴露边界，避免误用。

| 限制 | 阈值 | 说明 |
|---|---|---|
| **单 Redis 峰值** | ≤ 50K 日志/秒 | 假设 4 worker × Pipeline 500 条/批 × 25 批/秒；超过需评估 Redis 性能 |
| **全局队列裁剪粒度** | 100000 条（默认） | 突发流量瞬时可能超���，受 LLEN 单次裁剪限制 |
| **单 trace 日志数** | 5000 条（默认） | 超限后 LTRIM 丢弃最老的，业务死循环时高频日志丢失 |
| **控制台查询响应** | ≤ 1000 条 | 单 trace 大于此值会被截断，需用导出接口 |
| **采样支持** | ❌ v1.0 不支持 | 高 QPS 服务（>10K QPS）慎用，可能撑爆 Redis |
| **跨 Region** | ❌ 不建议 | Redis 网络延迟 > 10ms 时 Pipeline 优势丧失 |
| **Redis Cluster 兼容性** | ⚠️ 部分 | `trace_log:{traceId}` 在同一槽位 OK；`trace_global_queue` 是单 key 热点，建议分片（见 `global-queue-shards`） |
| **日志不持久化** | ⚠️ 设计如此 | 仅作为短期调试工具，**关键日志仍走 ELK / Loki** |
| **不支持日志采样** | ⚠️ v1.0 | 计划 v2.5 自适应采样 |
| **不支持 OTel Logs 协议** | ⚠️ 长期 | 当前仅 JSON 格式，未对接 OTLP |

---

## 十、测试矩阵

> 实现前定清单，避免漏测。

### 10.1 单元测试（unit/）

| 类 | 关键用例 |
|---|---|
| `AsyncRedisLogAppender` | append 耗时 < 0.05ms / 队列满丢弃计数 / shutdown drain 超时 / RateStop 计数 |
| `DynamicLevelTurboFilter` | MDC 命中 ACCEPT / 未命中 DENY / 多级（DEBUG 提权允许 TRACE） / 线程复用无污染 |
| `TraceLogSwitchInterceptor` | 命中规则提权 / 多规则取最高 / afterCompletion 清理 MDC / SpEL 异常降级 |
| `TraceIdResolver` | 32 hex 标准化 / 非 hex 输入处理 / null 返回 |
| `SwitchRuleCache` | 加载已存在 / Pub/Sub 接收 / TTL 失效 |
| `RateLimiter` | 200 条/秒限制 / 突发令牌桶 |

### 10.2 功能测试（functional/）

| 类 | 关键用例 |
|---|---|
| `TraceLogStore` | SETNX 真首次执行 Lua / SETNX 失败跳过 Lua / Pipeline RPUSH+LTRIM / Lua 容量裁剪 / Lua TTL 设置 |
| `PubSub` | 启动 MGET 加载 / 增量接收 / 断连重连 / 5s 重拉周期验证 |
| `StreamSubscriber`（可选升级） | XREADGROUP / ack / last-delivered-id 续读 |
| `LogExporter` | JSON Lines 导出 / 文本导出 / gzip 压缩 / 10MB 截断 / 频控 5次/分钟 |
| `LocalFallbackWriter` | 滚动文件 100MB / 1h 切换 / Replayer 重连回灌 / 时间戳过期跳过 |

### 10.3 集成测试（integration/）

| 场景 | 验证点 |
|---|---|
| **多节点聚合** | 3 个 Spring Boot 实例共享 Redis，同 traceId 日志聚到同一 List |
| **跨服务 traceId** | 服务 A 调用服务 B，A 和 B 的日志在同一 List |
| **Redis Cluster hashtag** | 分片模式下 `trace_log:{traceId}` 槽位一致；`trace_global_queue:{shard}` 分片正确 |
| **Redis 故障降级** | 停止 Redis → 队列累积到 fallback 文件 → 重启 Redis → 自动回灌 |
| **优雅停机** | SIGTERM → 等待 10s drain → 超时强制退出 + ERROR 日志 |
| **Pub/Sub 断连** | kill Redis pub/sub 连接 → 5s 重拉生效 → 状态恢复一致 |
| **多租户隔离** | tenant A 无法查到 tenant B 的 traceId |
| **IP 白名单** | 非白名单 IP 访问控制 API → 403 |
| **启动 fail-fast** | 未配置 `api.auth-validator-bean` → 启动失败 / 未配置 `tenant.key-spel` 但开启 tenant → 启动失败 |

### 10.4 性能测试（performance/）

| 场景 | 目标 |
|---|---|
| **业务线程序列化耗时** | append() p99 < 0.05ms |
| **Worker 吞吐** | 单 worker 持续 ≥ 10K evt/s |
| **Redis 写入吞吐** | 4 worker × Pipeline ≥ 30K evt/s |
| **Redis 故障切换** | 检测 + 切降级 ≤ 3s |
| **10w trace 满载** | 持续运行 1h，Redis 内存稳定（裁剪生效） |

### 10.5 手动测试（manual/）

| 场景 | 验证点 |
|---|---|
| **控制台查询** | 输入 traceId → 看到全链路日志 |
| **控制台开关** | 开 DEBUG → 该用户后续请求日志提权 → 1h 后自动失效 |
| **控制台导出** | 导出为 .log.gz → 解压查看正常 |
| **断网恢复** | kill -9 网络 → 重启 → Pub/Sub 重连 + 重拉生效 |

---

## 十一、责任边界

> **明确框架提供什么、业务方负责什么**，避免实现时扯皮。

| 责任项 | 框架负责 | 业务方负责 |
|---|---|---|
| **traceId 生成与透传** | ❌（仅读取） | ✅ 接入 Micrometer Tracing / OTel SDK |
| **traceId 注入 MDC** | ✅（`TraceIdResolver` 兜底） | ❌ |
| **traceId 标准化 32-hex** | ✅ | ❌ |
| **动态级别提权（TurboFilter）** | ✅ | ❌ |
| **敏感字段脱敏** | ❌（仅暴露扩展点） | ✅ 接入 `framework4j-sensitive` 或自实现 `LogMaskingConverter` |
| **Redis 高可用** | ❌ | ✅ 主从 / Cluster / Sentinel 配置 |
| **Redis Key 容量管控** | ✅ | ❌（仅配置参数） |
| **本地降级目录** | ✅（写入） | ✅ **挂载 hostPath / PVC**（容器化必填） |
| **控制台 API 暴露** | ✅ | ❌ |
| **控制台鉴权** | ❌（仅暴露 `TraceLogAuthValidator` 接口） | ✅ 实现接口并接入 `framework4j-accesstoken` |
| **多租户隔离** | ✅（Redis Key 加前缀） | ✅ 实现 `tenant.key-spel` 取租户 |
| **审计日志** | ❌ | ✅ 接入 `framework4j-audit` |
| **指标暴露** | ✅（v2.4） | ❌ |
| **Grafana Dashboard** | ❌ | ✅（基于暴露的指标自建） |
| **告警规则** | ❌ | ✅（如 Redis 内存 > 8GB 告警） |
| **控制台前端** | ✅（单 HTML 文件） | ❌（如需定制可自行替换） |
| **导出格式扩展** | ✅（txt / json / gzip） | ✅ 自定义格式需实现 `LogExporter` 接口 |

---

## 十二、相关文档

- `framework4j-api/` — `ApiResponse` / `ApiCode` / `TraceContext`
- `framework4j-redis/` — `MultiRedisManager`（多 Redis 数据源）
- `framework4j-sql-tracing/` — `TraceIdDruidFilter`（trace_id 注入 MDC）
- `framework4j-accesstoken/` — `TokenContext`（取 userId）
- `framework4j-audit/` — `@Auditable` AOP（开关操作审计）
- `Java开发准则.md` §10 日志规范
- `mc-monitor` — Tracing / Logging / Metrics 三大支柱

---

**文档版本**：v1.3.3
**维护者**：framework4j-tracelog 模块作者
**反馈**：通过 `framework4j` 主仓库 Issue 提交

### 文档变更

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-08-24 | 初稿 |
| v1.1 | 2026-08-24 | 整合评审反馈：Pub/Sub 默认 5s 可配置；容量参数全部可配置；单线程改分片 Disruptor；SETNX 分布式首次判定；轻量级 append；per-trace 限速；graceful shutdown；敏感字段脱敏扩展点；API 鉴权 fail-fast；多租户隔离；日志导出接口；纯 HTML 单文件控制台（移除 Vue 3 计划）；新增 JSON Schema / 已知限制 / 测试矩阵 / 责任边界章节 |
| v1.2 | 2026-08-25 | **代码已落地**：实现 TenantKeyResolver（多租户 SpEL）/ FallbackReplayer（异步回灌）/ SwitchStreamsListener（Redis Streams 替代）/ TraceLogMetrics（Micrometer 6 类指标）/ SwitchRuleCache.valuesOf()（URL pattern Ant 匹配）；LocalFallbackWriter 启动硬失败；framework4j-web 升级为强依赖；删除冗余 GracefulShutdownHook（Disruptor 自带）；删除冗余 lifecycle 包；目录结构更新；已 `mvn install` 到本地 Maven |
| v1.3.0–v1.3.2 | 2026-08-25 | 接入实战修复（下游反馈）：**编程式注册** TurboFilter/Appender（logback-spring.xml 声明会因无无参构造启动失败，§5.4 重写为"零声明"）；`buildLogKey` 归一化 traceId（带横线 UUID 查询不命中）；`SwitchRule` type 小写归一化（写/读/匹配侧 key 一致）；`StringRedisTemplate` Bean 改名 `traceLogStringRedisTemplate`（避免与 Spring Boot 同名 Bean 冲突）；Interceptor 改拦 `/**` 并排除 tracelog 自身路径；resync SpEL 属性路径修正 `sync.*` |
| v1.3.3 | 2026-08-26 | **端到端冒烟通过**（§十三）+ 3 个运行链路修复：① SETNX 判定移入 Lua 内部（pipeline 盲发导致多节点重复入队，集成测试锁定）；② `SwitchRuleCache#replaceAll` diff 合并零窗口重拉（原 clear+重放每 5s 瞬时空窗，并发读 0 miss 单测锁定）；③ 查询/导出双字段名兼容 LogstashEncoder 默认名（`logger_name`/`thread_name`/`@timestamp`，控制台字段不再空白）；控制台 `/tracelog.html` view-controller 转发；集成测试回退本机 Redis（db 15 隔离 + flushDb） |
| v1.4.0 | 2026-08-26 | **新增敏感字段脱敏**（`SensitiveLogMasker`，按 key 匹配，Worker 线程，默认开启可配）；**新增 4 个运行链路集成测试**（多节点聚合 / resync diff / Streams 生命周期 / 停机 drain）；测试 45 → 58 |

### 实现状态

| 阶段 | 内容 | 状态 |
|---|---|---|
| 阶段 0 | 模块骨架（pom + 父 POM 注册） | ✅ |
| 阶段 1 | 配置类与自动装配 | ✅ |
| 阶段 2 | Redis 存储与降级（含 Replayer） | ✅ |
| 阶段 3 | 分片 Disruptor Appender | ✅ |
| 阶段 4 | 开关层（Pub/Sub + Streams + Interceptor） | ✅ |
| 阶段 5 | Logback TurboFilter 提权 | ✅ |
| 阶段 6 | API 层（查询 / 开关 / 导出） | ✅ |
| 阶段 7 | 纯 HTML 单文件控制台 | ✅ |
| 阶段 8 | 单元测试（40 通过 / 0 失败） | ✅ |
| 阶段 9 | 集成测试（5 通过；嵌入式失败自动回退本机 Redis db15） | ✅ |
| 阶段 10 | README + 父 POM 注册 + install | ✅ |
| 冒烟 | demo 端到端 7 项验证（§十三） | ✅ |

---

## 十三、运行链路验证（2026-08-26 实测）

> 环境：`framework4j-demo` + docker `redis:7.2`（localhost:6379），全链路手工冒烟。

| # | 验证项 | 结果 |
|---|---|---|
| 1 | 应用启动，TraceLog 全链路装配（FallbackWriter → Store → Replayer → Appender×2 workers → PubSub → TurboFilter → Resync） | ✅ |
| 2 | 业务请求日志按 traceId 聚合到 Redis（`trace_log:{32-hex}` List + `:meta` 标记 + 全局队列入队） | ✅ |
| 3 | 查询 API：**带横线 UUID** 也能命中（`buildLogKey` 归一化）；ts/level/logger/thread/tsIso 字段完整（LogstashEncoder 双字段名兼容） | ✅ |
| 4 | **开关提权**：`POST /api/logs/switch`（user:10086 DEBUG）→ 带 `X-User-Id: 10086` 的请求采集到 5 条（含 DEBUG×2 + TRACE×1）；无开关对照仅 INFO×2 | ✅ |
| 5 | 控制台 `GET /tracelog.html` → 200（view-controller 转发到 `classpath:/static/tracelog/index.html`，10.8KB） | ✅ |
| 6 | txt 导出（gzip）：378B，5 条含 DEBUG/TRACE，字段完整 | ✅ |
| 7 | 应用重启后���关仍生效（Redis 持久，重拉加载），提权请求仍采到 5 条 | ✅ |

测试汇总（v1.4.0）：**58 通过 / 0 失败 / 0 跳过**
（单元 49 = 原 40 + 脱敏 9；集成 9 = Store 5 + 运行链路 4）
（单元 40 + 集成 5；集成测试优先嵌入式 Redis（16380），失败自动回退本机 Redis **db 15**（`flushDb` 只清测试库，绝不触碰业务库 0））