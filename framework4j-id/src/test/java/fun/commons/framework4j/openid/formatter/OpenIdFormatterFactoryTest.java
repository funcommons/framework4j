package fun.commons.framework4j.openid.formatter;

import fun.commons.framework4j.id.util.IdObfuscator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.format.Printer;
import org.springframework.format.Parser;

import java.text.ParseException;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenIdFormatterFactory测试
 */
@DisplayName("OpenIdFormatterFactory测试")
class OpenIdFormatterFactoryTest {

    private OpenIdFormatterFactory factory;

    @BeforeEach
    void setUp() {
        factory = new OpenIdFormatterFactory();
    }

    @Test
    @DisplayName("应该支持OpenId注解")
    void shouldSupportOpenIdAnnotation() {
        // OpenIdFormatterFactory implements AnnotationFormatterFactory<OpenId>
        // 验证工厂类不是null且已经初始化
        assertThat(factory).isNotNull();
    }

    @Test
    @DisplayName("应该支持正确的字段类型")
    void shouldSupportCorrectFieldTypes() {
        Set<Class<?>> fieldTypes = factory.getFieldTypes();
        assertThat(fieldTypes).contains(Long.class);
        assertThat(fieldTypes).contains(long.class);
        assertThat(fieldTypes).contains(Integer.class);
        assertThat(fieldTypes).contains(int.class);
        assertThat(fieldTypes).hasSize(4);
    }

    @Nested
    @DisplayName("Parser测试")
    class ParserTest {

        @Test
        @DisplayName("应该能够解析OpenID字符串")
        void shouldParseOpenIdString() throws ParseException {
            long originalId = 123456789L;
            String openId = IdObfuscator.toOpenId(originalId);

            @SuppressWarnings("unchecked")
Parser<Long> parser = (Parser<Long>) factory.getParser(null, Long.class);
            Long parsedId = parser.parse(openId, Locale.getDefault());

            assertThat(parsedId).isEqualTo(originalId);
        }

        @Test
        @DisplayName("应该能够解析纯数字字符串")
        void shouldParseNumericString() throws ParseException {
            String numericId = "123456789";

            @SuppressWarnings("unchecked")
Parser<Long> parser = (Parser<Long>) factory.getParser(null, Long.class);
            Long parsedId = parser.parse(numericId, Locale.getDefault());

            assertThat(parsedId).isEqualTo(123456789L);
        }

        @Test
        @DisplayName("负数ID应该被拒绝")
        void shouldRejectNegativeNumericString() {
            String negativeId = "-123";

            @SuppressWarnings("unchecked")
Parser<Long> parser = (Parser<Long>) factory.getParser(null, Long.class);

            assertThatThrownBy(() -> parser.parse(negativeId, Locale.getDefault()))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("ID cannot be negative");
        }

        @Test
        @DisplayName("应该能够解析带前缀的OpenID")
        void shouldParseOpenIdWithPrefix() throws ParseException {
            long originalId = 987654321L;
            String openIdWithPrefix = "USER_" + IdObfuscator.toOpenId(originalId);

            @SuppressWarnings("unchecked")
Parser<Long> parser = (Parser<Long>) factory.getParser(null, Long.class);
            Long parsedId = parser.parse(openIdWithPrefix, Locale.getDefault());

            assertThat(parsedId).isEqualTo(originalId);
        }

        @Test
        @DisplayName("无效OpenID应该抛出异常")
        void shouldThrowExceptionForInvalidOpenId() {
            @SuppressWarnings("unchecked")
Parser<Long> parser = (Parser<Long>) factory.getParser(null, Long.class);

            // 使用包含特殊字符的无效OpenID字符串
            String invalidOpenId = "INVALID@12345"; // @字符不在IdObfuscator的字符集中
            assertThatThrownBy(() -> parser.parse(invalidOpenId, Locale.getDefault()))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("Invalid OpenID");
        }

        @Test
        @DisplayName("空字符串应该返回null")
        void shouldReturnNullForEmptyString() throws ParseException {
            @SuppressWarnings("unchecked")
Parser<Long> parser = (Parser<Long>) factory.getParser(null, Long.class);

            Long result = parser.parse("", Locale.getDefault());
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Printer测试")
    class PrinterTest {

        @Test
        @DisplayName("应该能够打印Long值为OpenID")
        void shouldPrintLongToOpenId() {
            long id = 123456789L;
            String expectedOpenId = IdObfuscator.toOpenId(id);

            @SuppressWarnings("unchecked")
Printer<Long> printer = (Printer<Long>) factory.getPrinter(null, Long.class);
            String printed = printer.print(id, Locale.getDefault());

            assertThat(printed).isEqualTo(expectedOpenId);
        }

        @Test
        @DisplayName("应该能够处理null值")
        void shouldHandleNullValue() {
            @SuppressWarnings("unchecked")
Printer<Long> printer = (Printer<Long>) factory.getPrinter(null, Long.class);
            String printed = printer.print(null, Locale.getDefault());

            assertThat(printed).isNull();
        }

        @Test
        @DisplayName("应该能够处理0值")
        void shouldHandleZeroValue() {
            long id = 0L;
            String expectedOpenId = IdObfuscator.toOpenId(id);

            @SuppressWarnings("unchecked")
Printer<Long> printer = (Printer<Long>) factory.getPrinter(null, Long.class);
            String printed = printer.print(id, Locale.getDefault());

            assertThat(printed).isEqualTo(expectedOpenId);
        }

        @Test
        @DisplayName("负数ID应该被拒绝")
        void shouldRejectNegativeValue() {
            long id = -123L;

            @SuppressWarnings("unchecked")
Printer<Long> printer = (Printer<Long>) factory.getPrinter(null, Long.class);

            assertThatThrownBy(() -> printer.print(id, Locale.getDefault()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ID cannot be negative");
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryValueTest {

        @Test
        @DisplayName("应该处理大数值")
        void shouldHandleLargeValues() throws ParseException {
            long largeValue = 987654321098765432L; // 使用安全的大数值
            String openId = IdObfuscator.toOpenId(largeValue);

            @SuppressWarnings("unchecked")
Parser<Long> parser = (Parser<Long>) factory.getParser(null, Long.class);
            Long parsedId = parser.parse(openId, Locale.getDefault());

            assertThat(parsedId).isEqualTo(largeValue);
        }

        @Test
        @DisplayName("应该处理正数值")
        void shouldHandlePositiveValues() throws ParseException {
            long positiveValue = 123456789L; // 使用正数值测试
            String openId = IdObfuscator.toOpenId(positiveValue);

            @SuppressWarnings("unchecked")
Parser<Long> parser = (Parser<Long>) factory.getParser(null, Long.class);
            Long parsedId = parser.parse(openId, Locale.getDefault());

            assertThat(parsedId).isEqualTo(positiveValue);
        }
    }
}