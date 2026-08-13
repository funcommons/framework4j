# framework4j 升级需求清单

| 项 | 说明 |
|---|---|
| 文档名称 | framework4j-id v1.2.1 → v1.3+ 升级需求 |
| 文档类型 | 外部依赖升级 Issue 清单 |
| 文档版本 | v1.0 |
| 创建日期 | 2026-08-13 |
| 状态 | 待提交 |
| 提交对象 | fun.commons.framework4j 维护团队 |
| 关联项目 | MMagiX(`fun.commons.mmagix.*`) |
| 关联内部设计 | `documents/09_openid-rollout-design.md` |

---

## 一、背景

MMagiX 项目已完成 `@OpenId` 全面落地的"一期 + 二期 #1/#2",目前正在推进二期 #3+(按业务域逐个补齐 Controller 入参 / Request DTO / Response VO 的 `@OpenId` 标注)。在该过程中,我们发现 framework4j-id **v1.2.1** 在多个边界场景上能力不足,需要打补丁或绕路,严重拖慢落地节奏。

本文档汇总这些能力缺口,作为 framework4j-id **v1.3.0**(或同等 minor 版本)的升级需求。

---

## 二、当前框架版本与已实施改造

```xml
<!-- backend/pom.xml -->
<framework4j.version>v1.2.1</framework4j.version>
<dependency>
  <groupId>com.github.funcommons.framework4j</groupId>
  <artifactId>framework4j-all</artifactId>
  <version>${framework4j.version}</version>
</dependency>
```

我们已完成/进行中的改造:

- ✅ **一期**:6 个裸 Entity 控制器 VO 化 + Controller 入参 `@OpenId`
- ✅ **二期 #1**:项目侧补丁 `OpenIdRequestBodyDeserializer`(本应由框架提供)
- ✅ **二期 #2**:audit 模块手动 `IdObfuscator` → 注解
- 🚧 **二期 #3+**:按业务域补齐 ~220 处 `@OpenId`(详见 `09_openid-rollout-design.md`)

---

## 三、需求清单(按优先级排序)

### P0 — 必须解决(否则落地无法继续)

#### R1. `@RequestBody` 反序列化器(框架原生支持)

**现状**:

framework4j-id v1.2.1 只提供 **序列化侧**(`BeanSerializerModifier`,扫描 `@OpenId` Long 字段 → 12 字符混淆串),**没有提供反序列化器**。这意味着:

- `@RequestParam Long xxxId` 由 `FormatterFactory` 兼容(纯数字串形态校验通过即可还原)
- `@PathVariable Long xxxId` 由 `OpenIdArgumentResolver` 还原
- **`@RequestBody` 字段里如果有 `@OpenId Long` 字段**,反序列化时框架 **不会** 自动从字符串还原,业务侧必须手工 `IdObfuscator.fromOpenId`

**项目侧临时补丁**:

我们已在 `bootstrap/.../config/OpenIdRequestBodyDeserializer.java` 实现了补丁,核心逻辑约 100 行,挂载 `BeanDeserializerModifier`,三条件白名单(类型 + 注解 + 字符串形态校验)。

**需求**:

将上述补丁下沉到 framework4j-id 模块,作为 `@OpenId` 注解的标准行为。命名建议:

```
fun.commons.framework4j.openid.jackson.OpenIdBeanDeserializerModifier
fun.commons.framework4j.openid.jackson.OpenIdRequestBodyDeserializerAutoConfiguration  // Spring Boot auto-config
```

并提供开关:

```yaml
framework4j:
  openid:
    enabled: true
    request-body-deserializer: true   # 默认 true,需要时可关闭(特殊 DTO 走自定义反序列化)
```

#### R2. `List<Long>` 反序列化支持

**现状**:

框架不支持 `List<Long>` 字段加 `@OpenId`。涉及场景:

| 文件:行 | 字段 | 业务 |
|---|---|---|
| `apikey/CreateApiKeyRequest.java:66` | `private List<Long> tagIds;` | 创建 API Key 时选标签 |
| `apikey/UpdateApiKeyRequest.java:57` | `private List<Long> tagIds;` | 更新标签 |
| `apikey/BatchRevokeRequest.java:27` | `private List<Long> ids;` | 批量吊销 |
| `asset/dto/request/AssetBatchRequest.java:19` | `private List<Long> ids;` | 批量移动/删除 |
| `chat/dto/request/SendChatMessageRequest.java:26` | `private List<Long> attachmentIds;` | 发消息附附件 |
| `model/dto/request/SyncChannelGroupsRequest.java:33` | 嵌套 record 的 `private Long groupId;` | 同步分组-渠道关系 |

错误信息大致为:

```
Could not read JSON: cannot deserialize Long from String value "abc123XYZ45_"
```

**需求**:

