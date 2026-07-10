package fun.commons.framework4j.web;



import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * ApiError record 测试
 */
@DisplayName("ApiError 测试")
class ApiErrorTest {

    @Test
    @DisplayName("of(field, message)：2 字段构造")
    void ofFieldMessage() {
        ApiError e = ApiError.of("email", "格式错误");
        assertThat(e.field()).isEqualTo("email");
        assertThat(e.message()).isEqualTo("格式错误");
        assertThat(e.code()).isNull();
        assertThat(e.rejectedValue()).isNull();
    }

    @Test
    @DisplayName("of(field, code, message)：3 字段构造")
    void ofFieldCodeMessage() {
        ApiError e = ApiError.of("email", "FORMAT_INVALID", "邮箱格式不正确");
        assertThat(e.field()).isEqualTo("email");
        assertThat(e.code()).isEqualTo("FORMAT_INVALID");
        assertThat(e.message()).isEqualTo("邮箱格式不正确");
    }

    @Test
    @DisplayName("of(field, code, message, rejectedValue)：4 字段构造")
    void ofFullConstructor() {
        ApiError e = ApiError.of("age", "OUT_OF_RANGE", "年龄超出范围", 200);
        assertThat(e.field()).isEqualTo("age");
        assertThat(e.code()).isEqualTo("OUT_OF_RANGE");
        assertThat(e.rejectedValue()).isEqualTo(200);
    }

    @Test
    @DisplayName("null field → 正常构造")
    void nullField() {
        ApiError e = ApiError.of(null, "error message");
        assertThat(e.field()).isNull();
        assertThat(e.message()).isEqualTo("error message");
    }

    @Test
    @DisplayName("null message → 正常构造")
    void nullMessage() {
        ApiError e = ApiError.of("field", null);
        assertThat(e.message()).isNull();
    }

    @Test
    @DisplayName("equals / hashCode：record 语义")
    void equalsHashCode() {
        ApiError e1 = ApiError.of("email", "FORMAT_INVALID", "格式错误");
        ApiError e2 = ApiError.of("email", "FORMAT_INVALID", "格式错误");
        ApiError e3 = ApiError.of("name", "REQUIRED_MISSING", "必填");
        assertThat(e1).isEqualTo(e2);
        assertThat(e1).isNotEqualTo(e3);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
    }

    @Test
    @DisplayName("toString：包含所有字段")
    void toStringContainsFields() {
        ApiError e = ApiError.of("email", "FORMAT_INVALID", "格式错误", "bad@email");
        String str = e.toString();
        assertThat(str).contains("email").contains("FORMAT_INVALID").contains("格式错误");
    }
}
