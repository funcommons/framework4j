package fun.commons.framework4j.openid;

import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.core.config.WebConfig;
import fun.commons.framework4j.openid.config.OpenIdSwaggerConfig;
import fun.commons.framework4j.openid.config.OpenIdSwaggerModelConfig;

import fun.commons.framework4j.id.config.IdSdkAutoConfiguration;
import fun.commons.framework4j.id.util.IdObfuscator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OpenID Web 全链路集成测试
 * <p>
 * 目的:
 * 验证在真实的 Spring Web 环境下，参数的自动解密(Formatter)和结果的自动混淆(FastJson2 Filter)是否生效。
 * 同时验证 Swagger 文档是否正确修正了类型。
 * <p>
 * 测试流程:
 * HTTP Request (String) -> Spring MVC (Formatter) -> Controller (Long) -> DTO (Long) -> FastJson2 (Filter) -> HTTP Response (String)
 */
@SpringBootTest(classes = OpenIdWebIntegrationTest.TestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test") // 确保 application-test.yml 生效 (fastjson.enabled=true)
@DisplayName("OpenID Web 集成测试")
class OpenIdWebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 测试场景 1: @PathVariable 自动解密
     * URL: /api/test/path/{id}
     */
    @Test
    @DisplayName("入参: @PathVariable 应自动解密")
    void shouldDeserializePathVariable() throws Exception {
        long originalId = 123456789L;
        String openId = IdObfuscator.toOpenId(originalId);

        // 发送: /api/test/path/ORD_...
        mockMvc.perform(get("/com/ldx2t/commons/api/test/path/{id}", openId))
                .andDo(print())
                .andExpect(status().isOk())
                // 验证: Controller 返回了 "Received: 123456789"，说明它成功拿到了解密后的 Long
                .andExpect(content().string("Received: " + originalId));
    }

    /**
     * 测试场景 2: @RequestParam 自动解密
     * URL: /api/test/param?id={id}
     */
    @Test
    @DisplayName("入参: @RequestParam 应自动解密")
    void shouldDeserializeRequestParam() throws Exception {
        long originalId = 987654321L;
        String openId = IdObfuscator.toOpenId(originalId);

        // 发送: /api/test/param?id=ORD_...
        mockMvc.perform(get("/com/ldx2t/commons/api/test/param").param("id", openId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("Received: " + originalId));
    }

    /**
     * 测试场景 3: 兼容纯数字入参
     * URL: /api/test/path/12345
     */
    @Test
    @DisplayName("入参: 纯数字应兼容解析")
    void shouldHandleRawNumberInput() throws Exception {
        long rawId = 10086L;

        // 发送: /api/test/path/10086
        mockMvc.perform(get("/com/ldx2t/commons/api/test/path/{id}", rawId))
                .andExpect(status().isOk())
                .andExpect(content().string("Received: " + rawId));
    }

    /**
     * 测试场景 4: JSON 响应自动混淆
     * URL: /api/test/json
     */
    @Test
    @DisplayName("出参: DTO @OpenId 字段应自动混淆")
    void shouldSerializeJsonWithObfuscation() throws Exception {
        long id = 55555L;
        String expectedOpenId = IdObfuscator.toOpenId(id);

        mockMvc.perform(get("/com/ldx2t/commons/api/test/json").param("id", String.valueOf(id)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // 验证: JSON 中的 id 字段是 String 类型的 OpenID
                .andExpect(jsonPath("$.id").value(expectedOpenId))
                // 验证: 未加 @OpenId 的 rawId 字段保持为数字
                .andExpect(jsonPath("$.rawId").value(id));
    }

    /**
     * 测试场景 5: List<Long> 集合自动混淆
     * URL: /api/test/list
     */
    @Test
    @DisplayName("出参: List<Long> @OpenId 字段应自动混淆")
    void shouldSerializeListWithObfuscation() throws Exception {
        long id1 = 111L;
        long id2 = 222L;
        String openId1 = IdObfuscator.toOpenId(id1);
        String openId2 = IdObfuscator.toOpenId(id2);

        mockMvc.perform(get("/com/ldx2t/commons/api/test/list")
                        .param("id1", String.valueOf(id1))
                        .param("id2", String.valueOf(id2)))
                .andDo(print())
                .andExpect(status().isOk())
                // 验证: ids 数组包含混淆后的字符串
                .andExpect(jsonPath("$.ids[0]").value(openId1))
                .andExpect(jsonPath("$.ids[1]").value(openId2));
    }

    /**
     * 测试场景 6: OpenAPI/Swagger 文档生成
     * URL: /v3/api-docs
     */
    @Test
    @DisplayName("文档: OpenAPI 描述应将 @OpenId 字段修正为 String")
    void shouldGenerateCorrectOpenApiDocs() throws Exception {
        System.out.println(mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // 1. 验证入参: path/{id} 的 id 参数类型应为 string (尽管 Java 是 Long)
                .andExpect(jsonPath("$.paths['/com/ldx2t/commons/api/test/path/{id}'].get.parameters[0].name").value("id"))
                .andExpect(jsonPath("$.paths['/com/ldx2t/commons/api/test/path/{id}'].get.parameters[0].schema.type").value("string"))

                // 2. 验证入参: param?id=... 的 id 参数类型应为 string
                .andExpect(jsonPath("$.paths['/com/ldx2t/commons/api/test/param'].get.parameters[0].name").value("id"))
                .andExpect(jsonPath("$.paths['/com/ldx2t/commons/api/test/param'].get.parameters[0].schema.type").value("string"))

                // 3. 验证出参: TestDTO 的 id 字段类型应为 string
                .andExpect(jsonPath("$.components.schemas.TestDTO.properties.id.type").value("string"))
                // 对照组: rawId 应该依然是 integer/int64
                .andExpect(jsonPath("$.components.schemas.TestDTO.properties.rawId.type").value("integer"))
                .andExpect(jsonPath("$.components.schemas.TestDTO.properties.rawId.format").value("int64"))

                // 4. 验证出参: TestListDTO 的 ids 列表元素类型应为 string
                .andExpect(jsonPath("$.components.schemas.TestListDTO.properties.ids.type").value("array"))
                .andExpect(jsonPath("$.components.schemas.TestListDTO.properties.ids.items.type").value("string"));
    }

    /**
     * 测试场景 7: OffsetDateTime 格式化
     * URL: /api/test/datetime
     */
    @Test
    @DisplayName("OffsetDateTime 应正确格式化为 ISO 8601")
    void shouldFormatOffsetDateTimeToIso8601() throws Exception {
        MvcResult result = mockMvc.perform(get("/com/ldx2t/commons/api/test/datetime"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        String jsonContent = result.getResponse().getContentAsString();
        System.out.println("OffsetDateTime JSON: " + jsonContent);

        // 验证包含 ISO 8601 格式的日期时间
        assertThat(jsonContent).contains("createAt");
        assertThat(jsonContent).contains("updateAt");

        // 验证格式：yyyy-MM-dd'T'HH:mm:ss.SSSXXX
        // 支持可变长度的毫秒部分（1-9位）
        assertThat(jsonContent).matches(".*\"createAt\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{1,9}[+-]\\d{2}:\\d{2}\".*");
        assertThat(jsonContent).matches(".*\"updateAt\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{1,9}[+-]\\d{2}:\\d{2}\".*");
    }

    /**
     * 测试场景 8: 固定时间 OffsetDateTime
     * URL: /api/test/fixed-datetime
     */
    @Test
    @DisplayName("固定时间 OffsetDateTime 应正确格式化")
    void shouldFormatFixedOffsetDateTime() throws Exception {
        MvcResult result = mockMvc.perform(get("/com/ldx2t/commons/api/test/fixed-datetime"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        String jsonContent = result.getResponse().getContentAsString();
        System.out.println("固定时间 JSON: " + jsonContent);

        // 验证包含指定的固定时间
        assertThat(jsonContent).contains("\"2024-01-01T12:00:00.123+08:00\"");
        assertThat(jsonContent).contains("\"2024-06-15T10:30:45.456-05:00\"");
    }

    // ==========================================
    // 测试配置与辅助类
    // ==========================================

    @SpringBootApplication
    @Import({
            IdSdkAutoConfiguration.class, // 核心 SDK
            WebConfig.class,              // Web 配置 (含 FastJson2 + OpenId Formatter)
            OpenIdSwaggerConfig.class,    // Swagger 入参适配
            OpenIdSwaggerModelConfig.class,// Swagger 模型适配
            TestController.class          // 必须显式导入inner class
    })
    static class TestConfig {
    }

    /**
     * 模拟业务 Controller
     */
    @RestController("openIdWebTestController")
    @RequestMapping("/com/ldx2t/commons/api/test")
    static class TestController {

        @GetMapping( value = "/path/{id}", produces = MediaType.TEXT_PLAIN_VALUE)
        public String testPath(@OpenId @PathVariable("id") Long id) {
            return "Received: " + id;
        }

        @GetMapping(value = "/param",produces = MediaType.TEXT_PLAIN_VALUE)
        public String testParam(@OpenId @RequestParam("id") Long id) {
            return "Received: " + id;
        }

        @GetMapping("/json")
        public TestDTO testJson(@RequestParam("id") Long id) {
            return new TestDTO(id, id);
        }

        @GetMapping("/list")
        public TestListDTO testList(@RequestParam("id1") Long id1, @RequestParam("id2") Long id2) {
            return new TestListDTO(Arrays.asList(id1, id2));
        }

        @GetMapping("/datetime")
        public DateTimeDTO testDateTime() {
            return new DateTimeDTO(1L, "Time Test", OffsetDateTime.now(), OffsetDateTime.now().plusHours(1));
        }

        @GetMapping("/fixed-datetime")
        public DateTimeDTO testFixedDateTime() {
            return new DateTimeDTO(
                    2L,
                    "Fixed Time Test",
                    OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 123000000, ZoneOffset.ofHours(8)),
                    OffsetDateTime.of(2024, 6, 15, 10, 30, 45, 456000000, ZoneOffset.ofHours(-5))
            );
        }
    }

    /**
     * 模拟 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestDTO {
        @OpenId
        private Long id;    // 应混淆

        private Long rawId; // 不应混淆
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestListDTO {
        @OpenId
        private List<Long> ids; // 列表应混淆
    }

    /**
     * 模拟包含 OffsetDateTime 的 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class DateTimeDTO {
        @OpenId
        private Long id;
        private String name;
        private OffsetDateTime createAt;
        private OffsetDateTime updateAt;
    }
}