支持以下两种形态(择一或同时):

**方案 A — 数组元素自动识别**(推荐)

```java
@OpenId
private List<Long> tagIds;   // 框架识别 List<Long> + 元素注解,按字符串数组还原
```

**方案 B — 拆为顶层 List<Long> + 显式注解**

```java
@OpenIdList  // 新注解
private List<Long> tagIds;
```

**项目侧临时方案**(框架不补时使用):

业务层手工反序列化,绕过 `@RequestBody` 路径:

```java
@PostMapping("/batch-revoke")
public ApiResponse<Void> batchRevoke(@RequestBody List<String> openIds) {
    List<Long> ids = openIds.stream()
        .map(IdObfuscator::fromOpenId)
        .toList();
    return ApiResponse.success(service.batchRevoke(ids));
}
```

---

### P1 — 强烈建议

#### R3. 嵌套 record / 嵌套对象字段支持

**现状**:

`BeanDeserializerModifier.updateBuilder()` 默认只扫描顶层 `SettableBeanProperty`,嵌套 record 字段(如 `SyncChannelGroupsRequest.GroupChannelItem.groupId`)上的 `@OpenId` **不生效**。

**需求**:

递归扫描所有 `BeanPropertyDefinition`(含嵌套),或新增 `@OpenIdRecursive` 注解标记容器/对象。

**临时方案**:

把嵌套字段"拍平"为顶层:

```java
// 旧
record SyncChannelGroupsRequest(List<GroupChannelItem> items) {}
record GroupChannelItem(Long groupId, Long channelId) {}

// 新(可选)
record SyncChannelGroupsRequest(
    @OpenId List<Long> groupIds,
    List<Long> channelIds
) {}
```

#### R4. `String` 路径参数 → `@OpenId Long` 自动迁移

**现状**:

项目里约 30+ 处 `@PathVariable String id` + `Long.parseLong(id)` 模式:

```
asset/.../AssetController.java:71  getAsset(@PathVariable String id) {
                                      Long realId = Long.parseLong(id);
                                  }
asset/.../AssetController.java:79  updateAsset(@PathVariable String id) { ... }
core/.../WorkController.java       createWork / getWork ... (全部 String workId)
core/.../SpeechWorkController.java 同上
core/.../PlatformController.java:171 updateUserStatus(@PathVariable String userId) { Long.parseLong }
core/.../AdminController.java:176   同上
```

这些"老 String 规避方案" 防了 JS 精度,但**没走混淆**,可被爬虫枚举。

**需求**:

提供 **格式化器兼容两种形态** 已经是默认行为(`framework4j.openid` 默认兼容数字串 + 12 字符混淆串),但项目侧不想逐处改 30+ 文件。能否:

- 提供 `@OpenIdCoexist`(假设命名)允许 Controller **同时接受** String 路径 + Long `@RequestBody`?
- 或者:提供一个 IDE refactor recipe / codemod 脚本?

#### R5. `Integer` ID 支持(决策点)

**现状**:

`asset/.../dto/response/TagVO.java` 用 `Integer id`(PostgreSQL SERIAL),framework 不支持混淆(`@OpenId` 仅作用于 `Long`/`long`)。项目侧注释明确写"字典表不混淆"。

**需求**:

**决策二选一**(请 framework 团队表态):

- **选项 A**:framework 长期仅支持 `Long`/`long`,文档明确写"Integer/Short 字段不属于 @OpenId 范围"。项目侧继续维持 Integer 字典,无改造。
- **选项 B**:framework 支持 `Integer`,让字典表也能混淆(算法独立于 Long 路径,因为 Integer 不会超 MAX_VALUE)。

**我们倾向选项 A**:文档化"Integer 字典不混淆"为官方立场,避免不必要的扩展。

---

### P2 — 改进

#### R6. `fail-fast` 错误信息优化

**现状**:

启动期 `FailFastValidator` 校验 `@OpenId @PathVariable` 缺 `name` 且编译期未加 `-parameters` 时,错误信息大致为:

```
启动失败: @PathVariable Long id 缺少 name 属性,需在编译期开启 -parameters
```

**需求**:

补充开发者友好的诊断信息:

```
启动失败: Controller fun.commons.mmagix.chat.ChatConversationController#rename
  @PathVariable Long id 在编译期未生成 parameter name (Maven -parameters 未开启)
  修复: pom.xml 中 maven-compiler-plugin 加 <configuration><parameters>true</parameters></configuration>
  或者: @PathVariable("id") Long id 显式声明 name
  参考: docs/framework4j/openid.md#pathvariable-name-required
```

并提供 Maven 插件自动检测 / 修复。

#### R7. 反序列化器支持自定义反序列化兜底

**现状**:

