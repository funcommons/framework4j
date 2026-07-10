# OpenID 注解数据类型扩展计划

## 1. 现状分析

### 1.1 当前支持的数据类型

**OpenID 注解当前实现支持以下数据类型：**

#### Spring MVC 参数绑定 (OpenIdFormatterFactory)
- ✅ `Long.class` - 包装类型
- ✅ `long.class` - 基本类型
- ❌ `Integer.class` - **不支持**
- ❌ `int.class` - **不支持**

#### FastJson2 序列化 (OpenIdAnnotationFilter)
- ✅ `Long` - 单个值类型
- ✅ `List<Long>` - 列表类型（通过检查第一个元素类型推断）
- ❌ `Integer` - **不支持**
- ❌ `List<Integer>` - **不支持**
- ❌ `Set<Long>`, `Set<Integer>` - **不支持**
- ❌ `long[]`, `int[]`, `Long[]`, `Integer[]` - **不支持**

#### Jackson 序列化 (OpenIdJsonSerializer & OpenIdJsonDeserializer)
- ✅ `Long` - 单个值类型（专门为 Long 设计的序列化器）
- ❌ `Integer` - **不支持**
- ❌ 所有集合和数组类型 - **不支持**

#### Swagger 文档支持 (OpenIdSwaggerConfig & OpenIdSwaggerModelConfig)
- ✅ **支持文档显示**：已经完整支持 `Long`, `Integer`, `List`, `Array` 的文档类型修正
- ✅ **集合类型文档**：`OpenIdSwaggerModelConfig` 已经支持集合/数组类型的 Swagger 文档修正
- ⚠️ **实现与功能不匹配**：文档显示支持但实际转换不支持

### 1.2 现状限制

1. **Integer/int 类型缺失**：
   - `OpenIdFormatterFactory` 只注册了 `Long.class` 和 `long.class`
   - `OpenIdJsonSerializer` 只继承 `JsonSerializer<Long>`
   - `OpenIdJsonDeserializer` 只返回 `Long` 类型

2. **集合类型支持不完整**：
   - `OpenIdAnnotationFilter` 只支持 `List<Long>`，且检查方式简单（只检查第一个元素）
   - Jackson 完全不支持集合类型转换
   - Spring MVC 参数绑定不支持集合类型

3. **架构分散**：
   - 缺乏统一的类型处理工具类
   - 各组件独立实现，没有共享逻辑
   - 类型检查和转换逻辑重复

4. **负数处理不完善**：
   - `OpenIdFormatterFactory` 支持负数但仅限兼容性处理
   - 缺乏统一的负数验证机制

## 2. 需求扩展

### 2.1 目标支持的数据类型

根据需求，需要支持以下数据类型：

#### 基本类型
- ✅ `Long` - 已支持
- ✅ `long` - 已支持
- 🆕 `Integer` - 新增支持
- 🆕 `int` - 新增支持

#### 集合类型
- ✅ `List<Long>` - 已支持
- 🆕 `List<Integer>` - 新增支持
- 🆕 `Set<Long>` - 新增支持
- 🆕 `Set<Integer>` - 新增支持
- 🆕 `long[]` - 新增支持
- 🆕 `Long[]` - 新增支持
- 🆕 `int[]` - 新增支持
- 🆕 `Integer[]` - 新增支持

### 2.2 约束条件

- ❌ **不支持负数**：所有 ID 类型必须为非负数
- ❌ **不支持其他类型**：如 String、Double、Float 等
- ✅ **空值处理**：null 值应正常处理，不进行转换

## 3. 差异分析

### 3.1 Integer 类型支持差异

**现状**：
- `OpenIdFormatterFactory.getFieldTypes()` 只返回 `{Long.class, long.class}`
- `OpenIdJsonSerializer` 继承 `JsonSerializer<Long>`，硬编码只支持 Long
- `OpenIdJsonDeserializer` 返回类型固定为 `Long`
- Integer 类型字段会被完全忽略，不进行 OpenID 转换

**目标**：
- 支持 `Integer` 和 `int` 类型的 OpenID 转换
- Integer 类型的值范围：0 到 2,147,483,647
- 所有组件都要支持 Integer 类型

