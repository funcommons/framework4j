package fun.commons.framework4j.openid.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.openid.util.OpenIdTypeSupport;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenId 请求体反序列化测试（R1 标量 / R2 集合 / R3 嵌套）。
 * <p>
 * 纯 {@link ObjectMapper} 层级，无需 Spring/Redis，最快反馈。挂载方式与生产一致：
 * 一个 {@link SimpleModule} 装 {@link OpenIdBeanDeserializerModifier}。
 * <p>
 * 覆盖矩阵：
 * <ul>
 *   <li>R1：合法 OpenId 还原 / 数字 token 透传 / 数字串透传 / 非法串抛错 / null / 空串→null /
 *       未标注 Long 与 BigDecimal 不受影响（选择性证明）</li>
 *   <li>R2：List / Set(去重) / Long[] / long[]，含混合元素、空数组、null、非法元素抛错</li>
 *   <li>R3：嵌套 record、多层嵌套 A.B.C、List 内嵌 record —— 验证 updateBuilder per-bean 自动递归</li>
 *   <li>toggle 对照：不装 modifier 时 @OpenId 失效（默认 Long deser 解析 12 字符串抛错）</li>
 * </ul>
 */
@DisplayName("OpenId 请求体反序列化（R1/R2/R3）")
class OpenIdRequestBodyDeserializationTest {

    private ObjectMapper mapper;  // 装 modifier（模拟 framework4j.openid.enabled=true + request-body-deserializer=true）
    private ObjectMapper plain;   // 不装 modifier（模拟 request-body-deserializer=false）

    @BeforeEach
    void setUp() {
        mapper = mapperWith(OpenIdTypeSupport.defaults());
        plain = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static ObjectMapper mapperWith(OpenIdTypeSupport typeSupport) {
        ObjectMapper m = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        SimpleModule module = new SimpleModule();
        module.setDeserializerModifier(new OpenIdBeanDeserializerModifier(typeSupport));
        m.registerModule(module);
        return m;
    }

    // ==================== R1：标量 @OpenId Long ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ScalarDto {
        @OpenId
        private Long tenantId;
        @OpenId
        private Long userId;
        private Long credits;       // 未标注 Long —— 必须走默认，不被接管
        private BigDecimal balance; // 未标注 BigDecimal
    }

    @Test
    @DisplayName("R1: 合法 OpenId 串 → 还原 Long")
    void validOpenIdDecoded() throws Exception {
        String oid = IdObfuscator.toOpenId(123L);
        ScalarDto dto = mapper.readValue("{\"tenantId\":\"" + oid + "\"}", ScalarDto.class);
        assertThat(dto.getTenantId()).isEqualTo(123L);
    }

    @Test
    @DisplayName("R1: 数字 token → 直接透传")
    void numericTokenPassesThrough() throws Exception {
        ScalarDto dto = mapper.readValue("{\"tenantId\":67890}", ScalarDto.class);
        assertThat(dto.getTenantId()).isEqualTo(67890L);
    }

    @Test
    @DisplayName("R1: 数字字符串 → 兼容期透传")
    void numericStringPassesThrough() throws Exception {
        ScalarDto dto = mapper.readValue("{\"tenantId\":\"99999\"}", ScalarDto.class);
        assertThat(dto.getTenantId()).isEqualTo(99999L);
    }