三条件白名单(类型 + 注解 + 形态校验)通过即还原,不通过走默认反序列化。但 `balance` / `credits` 等真数字字段如果意外加了 `@OpenId`,会被错误还原。

**需求**:

提供 `@OpenId(strict = false)`(默认 true),strict=false 时:

- 字符串形态校验失败 → 走默认(抛错或留原值,按设计决定)
- 不影响 `balance` 字段被误标的场景

#### R8. 文档化迁移指南

**现状**:

"老 String id" → "@OpenId Long id" 的迁移路径在 framework4j 文档中**没有官方迁移指南**,项目侧靠 `09_openid-rollout-design.md` 自己摸索。

**需求**:

在 `framework4j-id` 模块的 README/docs 下补充:

- 从 `Long.parseLong(String)` 模式迁移到 `@OpenId Long` 的对照表
- 从 `String.valueOf(entity.getId())` 出参(老规避方案)迁移到 `@OpenId Long id + framework 序列化` 的对照表
- 从 `IdObfuscator.toOpenId(id, "USR")` 手动调用迁移到注解的对照表(参考 audit 模块迁移)
- 灰度策略(开关 false → true 的回滚路径)

---

## 四、API 兼容性矩阵

| 现有行为 | v1.2.1 | v1.3 期望 | 兼容性 |
|---|---|---|---|
| `@OpenId @PathVariable Long` 反混淆 | ✅ | ✅ | 兼容 |
| `@OpenId @RequestParam Long` 反混淆 | ✅ | ✅ | 兼容 |
| `@OpenId Long` 字段序列化 | ✅ | ✅ | 兼容 |
| `@OpenId @RequestBody Long` 字段反序列化 | ❌(项目补丁) | ✅ | **新增能力** |
| `@OpenId List<Long>` 反序列化 | ❌ | ✅ | **新增能力** |
| `@OpenId` 嵌套 record 字段反序列化 | ❌ | ✅ | **新增能力** |
| `@OpenId Integer` 字段 | ❌ | 决策点 | 待定 |
| `@OpenId strict=false` 兜底 | ❌ | ✅ | **新增能力** |

---

## 五、版本规划建议

| 版本 | 内容 | 项目侧升级窗口 |
|---|---|---|
| **v1.2.2** (patch) | 错误信息优化(R6)、文档化(R8) | 立即可用 |
| **v1.3.0** (minor) | R1 + R2 + R3(`@RequestBody` 反序列化器原生、`List<Long>` 支持、嵌套字段支持) | 二期 #3 启动前(预计 2026-09) |
| **v1.4.0** (minor) | R4 + R7(String 路径兼容 + strict 兜底) | 二期 #7(全部 VO 迁移批次)前 |
| **v1.5.0** (minor) | R5(`Integer` 决策落地) | 不强制升级 |

---

## 六、测试要求(交付时附带的测试矩阵)

| 维度 | 测试 |
|---|---|
| `@RequestBody` 反序列化器 | ✅ `@OpenId Long` 字符串反混淆;✅ 数字字符串透传;✅ `balance` 字段无注解不受影响;✅ null/缺失字段 |
| `List<Long>` | ✅ `@OpenId List<Long>` 字符串数组反混淆;✅ 混合数字串 + 混淆串;✅ 空数组;✅ null |
| 嵌套 record | ✅ 嵌套 record 字段 `@OpenId` 生效;✅ 多层嵌套(`A.B.C`) |
| 兼容期 | ✅ 旧前端传数字 id (`"12345"`) 仍能解析为 `Long 12345` |
| 启动校验 | ✅ `@PathVariable` 缺 name + `-parameters` 关闭 → 启动失败信息明确 |
| 性能 | 100 万次反序列化压测,延迟 < 100 ms |

---

## 七、项目侧承诺

完成升级后,MMagiX 将:

1. 移除 `OpenIdRequestBodyDeserializer.java`(项目侧补丁,~100 行)
2. 删除 `bootstrap/.../config/OpenIdRequestBodyDeserializer` 的 test
3. pom.xml `framework4j.version` 升到 v1.3.0,移除 `<exclusion>` 或显式覆盖
4. ~220 处 `@OpenId` 改造全部基于框架原生能力完成,业务代码不再含任何 `IdObfuscator.fromOpenId` 手工调用

---

## 八、附:项目侧临时补丁完整代码(供 framework 团队参考实现)

> 以下三段代码来自 MMagiX 仓库当前 `openid` 分支,**已在线上生产跑通**,framework 团队可直接以这份代码为蓝本下沉到 framework4j-id 模块。

### 8.1 主代码:`OpenIdRequestBodyDeserializer.java`

路径:`backend/bootstrap/src/main/java/fun/commons/mmagix/bootstrap/config/OpenIdRequestBodyDeserializer.java`(136 行)

