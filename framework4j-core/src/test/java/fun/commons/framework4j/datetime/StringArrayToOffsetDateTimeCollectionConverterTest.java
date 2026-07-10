package fun.commons.framework4j.datetime;

import org.junit.jupiter.api.*;
import org.springframework.core.convert.TypeDescriptor;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * StringArrayToOffsetDateTimeCollectionConverter 单元测试
 */
@DisplayName("StringArrayToOffsetDateTimeCollectionConverter 单元测试")
class StringArrayToOffsetDateTimeCollectionConverterTest {

    private StringArrayToOffsetDateTimeCollectionConverter converter;
    private TypeDescriptor stringArrayType;

    @BeforeEach
    void setUp() {
        converter = new StringArrayToOffsetDateTimeCollectionConverter();
        stringArrayType = TypeDescriptor.valueOf(String[].class);
    }

    // ==================== List<OffsetDateTime> 转换测试 ====================

    @Nested
    @DisplayName("List<OffsetDateTime> 转换测试")
    class ListConversionTest {

        @Test
        @DisplayName("应正确转换 String[] → List<OffsetDateTime>")
        void shouldConvertStringArrayToListOfOffsetDateTime() throws NoSuchFieldException {
            String[] source = {"2024-01-01T10:00:00+08:00", "2024-01-02T14:30:00+08:00"};
            TypeDescriptor targetType = getFieldTypeDescriptor(TestDTO.class, "times");

            Object result = converter.convert(source, stringArrayType, targetType);

            assertThat(result).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<OffsetDateTime> resultList = (List<OffsetDateTime>) result;
            assertThat(resultList).hasSize(2);
            assertThat(resultList.get(0)).isInstanceOf(OffsetDateTime.class);
            assertThat(resultList.get(0).getYear()).isEqualTo(2024);
            assertThat(resultList.get(0).getMonthValue()).isEqualTo(1);
            assertThat(resultList.get(0).getDayOfMonth()).isEqualTo(1);
        }

        @Test
        @DisplayName("应处理包含 null 的数组")
        void shouldHandleNullElementsInArray() throws NoSuchFieldException {
            String[] source = {"2024-01-01T10:00:00+08:00", null, "2024-01-02T14:30:00+08:00"};
            TypeDescriptor targetType = getFieldTypeDescriptor(TestDTO.class, "times");

            @SuppressWarnings("unchecked")
            List<OffsetDateTime> result = (List<OffsetDateTime>) converter.convert(source, stringArrayType, targetType);

            assertThat(result).hasSize(3);
            assertThat(result.get(0)).isNotNull();
            assertThat(result.get(1)).isNull();
            assertThat(result.get(2)).isNotNull();
        }

        @Test
        @DisplayName("应处理空数组")
        void shouldHandleEmptyArray() throws NoSuchFieldException {
            String[] source = {};
            TypeDescriptor targetType = getFieldTypeDescriptor(TestDTO.class, "times");

            @SuppressWarnings("unchecked")
            List<OffsetDateTime> result = (List<OffsetDateTime>) converter.convert(source, stringArrayType, targetType);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("应支持多种时间格式 (时间戳、ISO-8601、本地格式)")
        void shouldSupportMultipleTimeFormats() throws NoSuchFieldException {
            String[] source = {
                "1704081600000",                    // 时间戳
                "2024-01-01T10:00:00+08:00",       // ISO-8601
                "2024-01-01 10:00:00"              // 本地格式
            };
            TypeDescriptor targetType = getFieldTypeDescriptor(TestDTO.class, "times");

            @SuppressWarnings("unchecked")
            List<OffsetDateTime> result = (List<OffsetDateTime>) converter.convert(source, stringArrayType, targetType);

            assertThat(result).hasSize(3);
            assertThat(result).allMatch(Objects::nonNull);
        }

        @Test
        @DisplayName("应处理包含空字符串的数组")
        void shouldHandleArrayWithBlankStrings() throws NoSuchFieldException {
            String[] source = {"2024-01-01T10:00:00+08:00", "", "   ", "2024-01-02T14:30:00+08:00"};
            TypeDescriptor targetType = getFieldTypeDescriptor(TestDTO.class, "times");

            @SuppressWarnings("unchecked")
            List<OffsetDateTime> result = (List<OffsetDateTime>) converter.convert(source, stringArrayType, targetType);

            assertThat(result).hasSize(4);
            assertThat(result.get(0)).isNotNull();
            assertThat(result.get(1)).isNull(); // 空字符串转换为 null
            assertThat(result.get(2)).isNull(); // 空白字符串转换为 null
            assertThat(result.get(3)).isNotNull();
        }
    }