**影响**：
- 需要修改所有转换组件的类型注册和处理逻辑
- 需要处理 Integer 范围限制和溢出检查
- 需要创建通用的序列化器/反序列化器

### 3.2 集合类型支持差异

**现状**：
```java
// OpenIdAnnotationFilter 只支持 List<Long>，且检查方式简单
if (value instanceof List) {
    List<?> list = (List<?>) value;
    if (list.isEmpty() || list.get(0) instanceof Long) {
        return list.stream().map(item -> IdObfuscator.toOpenId((Long) item))
    }
}
// Jackson 完全不支持集合类型
// Spring MVC Formatter 不支持集合类型
```

**目标**：
```java
// 支持多种集合类型，且精确类型检查
支持：
- List<Long>, List<Integer>
- Set<Long>, Set<Integer>
- long[], Long[], int[], Integer[]
- 所有组件都要支持这些类型
```

### 3.3 架构差异

**现状**：
- 各组件独立实现，逻辑分散
- 没有统一的类型处理工具类
- 类型检查和转换逻辑重复
- 配置类分散，缺乏统一管理

**目标**：
- 创建统一的 `OpenIdTypeUtils` 工具类
- 所有组件共享类型处理逻辑
- 统一的配置管理
- 更好的可测试性和可维护性

### 3.4 Swagger 支持差异

**现状**：
- `OpenIdSwaggerConfig` 和 `OpenIdSwaggerModelConfig` 已经支持文档显示
- 文档显示支持各种类型但实际转换不支持
- 存在功能与文档不匹配的问题

**目标**：
- 保持 Swagger 文档支持的完整性
- 确保实际转换功能与文档描述一致

## 4. 实现方案

### 4.1 核心工具类扩展

#### 4.1.1 创建 OpenIdTypeUtils 工具类
```java
public final class OpenIdTypeUtils {

    // 支持的单一ID类型
    private static final Set<Class<?>> SUPPORTED_SINGLE_TYPES = Set.of(
        Long.class, long.class,
        Integer.class, int.class
    );

    // 支持的集合类型
    private static final Set<Class<?>> SUPPORTED_COLLECTION_TYPES = Set.of(
        List.class, Set.class
    );

    // 支持的数组类型
    private static final Set<Class<?>> SUPPORTED_ARRAY_TYPES = Set.of(
        long[].class, Long[].class,
        int[].class, Integer[].class
    );

    // 类型检查方法
    // 类型转换方法
    // 集合类型安全处理
    // 负数验证
}
```

### 4.2 OpenIdFormatterFactory 扩展

#### 4.2.1 扩展支持的字段类型
```java
@Override
public Set<Class<?>> getFieldTypes() {
    Set<Class<?>> types = new HashSet<>();
    // 现有支持
    types.add(Long.class);
    types.add(long.class);
    // 新增支持
    types.add(Integer.class);
    types.add(int.class);
    return Collections.unmodifiableSet(types);
}
```

#### 4.2.2 修改 Parser 逻辑
```java
@Override
public Parser<?> getParser(OpenId annotation, Class<?> fieldType) {
    return (text, locale) -> {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        // 纯数字兼容性检查
        if (text.matches("^-?\\d+$")) {
            long numericValue = Long.parseLong(text);
            // 负数检查
            if (numericValue < 0) {
                throw new ParseException("ID cannot be negative: " + text, 0);
            }
            // 根据目标类型返回相应值
            if (fieldType == Integer.class || fieldType == int.class) {
                if (numericValue > Integer.MAX_VALUE) {
                    throw new ParseException("Value too large for Integer: " + text, 0);
                }
                return (int) numericValue;
            }
            return numericValue;
        }

        // OpenID 还原
        long decodedValue = IdObfuscator.fromOpenId(text);
        // 负数检查
        if (decodedValue < 0) {
            throw new ParseException("Decoded ID cannot be negative", 0);
        }

        // 根据目标类型转换
        if (fieldType == Integer.class || fieldType == int.class) {
            if (decodedValue > Integer.MAX_VALUE) {
                throw new ParseException("Decoded value too large for Integer", 0);
            }
            return (int) decodedValue;
        }
        return decodedValue;
    };
}
```

