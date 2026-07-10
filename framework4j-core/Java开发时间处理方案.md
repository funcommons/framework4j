# Java开发时间处理规范

> **版本**: v1.3.0
> **技术栈**: PostgreSQL (UTC) + Java (OffsetDateTime) + FastJSON2
> **核心策略**: 物理存储 UTC → 逻辑层 GMT+8 → 前后端交互 GMT+8 → 前端展示本地格式
> **重要变更**: 前后端交互统一使用 GMT+8 时区，前端解析时间禁止使用用户浏览器时区

---

## 📋 目录

- [1. 核心原则](#1-核心原则)
- [2. 时间格式规范](#2-时间格式规范)
- [3. PostgreSQL 数据库规范](#3-postgresql-数据库规范)
- [4. Java 后端开发规范](#4-java-后端开发规范)
- [5. 前端开发规范](#5-前端开发规范)
- [6. 微服务交互规范](#6-微服务交互规范)
- [7. 附录](#7-附录)

---

## 1. 核心原则

### 🎯 核心规范

1. **数据类型规范**：**禁止使用 String 类型传递时间参数**
   - Controller、Service、Mapper 方法参数**必须**使用 `OffsetDateTime`
   - 依赖 Spring MVC 自动转换，无需手动解析
   - 类型安全，避免运行时错误

2. **时间格式注解规则**：后端到前端默认使用 GMT+8 ISO-8601 格式

| 场景 | 注解位置 | 输出格式 | 说明 |
|------|---------|----------|------|
| **前端API（默认）** | ❌ 不使用注解 | `2025-12-10T10:00:00+08:00` | **默认推荐**，GMT+8 时区明确 |
| **前端API（简化）** | ✅ Controller 方法/类加 `@LocalTimeFormat` | `yyyy-MM-dd HH:mm:ss` | **需要在设计文档和接口文档中说明** |
| **内部API** | ❌ 不使用注解 | `2025-12-10T10:00:00+08:00` | 保留完整时区信息 |
| **Entity** | ❌ 不使用注解 | `2025-12-10T10:00:00+08:00` | 数据库实体类 |

**重要说明**:
- **默认推荐**：前后端交互使用 GMT+8 ISO-8601 格式（`2025-12-10T10:00:00+08:00`），时区明确，无歧义
- **特殊需求**：如果需要使用本地时间格式（`yyyy-MM-dd HH:mm:ss`），必须：
  1. 在 Controller 方法或类上添加 `@LocalTimeFormat` 注解
  2. 在设计文档中明确说明该接口使用本地时间格式
  3. 在接口文档（Swagger/OpenAPI）中标注时间格式说明
- **注意**：`@LocalTimeFormat` 注解加在 **Controller 方法或类**上，而非 VO/DTO 字段上

3. **时区配置规范**：使用系统默认时区，避免硬编码
   - 代码中使用 `OffsetDateTime.now()`（不带参数）
   - 通过 JVM 参数 `-Duser.timezone` 或环境变量 `TZ` 配置时区
   - 全球化部署只需修改环境配置，无需修改代码

4. **数据库时区配置**：确保客户端与数据库时区一致
   - **推荐**：`ALTER USER <用户名> SET timezone TO 'Asia/Shanghai';`（永久生效）
   - 备选：连接字符串加 `timezone` 参数（临时生效）
   - 配置后查询结果显示 GMT+8 格式，清晰无歧义

5. **特殊场景时间格式选择**：消息队列 / JSONB 字段 / 日志
   - **高性能场景（推荐）**：使用**时间戳** (long 类型)
     - 理由：绝对物理时间，无视配置差异，序列化/反序列化开销最小
     - 适用：Kafka/RocketMQ 消息体、Redis JSONB、高频日志
   - **普通场景**：使用 **ISO-8601** (`2025-12-10T10:00:00+08:00`)
     - 理由：调试方便，可读性好，显式携带时区防止歧义
     - 适用：低频消息、配置文件、审计日志

### 📊 各层职责对比

| 层级 | 技术组件 | 时间格式 | 时区处理 | 示例                         |
|------|----------|----------|----------|----------------------------|
| **数据库** | PostgreSQL + TIMESTAMPTZ | **UTC** | 自动转换 | `2025-12-10T02:00:00Z UTC` |
| **连接层** | JDBC Driver | **GMT+8** | 自动转换 | `2025-12-10 10:00:00+08:00` |
| **应用层** | Java + OffsetDateTime | **GMT+8** | 显式保留 | `2025-12-10T10:00:00+08:00` |
| **前端API（默认）** | REST API（无注解） | **GMT+8 ISO-8601** | 保留时区 | `"2025-12-10T10:00:00+08:00"` |
| **前端API（简化）** | REST API + `@LocalTimeFormat` | **本地格式** | 格式化输出 | `"2025-12-10 10:00:00"` ⚠️ **需文档说明** |
| **内部API** | Feign/Dubbo（无注解） | **GMT+8 ISO-8601** | 保留时区 | `"2025-12-10T10:00:00+08:00"` |

---

## 2. 时间格式规范

### 📚 5种时间格式对照表

本规范涉及5种常用时间格式，每种格式有各自的应用场景和优缺点。

| 格式名称              | 简写                  | 格式示例 | Java类型 | 数据大小 | 主要特点 |
|-------------------|---------------------|---------|----------|----------|----------|
| **UTC 格式**        | UTC                 | `2025-12-10T02:00:00Z` | String | ~20字节 | ✅ 标准UTC<br>✅ 零时区<br>⚠️ 需换算 |
| **GMT 北京时间** | GMT+8 | `2025-12-10T10:00:00+08:00` | String | ~30字节 | ✅ 国际标准<br>✅ 显式时区<br>✅ 可读性好 |
| **本地简化格式**        | Local Format        | `2025-12-10 10:00:00` | String | ~19字节 | ✅ 简洁<br>✅ 用户友好<br>⚠️ 无时区信息 |
| **Unix 时间戳（毫秒）**  | Timestamp / Millis  | `1733882400000` | Long | 8字节 | ✅ 性能极高<br>✅ 绝对时间<br>❌ 不易读 |
| **Unix 时间戳（秒）**   | Timestamp / Epoch   | `1733882400` | Long | 8字节 | ✅ 性能极高<br>✅ 兼容性好<br>❌ 不易读 |

---

### 1️⃣ ISO-8601 完整格式（推荐）

**格式名称**：ISO-8601 Time Format with Timezone

**简写**：ISO-8601 / ISO / ISO Format

**格式说明**：国际标准时间表示格式（ISO 8601），包含完整的年月日、时分秒和时区偏移信息。

**格式示例**：
```
2025-12-10T10:00:00+08:00  （中国区 GMT+8）
2025-12-10T14:30:15-05:00  （美国东部 EST）
2025-12-10T19:45:30+00:00  （伦敦 GMT）
```

**格式结构**：
```
YYYY-MM-DD T HH:mm:ss ±HH:mm
│       │ │ │       │ │
│       │ │ │       │ └─ 时区偏移（小时:分钟）
│       │ │ │       └─── 秒（00-59）
│       │ │ └─────────── 分钟（00-59）
│       │ └───────────── 小时（00-23）
│       └─────────────── 日期时间分隔符
└─────────────────────── 日期（年-月-日）
```

**Java 代码示例**：
```java
// 后端生成 ISO-8601 格式
OffsetDateTime now = OffsetDateTime.now();
String isoString = now.toString();
// 输出: "2025-12-10T10:00:00+08:00"

// 解析 ISO-8601 格式
OffsetDateTime parsed = OffsetDateTime.parse("2025-12-10T10:00:00+08:00");
```

**JavaScript 代码示例**：
```javascript
// 前端生成 ISO-8601 格式（使用 DateTimeUtils）
const isoString = DateTimeUtils.toISO(new Date());
// 输出: "2025-12-10T10:00:00+08:00"

// 前端解析 ISO-8601 格式
const date = DateTimeUtils.from("2025-12-10T10:00:00+08:00");
```

**使用场景**：
- ✅ **前端 ↔ 后端 API 交互（默认推荐）**
- ✅ **微服务 ↔ 微服务 RPC 调用**
- ✅ 审计日志、操作日志
- ✅ 配置文件、低频消息队列

**优点**：
- ✅ 国际标准，跨语言、跨平台兼容性好
- ✅ 显式携带时区信息，避免时区歧义
- ✅ 人类可读，便于调试和日志查看
- ✅ 标准格式，无需自定义解析器

**缺点**：
- ❌ 字符串较长（~30字节），序列化开销较大
- ❌ 解析速度比时间戳慢（~10倍）

**何时使用**：
- 🎯 **默认选择**：前后端交互、微服务调用、日志记录
- 🎯 当需要保留完整时区信息时
- 🎯 当可读性和调试便利性优先于性能时

---

### 2️⃣ 本地简化格式（特殊场景）

**格式名称**：Local Simplified Time Format

**简写**：Local Format / 简化格式 / 本地格式

**格式说明**：去除时区信息的简化时间表示，适合前端用户展示。⚠️ **无时区信息，必须配合 @LocalTimeFormat 注解和文档说明使用。**

**格式示例**：
```
2025-12-10 10:00:00  （中国区默认格式）
12/10/2025 10:00:00  （美国区格式）
10/12/2025 10:00:00  （欧洲区格式）
2025年12月10日 10:00:00  （日本区格式）
```

**格式结构**：
```
YYYY-MM-DD HH:mm:ss
│       │  │
│       │  └─────── 时间（时:分:秒）
│       └────────── 空格分隔符
└────────────────── 日期（年-月-日）
```

**Java 代码示例**：
```java
// 后端生成本地格式（需要 @LocalTimeFormat 注解）
@RestController
public class UserController {

    @LocalTimeFormat  // ⚠️ 必需注解
    @GetMapping("/users")
    public Result<List<UserVO>> list() {
        // UserVO 中的 OffsetDateTime 字段自动格式化为 "yyyy-MM-dd HH:mm:ss"
        return Result.success(userService.list());
    }
}

// 手动格式化（不推荐）
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
String localString = offsetDateTime.format(formatter);
// 输出: "2025-12-10 10:00:00"
```

**JavaScript 代码示例**：
```javascript
// 前端展示本地格式
const displayTime = DateTimeUtils.format(dateInput);
// 输出: "2025-12-10 10:00:00"

// 前端展示自定义格式
const customFormat = DateTimeUtils.format(dateInput, 'yyyy-MM-dd');
// 输出: "2025-12-10"
```

**使用场景**：
- ✅ 前端用户界面展示（表格、卡片、详情页）
- ✅ 用户输入时的默认格式
- ⚠️ **需要在设计文档和接口文档中明确说明**

**优点**：
- ✅ 简洁、用户友好
- ✅ 符合用户阅读习惯
- ✅ 字符串较短（~19字节）

**缺点**：
- ❌ **无时区信息，容易产生歧义**
- ❌ 不同地区格式不同，需要配置
- ❌ 不适合跨时区场景

**何时使用**：
- 🎯 **仅用于前端展示层**
- 🎯 用户界面需要简洁格式时
- 🎯 **必须在接口文档中说明时区假设**

**⚠️ 重要警告**：
- **禁止**在后端 Service 层、Mapper 层使用本地格式
- **禁止**在微服务 RPC 调用中使用本地格式
- **必须**在 Controller 上加 `@LocalTimeFormat` 注解
- **必须**在设计文档和接口文档中说明

---

### 3️⃣ Unix 时间戳（高性能场景）

**格式名称**：Unix Timestamp (Milliseconds)

**简写**：Timestamp / Epoch / Unix Time / Millis

**格式说明**：自 1970-01-01 00:00:00 UTC 以来经过的毫秒数，表示绝对物理时间，与时区无关。

**格式示例**：
```
1733882400000  （毫秒级，13位）
1733882400     （秒级，10位）
```

**数值含义**：
```
1733882400000 毫秒 = 1733882400 秒
                  = 2025-12-10 10:00:00 GMT+8
                  = 2025-12-10 02:00:00 UTC
```

**Java 代码示例**：
```java
// 获取当前时间戳（毫秒）
long timestamp = System.currentTimeMillis();
// 输出: 1733882400000

// OffsetDateTime 转时间戳
long millis = offsetDateTime.toInstant().toEpochMilli();

// 时间戳转 OffsetDateTime
OffsetDateTime dateTime = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())
    .toOffsetDateTime();
```

**JavaScript 代码示例**：
```javascript
// 获取当前时间戳
const timestamp = Date.now();
// 输出: 1733882400000

// Date 对象转时间戳
const millis = DateTimeUtils.toTimestamp(new Date());

// 时间戳转 Date 对象
const date = DateTimeUtils.from(1733882400000);
```

**使用场景**：
- ✅ **Kafka/RocketMQ 高吞吐消息队列**
- ✅ **Redis 缓存、PostgreSQL JSONB 字段**
- ✅ **高频日志系统（ELK、Loki）**
- ✅ 时序数据库（InfluxDB、Prometheus）
- ✅ 性能敏感的场景（>1000条/秒）

**优点**：
- ✅ **性能极高**：序列化/反序列化速度是 ISO-8601 的 10倍
- ✅ **存储空间小**：8字节固定大小，是 ISO-8601 的 1/4
- ✅ **绝对时间**：不受时区配置影响，全球统一
- ✅ **计算简单**：时间差直接减法，无需解析

**缺点**：
- ❌ **不易读**：人类无法直接识别
- ❌ 调试困难：需要工具转换才能理解
- ❌ 丢失时区上下文

**何时使用**：
- 🎯 **性能优先场景**：吞吐量 >1000条/秒
- 🎯 **存储空间敏感**：JSONB 字段、大量时间数据
- 🎯 **计算密集场景**：时间差统计、聚合分析

**性能对比（100万次操作）**：
| 操作 | 时间戳 | ISO-8601 | 性能差异 |
|------|--------|----------|----------|
| 序列化 | ~50ms | ~500ms | **10倍** |
| 反序列化 | ~30ms | ~300ms | **10倍** |
| JSON大小（1万条） | ~80KB | ~320KB | **4倍** |

---

### 4️⃣ UTC 格式（国际化系统）

**格式名称**：UTC Time Format (Zulu Time)

**简写**：UTC / Zulu / Z Time / GMT

**格式说明**：协调世界时格式，使用 `Z` 后缀表示零时区（UTC+0），适合国际化系统。

**格式示例**：
```
2025-12-10T02:00:00Z  （UTC 时间，对应 GMT+8 的 10:00:00）
2025-12-09T18:00:00Z  （UTC 时间，对应 GMT-8 的 10:00:00）
```

**格式结构**：
```
YYYY-MM-DD T HH:mm:ss Z
│       │ │ │       │ └─ Z 表示 UTC 零时区
│       │ │ └─────────── 时分秒（UTC）
│       │ └───────────── 日期时间分隔符
└───────────────────── 日期（UTC）
```

**Java 代码示例**：
```java
// 生成 UTC 格式
OffsetDateTime utcTime = OffsetDateTime.now(ZoneOffset.UTC);
String utcString = utcTime.toString();
// 输出: "2025-12-10T02:00:00Z"

// 解析 UTC 格式
OffsetDateTime parsed = OffsetDateTime.parse("2025-12-10T02:00:00Z");

// 转换为本地时区
OffsetDateTime localTime = parsed.atZoneSameInstant(ZoneId.systemDefault())
    .toOffsetDateTime();
// 输出: 2025-12-10T10:00:00+08:00（如果系统时区为 GMT+8）
```

**JavaScript 代码示例**：
```javascript
// 生成 UTC 格式
const utcString = new Date().toISOString();
// 输出: "2025-12-10T02:00:00.000Z"

// 解析 UTC 格式
const date = new Date("2025-12-10T02:00:00Z");
```

**使用场景**：
- ✅ 国际化应用（全球部署）
- ✅ 数据库物理存储（PostgreSQL TIMESTAMPTZ）
- ✅ 跨时区数据交换
- ✅ 航空、航海、科学计算

**优点**：
- ✅ 全球统一基准时间
- ✅ 避免夏令时问题
- ✅ 数据库存储标准

**缺点**：
- ❌ 不符合用户本地习惯
- ❌ 需要换算才能理解
- ❌ API 交互不够直观

**何时使用**：
- 🎯 数据库物理存储层
- 🎯 跨时区系统数据交换
- 🎯 需要全球统一时间基准时

---

### 5️⃣ Unix 时间戳（秒级）

**格式名称**：Unix Timestamp (Seconds)

**简写**：Timestamp / Epoch / Unix Time / Seconds

**格式说明**：自 1970-01-01 00:00:00 UTC 以来经过的秒数，表示绝对物理时间，与时区无关。秒级时间戳兼容性更好，适合外部系统集成。

**格式示例**：
```
1733882400       （秒级，10位）
```

**数值含义**：
```
1733882400 秒 = 1733882400000 毫秒
             = 2025-12-10 10:00:00 GMT+8
             = 2025-12-10 02:00:00 UTC
```

**格式对比**：
```
毫秒级（13位）: 1733882400000  → Java/JavaScript 常用
秒级（10位）:   1733882400     → Unix/Linux/PHP 常用，外部系统集成
```

**Java 代码示例**：
```java
// 获取当前时间戳（秒）
long timestampSeconds = Instant.now().getEpochSecond();
// 或者
long timestampSeconds = System.currentTimeMillis() / 1000;
// 输出: 1733882400

// OffsetDateTime 转秒级时间戳
long seconds = offsetDateTime.toEpochSecond();

// 秒级时间戳转 OffsetDateTime
OffsetDateTime dateTime = Instant.ofEpochSecond(timestampSeconds)
    .atZone(ZoneId.systemDefault())
    .toOffsetDateTime();

// 秒级与毫秒级互转
long millis = timestampSeconds * 1000;  // 秒 → 毫秒
long seconds = timestampMillis / 1000;  // 毫秒 → 秒
```

**JavaScript 代码示例**：
```javascript
// 获取当前秒级时间戳
const timestampSeconds = Math.floor(Date.now() / 1000);
// 输出: 1733882400

// Date 对象转秒级时间戳
const seconds = Math.floor(new Date().getTime() / 1000);

// 秒级时间戳转 Date 对象
const date = new Date(timestampSeconds * 1000);

// DateTimeUtils 兼容秒级时间戳
const date = DateTimeUtils.from(1733882400);  // 自动识别10位秒级
```

**使用场景**：
- ✅ **外部系统集成**（Unix/Linux 系统、PHP/Python 应用）
- ✅ **URL 参数传递**（短小简洁）
- ✅ **数据库索引字段**（整数索引性能高）
- ✅ **缓存过期时间**（Redis TTL、Cookie expires）
- ✅ **API 签名时间戳**（防重放攻击）

**优点**：
- ✅ **兼容性极好**：Unix/Linux 标准，跨语言通用
- ✅ **URL 友好**：仅10位数字，适合 GET 参数
- ✅ **存储空间小**：8字节 Long 类型
- ✅ **索引性能高**：整数类型比字符串快
- ✅ **绝对时间**：不受时区配置影响

**缺点**：
- ❌ **不易读**：人类无法直接识别
- ❌ **精度较低**：秒级精度，不适合高精度场景
- ❌ 需要转换为毫秒才能用于 Java/JavaScript

**何时使用**：
- 🎯 **外部系统对接**：与 Unix/Linux、PHP、Python 系统交互
- 🎯 **URL 参数**：时间参数需要通过 URL 传递
- 🎯 **数据库索引**：需要对时间字段建立索引
- 🎯 **缓存系统**：Redis 过期时间、Cookie expires
- 🎯 **API 签名**：防重放攻击的时间戳参数

**秒级 vs 毫秒级对比**：
| 对比维度 | 秒级（10位） | 毫秒级（13位） | 推荐场景 |
|---------|-------------|---------------|----------|
| **兼容性** | Unix/Linux 标准 | Java/JS 标准 | 外部集成→秒级 |
| **精度** | 1秒 | 1毫秒 | 高精度→毫秒级 |
| **长度** | 10位数字 | 13位数字 | URL传递→秒级 |
| **使用** | `epochSecond` | `epochMilli` | 内部应用→毫秒级 |

**⚠️ 注意事项**：
- Java/JavaScript 默认使用毫秒级，需要转换
- URL 参数传递推荐秒级（更短）
- 内部应用推荐毫秒级（更精确）
- 数据库存储可以用秒级（节省空间）

```java
// ✅ 正确：外部 API 对接（秒级）
@GetMapping("/external/orders")
public Result<List<OrderVO>> getOrders(
    @RequestParam Long startTime,  // 秒级时间戳（10位）
    @RequestParam Long endTime
) {
    // 转换为 OffsetDateTime
    OffsetDateTime start = Instant.ofEpochSecond(startTime)
        .atZone(ZoneId.systemDefault()).toOffsetDateTime();
    OffsetDateTime end = Instant.ofEpochSecond(endTime)
        .atZone(ZoneId.systemDefault()).toOffsetDateTime();

    return Result.success(orderService.findByTimeRange(start, end));
}

// ✅ 正确：内部消息队列（毫秒级）
@Data
public class OrderEvent {
    private Long orderId;
    private Long createTime;  // 毫秒级时间戳（13位）
}

// ✅ 正确：Redis 缓存过期时间（秒级）
long expireSeconds = 3600;  // 1小时
redisTemplate.expire(key, expireSeconds, TimeUnit.SECONDS);
```

---

### 🎯 格式选择决策树

```
需要传递时间数据？
├─ 需要保留完整时区信息？
│  ├─ YES：性能是否关键？
│  │  ├─ 高性能要求（>1000条/秒）→ ③ Unix 时间戳（毫秒）
│  │  └─ 普通性能要求 → ① ISO-8601 格式（推荐）
│  └─ NO：用途？
│     ├─ 前端展示 → ② 本地简化格式（需文档说明）
│     ├─ 全球统一基准 → ④ UTC 格式
│     └─ 外部系统集成/URL参数 → ⑤ 时间戳（秒）
│
└─ 各场景推荐格式：
   ├─ 前端 ↔ 后端 API（默认） → ① ISO-8601
   ├─ 前端 ↔ 后端 API（简化） → ② 本地格式 + @LocalTimeFormat
   ├─ 微服务 RPC 调用 → ① ISO-8601
   ├─ Kafka/RocketMQ 消息 → ③ 时间戳（毫秒）
   ├─ Redis/JSONB 存储 → ③ 时间戳（毫秒）
   ├─ 数据库物理存储 → ④ UTC（TIMESTAMPTZ 自动转换）
   ├─ 前端用户展示 → ② 本地格式
   ├─ 审计日志 → ① ISO-8601
   ├─ 外部系统对接 → ⑤ 时间戳（秒）
   └─ URL 参数传递 → ⑤ 时间戳（秒）
```

---

### 📊 5种格式对比总结

| 对比维度 | ① ISO-8601 | ② 本地格式 | ③ 时间戳（毫秒） | ④ UTC | ⑤ 时间戳（秒） |
|---------|-----------|----------|---------------|-------|---------------|
| **可读性** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐ | ⭐⭐⭐⭐ | ⭐ |
| **时区信息** | ✅ 完整 | ❌ 无 | ✅ 绝对 | ✅ UTC | ✅ 绝对 |
| **序列化性能** | 中等 | 中等 | **极高** | 中等 | **极高** |
| **存储空间** | 30字节 | 19字节 | **8字节** | 20字节 | **8字节** |
| **跨时区兼容** | ⭐⭐⭐⭐⭐ | ⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **调试便利性** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐ | ⭐⭐⭐⭐ | ⭐ |
| **API 交互** | ✅ 推荐 | ⚠️ 特殊 | ❌ 不推荐 | ✅ 可用 | ⚠️ 外部API |
| **消息队列** | ⚠️ 普通 | ❌ 禁止 | ✅ 推荐 | ⚠️ 可用 | ⚠️ 可用 |
| **前端展示** | ⚠️ 需转换 | ✅ 推荐 | ❌ 需转换 | ❌ 需转换 | ❌ 需转换 |
| **外部系统** | ✅ 可用 | ❌ 不推荐 | ⚠️ 需说明 | ✅ 可用 | ✅ 推荐 |
| **URL 参数** | ⚠️ 太长 | ❌ 无时区 | ⚠️ 太长 | ⚠️ 太长 | ✅ 推荐 |
| **推荐指数** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |

**推荐使用优先级**：
1. **默认首选**：① ISO-8601（API 交互、RPC 调用、日志）
2. **性能优先**：③ 时间戳（毫秒）（消息队列、JSONB、高频日志）
3. **展示优先**：② 本地格式（前端 UI，需文档说明）
4. **外部集成**：⑤ 时间戳（秒）（Unix/Linux、PHP、URL 参数）
5. **特定场景**：④ UTC（数据库存储）

---

## 3. PostgreSQL 数据库规范

### 连接配置

#### 方式一：用户级别配置（推荐）

> 💡 **必选方式**：在数据库服务器端为用户配置默认时区，一次配置永久生效

**配置步骤：**

```sql
-- 为指定用户设置默认时区 如：美国部署应用不同的账号和对应的时区
ALTER USER your_username SET timezone TO 'Asia/Shanghai';

-- 验证配置
SHOW timezone;  -- 重连后显示 GMT+8
```

**优势：**
- ✅ 一次配置，永久生效，无需每次连接都加参数
- ✅ 所有客户端工具（pgAdmin、DataGrip、应用程序）自动生效
- ✅ 查询结果显示为 GMT+8 格式，清晰直观，不会歧义
- ✅ 适合团队统一配置，减少配置错误

**使用示例（pgAdmin 4）：**

```sql
-- 1. 执行配置
ALTER USER postgres SET timezone TO 'Asia/Shanghai';

-- 2. 断开连接并重新连接

-- 3. 查询验证
SELECT now();
-- 结果显示：2025-12-11 10:00:00+08
-- 格式为 GMT+8，清晰明确，无歧义

SELECT create_time FROM sys_log LIMIT 1;
-- 结果显示：2025-12-10 10:00:00+08
-- 所有 TIMESTAMPTZ 字段都自动显示为 GMT+8
```

#### 方式二：连接字符串参数

> **必选方式**：需要在每次连接时添加 `timezone` 参数， 与方案一冗余 确保时区一致。

**各类工具连接示例：**

```bash
# JDBC 连接（Java应用）
jdbc:postgresql://localhost:5432/mydb?timezone=Asia/Shanghai

# psql 命令行工具
PGTZ=Asia/Shanghai psql -h localhost -U postgres -d mydb

# DataGrip / DBeaver 等 GUI 工具
URL: jdbc:postgresql://localhost:5432/mydb
高级选项 → 添加参数: timezone=Asia/Shanghai

# Python psycopg2
conn = psycopg2.connect(
    host="localhost",
    database="mydb",
    user="postgres",
    password="password",
    options="-c timezone=Asia/Shanghai"
)
```


**为什么需要时区配置？**
- 确保客户端与数据库之间时区一致
- 避免时间数据在传输过程中出现时区偏移
- 保证 `OffsetDateTime` 能正确获取带时区的时间数据
- 不要改数据库的默认时区，保持 UTC 标准


### 建表规范

> ❗ **重要**: 必须使用 `TIMESTAMPTZ` 而非 `TIMESTAMP`

```sql
CREATE TABLE sys_log (
    id BIGSERIAL PRIMARY KEY,
    create_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sys_log_create_time ON sys_log(create_time);
```

### 查询示例

#### 时区处理说明

> 💡 **重要**：在已设置时区（`ALTER USER SET timezone`）的情况下，带时区和不带时区的时间字符串**等价**

**前提条件：** 已执行 `ALTER USER your_username SET timezone TO 'Asia/Shanghai';` 并重新连接

```sql
-- ✅ 推荐：不指定时区，依赖 session 配置（前提：已设置时区）
WHERE create_time >= '2025-12-10 00:00:00'

-- ❌ 不允许：显式指定时区，时区不可以硬编码到程序中
WHERE create_time >= '2025-12-10 00:00:00+08:00'

-- ❌ 不允许：使用 Java 拼接时间字符串（应该使用 OffsetDateTime 参数）
WHERE create_time >= '" + timeString + "'  -- 反模式
```

#### 其他查询示例

```sql
-- ✅ 按天分组统计
SELECT date_trunc('day', create_time) as stat_date, count(*)
FROM sys_log
GROUP BY 1
ORDER BY 1;

-- ❌ 错误：类型转换导致索引失效
SELECT * FROM sys_log WHERE create_time::DATE = '2025-12-10';
```

---

## 4. Java 后端开发规范

### 4.1 系统时区配置

> ⚠️ **重要**：使用 `OffsetDateTime.now()` 依赖系统默认时区，必须正确配置环境时区

**JVM 参数（推荐）**
```bash
java -Duser.timezone=Asia/Shanghai -jar your-application.jar
```

**环境变量**
```bash
# Linux/Mac
export TZ=Asia/Shanghai

# Docker
docker run -e TZ=Asia/Shanghai your-image
```

**验证时区**
```java
@PostConstruct
public void checkTimezone() {
    String timezone = TimeZone.getDefault().getID();
    log.info("当前系统时区: {}", timezone);
    if (!"Asia/Shanghai".equals(timezone)) {
        log.warn("警告：当前时区不是 Asia/Shanghai，实际为: {}", timezone);
    }
}
```

### 4.2 数据库连接配置

> ⚠️ **关键配置**：JDBC URL 必须包含 `timezone` 参数，与系统时区保持一致

**Spring Boot 应用配置**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb?timezone=Asia/Shanghai
    username: postgres
    password: password
    driver-class-name: org.postgresql.Driver
```

**多数据源配置示例**
```yaml
ldx2t:
  commons:
    datasource:
      default:
        url: jdbc:postgresql://localhost:5432/db1?timezone=Asia/Shanghai
        username: user1
        password: pass1
      secondary:
        url: jdbc:postgresql://localhost:5432/db2?timezone=Asia/Shanghai
        username: user2
        password: pass2
```

**MyBatis Plus / Druid 配置**
```yaml
spring:
  datasource:
    druid:
      url: jdbc:postgresql://localhost:5432/mydb?timezone=Asia/Shanghai
      username: postgres
      password: password
      initial-size: 5
      max-active: 20
```

> 💡 **全球化部署**：美国部署改为 `timezone=America/New_York`，同时修改 JVM 参数，无需修改代码。
>
> ⚠️ **注意事项**：
> - timezone 参数值必须与 JVM 的 `-Duser.timezone` 或环境变量 `TZ` 保持一致
> - 开发工具（DataGrip、DBeaver）连接数据库时也要配置 timezone 参数
> - 如果不配置 timezone，可能导致时间数据读取时丢失时区信息

### 4.3 实体类定义

**Entity（数据库实体）**
```java
@Getter @Setter @ToString
public class UserEntity {
    @TableField("create_time")
    private OffsetDateTime createTime;  // 不加注解

    @TableField("update_time")
    private OffsetDateTime updateTime;
}
```

**前端VO（无需注解）**
```java
// VO 类本身无需注解
@Data
public class UserVO {
    private OffsetDateTime createTime;  // 无需注解
    private OffsetDateTime birthday;    // 无需注解
}

// ✅ 注解加在 Controller 方法或类上（见 3.5 节）
```

**内部DTO（不加注解）**
```java
@Data
public class UserInternalDTO {
    // ⚠️ 内部API：不加注解，保留时区信息
    private OffsetDateTime createTime;  // 输出 "2025-12-10T10:00:00+08:00"
}
```

### 4.4 业务逻辑处理

#### 获取当前时间

```java
@Service
public class UserService {
    // ✅ 推荐：使用系统默认时区
    // 优势：多地区部署只需修改环境变量，无需修改代码
    public void createUser() {
        OffsetDateTime now = OffsetDateTime.now();
        user.setCreateTime(now);
    }

    // ⚠️ 不推荐：硬编码时区
    public void badExample() {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Shanghai"));
    }
}
```

#### 方法参数传递

> ⚠️ **重要原则**：**禁止使用 String 类型传递时间参数，必须使用 `OffsetDateTime` 类型**

**错误示例（禁止）**
```java
// ❌ 错误：使用 String 传递时间，需要手动解析转换
@Service
public class OrderService {

    // ❌ 反模式：String 参数
    public List<Order> getOrdersByTimeRange(String startTime, String endTime) {
        // 手动解析字符串，容易出错
        OffsetDateTime start = OffsetDateTime.parse(startTime + "+08:00");
        OffsetDateTime end = OffsetDateTime.parse(endTime + "+08:00");

        return orderRepository.findByCreateTimeBetween(start, end);
    }

    // ❌ 反模式：在业务方法中传递 String
    public void processOrder(String orderTime) {
        // 需要手动转换，增加复杂度
        OffsetDateTime time = parseOrderTime(orderTime);
        // ...
    }
}
```

**正确示例（推荐）**
```java
// ✅ 正确：直接使用 OffsetDateTime 类型
@Service
public class OrderService {

    // ✅ 推荐：使用 OffsetDateTime 参数
    public List<Order> getOrdersByTimeRange(
        OffsetDateTime startTime,
        OffsetDateTime endTime
    ) {
        // 直接使用，无需转换
        return orderRepository.findByCreateTimeBetween(startTime, endTime);
    }

    // ✅ 推荐：业务方法直接使用 OffsetDateTime
    public void processOrder(OffsetDateTime orderTime) {
        // 类型安全，无需转换
        if (orderTime.isBefore(OffsetDateTime.now().minusHours(24))) {
            throw new BusinessException("订单已超时");
        }
        // ...
    }

    // ✅ 推荐：时间计算清晰直观
    public boolean isOrderExpired(OffsetDateTime orderTime) {
        return orderTime.plusHours(24).isBefore(OffsetDateTime.now());
    }
}
```

**为什么禁止使用 String？**
1. **类型安全**：编译期类型检查，避免运行时错误
2. **避免手动解析**：减少字符串解析代码，降低出错概率
3. **时区明确**：`OffsetDateTime` 自带时区信息，避免歧义
4. **API统一**：Spring MVC 自动将请求参数转换为 `OffsetDateTime`
5. **代码简洁**：无需重复编写转换逻辑

#### 时间计算

```java
// ✅ 计算订单超时
OffsetDateTime timeout = OffsetDateTime.now().plusHours(24);

// ✅ 查询今天创建的订单
OffsetDateTime today = LocalDate.now()
    .atStartOfDay()
    .atZone(ZoneId.systemDefault())
    .toOffsetDateTime();

// ✅ 判断时间是否在范围内
boolean inRange = orderTime.isAfter(startTime) && orderTime.isBefore(endTime);
```

### 4.5 Controller 交互

| API类型 | 注解位置 | 输出格式 | 说明 |
|---------|---------|----------|------|
| **前端API（默认）** | 无注解 | `2025-12-10T10:00:00+08:00` | **推荐**，时区明确 |
| **前端API（简化）** | Controller 方法/类 | `yyyy-MM-dd HH:mm:ss` | 需在设计文档和接口文档说明 |
| **内部API** | 无注解 | `2025-12-10T10:00:00+08:00` | 保留时区 |

#### 推荐示例（默认方式）

> ✅ **默认推荐**：前端API不加注解，输出 GMT+8 ISO-8601 格式

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    // ✅ 推荐：不加注解，输出 GMT+8 ISO-8601 格式
    @GetMapping("/list")
    public Result<List<UserVO>> list(
        @RequestParam(required = false) OffsetDateTime startTime,
        @RequestParam(required = false) OffsetDateTime endTime
    ) {
        // Spring MVC 自动转换入参，支持多种格式：
        // - 2025-12-10 10:00:00
        // - 2025-12-10T10:00:00+08:00
        // - 1733882400000 (时间戳)

        // UserVO 中的 OffsetDateTime 输出 "2025-12-10T10:00:00+08:00"
        // 时区明确，前端可以正确解析
        return Result.success(userService.list(startTime, endTime));
    }

    @GetMapping("/detail/{id}")
    public Result<UserVO> detail(@PathVariable Long id) {
        // OffsetDateTime 输出 "2025-12-10T10:00:00+08:00"
        return Result.success(userService.getById(id));
    }
}
```

**JSON 输出示例**：

```json
{
  "code": 0,
  "data": {
    "id": 1,
    "name": "张三",
    "createTime": "2025-12-10T10:00:00+08:00",
    "birthday": "1990-01-15T00:00:00+08:00"
  }
}
```

#### 特殊场景（简化格式 - 需文档说明）

> ⚠️ **特殊需求**：如果确实需要简化格式，必须加 `@LocalTimeFormat` 注解，并在设计文档和接口文档中说明

**方式1：方法级别注解**

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /**
     * 查询订单列表
     *
     * ⚠️ 时间格式说明：
     * 本接口返回的时间字段使用简化格式 "yyyy-MM-dd HH:mm:ss"
     * 时区固定为 GMT+8（北京时间）
     * 前端解析时需使用 GMT+8 时区，不可使用浏览器本地时区
     */
    @ApiOperation(value = "订单列表", notes = "时间格式: yyyy-MM-dd HH:mm:ss (GMT+8)")
    @LocalTimeFormat  // ⚠️ 必须在接口文档中说明时间格式
    @GetMapping("/list")
    public Result<List<OrderVO>> list(
        @RequestParam(required = false) OffsetDateTime startTime,
        @RequestParam(required = false) OffsetDateTime endTime
    ) {
        // OrderVO 中的 OffsetDateTime 自动格式化为 "yyyy-MM-dd HH:mm:ss"
        return Result.success(orderService.list(startTime, endTime));
    }
}
```

**方式2：类级别注解**

```java
/**
 * 报表 Controller
 *
 * ⚠️ 时间格式说明：
 * 本类所有接口返回的时间字段使用简化格式 "yyyy-MM-dd HH:mm:ss"
 * 时区固定为 GMT+8（北京时间）
 * 前端解析时需使用 GMT+8 时区，不可使用浏览器本地时区
 */
@RestController
@RequestMapping("/api/reports")
@LocalTimeFormat  // ⚠️ 类级别注解，所有方法生效
@Api(tags = "报表接口", description = "所有时间字段格式: yyyy-MM-dd HH:mm:ss (GMT+8)")
public class ReportController {

    @GetMapping("/daily")
    public Result<DailyReportVO> getDailyReport() {
        // 自动格式化为 "yyyy-MM-dd HH:mm:ss"
    }

    @GetMapping("/monthly")
    public Result<MonthlyReportVO> getMonthlyReport() {
        // 自动格式化为 "yyyy-MM-dd HH:mm:ss"
    }
}
```

**VO 类定义（无需注解）**

```java
// VO 类本身无需注解
@Data
@ApiModel("用户信息")
public class UserVO {
    @ApiModelProperty("用户ID")
    private Long id;

    @ApiModelProperty(value = "创建时间", example = "2025-12-10T10:00:00+08:00", notes = "GMT+8时区")
    private OffsetDateTime createTime;  // 无需注解

    @ApiModelProperty(value = "生日", example = "1990-01-15T00:00:00+08:00", notes = "GMT+8时区")
    private OffsetDateTime birthday;    // 无需注解
}

@Data
@ApiModel("搜索请求")
public class SearchRequest {
    @ApiModelProperty("关键词")
    private String keyword;

    @ApiModelProperty(value = "开始时间", example = "2025-12-10T00:00:00+08:00")
    private OffsetDateTime startTime;  // 无需注解

    @ApiModelProperty(value = "结束时间", example = "2025-12-10T23:59:59+08:00")
    private OffsetDateTime endTime;    // 无需注解
}
```

#### 错误示例（禁止）

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    // ❌ 错误：使用 String 参数
    @GetMapping("/list")
    public Result<List<OrderVO>> badList(
        @RequestParam(required = false) String startTime,  // ❌ 禁止
        @RequestParam(required = false) String endTime     // ❌ 禁止
    ) {
        // 需要手动解析转换，增加出错风险
        OffsetDateTime start = parseTime(startTime);
        OffsetDateTime end = parseTime(endTime);

        return Result.success(orderService.list(start, end));
    }

    // ❌ 错误：直接返回 Entity（未加注解）
    @GetMapping("/{id}")
    public Result<UserEntity> badGetById(@PathVariable Long id) {
        return Result.success(userService.getById(id));  // 应转为 UserVO
    }
}
```

---

## 5. 前端开发规范

### 🎯 核心原则

1. **数据交互使用 GMT+8 时区**
   - 后端返回的时间统一为 GMT+8 时区（默认 ISO-8601 格式或简化格式）
   - 前端提交时间必须转换为 GMT+8 时区
   - **禁止使用用户浏览器本地时区**

2. **展示层使用本地时间格式**
   - 前端展示时间格式：`yyyy-MM-dd HH:mm:ss`
   - 时区固定为 GMT+8（北京时间）
   - 不随用户浏览器时区变化

3. **兼容全格式时间输入**
   - 支持 ISO-8601 格式：`2025-12-10T10:00:00+08:00`
   - 支持简化格式：`2025-12-10 10:00:00`
   - 支持时间戳：`1733882400000`
   - 支持日期格式：`2025-12-10`

### ⚙️ 配置文件要求

前端项目必须在配置文件中声明时区和时间格式配置项，确保全项目统一的时间处理标准。

#### 中国区配置（强制要求）

```javascript
// config/app.config.js
export default {
  // 时区配置：中国区统一使用 Asia/Shanghai
  timezone: 'Asia/Shanghai',

  // 本地时间格式：前端展示时间格式
  localTimeFormat: 'yyyy-MM-dd HH:mm:ss',

  // GMT+8 时区偏移（毫秒）
  timezoneOffset: 8 * 60 * 60 * 1000,

  // ISO-8601 时区后缀
  timezoneISO: '+08:00'
};
```

**TypeScript 配置示例**：
```typescript
// config/app.config.ts
export interface AppConfig {
  /** 时区标识符（IANA timezone） */
  timezone: string;

  /** 本地时间格式（展示层） */
  localTimeFormat: string;

  /** 时区偏移（毫秒） */
  timezoneOffset: number;

  /** ISO-8601 时区后缀 */
  timezoneISO: string;
}

const config: AppConfig = {
  timezone: 'Asia/Shanghai',
  localTimeFormat: 'yyyy-MM-dd HH:mm:ss',
  timezoneOffset: 8 * 60 * 60 * 1000,
  timezoneISO: '+08:00'
};

export default config;
```

**环境变量配置（推荐）**：
```bash
# .env.production（中国区生产环境）
VITE_TIMEZONE=Asia/Shanghai
VITE_LOCAL_TIME_FORMAT=yyyy-MM-dd HH:mm:ss
VITE_TIMEZONE_OFFSET=28800000
VITE_TIMEZONE_ISO=+08:00
```

```javascript
// vite.config.js 或 webpack.config.js
export default {
  define: {
    'import.meta.env.VITE_TIMEZONE': JSON.stringify(process.env.VITE_TIMEZONE || 'Asia/Shanghai'),
    'import.meta.env.VITE_LOCAL_TIME_FORMAT': JSON.stringify(process.env.VITE_LOCAL_TIME_FORMAT || 'yyyy-MM-dd HH:mm:ss'),
    'import.meta.env.VITE_TIMEZONE_OFFSET': Number(process.env.VITE_TIMEZONE_OFFSET || 28800000),
    'import.meta.env.VITE_TIMEZONE_ISO': JSON.stringify(process.env.VITE_TIMEZONE_ISO || '+08:00')
  }
};
```

#### 外国区配置（示例）

**美国东部时区（EST/EDT）**：
```javascript
// config/app.config.js
export default {
  timezone: 'America/New_York',         // 美国东部时区
  localTimeFormat: 'MM/dd/yyyy HH:mm:ss', // 美式日期格式
  timezoneOffset: -5 * 60 * 60 * 1000,  // EST: UTC-5
  timezoneISO: '-05:00'
};
```

**欧洲伦敦时区（GMT/BST）**：
```javascript
// config/app.config.js
export default {
  timezone: 'Europe/London',            // 伦敦时区
  localTimeFormat: 'dd/MM/yyyy HH:mm:ss', // 欧式日期格式
  timezoneOffset: 0,                    // GMT: UTC+0
  timezoneISO: '+00:00'
};
```

**日本时区（JST）**：
```javascript
// config/app.config.js
export default {
  timezone: 'Asia/Tokyo',               // 日本时区
  localTimeFormat: 'yyyy年MM月dd日 HH:mm:ss', // 日式格式
  timezoneOffset: 9 * 60 * 60 * 1000,   // JST: UTC+9
  timezoneISO: '+09:00'
};
```

#### 配置验证检查

**启动时配置验证**：
```javascript
// utils/configValidator.js
import appConfig from '@/config/app.config';

/**
 * 验证应用配置是否完整
 * 应在应用启动时调用
 */
export function validateAppConfig() {
  const requiredKeys = ['timezone', 'localTimeFormat', 'timezoneOffset', 'timezoneISO'];
  const missingKeys = requiredKeys.filter(key => !appConfig[key]);

  if (missingKeys.length > 0) {
    throw new Error(
      `[配置错误] 缺少必需的时间配置项: ${missingKeys.join(', ')}\n` +
      `请在 config/app.config.js 中配置这些项。\n` +
      `中国区示例: timezone='Asia/Shanghai', localTimeFormat='yyyy-MM-dd HH:mm:ss'`
    );
  }

  // 验证时区格式
  if (!appConfig.timezone.includes('/')) {
    console.warn(`[配置警告] timezone 应使用 IANA 格式 (例如: Asia/Shanghai)，当前值: ${appConfig.timezone}`);
  }

  // 验证时区偏移一致性
  const expectedOffset = parseTimezoneISO(appConfig.timezoneISO);
  if (expectedOffset !== appConfig.timezoneOffset) {
    console.error(
      `[配置错误] timezoneOffset (${appConfig.timezoneOffset}) 与 timezoneISO (${appConfig.timezoneISO}) 不匹配`
    );
  }

  console.log(`[配置验证] 时间配置加载成功: ${appConfig.timezone} (${appConfig.timezoneISO})`);
}

function parseTimezoneISO(iso) {
  const match = iso.match(/^([+-])(\d{2}):(\d{2})$/);
  if (!match) return 0;
  const sign = match[1] === '+' ? 1 : -1;
  const hours = parseInt(match[2], 10);
  const minutes = parseInt(match[3], 10);
  return sign * (hours * 60 + minutes) * 60 * 1000;
}
```

**在 main.js/main.ts 中调用**：
```javascript
// main.js
import { createApp } from 'vue';
import App from './App.vue';
import { validateAppConfig } from './utils/configValidator';

// 验证配置
validateAppConfig();

const app = createApp(App);
app.mount('#app');
```

#### 配置使用示例

**在 DateTimeUtils 中使用配置**：
```javascript
// utils/DateTimeUtils.js
import appConfig from '@/config/app.config';

class DateTimeUtils {
  // 使用配置文件的时区偏移
  static GMT8_OFFSET = appConfig.timezoneOffset;
  static TIMEZONE_ISO = appConfig.timezoneISO;

  /**
   * 格式化时间为本地格式
   * @param {Date|string|number} dateInput
   * @returns {string} 格式化后的时间字符串
   */
  static format(dateInput) {
    const date = this.from(dateInput);
    if (!date) return '';

    // 使用配置文件的格式
    return this.formatByPattern(date, appConfig.localTimeFormat);
  }

  /**
   * 转换为 ISO-8601 格式（用于提交后端）
   * @param {Date|string|number} dateInput
   * @returns {string} ISO-8601 格式字符串
   */
  static toISO(dateInput) {
    const date = this.from(dateInput);
    if (!date) return '';

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');

    // 使用配置文件的时区后缀
    return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}${appConfig.timezoneISO}`;
  }

  // ... 其他方法
}

export default DateTimeUtils;
```

#### 配置清单

**前端项目启动前必须检查：**
- [ ] config/app.config.js 文件已创建
- [ ] `timezone` 配置已设置（中国区：`Asia/Shanghai`）
- [ ] `localTimeFormat` 配置已设置（中国区：`yyyy-MM-dd HH:mm:ss`）
- [ ] `timezoneOffset` 配置已设置（中国区：`28800000` 毫秒）
- [ ] `timezoneISO` 配置已设置（中国区：`+08:00`）
- [ ] main.js 中已添加配置验证调用 `validateAppConfig()`
- [ ] DateTimeUtils 已引用配置文件的值
- [ ] 环境变量配置（.env.production）已设置

### 📦 工具函数封装

#### DateTimeUtils.js

```javascript
/**
 * 前端时间处理工具类
 * 核心原则：统一使用配置文件中的时区，不使用用户浏览器时区
 *
 * @author LDX2T
 * @version 1.1.0
 */
import appConfig from '@/config/app.config';

class DateTimeUtils {
  /**
   * 时区偏移（毫秒）
   * 从配置文件读取，中国区默认 GMT+8
   */
  static TIMEZONE_OFFSET = appConfig.timezoneOffset;

  /**
   * ISO-8601 时区后缀
   * 从配置文件读取，中国区默认 +08:00
   */
  static TIMEZONE_ISO = appConfig.timezoneISO;

  /**
   * 解析各种格式的时间字符串/时间戳，统一转换为 Date 对象
   *
   * 支持的格式：
   * - ISO-8601: "2025-12-10T10:00:00+08:00"
   * - 简化格式: "2025-12-10 10:00:00"
   * - 日期格式: "2025-12-10"
   * - 时间戳: 1733882400000
   * - Date 对象: new Date()
   *
   * @param {string|number|Date|null|undefined} dateInput - 时间输入
   * @returns {Date|null} Date 对象，解析失败返回 null
   *
   * @example
   * DateTimeUtils.from("2025-12-10T10:00:00+08:00")  // Date object
   * DateTimeUtils.from("2025-12-10 10:00:00")         // Date object
   * DateTimeUtils.from(1733882400000)                 // Date object
   * DateTimeUtils.from("2025-12-10")                  // Date object (00:00:00)
   * DateTimeUtils.from("invalid")                     // null
   */
  static from(dateInput) {
    // null/undefined 处理
    if (dateInput == null || dateInput === '') {
      return null;
    }

    // 已经是 Date 对象
    if (dateInput instanceof Date) {
      return isNaN(dateInput.getTime()) ? null : dateInput;
    }

    // 数字类型：时间戳
    if (typeof dateInput === 'number') {
      // 支持秒级时间戳（10位）和毫秒级时间戳（13位）
      const timestamp = dateInput < 10000000000 ? dateInput * 1000 : dateInput;
      const date = new Date(timestamp);
      return isNaN(date.getTime()) ? null : date;
    }

    // 字符串类型：多种格式解析
    if (typeof dateInput === 'string') {
      const str = dateInput.trim();

      // 格式1：ISO-8601 完整格式（带时区）
      // "2025-12-10T10:00:00+08:00" 或 "2025-12-10T10:00:00Z"
      if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}([+-]\d{2}:\d{2}|Z)$/.test(str)) {
        const date = new Date(str);
        return isNaN(date.getTime()) ? null : date;
      }

      // 格式2：简化格式 "yyyy-MM-dd HH:mm:ss"
      // ⚠️ 重要：此格式视为配置文件中的时区，不是用户本地时区
      if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(str)) {
        // 替换空格为 T，添加配置文件的时区后缀
        const isoString = str.replace(' ', 'T') + this.TIMEZONE_ISO;
        const date = new Date(isoString);
        return isNaN(date.getTime()) ? null : date;
      }

      // 格式3：日期格式 "yyyy-MM-dd"（时间默认为 00:00:00）
      // ⚠️ 重要：此格式视为配置文件中的时区
      if (/^\d{4}-\d{2}-\d{2}$/.test(str)) {
        const isoString = str + 'T00:00:00' + this.TIMEZONE_ISO;
        const date = new Date(isoString);
        return isNaN(date.getTime()) ? null : date;
      }

      // 格式4：时间戳字符串 "1733882400000"
      if (/^\d+$/.test(str)) {
        return this.from(Number(str));
      }

      // 其他格式：尝试直接解析（可能不精确，应避免）
      console.warn('DateTimeUtils: 未知时间格式，尝试直接解析:', dateInput);
      const date = new Date(str);
      return isNaN(date.getTime()) ? null : date;
    }

    // 不支持的类型
    console.error('DateTimeUtils: 不支持的时间类型:', typeof dateInput, dateInput);
    return null;
  }

  /**
   * 格式化 Date 对象为本地时间格式
   * 从配置文件读取格式模板（中国区默认 yyyy-MM-dd HH:mm:ss）
   *
   * @param {Date|string|number} dateInput - 时间输入
   * @param {string} format - 格式模板（可选，默认使用配置文件中的格式）
   * @returns {string} 格式化后的时间字符串
   *
   * @example
   * DateTimeUtils.format(new Date())                    // "2025-12-10 10:00:00"
   * DateTimeUtils.format("2025-12-10T10:00:00+08:00")   // "2025-12-10 10:00:00"
   * DateTimeUtils.format(1733882400000)                 // "2025-12-10 10:00:00"
   * DateTimeUtils.format(new Date(), 'yyyy-MM-dd')      // "2025-12-10"
   */
  static format(dateInput, format = appConfig.localTimeFormat) {
    const date = this.from(dateInput);
    if (!date) return '';

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');

    return format
      .replace('yyyy', year)
      .replace('MM', month)
      .replace('dd', day)
      .replace('HH', hours)
      .replace('mm', minutes)
      .replace('ss', seconds);
  }

  /**
   * 转换为 ISO-8601 格式
   * 用于提交数据到后端，使用配置文件中的时区（中国区默认 +08:00）
   *
   * @param {Date|string|number} dateInput - 时间输入
   * @returns {string} ISO-8601 格式字符串 "2025-12-10T10:00:00+08:00"
   *
   * @example
   * DateTimeUtils.toISO(new Date())                  // "2025-12-10T10:00:00+08:00"
   * DateTimeUtils.toISO("2025-12-10 10:00:00")       // "2025-12-10T10:00:00+08:00"
   */
  static toISO(dateInput) {
    const date = this.from(dateInput);
    if (!date) return '';

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');

    return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}${this.TIMEZONE_ISO}`;
  }

  /**
   * 转换为时间戳（毫秒）
   *
   * @param {Date|string|number} dateInput - 时间输入
   * @returns {number|null} 时间戳（毫秒），解析失败返回 null
   *
   * @example
   * DateTimeUtils.toTimestamp("2025-12-10T10:00:00+08:00")  // 1733882400000
   */
  static toTimestamp(dateInput) {
    const date = this.from(dateInput);
    return date ? date.getTime() : null;
  }

  /**
   * 计算相对时间（多久之前/之后）
   *
   * @param {Date|string|number} dateInput - 时间输入
   * @returns {string} 相对时间描述
   *
   * @example
   * DateTimeUtils.fromNow(Date.now() - 3600000)  // "1小时前"
   * DateTimeUtils.fromNow(Date.now() + 3600000)  // "1小时后"
   */
  static fromNow(dateInput) {
    const date = this.from(dateInput);
    if (!date) return '';

    const diff = Date.now() - date.getTime();
    const absDiff = Math.abs(diff);
    const suffix = diff > 0 ? '前' : '后';

    const minutes = Math.floor(absDiff / 60000);
    if (minutes < 1) return '刚刚';
    if (minutes < 60) return `${minutes}分钟${suffix}`;

    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}小时${suffix}`;

    const days = Math.floor(hours / 24);
    if (days < 30) return `${days}天${suffix}`;

    const months = Math.floor(days / 30);
    if (months < 12) return `${months}个月${suffix}`;

    const years = Math.floor(months / 12);
    return `${years}年${suffix}`;
  }

  /**
   * 判断是否是今天
   *
   * @param {Date|string|number} dateInput - 时间输入
   * @returns {boolean} 是否是今天
   */
  static isToday(dateInput) {
    const date = this.from(dateInput);
    if (!date) return false;

    const today = new Date();
    return date.getFullYear() === today.getFullYear() &&
           date.getMonth() === today.getMonth() &&
           date.getDate() === today.getDate();
  }

  /**
   * 获取当前时间（GMT+8）
   *
   * @returns {Date} 当前时间
   */
  static now() {
    return new Date();
  }
}

