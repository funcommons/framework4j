# **分布式 ID 安全混淆 (OpenID) 技术方案**

**版本**: v1.0.0

**状态**: \[已实现\]

**维护者**: 基础架构组

**当前实现版本**: v1.0.0-SNAPSHOT

## **1\. 背景与目标**

在分布式系统中，后端普遍采用 **Snowflake (雪花算法)** 生成 19 位 Long 类型的全局唯一 ID。直接将此 ID 暴露给前端存在以下问题：

1. **精度丢失**: JavaScript 的 Number 类型最大安全整数为 $2^{53}-1$，无法精确表示 19 位的 Java Long，导致最后几位变为 0。  
2. **安全隐患**: 连续或有规律的 ID 容易被竞争对手爬取，推算出业务增长量（如订单量、用户量）。  
3. **用户体验**: 纯数字 ID 在 URL 或分享链接中缺乏辨识度。

本方案目标:  
建立一套对业务代码低侵入的机制，实现：

* **后端 (Java)**: 全程使用 Long 类型，享受强类型和高性能索引的优势。  
* **前端/API**: 全程使用 String 类型 (OpenID)，解决精度问题并隐藏真实 ID。  
* **文档**: Swagger/OpenAPI 自动适配，所见即所得。

## **2\. 总体架构**

本方案采用 **"注解驱动 \+ AOP 切面"** 的设计思想，通过单一注解 @OpenId 打通 Spring MVC、JSON 序列化器和 Swagger 文档。

### **2.1 核心流程图**

sequenceDiagram  
    participant C as 前端/客户端  
    participant S as Spring MVC (Formatter)  
    participant Ctrl as Controller/Service  
    participant J as FastJson2 (Filter)  
    participant D as Swagger/OpenAPI

    Note over C,S: 1\. 入参阶段 (String \-\> Long)  
    C-\>\>S: GET /users/ORD\_Xy7Z (OpenID)  
    S-\>\>S: 扫描 @OpenId 注解  
    S-\>\>S: 调用 IdObfuscator.fromOpenId()  
    S-\>\>Ctrl: 注入 Long id \= 12345

    Note over Ctrl: 2\. 业务处理 (全程 Long)  
    Ctrl-\>\>Ctrl: 数据库查询 (Use BigInt Index)  
      
    Note over Ctrl,J: 3\. 出参阶段 (Long \-\> String)  
    Ctrl-\>\>J: 返回 UserDTO (id=12345)  
    J-\>\>J: 扫描 @OpenId 注解  
    J-\>\>J: 调用 IdObfuscator.toOpenId()  
    J--\>\>C: JSON { "id": "ORD\_Xy7Z" }

    Note over D: 4\. 文档生成  
    D-\>\>D: 扫描 @OpenId  
    D-\>\>C: 文档显示: id (string), example: "ORD\_Xy7Z"

## **3\. 详细设计与实现**

### **3.1 核心注解 @OpenId**

这是方案的唯一入口，用于标记需要混淆的字段或参数。

@Target({ElementType.PARAMETER, ElementType.FIELD})  
@Retention(RetentionPolicy.RUNTIME)  
@Documented  
public @interface OpenId {  
}

### **3.2 入参自动转换 (Spring MVC)**

利用 AnnotationFormatterFactory 实现 Controller 参数的自动解密。

* **组件**: OpenIdFormatterFactory  
* **逻辑**: 拦截带有 @OpenId 的 String 参数，自动转换为 Long。  
* **容错**: 如果传入的是纯数字（兼容旧接口），则直接解析为 Long；如果是 OpenID 字符串，则进行反解。

### **3.3 出参自动混淆 (FastJson2)**

利用 FastJson2 的 ContextValueFilter 实现 DTO 序列化时的自动加密。

* **组件**: OpenIdAnnotationFilter  
* **逻辑**:  
  1. 拦截 JSON 序列化过程。  
  2. 检查字段上是否有 @OpenId。  
  3. 如果是 Long，转为 OpenID 字符串。  
  4. 如果是 List\<Long\>，遍历转换为 OpenID 字符串数组。  
* **优势**: 相比 serializeUsing，此方式无需为 List 单独指定 Serializer，且代码更整洁。