    // ==================== Set<OffsetDateTime> 转换测试 ====================

    @Nested
    @DisplayName("Set<OffsetDateTime> 转换测试")
    class SetConversionTest {

        @Test
        @DisplayName("应正确转换 String[] → Set<OffsetDateTime>")
        void shouldConvertStringArrayToSetOfOffsetDateTime() throws NoSuchFieldException {
            String[] source = {"2024-01-01T10:00:00+08:00", "2024-01-02T14:30:00+08:00"};
            TypeDescriptor targetType = getFieldTypeDescriptor(TestDTO.class, "uniqueTimes");

            Object result = converter.convert(source, stringArrayType, targetType);

            assertThat(result).isInstanceOf(Set.class);
            @SuppressWarnings("unchecked")
            Set<OffsetDateTime> resultSet = (Set<OffsetDateTime>) result;
            assertThat(resultSet).hasSize(2);
        }

        @Test
        @DisplayName("应去除重复元素")
        void shouldRemoveDuplicateElements() throws NoSuchFieldException {
            OffsetDateTime fixedTime = OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(8));
            String timeString = "2024-01-01T10:00:00+08:00";

            String[] source = {
                timeString,
                timeString,  // 重复
                "2024-01-02T14:30:00+08:00"
            };
            TypeDescriptor targetType = getFieldTypeDescriptor(TestDTO.class, "uniqueTimes");

            @SuppressWarnings("unchecked")
            Set<OffsetDateTime> result = (Set<OffsetDateTime>) converter.convert(source, stringArrayType, targetType);

            assertThat(result).hasSize(2); // 去重后只有 2 个
        }

        @Test
        @DisplayName("应保持插入顺序（使用 LinkedHashSet）")
        void shouldPreserveInsertionOrder() throws NoSuchFieldException {
            String[] source = {
                "2024-01-03T10:00:00+08:00",
                "2024-01-01T10:00:00+08:00",
                "2024-01-02T10:00:00+08:00"
            };
            TypeDescriptor targetType = getFieldTypeDescriptor(TestDTO.class, "uniqueTimes");

            @SuppressWarnings("unchecked")
            Set<OffsetDateTime> result = (Set<OffsetDateTime>) converter.convert(source, stringArrayType, targetType);

            assertThat(result).hasSize(3);
            // LinkedHashSet 保持插入顺序
            Iterator<OffsetDateTime> iterator = result.iterator();
            OffsetDateTime first = iterator.next();
            assertThat(first.getDayOfMonth()).isEqualTo(3); // 第一个插入的
        }

        @Test
        @DisplayName("应处理空 Set")
        void shouldHandleEmptySet() throws NoSuchFieldException {
            String[] source = {};
            TypeDescriptor targetType = getFieldTypeDescriptor(TestDTO.class, "uniqueTimes");

            @SuppressWarnings("unchecked")
            Set<OffsetDateTime> result = (Set<OffsetDateTime>) converter.convert(source, stringArrayType, targetType);

            assertThat(result).isEmpty();
        }
    }

    // ==================== OffsetDateTime[] 数组转换测试 ====================

    @Nested
    @DisplayName("OffsetDateTime[] 数组转换测试")
    class ArrayConversionTest {

