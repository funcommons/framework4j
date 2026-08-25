package fun.commons.framework4j.tracelog.unit;

import fun.commons.framework4j.tracelog.store.TraceIdNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TraceIdNormalizer 单元测试（覆盖设计文档 §3.1.5 全部边界）。
 */
@DisplayName("TraceId 标准化器")
class TraceIdNormalizerTest {

    @Test
    @DisplayName("32 位 hex → 直接返回小写")
    void exactly32Hex() {
        assertThat(TraceIdNormalizer.normalize("4BF92F3577B34DA6A3CE929D0E0E4736"))
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    @DisplayName("不足 32 位 → 左补 0")
    void leftPadZeros() {
        // 6 字符 + 26 个 0 = 32
        assertThat(TraceIdNormalizer.normalize("abc123"))
                .isEqualTo("00000000000000000000000000abc123");
        assertThat(TraceIdNormalizer.normalize("abc123").length()).isEqualTo(32);
    }

    @Test
    @DisplayName("超过 32 位 → 截断右段（保留末尾 32 位）")
    void truncate() {
        String input = "00000000000000000000000000000000abcd"; // 32 zero + abcd = 36
        // 截断: input.substring(36-32) = input.substring(4) = 最后 32 位
        // 32-4 = 28 个 0 + abcd = 32
        assertThat(TraceIdNormalizer.normalize(input))
                .isEqualTo("0000000000000000000000000000abcd");
        assertThat(TraceIdNormalizer.normalize(input).length()).isEqualTo(32);
    }

    @Test
    @DisplayName("B3 格式带连字符 → 去除连字符")
    void stripHyphen() {
        assertThat(TraceIdNormalizer.normalize("4bf92f35-77b3-4da6-a3ce-929d0e0e4736"))
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    @DisplayName("非 hex → null")
    void nonHex() {
        assertThat(TraceIdNormalizer.normalize("xyz123")).isNull();
        assertThat(TraceIdNormalizer.normalize("hello world")).isNull();
        assertThat(TraceIdNormalizer.normalize("123g")).isNull();
    }

    @Test
    @DisplayName("null / 空字符串 → null")
    void nullOrEmpty() {
        assertThat(TraceIdNormalizer.normalize(null)).isNull();
        assertThat(TraceIdNormalizer.normalize("")).isNull();
        assertThat(TraceIdNormalizer.normalize("   ")).isNull();
    }

    @Test
    @DisplayName("isValidHex32 校验")
    void isValidHex32() {
        assertThat(TraceIdNormalizer.isValidHex32("4bf92f3577b34da6a3ce929d0e0e4736")).isTrue();
        assertThat(TraceIdNormalizer.isValidHex32("4bf92f3577b34da6a3ce929d0e0e47")).isFalse(); // 30 位
        assertThat(TraceIdNormalizer.isValidHex32("4bf92f3577b34da6a3ce929d0e0e4736z")).isFalse(); // 含非 hex
        assertThat(TraceIdNormalizer.isValidHex32(null)).isFalse();
    }
}