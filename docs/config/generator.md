# 在线配置生成

> 启动 demo 后访问 [http://localhost:8080/config-tool.html](http://localhost:8080/config-tool.html)

## 使用方式

1. **勾选模块**：按需勾选要启用的模块（默认全选）
2. **填参数**：每个模块展开配置表单（默认值已预填，可直接修改）
3. **实时预览**：右侧自动生成 `framework4j:` YAML 段
4. **复制**：点击"复制到剪贴板"按钮

## 静态访问

配置工具是纯 HTML + vanilla JS 页面，无需后端支持。可直接在浏览器打开：

```bash
# 方式 1：启动 demo 后访问
open http://localhost:8080/config-tool.html

# 方式 2：直接打开文件
open framework4j-demo/src/main/resources/static/config-tool.html
```

## 覆盖的模块

| 模块 | 配置项数 | 说明 |
|---|---|---|
| Redis | 4 | host/port/database/enabled |
| AccessToken | 5 | secret-key/hash-salt/expire-time |
| 签名 | 4 | timestamp-tolerance/nonce-ttl |
| 限流 | 5 | limit/window/scope/whitelist |
| 缓存 | 4 | ttl/null-ttl/l1/single-flight |
| 审计 | 3 | hash-chain/algorithm |
| 脱敏 | 2 | encryption-key |
| 幂等 | 2 | ttl |
| 分布式 ID | 2 | worker-id-strategy |
| 时间处理 | 1 | enabled |
| SQL 追踪 | 2 | mode |
