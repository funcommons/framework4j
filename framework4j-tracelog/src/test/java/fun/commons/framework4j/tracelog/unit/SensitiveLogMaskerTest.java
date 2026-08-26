package fun.commons.framework4j.tracelog.unit;

import fun.commons.framework4j.tracelog.appender.SensitiveLogMasker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SensitiveLogMasker} 脱敏测试。
 * <p>
 * 覆盖：JSON 字段值 / message 内转义嵌套 JSON / message 内 kv 形态 /
 * 大小写 / 非敏感字段不误伤 / 关闭开关直通 / 值含转义字符。
 */
@DisplayName("SensitiveLogMasker 日志脱敏")
class SensitiveLogMaskerTest {

    private static final List<String> DEFAULT_KEYS = List.of(
            "password", "token", "authorization", "cookie", "secret");

    private SensitiveLogMasker on() {
        return new SensitiveLogMasker(true, DEFAULT_KEYS);
    }

    @Test
    @DisplayName("JSON 字段值被脱敏，其他字段原样保留")
    void jsonFieldValueMasked() {
        String line = "{\"@timestamp\":\"2026-08-26T16:00:00Z\",\"level\":\"INFO\","
                + "\"message\":\"login\",\"password\":\"p@ssw0rd!\"}";
        String out = on().mask(line);
        assertThat(out).contains("\"password\":\"******\"");
        assertThat(out).doesNotContain("p@ssw0rd");
        assertThat(out).contains("\"message\":\"login\"");
    }

    @Test
    @DisplayName("message 字段内转义嵌套的 JSON（LogstashEncoder 转义后）也被脱敏")
    void escapedJsonInsideMessage() {
        // 实际日志行: message 值是 "user login {\"password\":\"secret123\"}" 转义后的形态
        String line = "{\"message\":\"user login {\\\"password\\\":\\\"secret123\\\"}\",\"level\":\"INFO\"}";
        String out = on().mask(line);
        assertThat(out).doesNotContain("secret123");
        assertThat(out).contains("******");
    }

    @Test
    @DisplayName("message 内 kv 形态（password=xxx / token: xxx）被脱敏")
    void kvInMessage() {
        assertThat(on().mask("msg login password=hunter2 ok"))
                .isEqualTo("msg login password=****** ok");
        assertThat(on().mask("msg token: abc.def.ghi end"))
                .isEqualTo("msg token: ****** end");
    }

    @Test
    @DisplayName("大小写不敏感（PASSWORD / Authorization）")
    void caseInsensitive() {
        String out = on().mask("{\"PASSWORD\":\"xyz\",\"Authorization\":\"Bearer jwt\"}");
        assertThat(out).contains("\"PASSWORD\":\"******\"");
        assertThat(out).doesNotContain("jwt").doesNotContain("xyz");
    }

    @Test
    @DisplayName("非敏感字段不误伤（username/orderId 等）")
    void nonSensitiveUntouched() {
        String line = "{\"username\":\"alice\",\"orderId\":\"OD-123\",\"level\":\"INFO\"}";
        assertThat(on().mask(line)).isEqualTo(line);
    }

    @Test
    @DisplayName("关闭开关 → 原样直通")
    void disabledPassthrough() {
        String line = "{\"password\":\"p@ss\"}";
        assertThat(new SensitiveLogMasker(false, DEFAULT_KEYS).mask(line)).isEqualTo(line);
    }

    @Test
    @DisplayName("空 key 列表 → 不匹配任何内容")
    void emptyKeys() {
        String line = "{\"password\":\"p@ss\"}";
        assertThat(new SensitiveLogMasker(true, List.of()).mask(line)).isEqualTo(line);
    }

    @Test
    @DisplayName("值内含转义字符的 JSON 字段也整体脱敏")
    void valueWithEscapes() {
        String line = "{\"token\":\"ab\\\"cd\"}";
        String out = on().mask(line);
        assertThat(out).doesNotContain("ab\\\"cd");
        assertThat(out).contains("******");
    }

    @Test
    @DisplayName("含正则元字符的自定义 key 不会破坏匹配（Pattern.quote）")
    void regexMetaKey() {
        SensitiveLogMasker masker = new SensitiveLogMasker(true, List.of("api-key+"));
        assertThat(masker.mask("{\"api-key+\":\"v1\"}")).contains("******");
    }
}