### 4.3 FastJson2 过滤器扩展

#### 4.3.1 重构 OpenIdAnnotationFilter
```java
public class OpenIdAnnotationFilter implements ContextValueFilter {

    @Override
    public Object process(BeanContext context, Object object, String name, Object value) {
        if (value == null) {
            return null;
        }

        OpenId annotation = context.getAnnotation(OpenId.class);
        if (annotation == null) {
            return value;
        }

        // 使用工具类进行统一处理
        return OpenIdTypeUtils.convertToOpenId(value);
    }
}
```

#### 4.3.2 OpenIdTypeUtils 转换方法
```java
public static Object convertToOpenId(Object value) {
    // 单一值类型
    if (isSupportedSingleType(value.getClass())) {
        return convertSingleToOpenId(value);
    }

    // List 类型
    if (value instanceof List) {
        return convertListToOpenId((List<?>) value);
    }

    // Set 类型
    if (value instanceof Set) {
        return convertSetToOpenId((Set<?>) value);
    }

    // 数组类型
    if (value.getClass().isArray()) {
        return convertArrayToOpenId(value);
    }

    return value;
}
```

### 4.4 Jackson 序列化器扩展

#### 4.4.1 创建通用 OpenIdJsonSerializer
```java
public class OpenIdJsonSerializer extends JsonSerializer<Object> {

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        Object converted = OpenIdTypeUtils.convertToOpenId(value);
        gen.writeObject(converted);
    }

    @Override
    public Class<?> handledType() {
        return Object.class; // 处理所有支持的类型
    }
}
```

### 4.5 Swagger 文档类型修正

#### 4.5.1 扩展 OpenIdSwaggerConfig
```java
private void modifyOpenIdFields(List<ResolvedMethodParameter> parameters) {
    parameters.stream()
        .filter(p -> p.hasParameterAnnotation(OpenId.class))
        .forEach(p -> {
            Class<?> parameterType = p.getParameter().getType();
            // 根据实际类型设置文档类型
            if (OpenIdTypeUtils.isSupportedSingleType(parameterType) ||
                OpenIdTypeUtils.isSupportedCollectionType(parameterType)) {
                p.setParameterModel(new RequestParameterBuilder()
                    .name(p.getParameterName())
                    .in(ParameterIn.PATH.toString())
                    .query(q -> q.style(ParameterStyle.SIMPLE))
                    .required(true)
                    .schema(new SchemaModel().type("string").format("open-id"))
                    .build());
            }
        });
}
```

## 5. 实现计划

### 5.1 第一阶段：核心工具类

**任务**：
1. 创建 `OpenIdTypeUtils` 工具类
2. 实现类型检查、转换、验证方法
3. 添加完整的单元测试

**文件**：
- `ldx2t-commons-core/src/main/java/com/ldx2t/commons/openid/util/OpenIdTypeUtils.java`
- `ldx2t-commons-core/src/test/java/com/ldx2t/commons/openid/util/OpenIdTypeUtilsTest.java`

### 5.2 第二阶段：Spring MVC 集成

**任务**：
1. 扩展 `OpenIdFormatterFactory` 支持 Integer/int
2. 增强类型安全和负数检查
3. 添加参数验证

**文件**：
- `ldx2t-commons-core/src/main/java/com/ldx2t/commons/openid/formatter/OpenIdFormatterFactory.java`

### 5.3 第三阶段：FastJson2 集成

**任务**：
1. 重构 `OpenIdAnnotationFilter` 使用新的工具类
2. 支持所有目标数据类型
3. 优化性能和类型安全

**文件**：
- `ldx2t-commons-core/src/main/java/com/ldx2t/commons/openid/fastjson2/OpenIdAnnotationFilter.java`

### 5.4 第四阶段：Jackson 集成

**任务**：
1. 创建通用的 `OpenIdJsonSerializer`
2. 配置支持多种数据类型
3. 更新配置类