```java
package fun.commons.mmagix.bootstrap.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.openid.annotation.OpenId;
import java.io.IOException;
import java.util.Iterator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 项目侧补丁: 还原 framework4j-id v1.2.1 缺失的 JSON body OpenId 字段反序列化能力.
 *
 * <p>framework4j 只支持序列化侧 (BeanSerializerModifier) 和 Query/Path 还原;
 * JSON body 内 OpenId 字段从 String 还原为 Long 必须由项目侧补全.
 *
 * <p>核心约束 (三条件):
 * <ol>
 *   <li>字段类型 = Long / long</li>
 *   <li>字段标注了 OpenId</li>
 *   <li>JSON 值是 string 且通过 IdObfuscator.isValid 形态校验</li>
 * </ol>
 * 三个条件同时满足才还原; 其它场景走默认反序列化 (保护 balance/credits 等真数字字段)。
 *
 * <p>框架限制 (与 framework4j 一致): 仅处理顶层标量字段, 不支持 List<Long>/Map 值/Integer.
 *
 * <p>v2 改造: 改用 BeanPostProcessor 在 ObjectMapper 完全初始化后手动注册 SimpleModule,
 * 绕开 Jackson2ObjectMapperBuilderCustomizer 的 modulesToInstall 整体替换语义
 * (后执行的 framework4j customizer 会冲掉前面的 SimpleModule, 留下 SimpleModule-3/4
 * 但 setupContext 未被调用的孤儿 module, 表现为 _modifiers.length=1 不含本项目 modifier).
 * BeanPostProcessor 走 ObjectMapper.registerModule(), 已无 modulesToInstall 替换问题.
 *
 * @author system
 */
@Slf4j
@Configuration
public class OpenIdRequestBodyDeserializer {

    @Bean
    public BeanPostProcessor openIdRequestBodyDeserializerPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (!(bean instanceof ObjectMapper)) {
                    return bean;
                }
                ObjectMapper mapper = (ObjectMapper) bean;
                SimpleModule module = new SimpleModule("project-openid-requestbody");
                module.setDeserializerModifier(new OpenIdBeanDeserializerModifier());
                mapper.registerModule(module);
                log.info("[OpenId] JSON body 反序列化器补丁已注册 (BeanPostProcessor, ObjectMapper={})",
                        beanName);
                return mapper;
            }
        };
    }

    static class OpenIdBeanDeserializerModifier extends BeanDeserializerModifier {
        @Override
        public BeanDeserializerBuilder updateBuilder(DeserializationConfig config,
                                                     BeanDescription beanDesc,
                                                     BeanDeserializerBuilder builder) {
            Iterator<SettableBeanProperty> it = builder.getProperties();
            while (it.hasNext()) {
                SettableBeanProperty prop = it.next();
                OpenId anno = prop.getAnnotation(OpenId.class);
                if (anno == null) {
                    continue;
                }
                if (!isLongLike(prop.getType().getRawClass())) {
                    continue;
                }
                JsonDeserializer<?> deser = new OpenIdLongDeserializer(prop.getType().getRawClass());
                builder.addOrReplaceProperty(prop.withValueDeserializer(deser), false);
            }
            return builder;
        }

        private static boolean isLongLike(Class<?> c) {
            return c == Long.class || c == long.class;
        }
    }

    static class OpenIdLongDeserializer extends StdDeserializer<Object> {

        private final Class<?> target;

        OpenIdLongDeserializer(Class<?> target) {
            super(target);
            this.target = target;
        }

        @Override
        public Object deserialize(JsonParser p, com.fasterxml.jackson.databind.DeserializationContext ctxt)
                throws IOException {
            if (p.currentToken() == null) {
                return null;
            }
            if (p.currentToken().isNumeric()) {
                Number n = p.getNumberValue();
                if (target == long.class || target == Long.class) {
                    return n.longValue();
                }
                return n;
            }
            if (p.currentToken() == com.fasterxml.jackson.core.JsonToken.VALUE_STRING) {
                String s = p.getValueAsString();
                if (s == null || s.isEmpty()) {
                    return null;
                }
                if (IdObfuscator.isValid(s)) {
                    try {
                        return IdObfuscator.fromOpenId(s);
                    } catch (IllegalArgumentException e) {
                        log.debug("[OpenId] fromOpenId 失败: {}", s);
                        throw ctxt.weirdStringException(s, target, "形态像混淆串但还原失败");
                    }
                }
                return ctxt.readValue(p, target);
            }
            return ctxt.readValue(p, target);
        }
    }
}
```

### 8.2 单测:`OpenIdRequestBodyDeserializerTest.java`

路径:`backend/bootstrap/src/test/java/fun/commons/mmagix/bootstrap/config/OpenIdRequestBodyDeserializerTest.java`(148 行)