// 导出（ES6 模块）
export default DateTimeUtils;

// 或者 CommonJS 导出
// module.exports = DateTimeUtils;

// 或者全局变量（浏览器环境）
// window.DateTimeUtils = DateTimeUtils;
```

### 💻 使用示例

#### React 组件示例

```jsx
import React, { useState, useEffect } from 'react';
import DateTimeUtils from '@/utils/DateTimeUtils';
import { getUserList } from '@/api/user';

/**
 * 用户列表组件
 * 展示用户创建时间，使用本地格式 yyyy-MM-dd HH:mm:ss
 */
function UserList() {
  const [users, setUsers] = useState([]);

  useEffect(() => {
    loadUsers();
  }, []);

  const loadUsers = async () => {
    // 后端返回的时间格式：
    // 默认: "2025-12-10T10:00:00+08:00" (ISO-8601 GMT+8)
    // 或简化: "2025-12-10 10:00:00" (需要 @LocalTimeFormat 注解)
    const response = await getUserList();
    setUsers(response.data);
  };

  return (
    <div>
      <h2>用户列表</h2>
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>姓名</th>
            <th>创建时间</th>
            <th>相对时间</th>
          </tr>
        </thead>
        <tbody>
          {users.map(user => (
            <tr key={user.id}>
              <td>{user.id}</td>
              <td>{user.name}</td>
              {/* 展示本地格式：yyyy-MM-dd HH:mm:ss */}
              <td>{DateTimeUtils.format(user.createTime)}</td>
              {/* 展示相对时间：1小时前 */}
              <td>{DateTimeUtils.fromNow(user.createTime)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default UserList;
```

#### Vue 组件示例

```vue
<template>
  <div class="user-list">
    <h2>用户列表</h2>
    <el-table :data="users" style="width: 100%">
      <el-table-column prop="id" label="ID" width="80"></el-table-column>
      <el-table-column prop="name" label="姓名" width="120"></el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">
          <!-- 展示本地格式：yyyy-MM-dd HH:mm:ss -->
          {{ formatTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="相对时间" width="120">
        <template #default="{ row }">
          {{ fromNow(row.createTime) }}
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import DateTimeUtils from '@/utils/DateTimeUtils';
import { getUserList } from '@/api/user';

const users = ref([]);

onMounted(() => {
  loadUsers();
});

const loadUsers = async () => {
  const response = await getUserList();
  users.value = response.data;
};

// 格式化时间
const formatTime = (time) => {
  return DateTimeUtils.format(time);
};

// 相对时间
const fromNow = (time) => {
  return DateTimeUtils.fromNow(time);
};
</script>
```

#### 表单提交示例

```javascript
import DateTimeUtils from '@/utils/DateTimeUtils';
import { createOrder } from '@/api/order';

/**
 * 创建订单表单提交
 */
async function handleSubmit(formData) {
  // 表单中的时间选择器返回的可能是：
  // - Date 对象
  // - 字符串 "2025-12-10 10:00:00"
  // - 时间戳

  // 方式1：转换为 ISO-8601 格式（推荐）
  const orderData = {
    productId: formData.productId,
    quantity: formData.quantity,
    // 转换为 ISO-8601 格式提交
    deliveryTime: DateTimeUtils.toISO(formData.deliveryTime),
    // 输出: "2025-12-15T14:00:00+08:00"
  };

  // 方式2：转换为时间戳（高性能场景）
  const orderData2 = {
    productId: formData.productId,
    quantity: formData.quantity,
    // 转换为时间戳提交
    deliveryTime: DateTimeUtils.toTimestamp(formData.deliveryTime),
    // 输出: 1734238800000
  };

  // 提交到后端
  const response = await createOrder(orderData);
  console.log('订单创建成功:', response.data);
}

// 使用示例
handleSubmit({
  productId: 123,
  quantity: 2,
  deliveryTime: '2025-12-15 14:00:00'  // 表单选择器返回的字符串
});
```

#### 日期选择器集成（Ant Design）

```jsx
import React, { useState } from 'react';
import { DatePicker, Form, Button } from 'antd';
import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';
import DateTimeUtils from '@/utils/DateTimeUtils';
import appConfig from '@/config/app.config';

// ⚠️ 重要：配置 dayjs 使用配置文件中的时区
dayjs.extend(utc);
dayjs.extend(timezone);
dayjs.tz.setDefault(appConfig.timezone);  // 使用配置文件的时区（中国区：Asia/Shanghai）

function OrderForm() {
  const [form] = Form.useForm();

  const handleSubmit = async (values) => {
    // dayjs 对象转换为 ISO-8601 字符串
    const orderData = {
      ...values,
      deliveryTime: DateTimeUtils.toISO(values.deliveryTime),
      // 或者：values.deliveryTime.format(`YYYY-MM-DDTHH:mm:ss${appConfig.timezoneISO}`)
    };

    console.log('提交数据:', orderData);
    // 输出: { deliveryTime: "2025-12-15T14:00:00+08:00", ... }
  };

  return (
    <Form form={form} onFinish={handleSubmit}>
      <Form.Item
        label="配送时间"
        name="deliveryTime"
        rules={[{ required: true, message: '请选择配送时间' }]}
      >
        <DatePicker
          showTime
          format={appConfig.localTimeFormat.replace('yyyy', 'YYYY').replace('dd', 'DD').replace('mm', 'mm').replace('ss', 'ss')}
          placeholder="选择配送时间"
          // ⚠️ 重要：禁止使用用户浏览器时区
          style={{ width: '100%' }}
        />
      </Form.Item>

      <Form.Item>
        <Button type="primary" htmlType="submit">
          提交订单
        </Button>
      </Form.Item>
    </Form>
  );
}

export default OrderForm;
```

#### 日期选择器集成（Element Plus）

```vue
<template>
  <el-form :model="formData" :rules="rules" ref="formRef">
    <el-form-item label="配送时间" prop="deliveryTime">
      <el-date-picker
        v-model="formData.deliveryTime"
        type="datetime"
        placeholder="选择配送时间"
        :format="displayFormat"
        :value-format="isoFormat"
        style="width: 100%"
      />
    </el-form-item>

    <el-form-item>
      <el-button type="primary" @click="handleSubmit">提交订单</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { ref, reactive, computed } from 'vue';
import { ElMessage } from 'element-plus';
import DateTimeUtils from '@/utils/DateTimeUtils';
import appConfig from '@/config/app.config';
import { createOrder } from '@/api/order';

const formRef = ref(null);
const formData = reactive({
  deliveryTime: null,
  // 其他字段...
});

// 从配置文件读取格式
const displayFormat = computed(() => {
  // 将配置文件的格式转换为 Element Plus 支持的格式
  return appConfig.localTimeFormat
    .replace('yyyy', 'YYYY')
    .replace('dd', 'DD');
});

const isoFormat = computed(() => {
  // ISO-8601 格式，包含时区信息
  return `YYYY-MM-DDTHH:mm:ss${appConfig.timezoneISO}`;
});

const rules = {
  deliveryTime: [
    { required: true, message: '请选择配送时间', trigger: 'change' }
  ],
};

const handleSubmit = async () => {
  const valid = await formRef.value.validate();
  if (!valid) return;

  // Element Plus 的 value-format 已经返回 ISO-8601 格式
  // 如果没有配置 value-format，需要手动转换
  const orderData = {
    deliveryTime: typeof formData.deliveryTime === 'string'
      ? formData.deliveryTime
      : DateTimeUtils.toISO(formData.deliveryTime),
  };

  try {
    await createOrder(orderData);
    ElMessage.success('订单创建成功');
  } catch (error) {
    ElMessage.error('订单创建失败: ' + error.message);
  }
};
</script>
```

### ⚠️ 常见错误示例

#### 错误1：使用用户浏览器时区

```javascript
// ❌ 错误：使用 new Date() 直接解析，可能使用用户浏览器时区
const badParse = (timeString) => {
  // 问题：不同用户浏览器时区不同，解析结果不一致
  const date = new Date(timeString);  // 危险！
  return date.toLocaleString();
};

// ✅ 正确：使用 DateTimeUtils.from() 统一解析为配置文件中的时区
const goodParse = (timeString) => {
  // 固定使用配置文件的时区（中国区：GMT+8），全球用户看到的时间一致
  const date = DateTimeUtils.from(timeString);
  return DateTimeUtils.format(date);
};
```

#### 错误2：时区配置错误

```javascript
// ❌ 错误：dayjs 使用用户本地时区
import dayjs from 'dayjs';

const badFormat = (time) => {
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss');
  // 问题：在美国的用户会显示美国时间，而非配置文件中的时区
};

// ✅ 正确：dayjs 配置使用配置文件中的时区
import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';
import appConfig from '@/config/app.config';

dayjs.extend(utc);
dayjs.extend(timezone);
dayjs.tz.setDefault(appConfig.timezone);  // 使用配置文件的时区（中国区：Asia/Shanghai）

const goodFormat = (time) => {
  return dayjs(time).tz(appConfig.timezone).format('YYYY-MM-DD HH:mm:ss');
  // 所有用户都显示配置文件中的时区时间（中国区：GMT+8）
};
```

#### 错误3：提交时间格式不规范

```javascript
// ❌ 错误：提交简化格式，时区不明确
const badSubmit = async (formData) => {
  const data = {
    deliveryTime: formData.deliveryTime.format('YYYY-MM-DD HH:mm:ss'),
    // 输出: "2025-12-15 14:00:00" - 没有时区信息！
  };
  await createOrder(data);
};

// ✅ 正确：提交 ISO-8601 格式，时区明确
const goodSubmit = async (formData) => {
  const data = {
    deliveryTime: DateTimeUtils.toISO(formData.deliveryTime),
    // 输出: "2025-12-15T14:00:00+08:00" - 时区明确（中国区）
  };
  await createOrder(data);
};
```

### 📝 前端开发检查清单

- [ ] **配置文件（强制要求）**
  - [ ] config/app.config.js 文件已创建
  - [ ] `timezone` 配置已设置（中国区：`Asia/Shanghai`）
  - [ ] `localTimeFormat` 配置已设置（中国区：`yyyy-MM-dd HH:mm:ss`）
  - [ ] `timezoneOffset` 配置已设置（中国区：`28800000` 毫秒）
  - [ ] `timezoneISO` 配置已设置（中国区：`+08:00`）
  - [ ] main.js 中已添加 `validateAppConfig()` 配置验证
  - [ ] 环境变量配置（.env.production）已设置
  - [ ] DateTimeUtils 已引用配置文件的值

- [ ] **时区配置**
  - [ ] 时间工具库（dayjs/moment/date-fns）配置使用 GMT+8 时区
  - [ ] 日期选择器组件配置使用 GMT+8 时区
  - [ ] **禁止使用用户浏览器本地时区**

- [ ] **数据展示**
  - [ ] 使用 `DateTimeUtils.format()` 格式化显示时间
  - [ ] 展示格式固定为 `yyyy-MM-dd HH:mm:ss`
  - [ ] 相对时间使用 `DateTimeUtils.fromNow()`

- [ ] **数据提交**
  - [ ] 使用 `DateTimeUtils.toISO()` 转换为 ISO-8601 格式
  - [ ] 确保提交的时间包含 `+08:00` 时区信息
  - [ ] 或者使用时间戳 `DateTimeUtils.toTimestamp()`

- [ ] **数据解析**
  - [ ] 使用 `DateTimeUtils.from()` 解析后端返回的时间
  - [ ] 兼容 ISO-8601、简化格式、时间戳等多种格式
  - [ ] 确保解析时使用 GMT+8 时区，不使用用户浏览器时区

---

## 6. 微服务交互规范

### 通讯原则

| 通讯类型 | 注解使用 | 时间格式 | 原因 |
|----------|----------|----------|------|
| **前端 ↔ 后端（推荐）** | **无注解** | `2025-12-10T10:00:00+08:00` | GMT+8 时区明确，国际标准 |
| **前端 ↔ 后端（特殊）** | `@LocalTimeFormat` | `yyyy-MM-dd HH:mm:ss` | 用户友好，需在设计文档中说明 |
| **微服务 ↔ 微服务** | **无注解** | `2025-12-10T10:00:00+08:00` | 保留时区 |

### Feign 接口

```java
@FeignClient(name = "order-service")
public interface OrderServiceClient {
    @GetMapping("/internal/orders")
    List<OrderInternalDTO> getOrders(
        @RequestParam("startTime") OffsetDateTime startTime,
        @RequestParam("endTime") OffsetDateTime endTime
    );
}

// ✅ 内部DTO：不加注解
@Data
public class OrderInternalDTO {
    private Long id;
    private OffsetDateTime createTime;  // 输出 ISO-8601 格式
    private BigDecimal amount;
}

// ❌ 错误：内部DTO加注解
@Data
public class OrderBadDTO {
    @LocalTimeFormat  // 错误：丢失时区信息
    private OffsetDateTime createTime;
}
```

### Dubbo RPC

```java
@Data
public class UserInternalDTO implements Serializable {
    private Long id;
    private OffsetDateTime createTime;  // ✅ 不加注解，保留时区
}
```

### 消息队列 / JSONB 字段 / 日志

> 🎯 **核心原则**：根据性能要求选择时间格式
> - **高性能场景**：使用时间戳 (long 类型)
> - **普通场景**：使用 ISO-8601 格式

#### 场景对比表

| 场景类型 | 推荐格式 | 数据类型 | 示例 | 适用场景 |
|---------|---------|---------|------|---------|
| **高性能** | 时间戳 | `Long` | `1733882400000` | Kafka/RocketMQ 消息、Redis JSONB、高频日志 |
| **普通** | ISO-8601 | `String` | `"2025-12-10T10:00:00+08:00"` | 审计日志、配置文件、低频消息 |

#### 方案一：高性能场景（推荐）

**适用场景：**
- Kafka/RocketMQ 等高吞吐消息队列
- Redis、PostgreSQL 的 JSONB 字段
- ELK 等高频日志收集系统
- 时序数据库写入

**理由：**
- ✅ **绝对物理时间**：不受任何时区配置影响，全球统一
- ✅ **序列化开销最小**：long 类型直接序列化为数字，无字符串解析
- ✅ **存储空间小**：8字节固定大小
- ✅ **计算性能高**：时间差计算直接减法，无需解析

**代码示例：**

```java
// ✅ 高性能方案：使用时间戳
@Data
@AllArgsConstructor
public class OrderCreatedEvent {
    private Long orderId;
    private Long userId;
    private BigDecimal amount;

    // 使用 long 类型时间戳
    private Long createTime;     // System.currentTimeMillis()
    private Long updateTime;
}

// 生产者
@Component
public class OrderEventProducer {
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    public void sendOrderCreated(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(
            order.getId(),
            order.getUserId(),
            order.getAmount(),
            System.currentTimeMillis(),  // 当前时间戳
            System.currentTimeMillis()
        );
        rocketMQTemplate.convertAndSend("order-created-topic", event);
    }
}

// 消费者：转换为 OffsetDateTime
@RocketMQMessageListener(topic = "order-created-topic", consumerGroup = "order-group")
@Component
public class OrderCreatedConsumer implements RocketMQListener<OrderCreatedEvent> {

    @Override
    public void onMessage(OrderCreatedEvent event) {
        // 转换为 OffsetDateTime（使用系统默认时区）
        OffsetDateTime createTime = Instant.ofEpochMilli(event.getCreateTime())
            .atZone(ZoneId.systemDefault())
            .toOffsetDateTime();

        log.info("订单创建时间: {}", createTime);  // 输出: 2025-12-10T10:00:00+08:00
        processOrder(event, createTime);
    }
}

// PostgreSQL JSONB 字段存储
@Data
public class UserProfile {
    private Long userId;

    @TableField(typeHandler = JsonTypeHandler.class)
    private UserSettings settings;  // JSONB 字段
}

@Data
public class UserSettings {
    private String theme;
    private Long lastLoginTime;  // ✅ 使用时间戳
    private Long accountCreatedAt;
}

// Redis 缓存存储
@Component
public class UserCacheService {
    @Autowired
    private StringRedisTemplate redisTemplate;

    public void cacheUserActivity(Long userId) {
        String key = "user:activity:" + userId;
        String value = JSON.toJSONString(Map.of(
            "userId", userId,
            "lastActiveTime", System.currentTimeMillis()  // ✅ 时间戳
        ));
        redisTemplate.opsForValue().set(key, value, Duration.ofHours(24));
    }
}
```

#### 方案二：普通场景

**适用场景：**
- 审计日志、操作日志（低频）
- 配置文件时间字段
- 低频异步任务消息
- 调试和排错需要可读性的场景

**理由：**
- ✅ **可读性好**：人类直接可读，调试方便
- ✅ **显式时区**：时区信息明确，避免歧义
- ✅ **标准格式**：符合 ISO-8601 国际标准

**代码示例：**

```java
// 普通方案：使用 ISO-8601 字符串
@Data
public class AuditLogEvent {
    private Long id;
    private String userId;
    private String action;

    // 使用 ISO-8601 字符串（调试友好）
    private String occurredAt;  // "2025-12-10T10:00:00+08:00"
}

// 生产者
public void sendAuditLog(String userId, String action) {
    AuditLogEvent event = new AuditLogEvent();
    event.setUserId(userId);
    event.setAction(action);
    event.setOccurredAt(OffsetDateTime.now().toString());  // ISO-8601 格式

    kafkaTemplate.send("audit-log-topic", event);
}

// 消费者：解析 ISO-8601 字符串
public void onAuditLog(AuditLogEvent event) {
    // 解析为 OffsetDateTime
    OffsetDateTime occurredAt = OffsetDateTime.parse(event.getOccurredAt());

    log.info("审计日志: {} 在 {} 执行了 {}",
        event.getUserId(),
        occurredAt,  // 带时区的时间
        event.getAction()
    );
}

// 配置文件场景
@Data
@ConfigurationProperties(prefix = "app.schedule")
public class ScheduleConfig {
    // 使用 ISO-8601 字符串便于配置
    private String startTime;  // "2025-12-10T09:00:00+08:00"
    private String endTime;    // "2025-12-10T18:00:00+08:00"

    public OffsetDateTime getStartTimeAsDateTime() {
        return OffsetDateTime.parse(startTime);
    }
}
```

#### 性能对比

| 维度 | 时间戳 (long) | ISO-8601 (String) | 差异 |
|------|--------------|-------------------|------|
| **序列化大小** | 8 字节 | 30+ 字节 | **~4倍** |
| **序列化速度** | 极快（数字） | 较慢（字符串拼接） | **~10倍** |
| **反序列化速度** | 极快（数字） | 慢（字符串解析） | **~10倍** |
| **可读性** | 差（需转换） | 好（直接可读） | - |
| **时区歧义** | 无（绝对时间） | 无（显式时区） | - |


#### 选择建议

**使用时间戳的场景：**
- ✅ Kafka/RocketMQ 高吞吐消息队列
- ✅ Redis 缓存，频繁读写
- ✅ PostgreSQL JSONB 字段，存储空间敏感
- ✅ 时序数据库（InfluxDB、Prometheus）
- ✅ ELK 日志系统，每秒数万条日志

**使用 ISO-8601 的场景：**
- ✅ 审计日志，低频但需要可读性
- ✅ 配置文件，人工维护
- ✅ 低频异步任务（每小时几条）
- ✅ 调试场景，需要快速理解时间含义

---

## 7. 附录

### A. 时间格式使用场景对照表

| 类名 | 场景类型 | 数据类型 | 注解/格式 | 输出格式 | 使用场景 |
|------|---------|---------|----------|----------|----------|
| `UserVO` | **前端VO（推荐）** | `OffsetDateTime` | ❌ **不使用注解** | `"2025-12-10T10:00:00+08:00"` | **Controller 返回前端（默认）** |
| `UserVO` | **前端VO（特殊）** | `OffsetDateTime` | ✅ `@LocalTimeFormat` | `"2025-12-10 10:00:00"` | Controller 返回前端（需文档说明） |
| `UserCreateDTO` | **前端DTO（推荐）** | `OffsetDateTime` | ❌ **不使用注解** | `"2025-12-10T10:00:00+08:00"` | **Controller 接收前端（默认）** |
| `UserCreateDTO` | **前端DTO（特殊）** | `OffsetDateTime` | ✅ `@LocalTimeFormat` | `"2025-12-10 10:00:00"` | Controller 接收前端（需文档说明） |
| `UserEntity` | 数据库实体 | `OffsetDateTime` | ❌ 不使用注解 | `"2025-12-10T10:00:00+08:00"` | MyBatis 数据库映射 |
| `UserInternalDTO` | 内部DTO | `OffsetDateTime` | ❌ 不使用注解 | `"2025-12-10T10:00:00+08:00"` | Feign/Dubbo RPC |
| `OrderEvent` | **高性能消息** | `Long` | ⚡ 时间戳 | `1733882400000` | **Kafka/RocketMQ** |
| `AuditLogEvent` | **普通消息** | `String` | 📝 ISO-8601 | `"2025-12-10T10:00:00+08:00"` | 审计日志、配置文件 |
| `UserSettings` | **JSONB字段** | `Long` | ⚡ 时间戳 | `1733882400000` | **PostgreSQL JSONB、Redis** |
| `SystemConfig` | 配置类 | `String` | 📝 ISO-8601 | `"2025-12-10T10:00:00+08:00"` | application.yml 配置 |

#### 快速决策表

| 场景 | 推荐类型 | 理由 |
|------|---------|------|
| **前端API（默认）** | `OffsetDateTime` **不加注解** | **GMT+8 时区明确，国际标准** |
| **前端API（特殊）** | `OffsetDateTime` + `@LocalTimeFormat` | 简洁用户友好，需文档说明 |
| **微服务RPC** | `OffsetDateTime` 不加注解 | 保留时区信息 |
| **数据库字段** | `OffsetDateTime` | 类型安全、自动转换 |
| **Kafka/RocketMQ** | `Long` 时间戳 | **性能10倍提升** |
| **Redis JSONB** | `Long` 时间戳 | 存储空间节省75% |
| **审计日志** | `String` ISO-8601 | 可读性好、便于调试 |
| **配置文件** | `String` ISO-8601 | 人工维护友好 |

**典型错误示例**

**错误1：在 VO 字段上加注解（无效）**
```java
// ❌ 错误：在 VO 字段上加注解
@Data
public class UserVO {
    @LocalTimeFormat  // ❌ 错误位置：注解不支持字段
    private OffsetDateTime createTime;
}

// ✅ 正确：在 Controller 方法/类上加注解
@RestController
public class UserController {
    @LocalTimeFormat  // ✅ 正确位置
    @GetMapping("/users")
    public Result<List<UserVO>> list() {
        // UserVO 中的 OffsetDateTime 自动格式化
    }
}
```

**错误2：使用 String 类型传递时间**
```java
// ❌ 错误：使用 String 参数
@Service
public class UserService {
    public List<User> getUsers(String startTime, String endTime) {
        // 需要手动解析，容易出错
        OffsetDateTime start = OffsetDateTime.parse(startTime);
        OffsetDateTime end = OffsetDateTime.parse(endTime);
        return userRepository.findByCreateTimeBetween(start, end);
    }
}

// ✅ 正确：使用 OffsetDateTime 参数
@Service
public class UserService {
    public List<User> getUsers(OffsetDateTime startTime, OffsetDateTime endTime) {
        // 直接使用，类型安全
        return userRepository.findByCreateTimeBetween(startTime, endTime);
    }
}
```

**错误3：内部API加注解（丢失时区信息）**
```java
// ❌ 错误：内部API加注解
@RestController
public class InternalOrderController {
    @LocalTimeFormat  // ❌ 错误：微服务调用会丢失时区
    @GetMapping("/internal/orders")
    public List<OrderInternalDTO> getOrders() {
        // 应该不加注解，保留 ISO-8601 格式
    }
}

// ✅ 正确：内部API不加注解
@RestController
public class InternalOrderController {
    // ✅ 无注解，保留时区信息
    @GetMapping("/internal/orders")
    public List<OrderInternalDTO> getOrders() {
        // OrderInternalDTO 输出 "2025-12-10T10:00:00+08:00"
    }
}
```

### B. 时间格式完整对比

| 格式 | 示例 | 数据类型 | 大小 | 优点 | 缺点 | 适用场景 |
|------|------|---------|------|------|------|----------|
| **时间戳** | `1733882400000` | `Long` | 8字节 | 性能极高，绝对时间 | 不易读 | **Kafka/RocketMQ、Redis JSONB、高频日志** |
| **ISO-8601** | `2025-12-10T10:00:00+08:00` | `String` | 30字节 | 标准，显式时区，可读 | 序列化慢 | **Feign/Dubbo RPC、审计日志、配置文件** |
| **本地格式** | `2025-12-10 10:00:00` | `String` | 19字节 | 简洁，用户友好 | 无时区信息 | **前端API展示** |
| **UTC格式** | `2025-12-10T02:00:00Z` | `String` | 20字节 | 标准UTC | 需换算 | 国际化系统 |

#### 场景选择决策树

```
时间数据用途
├─ 前端API？
│  ├─ 默认推荐 → GMT+8 ISO-8601 (不加注解)
│  └─ 特殊简化 → 本地格式 (yyyy-MM-dd HH:mm:ss) + @LocalTimeFormat + 需文档说明
│
├─ 微服务RPC调用？
│  └─ YES → GMT+8 ISO-8601 (不加注解)
│
├─ 消息队列/JSONB/日志？
│  ├─ 高性能要求？(>1000条/秒)
│  │  └─ YES → 时间戳 (Long)
│  └─ 调试/审计需要？
│     └─ YES → ISO-8601 (String)
│
└─ 数据库字段？
   └─ PostgreSQL TIMESTAMPTZ
```

#### 性能对比（参考数据）

**序列化/反序列化性能（100万次操作）：**

| 格式 | 序列化耗时 | 反序列化耗时 | JSON大小（1万条） |
|------|-----------|-------------|------------------|
| 时间戳 | ~50ms | ~30ms | ~80KB |
| ISO-8601 | ~500ms | ~300ms | ~320KB |
| 本地格式 | ~450ms | ~280ms | ~240KB |

**性能结论：**
- 时间戳性能是 ISO-8601 的 **10倍**
- 时间戳存储空间是 ISO-8601 的 **25%**
- 高吞吐场景（Kafka/RocketMQ）必须使用时间戳

### C. 代码检查清单

**数据库层**
- [ ] 使用 `TIMESTAMPTZ` 类型（不是 `TIMESTAMP`）
- [ ] **时区配置**（二选一）
  - [ ] **推荐**：数据库用户级别配置 `ALTER USER <用户名> SET timezone TO 'Asia/Shanghai';`
  - [ ] 备选：所有连接字符串都包含 `timezone` 参数
    - [ ] JDBC URL: `jdbc:postgresql://host:port/db?timezone=Asia/Shanghai`
    - [ ] 开发工具（DataGrip/DBeaver）也要配置 timezone
    - [ ] psql 命令行: `PGTZ=Asia/Shanghai psql ...`

**应用层**
- [ ] 系统时区配置（JVM参数 `-Duser.timezone` 或环境变量 `TZ`）
- [ ] JVM时区与JDBC timezone参数保持一致
- [ ] 使用 `OffsetDateTime.now()` 而非硬编码时区
- [ ] 实体类使用 `OffsetDateTime`
- [ ] **禁止使用 String 类型传递时间参数**
  - [ ] Service 方法参数使用 `OffsetDateTime`
  - [ ] Controller 方法参数使用 `OffsetDateTime`
  - [ ] 方法之间传递时间使用 `OffsetDateTime`

**API层**
- [ ] **前端 Controller（默认推荐）**：不加注解，输出 GMT+8 ISO-8601 格式
- [ ] **前端 Controller（特殊场景）**：方法或类上加 `@LocalTimeFormat` 注解，并在设计文档和接口文档中说明
- [ ] 内部 API Controller **不加**注解
- [ ] VO/DTO 类**无需**添加注解
- [ ] Controller 不使用 String 接收时间参数

**消息队列/JSONB/日志层**
- [ ] **高性能场景**（Kafka/RocketMQ/Redis/高频日志）
  - [ ] 使用时间戳 `Long` 类型
  - [ ] 消息体时间字段：`private Long createTime;`
  - [ ] Redis JSONB 字段：`private Long lastLoginTime;`
  - [ ] 验证性能：序列化速度 > 1000条/秒
- [ ] **普通场景**（审计日志/配置文件/低频消息）
  - [ ] 使用 ISO-8601 `String` 类型
  - [ ] 审计日志：`private String occurredAt;`
  - [ ] 配置文件：使用 ISO-8601 格式便于人工维护
  - [ ] 消费时正确解析：`OffsetDateTime.parse(event.getOccurredAt())`

### D. 全球化部署配置

**国内部署**

```bash
# 1. JVM 时区参数
-Duser.timezone=Asia/Shanghai

# 2. 数据库时区配置（推荐方式）
ALTER USER app_user SET timezone TO 'Asia/Shanghai';

# 3. JDBC 连接（备选方式）
jdbc:postgresql://localhost:5432/db?timezone=Asia/Shanghai
```

**美国部署**

```bash
# 1. JVM 时区参数
-Duser.timezone=America/New_York

# 2. 数据库时区配置（推荐方式）
ALTER USER app_user SET timezone TO 'America/New_York';

# 3. JDBC 连接（备选方式）
jdbc:postgresql://localhost:5432/db?timezone=America/New_York
```

**欧洲部署**

```bash
# 1. JVM 时区参数
-Duser.timezone=Europe/London

# 2. 数据库时区配置（推荐方式）
ALTER USER app_user SET timezone TO 'Europe/London';

# 3. JDBC 连接（备选方式）
jdbc:postgresql://localhost:5432/db?timezone=Europe/London
```

> 💡 **优势**：
> - 代码完全无需修改，只需调整环境配置
> - 推荐使用 `ALTER USER` 方式，一次配置永久生效
> - 使用 pgAdmin 等工具查询时，结果显示为 GMT+8/GMT-5 等格式，清晰无歧义