**文件**：
- `ldx2t-commons-core/src/main/java/com/ldx2t/commons/openid/jackson/OpenIdJsonSerializer.java`
- `ldx2t-commons-core/src/main/java/com/ldx2t/commons/openid/config/JacksonConfig.java`

### 5.5 第五阶段：Swagger 集成

**任务**：
1. 扩展 Swagger 配置支持新类型
2. 修正文档类型显示
3. 添加示例说明

**文件**：
- `ldx2t-commons-core/src/main/java/com/ldx2t/commons/openid/config/OpenIdSwaggerConfig.java`

### 5.6 第六阶段：综合测试

**任务**：
1. 创建完整的集成测试
2. 性能测试和优化
3. 兼容性测试

**文件**：
- `ldx2t-commons-core/src/test/java/com/ldx2t/commons/openid/AllTypesSupportTest.java`
- `ldx2t-commons-core/src/test/java/com/ldx2t/commons/openid/PerformanceTest.java`

## 6. 兼容性保证

### 6.1 向后兼容

- 现有的 `@OpenId Long` 用法保持不变
- API 接口不需要修改
- 配置文件无需更改

### 6.2 升级路径

1. **代码兼容**：现有代码无需修改
2. **新功能**：开发者可以直接使用新支持的数据类型
3. **渐进式采用**：可以逐步将 Integer ID 类型添加 `@OpenId` 注解

## 7. 风险评估

### 7.1 技术风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| Integer 类型溢出 | 高 | 中 | 严格的范围检查和异常处理 |
| 集合类型转换性能 | 中 | 低 | 优化类型检查算法 |
| 类型安全性 | 高 | 低 | 完整的单元测试覆盖 |
| 负数处理 | 中 | 中 | 输入验证和友好错误提示 |

### 7.2 兼容性风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| 现有 API 破坏 | 高 | 低 | 保持现有 API 不变 |
| 序列化格式变化 | 中 | 低 | 保持输出格式一致 |
| 配置冲突 | 低 | 低 | 默认配置保持不变 |

## 8. 测试策略

### 8.1 单元测试

```java
@ParameterizedTest
@ValueSource(classes = {Long.class, long.class, Integer.class, int.class})
void shouldSupportSingleTypes(Class<?> type) { ... }

@ParameterizedTest
@MethodSource("provideCollectionTypes")
void shouldSupportCollectionTypes(Class<?> collectionType, Class<?> elementType) { ... }

@Test
void shouldRejectNegativeValues() { ... }

@Test
void shouldHandleNullValues() { ... }
```

### 8.2 集成测试

```java
@SpringBootTest
@AutoConfigureTestDatabase
class OpenIdIntegrationTest {

    @Test
    void shouldWorkInControllerWithAllTypes() { ... }

    @Test
    void shouldWorkWithFastJson2Serialization() { ... }

    @Test
    void shouldWorkWithJacksonSerialization() { ... }
}
```

### 8.3 性能测试

- 大量数据转换性能测试
- 集合类型处理性能对比
- 内存使用情况监控

## 9. 文档更新

### 9.1 API 文档

更新以下文档：
- `OpenID 模块使用指南.md`
- `分布式 ID 安全混淆 (OpenID) 技术方案.md`

### 9.2 示例代码

```java
// 单一类型支持
@OpenId
private Long userId;

@OpenId
private Integer orderId;

// 集合类型支持
@OpenId
private List<Long> userIds;

@OpenId
private Set<Integer> orderIds;

@OpenId
private long[] userIds;

@OpenId
private int[] orderIds;
```

### 9.3 Controller 示例

```java
@RestController
public class UserController {

    @GetMapping("/users/{userId}")
    public User getUser(@OpenId Long userId) { ... }

    @GetMapping("/orders/{orderId}")
    public Order getOrder(@OpenId Integer orderId) { ... }

    @PostMapping("/users/batch")
    public List<User> getUsers(@RequestBody @OpenId List<Long> userIds) { ... }

    @PostMapping("/orders/batch")
    public Set<Order> getOrders(@RequestBody @OpenId Set<Integer> orderIds) { ... }
}
```

## 10. 总结