### **3.4 文档自动修正 (Swagger/SpringDoc)**

利用 Swagger 的 ModelConverter 和 OperationCustomizer 修正文档类型。

* **组件**: OpenIdSwaggerModelConfig  
* **逻辑**:  
  1. 扫描 DTO 和 Controller 参数。  
  2. 发现 @OpenId 时，将 Schema 类型从 integer(int64) 强制修改为 string。  
  3. 注入示例值 ORD\_Xy7Z... 和描述说明。

## **4\. 接入指南 (开发人员必读)**

### **4.1 场景一：DTO/VO 定义 (最常用)**

在返回给前端的对象中，直接在 Long 类型的 ID 字段上添加 @OpenId。

@Data  
public class OrderVO {  
    // ✅ 推荐: 自动混淆为 "id": "ORD\_Xy7Z..."  
    @OpenId  
    private Long id;

    // ✅ 支持 List: 自动混淆为 "tenantIds": \["TEN\_Ab...", "TEN\_Cd..."\]  
    @OpenId  
    private List\<Long\> tenantIds;

    // ❌ 普通字段: 不加注解，输出原始数字 (如果前端需要计算)  
    private Long createTime;  
}

### **4.2 场景二：Controller 参数接收**

在接收前端传来的 ID 时，添加 @OpenId 实现自动还原。

@RestController  
@RequestMapping("/orders")  
public class OrderController {

    // ✅ 路径参数: 自动将 URL 中的 "ORD\_Xy..." 还原为 12345  
    @GetMapping("/{id}")  
    public OrderVO getOrder(@OpenId @PathVariable Long id) {  
        return service.getById(id);  
    }

    // ✅ 查询参数: 自动还原  
    @GetMapping  
    public OrderVO findByOid(@OpenId @RequestParam("oid") Long orderId) {  
        return service.getById(orderId);  
    }  
}

### **4.3 场景三：手动转换 (极少使用)**

在某些特殊场景（如构建第三方回调 URL、拼接内部消息体）需要手动转换时：

// 加密  
String openId \= IdObfuscator.toOpenId(12345L);

// 解密  
Long id \= IdObfuscator.fromOpenId("ORD\_Xy7Z...");

## **5\. 常见问题 (Q\&A)**

Q1: 数据库里存的是什么？  
A: 数据库里永远存 BIGINT (Long)。严禁在数据库存 OpenID 字符串，这会导致索引失效和 JOIN 性能下降。  
Q2: 前端传了纯数字怎么办？  
A: 入参转换器做了兼容处理。如果前端传 12345，后端会直接解析为 12345L；如果传 ORD\_Xy...，则解析为对应的 Long。这保证了新老接口的平滑过渡。  
Q3: 为什么不全局开启 Long 转 String？  
A: 全局开启会导致 createTime (时间戳)、count (数量)、money (金额，如果用分存储) 等本该是数字的字段变成字符串，破坏前端的计算逻辑和图表展示。@OpenId 实现了按需混淆，是最安全的策略。  
Q4: MyBatis 层需要做处理吗？  
A: 不需要。MyBatis (DAO层) 负责与数据库交互，应保持原生 Long 类型。混淆是表现层 (Controller/API) 的职责，不要让混淆逻辑污染业务层和服务层。

## **6\. 依赖配置**

确保项目 pom.xml 引入了以下依赖：

\<dependencies\>  
    \<\!-- 基础工具包 (含 IdObfuscator) \--\>  
    \<dependency\>  
        \<groupId\>com.ldx2t\</groupId\>  
        \<artifactId\>ldx2t-commons-id\</artifactId\>  
        \<version\>${latest.version}\</version\>  
    \</dependency\>

    \<\!-- JSON 序列化 (FastJson2) \--\>  
    \<dependency\>  
        \<groupId\>com.alibaba.fastjson2\</groupId\>  
        \<artifactId\>fastjson2-extension-spring6\</artifactId\>  
    \</dependency\>  
\</dependencies\>

并在 application.yml 中开启 FastJson 支持：

ldx2t:  
  commons:  
    id:  
      fastjson:  
        enabled: true  
