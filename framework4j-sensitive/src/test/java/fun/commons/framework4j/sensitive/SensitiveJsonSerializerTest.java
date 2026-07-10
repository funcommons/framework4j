package fun.commons.framework4j.sensitive;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.sensitive.annotation.Sensitive;
import fun.commons.framework4j.sensitive.annotation.SensitiveRule;
import fun.commons.framework4j.sensitive.serializer.SensitiveJsonSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * SensitiveJsonSerializer 深度测试
 * <p>
 * 覆盖：null property / 所有规则序列化 / CUSTOM pattern / 无注解透传 / 嵌套对象
 */
@DisplayName("SensitiveJsonSerializer 序列化测试")
class SensitiveJsonSerializerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("所有规则同时序列化")
    void allRulesAtOnce() throws Exception {
        AllFields obj = new AllFields();
        obj.phone = "13812345678";
        obj.idCard = "110101199001011234";
        obj.bankCard = "6228123456785678";
        obj.email = "alice@test.com";
        obj.name = "张三丰";
        obj.address = "北京市朝阳区望京街";
        obj.all = "secret";
        obj.custom = "ABCDEFGH";

        String json = MAPPER.writeValueAsString(obj);

        assertThat(json).contains("138****5678");
        assertThat(json).contains("110101********1234");
        assertThat(json).contains("6228******5678");
        assertThat(json).contains("a***@test.com");
        assertThat(json).contains("张**");
        assertThat(json).contains("北京市朝阳区***");
        assertThat(json).contains("******");
        assertThat(json).contains("AB****GH");
        // 原始值不应出现
        assertThat(json).doesNotContain("13812345678");
        assertThat(json).doesNotContain("110101199001011234");
        assertThat(json).doesNotContain("6228123456785678");
        assertThat(json).doesNotContain("alice@test.com");
        assertThat(json).doesNotContain("secret");
        assertThat(json).doesNotContain("ABCDEFGH");
    }

    @Test
    @DisplayName("null 字段 → JSON 含 null（不抛异常）")
    void nullFieldsSerialize() throws Exception {
        AllFields obj = new AllFields();
        String json = MAPPER.writeValueAsString(obj);
        assertThat(json).contains("\"phone\":null");
    }

    @Test
    @DisplayName("空字符串字段 → 正常脱敏")
    void emptyStringFieldsSerialize() throws Exception {
        AllFields obj = new AllFields();
        obj.phone = "";
        String json = MAPPER.writeValueAsString(obj);
        assertThat(json).contains("\"phone\":\"\"");
    }

    @Test
    @DisplayName("无 @Sensitive 注解的字段 → 原样输出")
    void noAnnotationPassThrough() throws Exception {
        NoAnnotation obj = new NoAnnotation();
        obj.normal = "plain-value";
        String json = MAPPER.writeValueAsString(obj);
        assertThat(json).contains("plain-value");
    }

    @Test
    @DisplayName("短于脱敏长度的值 → 返回 ******")
    void tooShortValue() throws Exception {
        AllFields obj = new AllFields();
        obj.phone = "123"; // < 7 字符
        String json = MAPPER.writeValueAsString(obj);
        assertThat(json).contains("******");
    }

    @Test
    @DisplayName("CUSTOM pattern 格式：3,3,8")
    void customPattern388() throws Exception {
        CustomObj obj = new CustomObj();
        obj.value = "ABCDEFGHIJ"; // 10 字符
        String json = MAPPER.writeValueAsString(obj);
        // 前 3 + 8 星号 + 后 3
        assertThat(json).contains("ABC********HIJ");
    }

    @Test
    @DisplayName("CUSTOM pattern 格式：0,0,6（全星号）")
    void customPatternAllStars() throws Exception {
        CustomObj2 obj = new CustomObj2();
        obj.value = "anything";
        String json = MAPPER.writeValueAsString(obj);
        assertThat(json).contains("\"value\":\"******\"");
    }

    @Test
    @DisplayName("CUSTOM pattern 空 → 全星号")
    void customPatternEmpty() throws Exception {
        CustomObj3 obj = new CustomObj3();
        obj.value = "test";
        String json = MAPPER.writeValueAsString(obj);
        assertThat(json).contains("******");
    }

    @Test
    @DisplayName("数组中多个对象 → 每个都脱敏")
    void arrayOfObjects() throws Exception {
        AllFields obj1 = new AllFields();
        obj1.phone = "13800000001";
        AllFields obj2 = new AllFields();
        obj2.phone = "13900000002";

        String json = MAPPER.writeValueAsString(java.util.List.of(obj1, obj2));
        assertThat(json).contains("138****0001");
        assertThat(json).contains("139****0002");
    }

    // ========== 测试对象 ==========

    public static class AllFields {
        @Sensitive(SensitiveRule.PHONE) public String phone;
        @Sensitive(SensitiveRule.ID_CARD) public String idCard;
        @Sensitive(SensitiveRule.BANK_CARD) public String bankCard;
        @Sensitive(SensitiveRule.EMAIL) public String email;
        @Sensitive(SensitiveRule.NAME) public String name;
        @Sensitive(SensitiveRule.ADDRESS) public String address;
        @Sensitive(SensitiveRule.ALL) public String all;
        @Sensitive(value = SensitiveRule.CUSTOM, pattern = "2,2,4") public String custom;
    }

    public static class NoAnnotation {
        public String normal;
    }

    public static class CustomObj {
        @Sensitive(value = SensitiveRule.CUSTOM, pattern = "3,3,8")
        public String value;
    }

    public static class CustomObj2 {
        @Sensitive(value = SensitiveRule.CUSTOM, pattern = "0,0,6")
        public String value;
    }

    public static class CustomObj3 {
        @Sensitive(value = SensitiveRule.CUSTOM, pattern = "")
        public String value;
    }
}