```java
package fun.commons.mmagix.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.mmagix.bootstrap.config.OpenIdRequestBodyDeserializer.OpenIdBeanDeserializerModifier;
import fun.commons.mmagix.bootstrap.config.OpenIdRequestBodyDeserializer.OpenIdLongDeserializer;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OpenIdRequestBodyDeserializer 三条件白名单回归。
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>字段类型 = Long/long + @OpenId + 合法混淆串 → 还原</li>
 *   <li>字段类型 = Long/long + @OpenId + 数字 → 直接透传 (兼容期前端仍可能传数字 id)</li>
 *   <li>字段类型 = Long/long + @OpenId + 非混淆串 → 走默认反序列化 (抛错是预期)</li>
 *   <li>字段类型 = Long/long + 无 @OpenId + 字符串 → 走默认 (抛错是预期)</li>
 *   <li>字段类型 = Long/long + @OpenId + null → 透传 null</li>
 *   <li>字段类型 = BigDecimal + @OpenId (异常) → 不被反序列化器接管, 走默认</li>
 *   <li>字段类型 = BigDecimal + 无注解 + 字符串 → 走默认</li>
 * </ol>
 */
@DisplayName("OpenIdRequestBodyDeserializer 三条件白名单")
class OpenIdRequestBodyDeserializerTest {

    static class UserCreateRequest {
        @OpenId
        public Long tenantId;
        @OpenId
        public Long userId;
        public BigDecimal balance;
        public Long credits;
        public BigDecimal amount;
    }

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        com.fasterxml.jackson.databind.module.SimpleModule module =
                new com.fasterxml.jackson.databind.module.SimpleModule("test");
        module.setDeserializerModifier(new OpenIdBeanDeserializerModifier());
        mapper.registerModule(module);
    }

    @Test
    @DisplayName("条件1: Long + @OpenId + 合法混淆串 → 还原")
    void openIdAnnotatedLongWithValidOpenIdStringDecoded() throws Exception {
        String validOpenId = IdObfuscator.toOpenId(12345L);
        String json = "{\"tenantId\":\"" + validOpenId + "\"}";
        UserCreateRequest vo = mapper.readValue(json, UserCreateRequest.class);
        assertThat(vo.tenantId).isEqualTo(12345L);
    }

    @Test
    @DisplayName("条件2: Long + @OpenId + 数字 → 直接透传")
    void openIdAnnotatedLongWithNumericPassesThrough() throws Exception {
        String json = "{\"tenantId\":67890}";
        UserCreateRequest vo = mapper.readValue(json, UserCreateRequest.class);
        assertThat(vo.tenantId).isEqualTo(67890L);
    }

    @Test
    @DisplayName("条件3: Long + @OpenId + 非混淆串 → 走默认 (抛错)")
    void openIdAnnotatedLongWithNonOpenIdStringFails() {
        String json = "{\"tenantId\":\"hello\"}";
        assertThatThrownBy(() -> mapper.readValue(json, UserCreateRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.MismatchedInputException.class);
    }

    @Test
    @DisplayName("条件4: Long + 无 @OpenId + 字符串 → 走默认 (抛错)")
    void plainLongWithStringFails() {
        String json = "{\"credits\":\"abc\"}";
        assertThatThrownBy(() -> mapper.readValue(json, UserCreateRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.MismatchedInputException.class);
    }

    @Test
    @DisplayName("条件5: Long + @OpenId + null → 透传 null")
    void openIdAnnotatedLongWithNullPassesThrough() throws Exception {
        String json = "{\"tenantId\":null}";
        UserCreateRequest vo = mapper.readValue(json, UserCreateRequest.class);
        assertThat(vo.tenantId).isNull();
    }

    @Test
    @DisplayName("条件6: BigDecimal + 无注解 + 字符串 → 走默认")
    void plainBigDecimalAcceptsString() throws Exception {
        String json = "{\"balance\":\"100.50\"}";
        UserCreateRequest vo = mapper.readValue(json, UserCreateRequest.class);
        assertThat(vo.balance).isEqualByComparingTo(new BigDecimal("100.50"));
    }

    @Test
    @DisplayName("条件7: Long + @OpenId + 空字符串 → null")
    void openIdAnnotatedLongWithEmptyStringIsNull() throws Exception {
        String json = "{\"tenantId\":\"\"}";
        UserCreateRequest vo = mapper.readValue(json, UserCreateRequest.class);
        assertThat(vo.tenantId).isNull();
    }

    @Test
    @DisplayName("OpenIdLongDeserializer 单测: 数字 token 直读转 Long")
    void standaloneDeserializerNumeric() throws Exception {
        OpenIdLongDeserializer d = new OpenIdLongDeserializer(Long.class);
        try (com.fasterxml.jackson.core.JsonParser p =
                mapper.getFactory().createParser("12345")) {
            p.nextToken();
            Object v = d.deserialize(p, mapper.getDeserializationContext());
            assertThat(v).isInstanceOf(Long.class).isEqualTo(12345L);
        }
    }

    @Test
    @DisplayName("OpenIdLongDeserializer 单测: 长数字 token 直读转 Long")
    void standaloneDeserializerLongNumeric() throws Exception {
        OpenIdLongDeserializer d = new OpenIdLongDeserializer(Long.class);
        try (com.fasterxml.jackson.core.JsonParser p =
                mapper.getFactory().createParser("2087212818467364866")) {
            p.nextToken();
            Object v = d.deserialize(p, mapper.getDeserializationContext());
            assertThat(v).isInstanceOf(Long.class).isEqualTo(2087212818467364866L);
        }
    }

    @Test
    @DisplayName("OpenIdLongDeserializer 单测: 原生 long.class 数字 token")
    void standaloneDeserializerPrimitiveLongNumeric() throws Exception {
        OpenIdLongDeserializer d = new OpenIdLongDeserializer(long.class);
        try (com.fasterxml.jackson.core.JsonParser p =
                mapper.getFactory().createParser("42")) {
            p.nextToken();
            Object v = d.deserialize(p, mapper.getDeserializationContext());
            assertThat(v).isInstanceOf(Long.class).isEqualTo(42L);
        }
    }
}
```