通过本次扩展，OpenID 注解将支持更丰富的数据类型，提供更好的开发体验。主要改进包括：

1. **完整的数字类型支持**：Long/long 和 Integer/int
2. **丰富的集合类型支持**：List、Set、数组
3. **更强的类型安全性**：严格的类型检查和负数验证
4. **更好的兼容性**：保持现有 API 不变
5. **完善的测试覆盖**：确保功能正确性和性能

这个计划将显著提升 OpenID 模块的实用性和易用性，同时保持系统的稳定性和安全性。

---

## 11. 基于实际代码分析的更新版本

### 11.1 实际架构分析

通过阅读所有 OpenID 相关的 Java 文件，发现以下关键信息：

#### 11.1.1 现有架构组件

**注解层**：
- `@OpenId` - 核心注解，支持 `@Target({ElementType.PARAMETER, ElementType.FIELD})`

**配置层**（已自动注册）：
- `WebConfig` - 统一 Web 配置（包含 FastJson2 + OpenId Formatter + DateTime 支持）
- `JacksonConfig` - 配置 Jackson 注解拦截器（可选）
- `OpenIdSwaggerConfig` - Swagger 入参文档适配
- `OpenIdSwaggerModelConfig` - Swagger 出参模型适配

**处理层**：
- `OpenIdFormatterFactory` - Spring MVC 参数绑定
- `OpenIdAnnotationFilter` - FastJson2 序列化过滤
- `OpenIdJsonSerializer` & `OpenIdJsonDeserializer` - Jackson 序列化/反序列化（已删除）

#### 11.1.2 现有功能优势

1. **完善的自动配置**：所有组件都通过 Spring Boot AutoConfiguration 自动注册
2. **多 JSON 库支持**：同时支持 Jackson 和 FastJson2
3. **完整的 Swagger 集成**：文档显示功能已经完善
4. **良好的架构分层**：注解、配置、处理分离

#### 11.1.3 主要缺陷

1. **类型支持有限**：
   ```java
   // OpenIdFormatterFactory.java:25-30
   @Override
   public Set<Class<?>> getFieldTypes() {
       Set<Class<?>> types = new HashSet<>();
       types.add(Long.class);
       types.add(long.class);
       return Collections.unmodifiableSet(types);  // 只有 Long/long
   }
   ```

2. **Jackson 组件硬编码类型**：
   ```java
   // OpenIdJsonSerializer.java:17
   public class OpenIdJsonSerializer extends JsonSerializer<Long> {
       // 硬编码只支持 Long
   }

   // OpenIdJsonDeserializer.java:17
   public class OpenIdJsonDeserializer extends JsonDeserializer<Long> {
       // 硬编码只返回 Long
   }
   ```

3. **FastJson2 集合处理简单**：
   ```java
   // OpenIdAnnotationFilter.java:43-44
   if (list.get(0) instanceof Long) {
       // 只检查第一个元素，不够安全
   }
   ```

### 11.2 更新的实现方案

#### 11.2.1 创建统一工具类

**文件**：`src/main/java/com/ldx2t/commons/openid/util/OpenIdTypeUtils.java`