        @Test
        @DisplayName("应正确转换 String[] → OffsetDateTime[]")
        void shouldConvertStringArrayToOffsetDateTimeArray() {
            String[] source = {"2024-01-01T10:00:00+08:00", "2024-01-02T14:30:00+08:00"};
            TypeDescriptor targetType = TypeDescriptor.valueOf(OffsetDateTime[].class);

            Object result = converter.convert(source, stringArrayType, targetType);

            assertThat(result).isInstanceOf(OffsetDateTime[].class);
            OffsetDateTime[] resultArray = (OffsetDateTime[]) result;
            assertThat(resultArray).hasSize(2);
            assertThat(resultArray[0]).isInstanceOf(OffsetDateTime.class);
        }

        @Test
        @DisplayName("应处理包含 null 的数组")
        void shouldHandleArrayWithNullElements() {
            String[] source = {"2024-01-01T10:00:00+08:00", null, "2024-01-02T14:30:00+08:00"};
            TypeDescriptor targetType = TypeDescriptor.valueOf(OffsetDateTime[].class);

            OffsetDateTime[] result = (OffsetDateTime[]) converter.convert(source, stringArrayType, targetType);

            assertThat(result).hasSize(3);
            assertThat(result[0]).isNotNull();
            assertThat(result[1]).isNull();
            assertThat(result[2]).isNotNull();
        }

        @Test
        @DisplayName("应处理空数组")
        void shouldHandleEmptyArrayToArray() {
            String[] source = {};
            TypeDescriptor targetType = TypeDescriptor.valueOf(OffsetDateTime[].class);

            OffsetDateTime[] result = (OffsetDateTime[]) converter.convert(source, stringArrayType, targetType);

            assertThat(result).isEmpty();
        }
    }

    // ==================== 边界情况测试 ====================

    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTest {

        @Test
        @DisplayName("应处理 null 输入")
        void shouldHandleNullInput() throws NoSuchFieldException {
            TypeDescriptor targetType = getFieldTypeDescriptor(TestDTO.class, "times");

            Object result = converter.convert(null, stringArrayType, targetType);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("应处理格式错误的字符串（抛出异常）")
        void shouldHandleInvalidFormat() throws NoSuchFieldException {
            String[] source = {"invalid-date-format"};
            TypeDescriptor targetType = getFieldTypeDescriptor(TestDTO.class, "times");

            assertThatThrownBy(() ->
                converter.convert(source, stringArrayType, targetType)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("不应处理非 OffsetDateTime 集合（返回 null）")
        void shouldNotHandleNonOffsetDateTimeCollection() throws NoSuchFieldException {
            String[] source = {"2024-01-01"};
            TypeDescriptor targetType = getFieldTypeDescriptor(TestDTO.class, "stringList");

            Object result = converter.convert(source, stringArrayType, targetType);

            assertThat(result).isNull(); // 让其他转换器处理
        }

        @Test
        @DisplayName("应正确识别 getConvertibleTypes")
        void shouldReturnCorrectConvertibleTypes() {
            Set<org.springframework.core.convert.converter.GenericConverter.ConvertiblePair> pairs =
                converter.getConvertibleTypes();

            assertThat(pairs).hasSize(3);
            assertThat(pairs).anyMatch(p ->
                p.getSourceType() == String[].class && p.getTargetType() == List.class);
            assertThat(pairs).anyMatch(p ->
                p.getSourceType() == String[].class && p.getTargetType() == Set.class);
            assertThat(pairs).anyMatch(p ->
                p.getSourceType() == String[].class && p.getTargetType() == OffsetDateTime[].class);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取字段的 TypeDescriptor
     */
    private TypeDescriptor getFieldTypeDescriptor(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Field field = clazz.getDeclaredField(fieldName);
        return new TypeDescriptor(field);
    }

    // ==================== 测试用 DTO ====================

    @SuppressWarnings("unused")
    static class TestDTO {
        public List<OffsetDateTime> times;
        public Set<OffsetDateTime> uniqueTimes;
        public List<String> stringList;
    }
}
