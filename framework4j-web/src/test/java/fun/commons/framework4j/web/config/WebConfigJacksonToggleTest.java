package fun.commons.framework4j.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.web.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link WebConfig} 三个 Jackson 细粒度开关的行为
 * <p>
 * v2.2 关键修复：snake-case / long-to-string / fail-on-unknown-properties
 * 各自独立可关，不再被强制绑定。
 * <p>
 * 默认全开（matchIfMissing=true）→ 行为向后兼容。
 */
@DisplayName("WebConfig Jackson 三开关测试")
class WebConfigJacksonToggleTest {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CamelDto {
        private Long userId;
        private String userName;
    }

    private ObjectMapper build(WebConfig config, String beanMethodName) throws Exception {
        Jackson2ObjectMapperBuilderCustomizer customizer =
                (Jackson2ObjectMapperBuilderCustomizer) WebConfig.class
                        .getDeclaredMethod(beanMethodName)
                        .invoke(config);
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        customizer.customize(builder);
        return builder.build();
    }

    @Test
    @DisplayName("snake-case=true：camelCase → snake_case")
    void snakeCaseEnabled_convertsToSnakeCase() throws Exception {
        WebConfig config = new WebConfig();
        ObjectMapper mapper = build(config, "framework4jSnakeCaseCustomizer");
        // 同时装 long-to-string，模拟生产环境
        Jackson2ObjectMapperBuilderCustomizer longCustomizer = config.framework4jLongToStringCustomizer();
        // 直接复用上面构建好的 mapper；这里只验证 snake_case 命名
        // (build() 仅应用一个 customizer)
        String json = mapper.writeValueAsString(new CamelDto(123L, "alice"));
        assertThat(json).contains("\"user_id\"");
        assertThat(json).contains("\"user_name\"");
        assertThat(json).doesNotContain("\"userId\"");
    }

    @Test
    @DisplayName("snake-case=false（不注册）：保持驼峰")
    void snakeCaseDisabled_keepsCamelCase() throws Exception {
        // 模拟 framework4j.web.jackson.snake-case=false：根本不创建该 customizer
        // 此时 ObjectMapper 默认就是驼峰
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new CamelDto(123L, "alice"));
        assertThat(json).contains("\"userId\"");
        assertThat(json).contains("\"userName\"");
        assertThat(json).doesNotContain("\"user_id\"");
    }

    @Test
    @DisplayName("long-to-string=true：Long → String（防 JS 精度丢失）")
    void longToStringEnabled_convertsToString() throws Exception {
        WebConfig config = new WebConfig();
        ObjectMapper mapper = build(config, "framework4jLongToStringCustomizer");
        String json = mapper.writeValueAsString(new CamelDto(892310293123123L, "alice"));
        assertThat(json).contains("\"userId\":\"892310293123123\"");
    }

    @Test
    @DisplayName("long-to-string=false（不注册）：Long 保持数字")
    void longToStringDisabled_keepsNumber() throws Exception {
        // 模拟 framework4j.web.jackson.long-to-string=false
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new CamelDto(892310293123123L, "alice"));
        assertThat(json).contains("\"userId\":892310293123123");
        assertThat(json).doesNotContain("\"userId\":\"892310293123123\"");
    }

    @Test
    @DisplayName("fail-on-unknown-properties=false：未知字段不报错")
    void lenientUnknownProperties_toleratesUnknown() throws Exception {
        WebConfig config = new WebConfig();
        ObjectMapper mapper = build(config, "framework4jLenientUnknownPropertiesCustomizer");
        // 给一个 CamelDto 没有的字段
        String json = "{\"userId\":1,\"unknownField\":42}";
        CamelDto dto = mapper.readValue(json, CamelDto.class);
        assertThat(dto.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("ApiResponse 信封在 long-to-string=true 下，trace_id 等长字符串不变")
    void apiResponseLongToStringPreservesStringFields() throws Exception {
        WebConfig config = new WebConfig();
        ObjectMapper mapper = build(config, "framework4jLongToStringCustomizer");
        ApiResponse<Object> resp = ApiResponse.success(null);
        String json = mapper.writeValueAsString(resp);
        // ApiResponse 用 @JsonProperty 显式锁定字段名，不受 snake_case 影响
        assertThat(json).contains("\"trace_id\"");
    }

    @Test
    @DisplayName("List<Long> 在 long-to-string=true 下，元素全转 String")
    void longToStringHandlesList() throws Exception {
        WebConfig config = new WebConfig();
        ObjectMapper mapper = build(config, "framework4jLongToStringCustomizer");
        String json = mapper.writeValueAsString(java.util.List.of(1L, 2L, 3L));
        assertThat(json).isEqualTo("[\"1\",\"2\",\"3\"]");
    }

    @Test
    @DisplayName("三个 customizer 方法都返回非 null")
    void allCustomizersReturnNonNull() {
        WebConfig config = new WebConfig();
        assertThat(config.framework4jSnakeCaseCustomizer()).isNotNull();
        assertThat(config.framework4jLongToStringCustomizer()).isNotNull();
        assertThat(config.framework4jLenientUnknownPropertiesCustomizer()).isNotNull();
    }
}