    @Test
    @DisplayName("R1: 非法字符串 → 抛 MismatchedInputException")
    void invalidStringRejected() {
        assertThatThrownBy(() -> mapper.readValue("{\"tenantId\":\"hello\"}", ScalarDto.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    @DisplayName("R1: null → null")
    void nullValuePassesThrough() throws Exception {
        ScalarDto dto = mapper.readValue("{\"tenantId\":null}", ScalarDto.class);
        assertThat(dto.getTenantId()).isNull();
    }

    @Test
    @DisplayName("R1: 空串 → null")
    void emptyStringBecomesNull() throws Exception {
        ScalarDto dto = mapper.readValue("{\"tenantId\":\"\"}", ScalarDto.class);
        assertThat(dto.getTenantId()).isNull();
    }

    @Test
    @DisplayName("R1: 未标注 Long(credits) 收到合法 OpenId 串 → 走默认 deser 抛错（证明未被接管）")
    void unannotatedLongNotTouched_openIdStringFails() {
        String oid = IdObfuscator.toOpenId(555L);
        assertThatThrownBy(() -> mapper.readValue("{\"credits\":\"" + oid + "\"}", ScalarDto.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    @DisplayName("R1: 未标注 Long(credits) 数字、BigDecimal(balance) 字符串 → 默认 deser 正常")
    void unannotatedFieldsUseDefaultDeserialization() throws Exception {
        ScalarDto dto = mapper.readValue("{\"credits\":12345,\"balance\":\"100.50\"}", ScalarDto.class);
        assertThat(dto.getCredits()).isEqualTo(12345L);
        assertThat(dto.getBalance()).isEqualByComparingTo(new BigDecimal("100.50"));
    }

    // ==================== R2：集合 / 数组 ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CollectionDto {
        @OpenId
        private List<Long> tagIds;
        @OpenId
        private Set<Long> setIds;
        @OpenId
        private Long[] longArr;
        @OpenId
        private long[] primArr;
        private List<Long> plainIds;   // 未标注
    }

    @Test
    @DisplayName("R2: List<Long> OpenId 串数组 → 还原")
    void listOpenIdsDecoded() throws Exception {
        String o1 = IdObfuscator.toOpenId(1L);
        String o2 = IdObfuscator.toOpenId(2L);
        CollectionDto dto = mapper.readValue("{\"tagIds\":[\"" + o1 + "\",\"" + o2 + "\"]}", CollectionDto.class);
        assertThat(dto.getTagIds()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("R2: List<Long> 混合 OpenId 串 + 数字 → 全部还原")
    void listMixedElements() throws Exception {
        String o1 = IdObfuscator.toOpenId(1L);
        CollectionDto dto = mapper.readValue("{\"tagIds\":[\"" + o1 + "\",2]}", CollectionDto.class);
        assertThat(dto.getTagIds()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("R2: 空数组 → 空集合；null → null")
    void emptyAndNullList() throws Exception {
        CollectionDto empty = mapper.readValue("{\"tagIds\":[]}", CollectionDto.class);
        assertThat(empty.getTagIds()).isEmpty();

        CollectionDto nul = mapper.readValue("{\"tagIds\":null}", CollectionDto.class);
        assertThat(nul.getTagIds()).isNull();
    }

    @Test
    @DisplayName("R2: Set<Long> 去重")
    void setDedup() throws Exception {
        String o1 = IdObfuscator.toOpenId(1L);
        String o2 = IdObfuscator.toOpenId(2L);
        CollectionDto dto = mapper.readValue(
                "{\"setIds\":[\"" + o1 + "\",\"" + o2 + "\",\"" + o1 + "\"]}", CollectionDto.class);
        assertThat(dto.setIds).containsExactlyInAnyOrder(1L, 2L);
        assertThat(dto.setIds).hasSize(2);
    }

    @Test
    @DisplayName("R2: Long[] 与 long[] 还原")
    void arraysDecoded() throws Exception {
        String o1 = IdObfuscator.toOpenId(10L);
        String o2 = IdObfuscator.toOpenId(20L);
        CollectionDto dto = mapper.readValue(
                "{\"longArr\":[\"" + o1 + "\"],\"primArr\":[\"" + o1 + "\",\"" + o2 + "\"]}", CollectionDto.class);
        assertThat(dto.longArr).containsExactly(10L);
        assertThat(dto.primArr).containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("R2: 非法元素 → 抛错")
    void listInvalidElementRejected() {
        assertThatThrownBy(() -> mapper.readValue("{\"tagIds\":[\"hello\"]}", CollectionDto.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    @DisplayName("R2: 未标注 List<Long>(plainIds) OpenId 串 → 默认 deser 抛错（证明未被接管）")
    void unannotatedListNotTouched() {
        String o1 = IdObfuscator.toOpenId(1L);
        assertThatThrownBy(() -> mapper.readValue("{\"plainIds\":[\"" + o1 + "\"]}", CollectionDto.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    // ==================== R3：嵌套 record / 多层嵌套 / List 内嵌对象 ====================

    record Inner(@OpenId Long groupId, Long channelId) {
    }

    record Outer(Inner inner, @OpenId Long topId) {
    }

    @Test
    @DisplayName("R3: 嵌套 record 字段 @OpenId 生效")
    void nestedRecordFieldDecoded() throws Exception {
        String g = IdObfuscator.toOpenId(7L);
        String t = IdObfuscator.toOpenId(5L);
        Outer outer = mapper.readValue(
                "{\"inner\":{\"groupId\":\"" + g + "\",\"channelId\":9},\"topId\":\"" + t + "\"}", Outer.class);
        assertThat(outer.inner().groupId()).isEqualTo(7L);
        assertThat(outer.inner().channelId()).isEqualTo(9L);
        assertThat(outer.topId()).isEqualTo(5L);
    }

    record Level3(@OpenId Long leafId) {
    }

    record Level2(Level3 c) {
    }

    record Level1(Level2 b) {
    }

    @Test
    @DisplayName("R3: 多层嵌套 A.B.C 叶子字段 @OpenId 生效")
    void deepNestingDecoded() throws Exception {
        String leaf = IdObfuscator.toOpenId(42L);
        Level1 root = mapper.readValue("{\"b\":{\"c\":{\"leafId\":\"" + leaf + "\"}}}", Level1.class);
        assertThat(root.b().c().leafId()).isEqualTo(42L);
    }

    record Item(@OpenId Long id) {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ListOfObjectsDto {
        private List<Item> items;   // 字段未标 @OpenId；Item.id 是 @OpenId → 走 per-bean modifier
    }

    @Test
    @DisplayName("R3: List 内嵌 record，元素对象的 @OpenId 字段生效")
    void listOfNestedObjectsDecoded() throws Exception {
        String o1 = IdObfuscator.toOpenId(100L);
        String o2 = IdObfuscator.toOpenId(200L);
        ListOfObjectsDto dto = mapper.readValue(
                "{\"items\":[{\"id\":\"" + o1 + "\"},{\"id\":\"" + o2 + "\"}]}", ListOfObjectsDto.class);
        assertThat(dto.getItems()).extracting(Item::id).containsExactly(100L, 200L);
    }

    // ==================== toggle 对照：不装 modifier 时 @OpenId 失效 ====================

    @Test
    @DisplayName("toggle: 不装 modifier（request-body-deserializer=false）→ @OpenId 串走默认 deser 抛错")
    void withoutModifierOpenIdStringRejected() {
        String oid = IdObfuscator.toOpenId(123L);
        assertThatThrownBy(() -> plain.readValue("{\"tenantId\":\"" + oid + "\"}", ScalarDto.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    // ==================== v1.3 三开关：Integer / String / strict ====================

    @Nested
    @DisplayName("Integer 支持（support-integer=true）")
    class IntegerSupport {

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        static class IntDto {
            @OpenId
            private Integer id;
            @OpenId
            private List<Integer> ids;
        }

        private final ObjectMapper intMapper = mapperWith(OpenIdTypeSupport.builder().integer(true).build());

        @Test
        @DisplayName("@OpenId Integer OpenId 串 → 还原为 Integer")
        void integerScalarDecoded() throws Exception {
            String oid = IdObfuscator.toOpenId(7L);
            IntDto dto = intMapper.readValue("{\"id\":\"" + oid + "\"}", IntDto.class);
            assertThat(dto.id).isEqualTo(7);
        }

        @Test
        @DisplayName("@OpenId List<Integer> → 还原为 List<Integer>")
        void integerListDecoded() throws Exception {
            String o1 = IdObfuscator.toOpenId(1L);
            String o2 = IdObfuscator.toOpenId(2L);
            IntDto dto = intMapper.readValue("{\"ids\":[\"" + o1 + "\",\"" + o2 + "\"]}", IntDto.class);
            assertThat(dto.ids).containsExactly(1, 2);
        }

        @Test
        @DisplayName("@OpenId Integer 超过 Int 范围 → toIntExact 抛错")
        void integerOverflowRejected() {
            // 99999999999 > Integer.MAX_VALUE，数字透传后 toIntExact 溢出
            assertThatThrownBy(() -> intMapper.readValue("{\"id\":\"99999999999\"}", IntDto.class))
                    .isInstanceOf(MismatchedInputException.class);
        }

        @Test
        @DisplayName("support-integer=false（默认）→ @OpenId Integer 字段不被接管，OpenId 串走默认抛错")
        void integerDisabledByDefault() {
            String oid = IdObfuscator.toOpenId(7L);
            assertThatThrownBy(() -> mapper.readValue("{\"id\":\"" + oid + "\"}", IntDto.class))
                    .isInstanceOf(MismatchedInputException.class);
        }
    }

    @Nested
    @DisplayName("String 支持（support-string=true，Long 为枢轴）")
    class StringSupport {

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        static class StrDto {
            @OpenId
            private String id;
            @OpenId
            private List<String> ids;
        }

        private final ObjectMapper strMapper = mapperWith(OpenIdTypeSupport.builder().string(true).build());

        @Test
        @DisplayName("@OpenId String OpenId 串 → 字段持数字串")
        void stringScalarDecodedToNumeric() throws Exception {
            String oid = IdObfuscator.toOpenId(123L);
            StrDto dto = strMapper.readValue("{\"id\":\"" + oid + "\"}", StrDto.class);
            assertThat(dto.id).isEqualTo("123");
        }

        @Test
        @DisplayName("@OpenId String 数字串 → 透传为数字串")
        void stringNumericPassthrough() throws Exception {
            StrDto dto = strMapper.readValue("{\"id\":\"99999\"}", StrDto.class);
            assertThat(dto.id).isEqualTo("99999");
        }

        @Test
        @DisplayName("@OpenId List<String> → List<数字串>")
        void stringListDecoded() throws Exception {
            String o1 = IdObfuscator.toOpenId(1L);
            String o2 = IdObfuscator.toOpenId(2L);
            StrDto dto = strMapper.readValue("{\"ids\":[\"" + o1 + "\",\"" + o2 + "\"]}", StrDto.class);
            assertThat(dto.ids).containsExactly("1", "2");
        }
    }

    @Nested
    @DisplayName("strict：accept-numeric-fallback=false")
    class StrictMode {

        private final ObjectMapper strictMapper = mapperWith(OpenIdTypeSupport.builder().fallback(false).build());

        @Test
        @DisplayName("合法 OpenId 串仍接受")
        void openIdStillAccepted() throws Exception {
            String oid = IdObfuscator.toOpenId(123L);
            ScalarDto dto = strictMapper.readValue("{\"tenantId\":\"" + oid + "\"}", ScalarDto.class);
            assertThat(dto.getTenantId()).isEqualTo(123L);
        }

        @Test
        @DisplayName("数字 token → 拒绝（strict 收口）")
        void numericTokenRejected() {
            assertThatThrownBy(() -> strictMapper.readValue("{\"tenantId\":99999}", ScalarDto.class))
                    .isInstanceOf(MismatchedInputException.class);
        }

        @Test
        @DisplayName("数字串 → 拒绝")
        void numericStringRejected() {
            assertThatThrownBy(() -> strictMapper.readValue("{\"tenantId\":\"99999\"}", ScalarDto.class))
                    .isInstanceOf(MismatchedInputException.class);
        }
    }
}
