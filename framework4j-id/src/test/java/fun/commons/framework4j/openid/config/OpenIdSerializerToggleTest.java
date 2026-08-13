package fun.commons.framework4j.openid.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.openid.util.OpenIdTypeSupport;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 @OpenId 序列化开关行为
 * <p>
 * v2.2 关键修复：移除 {@code @JsonSerialize} 字段级注解，改由 {@link OpenIdAutoConfiguration}
 * 通过 {@link OpenIdBeanSerializerModifier} 动态注册。
 * <p>
 * 本测试直接验证 Jackson 行为：
 * <ul>
 *   <li>装上 modifier（模拟 enabled=true）→ @OpenId 字段输出 12 字符混淆字符串</li>
 *   <li>不装 modifier（模拟 enabled=false）→ @OpenId 字段输出普通 Long</li>
 * </ul>
 */
@DisplayName("OpenId 序列化开关行为测试")
class OpenIdSerializerToggleTest {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SampleDto {
        @OpenId
        private Long id;

        private String name;
    }

    @Test
    @DisplayName("装上 modifier（模拟 enabled=true）：@OpenId 字段输出混淆字符串")
    void modifierEnabled_obfuscates() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.setSerializerModifier(new OpenIdBeanSerializerModifier(OpenIdTypeSupport.defaults()));
        mapper.registerModule(module);

        long raw = 123456789L;
        String expected = IdObfuscator.toOpenId(raw);

        String json = mapper.writeValueAsString(new SampleDto(raw, "hello"));
        assertThat(json).contains("\"id\":\"" + expected + "\"");
        assertThat(json).contains("\"name\":\"hello\"");
    }

    @Test
    @DisplayName("不装 modifier（模拟 enabled=false）：@OpenId 字段输出普通 Long（数字）")
    void modifierDisabled_keepsPlainLong() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        // 关键：不注册 OpenIdBeanSerializerModifier，等价于 framework4j.openid.enabled=false

        long raw = 123456789L;
        String json = mapper.writeValueAsString(new SampleDto(raw, "hello"));

        // id 字段输出原始数字（未混淆）
        assertThat(json).contains("\"id\":" + raw);
        // 绝对不含 12 字符混淆串
        String obfuscated = IdObfuscator.toOpenId(raw);
        assertThat(json).doesNotContain(obfuscated);
    }

    @Test
    @DisplayName("null 值：modifier 装上时输出 null（不抛 NPE）")
    void modifierEnabled_nullSerializedAsNull() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.setSerializerModifier(new OpenIdBeanSerializerModifier(OpenIdTypeSupport.defaults()));
        mapper.registerModule(module);

        String json = mapper.writeValueAsString(new SampleDto(null, "hello"));
        assertThat(json).contains("\"id\":null");
    }
}