### 8.3 容器级端到端:`OpenIdEndToEndIntegrationTest.java`

路径:`backend/bootstrap/src/test/java/fun/commons/mmagix/bootstrap/integration/OpenIdEndToEndIntegrationTest.java`(268 行)

```java
package fun.commons.mmagix.bootstrap.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.mmagix.bootstrap.BaseIntegrationTest;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * framework4j OpenId 全链路端到端集成测试 (二期 #2 补充).
 *
 * <p>目的: 锁定 framework4j + 项目侧补丁在 Spring MVC 容器内
 * <ul>
 *   <li>{@code OpenIdFormatterFactory} 入参还原</li>
 *   <li>{@code OpenIdBeanSerializerModifier} VO 字段出参序列化</li>
 *   <li>项目侧 {@code OpenIdRequestBodyDeserializer} @RequestBody 字段还原</li>
 * </ul>
 * 全链路生效, 升级 framework4j / Jackson / Spring 版本时可第一时间发现破坏性变更.
 *
 * <p>为什么用 test-only controller 而不是 audit 业务 controller:
 * <ul>
 *   <li>AccessLogController 等业务 controller 依赖 Service + Repository,
 *       不 mock 时 SQL 异常掩盖 framework4j 行为</li>
 *   <li>本测试只关注 framework4j 解析/序列化语义, 业务无关</li>
 *   <li>{@code @Import(TestOpenIdProbeController.class)} 仅在该测试上下文注入,
 *       不影响其它测试</li>
 * </ul>
 *
 * @author system
 */
@Import(OpenIdEndToEndIntegrationTest.TestOpenIdProbeController.class)
@DisplayName("OpenIdEndToEnd framework4j 全链路集成")
class OpenIdEndToEndIntegrationTest extends BaseIntegrationTest {

    private static final String OPEN_ID_PATH = "/v1/__test__/openid";
    private static final long CHANNEL_ID = 12345L;
    private static final long USER_ID = 88L;
    private static final long API_KEY_ID = 1001L;

    @Autowired
    private MockMvc mockMvc;

    // ======== 入参还原: @RequestParam @OpenId Long ========

    @Nested
    @DisplayName("A. @RequestParam @OpenId Long 入参还原 (OpenIdFormatterFactory)")
    class RequestParamResolution {

        @Test
        @DisplayName("12 字符串 OpenId → 还原 Long")
        void openIdStringDecodedToLong() throws Exception {
            String validOpenId = encode(CHANNEL_ID);

            mockMvc.perform(get(OPEN_ID_PATH + "/query")
                            .param("channel_id", validOpenId)
                            .param("user_id", encode(USER_ID))
                            .param("api_key_id", encode(API_KEY_ID)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rawChannelId").value(CHANNEL_ID))
                    .andExpect(jsonPath("$.rawUserId").value(USER_ID))
                    .andExpect(jsonPath("$.rawApiKeyId").value(API_KEY_ID))
                    .andExpect(jsonPath("$.channelIdOpenId").value(validOpenId));
        }

        @Test
        @DisplayName("纯数字字符串 → 兼容期直接放行 (二期 #1 OpenIdFormatterFactory 行为)")
        void numericStringPassThrough() throws Exception {
            mockMvc.perform(get(OPEN_ID_PATH + "/query")
                            .param("channel_id", "99999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rawChannelId").value(99999));
        }

        @Test
        @DisplayName("null/缺省入参 → null")
        void missingParamBecomesNull() throws Exception {
            mockMvc.perform(get(OPEN_ID_PATH + "/query"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rawChannelId").doesNotExist())
                    .andExpect(jsonPath("$.rawUserId").doesNotExist())
                    .andExpect(jsonPath("$.rawApiKeyId").doesNotExist());
        }

        @Test
        @DisplayName("非 OpenId 非数字字符串 → framework4j ParseException → GlobalExceptionHandler 返 ApiResponse 错误 (HTTP 200 + PARAM_FORMAT_ERROR)")
        void invalidStringRejected() throws Exception {
            // framework4j GlobalExceptionHandler.handleMethodArgumentTypeMismatchException 走 HTTP 200 + ApiResponse.fail(PARAM_FORMAT_ERROR)
            mockMvc.perform(get(OPEN_ID_PATH + "/query")
                            .param("channel_id", "not-an-open-id"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10102))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("类型不匹配")));
        }
    }

    // ======== 出参序列化: @OpenId Long 字段 ========

    @Nested
    @DisplayName("B. VO @OpenId Long 字段出参序列化 (OpenIdBeanSerializerModifier)")
    class ResponseSerialization {

        @Test
        @DisplayName("@OpenId Long → 12 字符串混淆")
        void openIdLongSerializesAs12Char() throws Exception {
            mockMvc.perform(get(OPEN_ID_PATH + "/response")
                            .param("id", String.valueOf(CHANNEL_ID)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(encode(CHANNEL_ID)))
                    .andExpect(jsonPath("$.nonOpenIdLong").value(CHANNEL_ID));
        }

        @Test
        @DisplayName("未标注 @OpenId 的 Long 字段 → Jackson 默认 (number)")
        void plainLongSerializedAsNumber() throws Exception {
            mockMvc.perform(get(OPEN_ID_PATH + "/response")
                            .param("id", String.valueOf(CHANNEL_ID)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nonOpenIdLong").value(CHANNEL_ID));
        }

        @Test
        @DisplayName("@OpenId Long null → JSON null")
        void nullOpenIdLongSerializedAsJsonNull() throws Exception {
            mockMvc.perform(get(OPEN_ID_PATH + "/response-null"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.nullValue()));
        }
    }

    // ======== 请求体还原: @RequestBody @OpenId Long 字段 ========

    @Nested
    @DisplayName("C. @RequestBody @OpenId Long 字段还原 (OpenIdRequestBodyDeserializer)")
    class RequestBodyDeserialization {

        @Test
        @DisplayName("请求体 OpenId 串 → 还原 Long 入参")
        void requestBodyOpenIdDecoded() throws Exception {
            String json = "{\"id\":\"" + encode(CHANNEL_ID) + "\"}";

            mockMvc.perform(post(OPEN_ID_PATH + "/body")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rawId").value(CHANNEL_ID))
                    .andExpect(jsonPath("$.id").value(encode(CHANNEL_ID)));
        }

        @Test
        @DisplayName("请求体数字 → 兼容期直接放行")
        void requestBodyNumericPassThrough() throws Exception {
            String json = "{\"id\":\"99999\"}";

            mockMvc.perform(post(OPEN_ID_PATH + "/body")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rawId").value(99999));
        }

        @Test
        @DisplayName("请求体非法 OpenId 串 → OpenIdLongDeserializer 抛 MismatchedInput → GlobalExceptionHandler 返 ApiResponse 错误 (HTTP 200)")
        void requestBodyInvalidOpenIdRejected() throws Exception {
            // framework4j GlobalExceptionHandler 把 HttpMessageNotReadableException 转 ApiResponse.fail
            String json = "{\"id\":\"not-a-valid-open-id\"}";

            mockMvc.perform(post(OPEN_ID_PATH + "/body")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(0)));
        }
    }

    // ======== 工具 ========

    private String encode(long id) {
        return fun.commons.framework4j.id.util.IdObfuscator.toOpenId(id);
    }

    // ======== Test-only controller ========

    @RestController
    @RequestMapping(OPEN_ID_PATH)
    static class TestOpenIdProbeController {

        @GetMapping("/query")
        public QueryResponse query(
                @RequestParam(name = "channel_id", required = false) @OpenId Long channelId,
                @RequestParam(name = "user_id", required = false) @OpenId Long userId,
                @RequestParam(name = "api_key_id", required = false) @OpenId Long apiKeyId) {
            QueryResponse resp = new QueryResponse();
            resp.rawChannelId = channelId;
            resp.rawUserId = userId;
            resp.rawApiKeyId = apiKeyId;
            if (channelId != null) {
                resp.channelIdOpenId = fun.commons.framework4j.id.util.IdObfuscator.toOpenId(channelId);
            }
            return resp;
        }

        @GetMapping("/response")
        public MixedResponse response(@RequestParam(required = false) Long id) {
            MixedResponse resp = new MixedResponse();
            resp.id = id;
            resp.nonOpenIdLong = id;
            return resp;
        }

        @GetMapping("/response-null")
        public MixedResponse responseNull() {
            return new MixedResponse();
        }

        @PostMapping("/body")
        public MixedResponse body(@RequestBody ProbeRequest input) {
            MixedResponse resp = new MixedResponse();
            resp.id = input.getId();
            resp.nonOpenIdLong = input.getId();
            resp.rawId = input.getId();
            return resp;
        }

        @Data
        static class ProbeRequest {
            @OpenId
            private Long id;
        }

        @Data
        static class QueryResponse {
            @JsonInclude(JsonInclude.Include.NON_NULL)
            private Long rawChannelId;
            @JsonInclude(JsonInclude.Include.NON_NULL)
            private Long rawUserId;
            @JsonInclude(JsonInclude.Include.NON_NULL)
            private Long rawApiKeyId;
            @JsonInclude(JsonInclude.Include.NON_NULL)
            private String channelIdOpenId;
        }

        @Data
        static class MixedResponse {
            @OpenId
            private Long id;
            private Long nonOpenIdLong;
            @JsonInclude(JsonInclude.Include.NON_NULL)
            private Long rawId;
        }
    }
}
```

