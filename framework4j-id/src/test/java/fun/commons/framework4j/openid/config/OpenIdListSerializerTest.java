package fun.commons.framework4j.openid.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.openid.util.OpenIdTypeSupport;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenId 集合/数组序列化测试（v1.3 序列化侧 List 修复）。
 * <p>
 * 既有 {@link OpenIdBeanSerializerModifier} 把 Long 专用 serializer 无差别装到所有 @OpenId 字段，
 * 导致 @OpenId List&lt;Long&gt; 在 Jackson 下序列化异常（{@code OpenIdWebIntegrationTest} 场景 5 处于 @Disabled）。
 * v1.3 改为类型感知：集合/数组走 {@link fun.commons.framework4j.openid.config.OpenIdCollectionJsonSerializer} 输出混淆串数组，标量 Long 行为不变。
 */
@DisplayName("OpenId 集合/数组序列化（v1.3 List 修复）")
class OpenIdListSerializerTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.setSerializerModifier(new OpenIdBeanSerializerModifier(OpenIdTypeSupport.defaults()));
        mapper.registerModule(module);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ListDto {
        @OpenId
        private List<Long> ids;
        @OpenId
        private Set<Long> setIds;
        @OpenId
        private Long[] longArr;
        @OpenId
        private long[] primArr;
        private List<Long> plainIds;   // 未标注 —— 保持数字
    }

    @Test
    @DisplayName("@OpenId List<Long> → 混淆串数组")
    void listSerializedAsObfuscatedArray() throws Exception {
        String o1 = IdObfuscator.toOpenId(1L);
        String o2 = IdObfuscator.toOpenId(2L);
        String json = mapper.writeValueAsString(new ListDto(Arrays.asList(1L, 2L), null, null, null, null));
        assertThat(json).contains("\"ids\":[\"" + o1 + "\",\"" + o2 + "\"]");
    }

    @Test
    @DisplayName("@OpenId List<Long> null → null；空集合 → []")
    void nullAndEmptyList() throws Exception {
        String jsonNull = mapper.writeValueAsString(new ListDto(null, null, null, null, null));
        assertThat(jsonNull).contains("\"ids\":null");

        String jsonEmpty = mapper.writeValueAsString(new ListDto(List.of(), null, null, null, null));
        assertThat(jsonEmpty).contains("\"ids\":[]");
    }

    @Test
    @DisplayName("@OpenId Set<Long> → 混淆串数组")
    void setSerializedAsObfuscatedArray() throws Exception {
        String o1 = IdObfuscator.toOpenId(1L);
        String o2 = IdObfuscator.toOpenId(2L);
        String json = mapper.writeValueAsString(
                new ListDto(null, new LinkedHashSet<>(Arrays.asList(1L, 2L)), null, null, null));
        assertThat(json).contains("\"setIds\":[\"" + o1 + "\",\"" + o2 + "\"]");
    }

    @Test
    @DisplayName("@OpenId Long[] 与 long[] → 混淆串数组")
    void arraysSerializedAsObfuscatedArray() throws Exception {
        String o1 = IdObfuscator.toOpenId(10L);
        String o2 = IdObfuscator.toOpenId(20L);
        String json = mapper.writeValueAsString(
                new ListDto(null, null, new Long[]{10L}, new long[]{10L, 20L}, null));
        assertThat(json).contains("\"longArr\":[\"" + o1 + "\"]");
        assertThat(json).contains("\"primArr\":[\"" + o1 + "\",\"" + o2 + "\"]");
    }

    @Test
    @DisplayName("未标注 List<Long>(plainIds) → 保持数字数组")
    void unannotatedListStaysNumeric() throws Exception {
        String json = mapper.writeValueAsString(new ListDto(null, null, null, null, Arrays.asList(1L, 2L)));
        assertThat(json).contains("\"plainIds\":[1,2]");
        // 不含混淆串
        assertThat(json).doesNotContain(IdObfuscator.toOpenId(1L));
    }

    @Nested
    @DisplayName("Integer/String 序列化（support-integer + support-string 开启）")
    class ExtendedTypes {

        private final ObjectMapper allMapper = mapperWith(OpenIdTypeSupport.allEnabled());

        private static ObjectMapper mapperWith(OpenIdTypeSupport ts) {
            ObjectMapper m = new ObjectMapper();
            SimpleModule module = new SimpleModule();
            module.setSerializerModifier(new OpenIdBeanSerializerModifier(ts));
            m.registerModule(module);
            return m;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        static class MixedDto {
            @OpenId
            private Integer intId;
            @OpenId
            private String strId;
            @OpenId
            private List<String> strIds;
            @OpenId
            private List<Integer> intIds;
        }

        @Test
        @DisplayName("@OpenId Integer → 混淆串")
        void integerScalarSerialized() throws Exception {
            String oid = IdObfuscator.toOpenId(5L);
            String json = allMapper.writeValueAsString(new MixedDto(5, null, null, null));
            assertThat(json).contains("\"intId\":\"" + oid + "\"");
        }

        @Test
        @DisplayName("@OpenId String(数字串) → 混淆串")
        void stringScalarSerialized() throws Exception {
            String oid = IdObfuscator.toOpenId(5L);
            String json = allMapper.writeValueAsString(new MixedDto(null, "5", null, null));
            assertThat(json).contains("\"strId\":\"" + oid + "\"");
        }

        @Test
        @DisplayName("@OpenId List<String> / List<Integer> → 混淆串数组")
        void stringAndIntegerListSerialized() throws Exception {
            String o1 = IdObfuscator.toOpenId(1L);
            String o2 = IdObfuscator.toOpenId(2L);
            String json = allMapper.writeValueAsString(
                    new MixedDto(null, null, Arrays.asList("1", "2"), Arrays.asList(1, 2)));
            assertThat(json).contains("\"strIds\":[\"" + o1 + "\",\"" + o2 + "\"]");
            assertThat(json).contains("\"intIds\":[\"" + o1 + "\",\"" + o2 + "\"]");
        }
    }
}
