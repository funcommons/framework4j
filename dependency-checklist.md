# 新增依赖审查清单

> 每次在 pom.xml 新增 `<dependency>` 前必须填写本表。PR 中附上。

## 审查项

| # | 检查项 | 结论 | 备注 |
|---|---|---|---|
| 1 | **是否核心依赖**（100% 用户都需要）？ | ✅ 是 → compile / ❌ 否 → 必须 `optional=true` | |
| 2 | **传递了哪些二级/三级依赖**？ | 列出 groupId:artifactId:version | 运行 `mvn dependency:tree` |
| 3 | **二级依赖是否与旧版生态兼容**？ | ✅ 兼容 / ❌ 不兼容 / ❓ 未知 | 检查大版本跳跃（如 jsqlparser 4.x→5.x） |
| 4 | **是否已有 optional / provided 替代方案**？ | | SDK 默认 optional，消费者按需引入 |
| 5 | **scope 是否正确**？ | compile(核心) / optional(可选) / test(仅测试) / provided(运行时由容器提供) | |
| 6 | **是否需要加 exclusion**？ | 如果二级依赖有害（如 fastjson2 autotype）→ exclusion | |
| 7 | **版本是否锁定在父 pom dependencyManagement**？ | ✅ 是 / ❌ 否（禁止子 pom 写 version） | |

## 历史案例

### jsqlparser 5.x 冲突（v1.1.1 P0 事故）

| 项 | 详情 |
|---|---|
| 新增依赖 | `mybatis-plus-jsqlparser:3.5.14` |
| 传递 | `jsqlparser:5.2`（4.x→5.x 包路径重构，破坏性变更） |
| 消费者影响 | 使用 mybatis-plus:3.5.5 的客户 → `NoClassDefFoundError: SelectExpressionItem` |
| 根因 | 未检查第 2/3 项，硬依赖替代 optional |
| 修复 | `optional=true` + `@ConditionalOnClass` |
| 教训 | 非核心依赖必须 optional；新依赖必须查传递链 |

## 模板（复制使用）

```markdown
## 新增依赖审查：<artifactId>

| # | 检查项 | 结论 |
|---|---|---|
| 1 | 核心依赖？ | |
| 2 | 传递依赖 | |
| 3 | 旧版兼容？ | |
| 4 | optional 方案 | |
| 5 | scope | |
| 6 | exclusion | |
| 7 | 版本锁定 | |
```