```java
package com.ldx2t.commons.openid.util;

import com.ldx2t.commons.id.util.IdObfuscator;
import java.text.ParseException;
import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OpenID 类型处理工具类
 * <p>
 * 统一处理所有 OpenID 相关的类型检查、转换和验证逻辑
 */
public final class OpenIdTypeUtils {

    // 支持的单一ID类型
    private static final Set<Class<?>> SUPPORTED_SINGLE_TYPES = Set.of(
        Long.class, long.class,
        Integer.class, int.class
    );

    // 支持的集合类型
    private static final Set<Class<?>> SUPPORTED_COLLECTION_TYPES = Set.of(
        List.class, Set.class
    );

    // 支持的数组类型
    private static final Map<Class<?>, Class<?>> ARRAY_TYPE_MAP = Map.of(
        long[].class, Long.class,
        Long[].class, Long.class,
        int[].class, Integer.class,
        Integer[].class, Integer.class
    );

    private OpenIdTypeUtils() {}

    /**
     * 检查是否为支持的单一类型
     */
    public static boolean isSupportedSingleType(Class<?> type) {
        return SUPPORTED_SINGLE_TYPES.contains(type);
    }

    /**
     * 检查是否为支持的集合类型
     */
    public static boolean isSupportedCollectionType(Class<?> type) {
        return SUPPORTED_COLLECTION_TYPES.contains(type);
    }

    /**
     * 检查是否为支持的数组类型
     */
    public static boolean isSupportedArrayType(Class<?> type) {
        return ARRAY_TYPE_MAP.containsKey(type);
    }

    /**
     * 获取数组元素的类型
     */
    public static Class<?> getArrayElementType(Class<?> arrayType) {
        return ARRAY_TYPE_MAP.get(arrayType);
    }

    /**
     * 验证值是否为有效ID（非负数检查）
     */
    public static void validateId(long value) throws IllegalArgumentException {
        if (value < 0) {
            throw new IllegalArgumentException("ID cannot be negative: " + value);
        }
    }

    /**
     * 转换为OpenID格式（序列化）
     */
    public static Object convertToOpenId(Object value) {
        if (value == null) {
            return null;
        }

        Class<?> valueType = value.getClass();

        // 单一值类型
        if (isSupportedSingleType(valueType)) {
            return convertSingleToOpenId(value);
        }

        // List 类型
        if (value instanceof List) {
            return convertListToOpenId((List<?>) value);
        }

        // Set 类型
        if (value instanceof Set) {
            return convertSetToOpenId((Set<?>) value);
        }

        // 数组类型
        if (valueType.isArray()) {
            return convertArrayToOpenId(value);
        }

        return value;
    }

    /**
     * 从OpenID格式转换（反序列化）
     */
    public static Object convertFromOpenId(String text, Class<?> targetType) throws ParseException {
        if (text == null || text.isEmpty()) {
            return null;
        }

        long decodedValue;
        if (text.matches("^-?\\d+$")) {
            // 纯数字兼容性
            decodedValue = Long.parseLong(text);
        } else {
            // OpenID 解码
            decodedValue = IdObfuscator.fromOpenId(text);
        }

        // 负数检查
        validateId(decodedValue);

        // 根据目标类型转换
        if (targetType == Integer.class || targetType == int.class) {
            if (decodedValue > Integer.MAX_VALUE) {
                throw new ParseException("Value too large for Integer: " + decodedValue, 0);
            }
            return (int) decodedValue;
        }

        return decodedValue;
    }

    // 私有转换方法
    private static String convertSingleToOpenId(Object value) {
        long longValue = ((Number) value).longValue();
        validateId(longValue);
        return IdObfuscator.toOpenId(longValue);
    }

    @SuppressWarnings("unchecked")
    private static List<String> convertListToOpenId(List<?> list) {
        if (list.isEmpty()) {
            return (List<String>) list;
        }
        return list.stream()
                .map(OpenIdTypeUtils::convertSingleToOpenId)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static Set<String> convertSetToOpenId(Set<?> set) {
        if (set.isEmpty()) {
            return (Set<String>) set;
        }
        return set.stream()
                .map(OpenIdTypeUtils::convertSingleToOpenId)
                .collect(Collectors.toSet());
    }

    private static Object convertArrayToOpenId(Object array) {
        int length = Array.getLength(array);
        if (length == 0) {
            return array;
        }

        Class<?> componentType = array.getClass().getComponentType();
        Class<?> elementType = getArrayElementType(array.getClass());

        if (elementType == null) {
            return array; // 不支持的数组类型
        }

        // 创建字符串数组
        String[] result = new String[length];
        for (int i = 0; i < length; i++) {
            Object element = Array.get(array, i);
            if (element != null) {
                result[i] = convertSingleToOpenId(element);
            }
        }
        return result;
    }
}
```

#### 11.2.2 更新现有组件

