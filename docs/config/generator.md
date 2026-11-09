# 配置速查

> 本页此前描述的「在线配置生成工具 config-tool.html」**从未发布**（v1.1.0 文档虚构，仓库中不存在该文件），
> v1.10 文档评审时移除该描述。配置获取途径以下方真实入口为准。

## 配置获取途径

### 1. 全模块配置参考（推荐）

**[配置参考 →](/config/reference)** —— 全模块配置键、默认值、说明速查表，随版本同步维护。

### 2. 模块文档

每个模块文档（左侧「模块」导航）含各自的完整配置表与最小可运行示例：

| 场景 | 入口 |
|---|---|
| 统一响应/错误码/trace 链路 | [framework4j-web](/modules/web) |
| 鉴权（JWT + Refresh + 角色） | [framework4j-accesstoken](/modules/accesstoken) |
| 多数据源 + Druid（filters/wall 注意事项） | [framework4j-datasource](/modules/datasource) |
| 多 Redis | [framework4j-redis](/modules/redis) |
| 分布式 ID / OpenID | [framework4j-id](/modules/id) |
| 时间序列化 wire 契约 | [framework4j-datetime](/modules/datetime) |
| 动态日志采集 | [framework4j-tracelog](/modules/tracelog) |

### 3. 最小可跑配置

[快速开始](/guide/quick-start) 含 JitPack/本地 install 双方式 + 最小 application.yml
（含 v1.8 起 id/openid 显式启用说明）。

## 真实存在的自动化工具

| 工具 | 位置 | 说明 |
|---|---|---|
| 统一 AI skill | [skills/framework4j](https://github.com/funcommons/framework4j/tree/main/skills/framework4j) | AI 编码工具引入后按规范使用 SDK，含模块路由表与跨模块硬规范 |
| 依赖泄漏防护 | `framework4j-compat-test` 的 `DependencyLeakGuardTest` | 构建期锁定依赖面契约（web starter 必须 optional 等） |