### 8.4 关键实现要点(供 framework 团队 review 时关注)

| 点 | 当前实现 | framework 化建议 |
|---|---|---|
| **挂载时机** | `BeanPostProcessor.postProcessAfterInitialization` 拦截 `ObjectMapper` bean | Spring Boot `AutoConfiguration` + `Jackson2ObjectMapperBuilderCustomizer`(framework4j 现有 customizer 走 modulesToInstall 会被冲掉,**必须**改 BeanPostProcessor 路径,见 v2 注释) |
| **模块优先级** | `SimpleModule` 名 `"project-openid-requestbody"`,框架如原生支持建议用 `"framework4j-openid-requestbody"` | 同名注册会被 Jackson 去重,无副作用 |
| **三条件白名单** | 类型 ∈ {Long, long} ∧ `@OpenId` 注解 ∧ `IdObfuscator.isValid(s)` | 直接下沉,无需改动 |
| **数字 token 直读** | `p.currentToken().isNumeric()` 走默认路径 `n.longValue()` | 同上 |
| **空字符串/缺省** | 空串 → null;字段缺失 → null | 同上 |
| **非 OpenId 字符串** | `IdObfuscator.isValid` 失败 → 走 `ctxt.readValue(p, target)` 抛 `MismatchedInputException` | 同上(预期行为) |
| **不接管 `Integer`** | `isLongLike` 仅判 `Long`/`long` | 保持(R5 待决策) |
| **不接管 `List<Long>`** | `BeanDeserializerModifier.updateBuilder` 只看顶层 `SettableBeanProperty` | **R2 需新增能力** |
| **不接管嵌套字段** | 同上 | **R3 需新增能力** |
| **日志规范** | `log.info` 注册一次 + `log.debug` 还原失败(避免日志洪水) | 同上 |

