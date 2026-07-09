# Framework4j Idempotency 用户指南

> **Idempotency-Key 拦截器 | Redis 状态机 | 防重放防重复提交**

## 📚 目录

- [核心特性](#核心特性)
- [快速开始](#快速开始)
  - [环境要求](#环境要求)
  - [三步接入](#三步接入)
- [配置详解](#配置详解)
  - [基础配置](#基础配置)
  - [配置示例](#配置示例)
- [使用指南](#使用指南)
  - [客户端调用](#1-客户端调用)
  - [服务端行为](#2-服务端行为)
  - [路径白名单](#3-路径白名单)
- [架构原理](#架构原理)
  - [Redis 存储结构](#redis-存储结构)
  - [状态机](#状态机)
  - [请求处理流程](#请求处理流程)
- [最佳实践](#最佳实践)
- [常见问题](#常见问题)
- [错误码](#错误码)
- [性能与可观测性](#性能与可观测性)

---

## 核心特性

| 特性 | 说明 | 优势 |
|------|------|------|
| **标准化 Header** | `Idempotency-Key: <uuid v4>` | 对齐 Stripe / 阿里云 / 字节大厂做法 |
| **Redis 状态机** | SETNX + 48h TTL，回放语义明确 | 高性能 + 强一致 |
| **Body 哈希校验** | 同 key + 不同 body 必拒（DUPLICATE_SUBMIT） | 防止 key 被误用 |
| **响应回放** | 业务完成后 Redis 缓存响应体，48h 内同请求秒级回放 | 网络重试/弱网安全 |
| **零侵入** | 业务 Controller 无需任何注解或代码改动 | 接入即用 |
| **UUID v4 严格校验** | header 必须符合 RFC 4122 v4 格式 | 防止客户端乱填 |
| **可降级** | `enabled=false` 时整条链路完全关闭 | 灰度上线 / 紧急回滚 |

---

## 快速开始

### 环境要求

| 组件 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 17+ | 必需 |
| Spring Boot | 3.2+ | 必需，使用 Jakarta 命名空间 |
| Redis | 6+ | 必需，用于幂等状态存储 |
| framework4j-core | 1.0.0-SNAPSHOT+ | 必需，提供 `ApiResponse` / `ApiCode` |
| framework4j-redis | 1.0.0-SNAPSHOT+ | 必需，通过 `MultiRedisManager` 取 `StringRedisTemplate` |

### 三步接入

**步骤 1：添加依赖**

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-idempotency</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

或通过聚合包：

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>framework4j-all</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**步骤 2：配置文件**

```yaml
framework4j:
  idempotency:
    enabled: true
                  # 默认 false，必须显式开启
        headerName: Idempotency-Key # 默认 Idempotency-Key
        keyPrefix: idem
                # 默认 idem
        ttlSeconds: 172800
             # 默认 48h
        bodyHashRequired: true
         # 默认 true
        redisName: default
             # 默认 default，从 framework4j-redis 取

**步骤 3：客户端在写接口上带 header 调用**

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 7f8e9a4b-c2d1-4a8e-b3c5-1d2e3f4a5b6c" \
  -d '{"sku_id": "SKU-001", "qty": 2}'
```

业务 Controller 不需要任何改动 —— 拦截器已自动生效。

---

## 配置详解

### 基础配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `framework4j.idempotency.enabled` | `boolean` | `false` | 是否启用幂等拦截。**必须** 显式设为 `true` |
| `framework4j.idempotency.headerName` | `String` | `Idempotency-Key` | 客户端传入的 header 名 |
| `framework4j.idempotency.keyPrefix` | `String` | `idem` | Redis key 前缀，与业务 key 隔离 |
| `framework4j.idempotency.ttlSeconds` | `long` | `172800` (48h) | 幂等状态在 Redis 的保留时长 |
| `framework4j.idempotency.bodyHashRequired` | `boolean` | `true` | 是否对 body 哈希后才允许重放 |
| `framework4j.idempotency.redisName` | `String` | `default` | 从 `MultiRedisManager` 取的 Redis 数据源名 |

### 配置示例

**生产推荐（默认行为）**：

```yaml
framework4j:
  idempotency:
    enabled: true
        ttlSeconds: 172800
      # 48h，对齐 mc-api-spec v1.6

**灰度期间（放宽 body 哈希校验）**：

```yaml
framework4j:
  idempotency:
    enabled: true
        bodyHashRequired: false
      # 任何 body 都视为同一请求，仅按 key 判重

**多 Redis 实例（业务 Redis 与幂等 Redis 隔离）**：

```yaml
framework4j:
  redis:
    enabled: true
        datasources:
          default: { host: redis-biz, port: 6379, database: 0 }
          idempotency: { host: redis-idem, port: 6379, database: 0 }
      enabled: true
      redisName: idempotency
        # 用专属 Redis
```

---

## 使用指南

### 1. 客户端调用

写操作（POST / PUT / DELETE）必须带 `Idempotency-Key`，且必须是 **UUID v4**（RFC 4122 格式：8-4-4-4-12，第 3 段以 `4` 开头，第 4 段以 `8/9/a/b` 开头）。

```javascript
// JavaScript 浏览器
const key = crypto.randomUUID(); // 现代浏览器原生 UUID v4
fetch('/api/orders', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Idempotency-Key': key,
  },
  body: JSON.stringify({ sku_id: 'SKU-001', qty: 2 })
});
```

```java
// Java 后端调用
String idempotencyKey = UUID.randomUUID().toString();
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
headers.set("Idempotency-Key", idempotencyKey);
```

**关键纪律**：
- ✅ 同一笔业务操作用同一个 key；网络重试时复用
- ❌ 不要每个 HTTP 请求生成一个新 key（会失去幂等意义）
- ❌ 不要把业务主键当 key（应保持 UUID 形态，防止泄露业务信息）

### 2. 服务端行为

| 场景 | 客户端 | 服务端响应 |
|------|--------|-----------|
| **GET / 读操作** | 不带 header | 正常处理（拦截器对无 header 直接放行） |
| **首次 POST** | 带合法 UUID v4 | 业务正常执行；afterCompletion 阶段缓存响应 |
| **重试（同 key + 同 body + 已完成）** | 同 key 重试 | **回放缓存的 200 响应体** |
| **重试（同 key + 同 body + 进行中）** | 同 key 重试 | **409 DUPLICATE_SUBMIT**（v1 简化策略） |
| **同 key + 不同 body** | key 复用但 body 改了 | **409 DUPLICATE_SUBMIT**（防误用） |
| **非法 UUID** | `Idempotency-Key: abc123` | **400 PARAM_FORMAT_ERROR** |

### 3. 路径白名单

默认拦截 `/api/**`。如需自定义：

```java
@Configuration
@ConditionalOnProperty(prefix = "framework4j.idempotency", name = "enabled", havingValue = "true")
public class CustomIdempotencyWebMvcConfig extends IdempotencyWebMvcConfig {
    public CustomIdempotencyWebMvcConfig(IdempotencyInterceptor interceptor) {
        super(interceptor);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(idempotencyInterceptor)
                .addPathPatterns("/api/orders/**", "/api/payments/**");
    }
}
```

> ⚠️ 拦截器需要**两次**读取 request body，框架已通过 `IdempotencyBodyCacheFilter`（`Ordered.HIGHEST_PRECEDENCE + 10`）将 request 包成 `ContentCachingRequestWrapper`。不要在自己代码里再次包装，会导致 body 读取为空。

---

## 架构原理

### Redis 存储结构

每个幂等 key 在 Redis 中存一条 `String`，TTL 48h：

```
Key:   idem:/api/orders:7f8e9a4b-c2d1-4a8e-b3c5-1d2e3f4a5b6c
Value: <sha256(body)>|<status>
       └─ 11 字符 ─┘
         └─ PENDING / OK:<response_json> / ERR:<http_status> ┘
```

**示例**：

```
idem:/api/orders:7f8e9a4b-c2d1-4a8e-b3c5-1d2e3f4a5b6c
→ 5e8a1b...|PENDING
→ 5e8a1b...|OK:{"code":0,"data":{"order_id":"OD001"}}
→ 5e8a1b...|ERR:500
```

### 状态机

```
        首次请求（SETNX 成功）
              │
              ▼
   ┌─────────────────────┐
   │
          PENDING
          │ ─── 同 key 重复请求 ──→ 409 DUPLICATE_SUBMIT
   └─────────────────────┘
              │
       业务执行完毕
              │
              ▼
   ┌─────────────────────┐
   │
     OK:<body>
             │ ─── 同 key 重试 ──→ 200 + 回放 <body>
   └─────────────────────┘

   异常路径：
              ▼
   ┌─────────────────────┐
   │
     ERR:<http_status>
     │ ─── 同 key 重试 ──→ 409 DUPLICATE_SUBMIT
   └─────────────────────┘
```

### 请求处理流程

```
HTTP POST /api/orders
        │
        ▼
[IdempotencyBodyCacheFilter]   ← 包 ContentCachingRequestWrapper (HIGHEST+10)
        │
        ▼
[IdempotencyInterceptor.preHandle]
   1. 读 header
   2. 无 → 放行；有 → 校验 UUID v4
   3. 算 body hash
   4. SETNX → 首次/已存在
   5. 已存在 → GET → 比较 hash → 放行/回放/409
        │
        ▼
[业务 Controller 执行]
        │
        ▼
[IdempotencyInterceptor.afterCompletion]
   - 2xx → 写回 "OK:<body>"
   - 非 2xx → 写回 "ERR:<status>"
```

---

## 最佳实践

### ✅ DO

1. **关键写操作都带 key**：订单创建 / 支付 / 退款 / 提现 / 邀请发放等"不可重复"的操作。
2. **前端在用户点击时生成 key，提交后丢弃**：用 `crypto.randomUUID()` 一行搞定。
3. **后端在网络重试前不要重新生成 key**：重试必须复用。
4. **业务失败也消耗 key**：若第一次 5xx，重试时拿到 `ERR:500` 状态会返 409 —— 这正是想要的行为（防止重试中真实成功的请求被前一次失败"覆盖"）。
5. **生产环境保留 body 哈希校验（默认）**：仅在你确认客户端不会乱改 body 时才关闭。

### ❌ DON'T

1. **不要给 GET 请求带 key**：GET 天然幂等，多此一举。
2. **不要用业务主键当 key**：`Idempotency-Key: order-12345` 会泄露业务信息；UUID 才是正解。
3. **不要在不同接口间复用 key**：key 必须与 URL 路径绑定（这是 Redis key 设计的一部分）。
4. **不要关闭 body 哈希校验以求"宽松"**：除非你能保证客户端不会改 body；否则会失去"防 key 误用"的核心防御。
5. **不要在 `spring.mvc.async` 异步接口上依赖回放**：v1 的 afterCompletion 在 sync 流程中可靠；async 流程的回放需自行实现。

---

## 常见问题

### Q1：拦截器会影响 GET 请求吗？

不会。`IdempotencyInterceptor` 仅校验 `Idempotency-Key` header；无 header 直接放行，不触 Redis。GET / OPTIONS / HEAD 都不需要带 key。

### Q2：拦截器与 `framework4j-accesstoken` 的 `TokenInterceptor` 顺序？

```
请求 → IdempotencyBodyCacheFilter (HIGHEST+10)
     → IdempotencyInterceptor (先于 TokenInterceptor 写 body cache)
     → TokenInterceptor (鉴权)
     → Controller
```

幂等校验在鉴权之前 —— 因为幂等是"同一请求识别"，鉴权是"用户身份识别"，两者独立。

### Q3：拦截器能关闭吗？

能。`framework4j.idempotency.enabled=false` 时：
- `IdempotencyAutoConfiguration` 不会注册
- `IdempotencyInterceptor` Bean 不会创建
- `IdempotencyWebMvcConfig` 不会注册
- 但 `IdempotencyBodyCacheFilter` 仍会注册（无副作用，纯包装）

### Q4：Redis 挂了会怎样？

`IdempotencyInterceptor` 内部不捕获 Redis 异常 —— 异常会冒泡到 `GlobalExceptionHandler`，返 10900 `SYSTEM_BUSY`（系统繁忙）。这是有意的设计：**幂等层是安全网，宁可报错不可漏放**。

如需降级为"Redis 故障时放行"，可在拦截器内增加 `try/catch` 并 `log.warn` 后返 `true`（业务自决）。

### Q5：body 含文件上传时 body 哈希怎么算？

`ContentCachingRequestWrapper` 默认缓存前 8KB body。文件上传超过 8KB 时哈希会失真。生产大文件场景建议：
- 仅对小 body（< 1MB）启用 `bodyHashRequired`
- 大文件接口关闭 body 哈希，仅按 key 判重

```yaml
framework4j:
  idempotency:
    enabled: true
        bodyHashRequired: false
      # 文件上传场景

### Q6：拦截器能跨服务实例生效吗？

能。Redis 是共享存储，任何一个实例首次处理后，48h 内所有实例都能识别同 key 重复请求。**这就是它的核心价值** —— 移动端在实例 A 创建订单后，重试打到实例 B 也能正确回放。

---

## 错误码

| HTTP 状态 | 业务 code | 含义 | 触发条件 |
|-----------|-----------|------|----------|
| 200 | 0 | 成功 / 回放 | 业务成功 或 同 key+同 body 已完成回放 |
| 400 | 10102 | `PARAM_FORMAT_ERROR` | `Idempotency-Key` 不是 UUID v4 格式 |
| 409 | 10501 | `DUPLICATE_SUBMIT` | 同 key+不同 body / 同 key+同 body 但首次未完成 |

错误响应体使用 `framework4j-core` 的 `ApiResponse` 信封（5 字段：`code` / `message` / `data` / `error` / `timestamp`）：

```json
{
  "code": 10501,
  "message": "请勿重复提交",
  "data": null,
  "error": null,
  "timestamp": 1782534061519
}
```

---

## 性能与可观测性

### 性能特征

| 操作 | 延迟 | 备注 |
|------|------|------|
| 无 header 请求 | 0 Redis 调用 | 仅做 header 存在性判断 |
| 首次请求 | +1 Redis `SETNX`（写） | 业务完成后 +1 `SET` 写回 |
| 重复请求（已完成） | +1 Redis `GET` | 直接返回缓存 body |
| 重复请求（进行中） | +1 Redis `SETNX` + 1 `GET` | 返 409 |

整体延迟影响 < 1ms（同 IDC Redis 场景）。

### 日志

拦截器使用 SLF4J，关键事件通过 Lombok `@Slf4j` 记录：

- 启动：`【Idempotency】注册 Idempotency-Key 拦截器，路径 /api/**`
- 启动：`【Idempotency】从 Redis 数据源 'xxx' 加载 StringRedisTemplate`
- 异常缓存值：`[Idempotency] 异常缓存值 key=xxx value=xxx`
- 回写失败：`[Idempotency] 回写 Redis 失败 key=xxx`

### 监控建议

| 指标 | 采集方式 | 告警阈值 |
|------|----------|----------|
| 重复请求命中率 | Micrometer `@Timed` 包 `preHandle` + `OK` 分支 | 命中率 > 30% 需排查客户端重试逻辑 |
| Redis SETNX 失败率 | 同上 | > 1% 检查 Redis 健康 |
| 平均耗时 | Micrometer Histogram | P99 > 5ms 需优化 |

### 与 framework4j-datasource 的链路追踪联动

`framework4j-datasource` 的 `TraceIdDruidFilter` 会把 `trace_id` 透传到 SQL 注释。配合本拦截器，幂等相关的 Redis 调用也会带上同一 `trace_id`，方便排查"为什么这次重试命中了缓存"这类问题。

---

## 退出 mc-doc-api 范围

本文档仅描述 framework4j-idempotency 客户端/服务端使用。如需编写业务 API 文档，请参考 `mc-doc-api` 主规范。