**1. 更新 OpenIdFormatterFactory**
```java
// 在现有文件基础上修改
@Override
public Set<Class<?>> getFieldTypes() {
    Set<Class<?>> types = new HashSet<>();
    types.add(Long.class);
    types.add(long.class);
    // 新增支持
    types.add(Integer.class);
    types.add(int.class);
    return Collections.unmodifiableSet(types);
}

@Override
public Parser<?> getParser(OpenId annotation, Class<?> fieldType) {
    return (text, locale) -> OpenIdTypeUtils.convertFromOpenId(text, fieldType);
}
```

**2. 更新 OpenIdAnnotationFilter**
```java
// 在现有文件基础上修改
@Override
public Object process(BeanContext context, Object object, String name, Object value) {
    if (value == null) {
        return null;
    }

    OpenId annotation = context.getAnnotation(OpenId.class);
    if (annotation == null) {
        return value;
    }

    // 使用统一工具类
    return OpenIdTypeUtils.convertToOpenId(value);
}
```

**3. 创建通用 Jackson 序列化器**
```java
// 替换现有的专用序列化器
public class OpenIdJsonSerializer extends JsonSerializer<Object> {
    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        Object converted = OpenIdTypeUtils.convertToOpenId(value);
        gen.writeObject(converted);
    }
}

public class OpenIdJsonDeserializer extends JsonDeserializer<Object> {
    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text == null || text.isEmpty()) {
            return null;
        }

        Class<?> targetType = ctxt.getContextualType().getRawClass();
        try {
            return OpenIdTypeUtils.convertFromOpenId(text, targetType);
        } catch (ParseException e) {
            throw new IOException("Invalid OpenID format: " + text, e);
        }
    }
}
```

### 11.3 配置更新

**无需修改的配置**：
- `OpenIdSwaggerConfig` - 已完善支持
- `OpenIdSwaggerModelConfig` - 已完善支持
- `WebConfig` - 统一 Web 配置，会自动使用更新后的 Formatter

**自动配置更新**：
```java
// 可选：创建统一配置类
@AutoConfiguration
@ConditionalOnProperty(name = "ldx2t.commons.openid.enabled", matchIfMissing = true)
public class OpenIdAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenIdTypeUtils openIdTypeUtils() {
        return new OpenIdTypeUtils();
    }
}
```

### 11.4 测试策略更新

#### 11.4.1 单元测试结构
```java
@ExtendWith(MockitoExtension.class)
class OpenIdTypeUtilsTest {

    @ParameterizedTest
    @ValueSource(classes = {Long.class, long.class, Integer.class, int.class})
    void shouldSupportSingleTypes(Class<?> type) { ... }

    @ParameterizedTest
    @MethodSource("provideCollectionTestData")
    void shouldConvertCollections(Object input, Object expected) { ... }

    @Test
    void shouldRejectNegativeValues() { ... }

    @Test
    void shouldHandleEdgeCases() { ... }
}
```

#### 11.4.2 集成测试结构
```java
@SpringBootTest
class OpenIdIntegrationTest {

    @Test
    void shouldWorkInControllerWithAllTypes() { ... }

    @Test
    void shouldWorkWithFastJson2() { ... }

    @Test
    void shouldWorkWithJackson() { ... }

    @Test
    void shouldGenerateCorrectSwaggerDocs() { ... }
}
```

### 11.5 实施优先级

**高优先级**（核心功能）：
1. 创建 `OpenIdTypeUtils` 工具类
2. 更新 `OpenIdFormatterFactory` 支持 Integer/int
3. 更新 `OpenIdAnnotationFilter` 支持所有类型

**中优先级**（完整支持）：
4. ~~重构 Jackson 序列化器/反序列化器~~（已删除）

**低优先级**（优化和测试）：
5. 完善单元测试和集成测试
6. 性能优化
8. 文档更新

### 11.6 关键优势

1. **最小侵入性**：基于现有架构，最大化复用现有代码
2. **统一处理**：所有类型转换逻辑集中在一个工具类
3. **向后兼容**：现有 `@OpenId Long` 用法完全不变
4. **配置继承**：现有自动配置和 Swagger 支持继续有效
5. **渐进升级**：可以逐步添加对现有 Integer/Collection 字段的支持

这个更新方案充分考虑了现有代码的实际情况，以最小的改动实现最大化的功能扩展。