### 8.5 framework 团队落地建议

1. **第一步**:把这三段代码直接复制到 `framework4j-id/src/main/java/fun/commons/framework4j/openid/jackson/` 下,改名 + 改包名为框架命名空间。
2. **第二步**:改 `BeanPostProcessor` 注册方式为 Spring Boot `AutoConfiguration`(参考 framework4j-web 已有的 `OpenIdWebAutoConfiguration` 模式)。
3. **第三步**:把单元测试和集成测试合并到 `framework4j-id/src/test/java/...` 下,跟随框架版本一起回归。
4. **第四步**:在 framework4j-id 的 `META-INF/spring/...AutoConfiguration.imports` 注册,实现"框架被引用即生效"。
5. **第五步**:发布 v1.3.0,MMagiX 删除项目侧 `OpenIdRequestBodyDeserializer.java` + 配套测试 3 个文件,pom 升到 v1.3.0。

---

## 九、联系方式

- 提交者:MMagiX OpenId 落地小组
- 分支:`openid`(基于 `glm52`)
- 关联文档:`backend/documents/09_openid-rollout-design.md`
- 评审清单(framework 侧请确认):
  - [ ] R1 反序列化器是否下沉到 framework?
  - [ ] R2 `List<Long>` 支持优先级确认
  - [ ] R3 嵌套字段方案(自动递归 vs 新注解)选定
  - [ ] R4 String 路径兼容方案选定(双向兼容 vs codemod)
  - [ ] R5 Integer 决策表态
  - [ ] R6~R8 排期确认