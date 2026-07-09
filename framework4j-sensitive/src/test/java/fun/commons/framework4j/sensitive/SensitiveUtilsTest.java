package fun.commons.framework4j.sensitive;

import fun.commons.framework4j.sensitive.annotation.SensitiveRule;
import fun.commons.framework4j.sensitive.util.SensitiveUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SensitiveUtils 脱敏规则测试（参数化）
 *
 * @since 2.1.0
 */
@DisplayName("SensitiveUtils 脱敏规则参数化测试")
class SensitiveUtilsTest {

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @DisplayName("各规则脱敏正确性")
    @CsvSource({
            "PHONE, 13812345678, 138****5678",
            "PHONE, 138, ******",
            "ID_CARD, 110101199001011234, 110101********1234",
            "ID_CARD, 12345, ******",
            "BANK_CARD, 6228123456785678, 6228******5678",
            "BANK_CARD, 6228, ******",
            "EMAIL, alice@example.com, a***@example.com",
            "EMAIL, b@test.com, b***@test.com",
            "EMAIL, x@y.z, x***@y.z",
            "NAME, 张三丰, 张**",
            "NAME, 张, *",
            "ADDRESS, 北京市朝阳区望京街1号, 北京市朝阳区***",
    })
    void desensitizeByRule(String ruleName, String input, String expected) {
        SensitiveRule rule = SensitiveRule.valueOf(ruleName);
        assertThat(SensitiveUtils.desensitize(input, rule)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(SensitiveRule.class)
    @DisplayName("null / 空字符串安全（原样返回）")
    void nullAndEmptySafe(SensitiveRule rule) {
        assertThat(SensitiveUtils.desensitize(null, rule)).isNull();
        assertThat(SensitiveUtils.desensitize("", rule)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("ALL 规则：固定返 ******")
    void allRule() {
        assertThat(SensitiveUtils.desensitize("any-secret", SensitiveRule.ALL)).isEqualTo("******");
    }
}
