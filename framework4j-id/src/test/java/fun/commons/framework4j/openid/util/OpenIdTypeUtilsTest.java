package fun.commons.framework4j.openid.util;

import fun.commons.framework4j.id.util.IdObfuscator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * OpenIdTypeUtils 单元测试
 */
@DisplayName("OpenIdTypeUtils 单元测试")
class OpenIdTypeUtilsTest {

    @Nested
    @DisplayName("类型检查测试")
    class TypeCheckingTest {

        @ParameterizedTest
        @ValueSource(classes = {Long.class, long.class, Integer.class, int.class})
        @DisplayName("应该支持所有单一ID类型")
        void shouldSupportSingleTypes(Class<?> type) {
            assertThat(OpenIdTypeUtils.isSupportedSingleType(type))
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(classes = {List.class, Set.class})
        @DisplayName("应该支持集合类型")
        void shouldSupportCollectionTypes(Class<?> type) {
            assertThat(OpenIdTypeUtils.isSupportedCollectionType(type))
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(classes = {long[].class, Long[].class, int[].class, Integer[].class})
        @DisplayName("应该支持数组类型")
        void shouldSupportArrayTypes(Class<?> type) {
            assertThat(OpenIdTypeUtils.isSupportedArrayType(type))
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(classes = {String.class, Double.class, Object.class})
        @DisplayName("不应该支持不相关的类型")
        void shouldNotSupportUnrelatedTypes(Class<?> type) {
            assertThat(OpenIdTypeUtils.isSupportedSingleType(type))
                    .isFalse();
            assertThat(OpenIdTypeUtils.isSupportedCollectionType(type))
                    .isFalse();
            assertThat(OpenIdTypeUtils.isSupportedArrayType(type))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("ID验证测试")
    class ValidationTest {

        @Test
        @DisplayName("应该接受有效的ID")
        void shouldAcceptValidId() {
            assertThatCode(() -> OpenIdTypeUtils.validateId(1L))
                    .doesNotThrowAnyException();
            assertThatCode(() -> OpenIdTypeUtils.validateId(0L))
                    .doesNotThrowAnyException();
            assertThatCode(() -> OpenIdTypeUtils.validateId(Long.MAX_VALUE))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("应该拒绝负数ID")
        void shouldRejectNegativeId() {
            assertThatThrownBy(() -> OpenIdTypeUtils.validateId(-1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("ID cannot be negative: -1");
        }
    }

    @Nested
    @DisplayName("序列化测试")
    class SerializationTest {

        @Test
        @DisplayName("应该正确转换单一ID类型")
        void shouldConvertSingleTypes() {
            // Long 类型
            Long longValue = 12345L;
            Object result = OpenIdTypeUtils.convertToOpenId(longValue);
            assertThat(result).isInstanceOf(String.class);
            assertThat(IdObfuscator.fromOpenId((String) result)).isEqualTo(longValue);

            // Integer 类型
            Integer intValue = 123;
            result = OpenIdTypeUtils.convertToOpenId(intValue);
            assertThat(result).isInstanceOf(String.class);
            assertThat(IdObfuscator.fromOpenId((String) result)).isEqualTo(intValue.longValue());
        }

        @Test
        @DisplayName("应该正确转换List类型")
        void shouldConvertListTypes() {
            List<Long> longList = Arrays.asList(1L, 2L, 3L);
            Object result = OpenIdTypeUtils.convertToOpenId(longList);
            assertThat(result).isInstanceOf(List.class);
            List<String> stringList = (List<String>) result;
            assertThat(stringList).hasSize(3);
            assertThat(stringList).allMatch(s -> s.length() > 0);

            List<Integer> intList = Arrays.asList(1, 2, 3);
            result = OpenIdTypeUtils.convertToOpenId(intList);
            assertThat(result).isInstanceOf(List.class);
        }

        @Test
        @DisplayName("应该正确转换Set类型")
        void shouldConvertSetTypes() {
            Set<Long> longSet = new HashSet<>(Arrays.asList(1L, 2L, 3L));
            Object result = OpenIdTypeUtils.convertToOpenId(longSet);
            assertThat(result).isInstanceOf(Set.class);
            Set<String> stringSet = (Set<String>) result;
            assertThat(stringSet).hasSize(3);

            Set<Integer> intSet = new HashSet<>(Arrays.asList(1, 2, 3));
            result = OpenIdTypeUtils.convertToOpenId(intSet);
            assertThat(result).isInstanceOf(Set.class);
        }

        @Test
        @DisplayName("应该正确转换数组类型")
        void shouldConvertArrayTypes() {
            long[] longArray = {1L, 2L, 3L};
            Object result = OpenIdTypeUtils.convertToOpenId(longArray);
            assertThat(result).isInstanceOf(String[].class);
            String[] stringArray = (String[]) result;
            assertThat(stringArray).hasSize(3);

            int[] intArray = {1, 2, 3};
            result = OpenIdTypeUtils.convertToOpenId(intArray);
            assertThat(result).isInstanceOf(String[].class);

            Long[] longObjArray = {1L, 2L, 3L};
            result = OpenIdTypeUtils.convertToOpenId(longObjArray);
            assertThat(result).isInstanceOf(String[].class);

            Integer[] intObjArray = {1, 2, 3};
            result = OpenIdTypeUtils.convertToOpenId(intObjArray);
            assertThat(result).isInstanceOf(String[].class);
        }

        @Test
        @DisplayName("应该正确处理空集合")
        void shouldHandleEmptyCollections() {
            List<Long> emptyList = Collections.emptyList();
            Object result = OpenIdTypeUtils.convertToOpenId(emptyList);
            assertThat(result).isInstanceOf(List.class);
            assertThat((List<?>) result).isEmpty();

            Set<Long> emptySet = Collections.emptySet();
            result = OpenIdTypeUtils.convertToOpenId(emptySet);
            assertThat(result).isInstanceOf(Set.class);
            assertThat((Set<?>) result).isEmpty();
        }

        @Test
        @DisplayName("应该正确处理null值")
        void shouldHandleNullValues() {
            Object result = OpenIdTypeUtils.convertToOpenId(null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("应该忽略不支持的类型")
        void shouldIgnoreUnsupportedTypes() {
            String unsupported = "test";
            Object result = OpenIdTypeUtils.convertToOpenId(unsupported);
            assertThat(result).isSameAs(unsupported);

            List<String> unsupportedList = Arrays.asList("test", "unsupported");
            result = OpenIdTypeUtils.convertToOpenId(unsupportedList);
            assertThat(result).isSameAs(unsupportedList);
        }
    }

    @Nested
    @DisplayName("反序列化测试")
    class DeserializationTest {

        @Test
        @DisplayName("应该正确转换为Long类型")
        void shouldConvertToLong() throws ParseException {
            String openId = IdObfuscator.toOpenId(12345L);
            Object result = OpenIdTypeUtils.convertFromOpenId(openId, Long.class);
            assertThat(result).isEqualTo(12345L);

            result = OpenIdTypeUtils.convertFromOpenId(openId, long.class);
            assertThat(result).isEqualTo(12345L);
        }

        @Test
        @DisplayName("应该正确转换为Integer类型")
        void shouldConvertToInteger() throws ParseException {
            String openId = IdObfuscator.toOpenId(123L);
            Object result = OpenIdTypeUtils.convertFromOpenId(openId, Integer.class);
            assertThat(result).isEqualTo(123);

            result = OpenIdTypeUtils.convertFromOpenId(openId, int.class);
            assertThat(result).isEqualTo(123);
        }

        @Test
        @DisplayName("应该处理纯数字兼容性")
        void shouldHandleNumericCompatibility() throws ParseException {
            Object result = OpenIdTypeUtils.convertFromOpenId("123", Long.class);
            assertThat(result).isEqualTo(123L);

            result = OpenIdTypeUtils.convertFromOpenId("456", Integer.class);
            assertThat(result).isEqualTo(456);
        }

        @Test
        @DisplayName("应该拒绝负数")
        void shouldRejectNegativeNumbers() {
            assertThatThrownBy(() -> OpenIdTypeUtils.convertFromOpenId("-1", Long.class))
                    .isInstanceOf(java.text.ParseException.class)
                    .hasMessageContaining("ID cannot be negative");
        }

        @Test
        @DisplayName("应该拒绝过大的Integer值")
        void shouldRejectTooLargeInteger() {
            long largeValue = (long) Integer.MAX_VALUE + 1;
            String openId = IdObfuscator.toOpenId(largeValue);

            assertThatThrownBy(() -> OpenIdTypeUtils.convertFromOpenId(openId, Integer.class))
                    .isInstanceOf(ParseException.class)
                    .hasMessageContaining("too large for Integer");
        }

        @Test
        @DisplayName("应该正确处理null和空字符串")
        void shouldHandleNullOrEmpty() throws ParseException {
            Object result = OpenIdTypeUtils.convertFromOpenId(null, Long.class);
            assertThat(result).isNull();

            result = OpenIdTypeUtils.convertFromOpenId("", Long.class);
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("性能测试")
    class PerformanceTest {

        @Test
        @Timeout(value = 1, unit = TimeUnit.SECONDS)
        @DisplayName("大量转换应该在高性能时间内完成")
        void shouldHandleBulkConversionsQuickly() {
            // 准备测试数据
            List<Long> largeList = new ArrayList<>();
            for (int i = 0; i < 10000; i++) {
                largeList.add((long) i);
            }

            // 执行转换
            Object result = OpenIdTypeUtils.convertToOpenId(largeList);
            assertThat(result).isInstanceOf(List.class);
            assertThat((List<?>) result).hasSize(10000);
        }

        @Test
        @Timeout(value = 1, unit = TimeUnit.SECONDS)
        @DisplayName("大量反转换应该在高性能时间内完成")
        void shouldHandleBulkReversionsQuickly() throws ParseException {
            // 准备测试数据
            List<String> openIdList = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                openIdList.add(IdObfuscator.toOpenId((long) i));
            }

            // 执行转换
            for (String openId : openIdList) {
                Object result = OpenIdTypeUtils.convertFromOpenId(openId, Long.class);
                assertThat(result).isInstanceOf(Long.class);
            }
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTest {

        @Test
        @DisplayName("应该正确处理最大值")
        void shouldHandleMaxValues() throws ParseException {
            Long maxLong = Long.MAX_VALUE;
            Object result = OpenIdTypeUtils.convertToOpenId(maxLong);
            assertThat(result).isInstanceOf(String.class);

            String openId = (String) result;
            Object reverted = OpenIdTypeUtils.convertFromOpenId(openId, Long.class);
            assertThat(reverted).isEqualTo(maxLong);

            Integer maxInt = Integer.MAX_VALUE;
            result = OpenIdTypeUtils.convertToOpenId(maxInt);
            openId = (String) result;
            reverted = OpenIdTypeUtils.convertFromOpenId(openId, Integer.class);
            assertThat(reverted).isEqualTo(maxInt);
        }

        @Test
        @DisplayName("应该正确处理零值")
        void shouldHandleZeroValue() throws ParseException {
            Long zeroLong = 0L;
            Object result = OpenIdTypeUtils.convertToOpenId(zeroLong);
            assertThat(result).isInstanceOf(String.class);

            String openId = (String) result;
            Object reverted = OpenIdTypeUtils.convertFromOpenId(openId, Long.class);
            assertThat(reverted).isEqualTo(zeroLong);

            Integer zeroInt = 0;
            result = OpenIdTypeUtils.convertToOpenId(zeroInt);
            openId = (String) result;
            reverted = OpenIdTypeUtils.convertFromOpenId(openId, Integer.class);
            assertThat(reverted).isEqualTo(zeroInt);
        }

        @Test
        @DisplayName("应该正确处理混合类型集合")
        void shouldHandleMixedTypeCollections() {
            List<Object> mixedList = Arrays.asList(1L, "test", 2);
            Object result = OpenIdTypeUtils.convertToOpenId(mixedList);
            // 应该只转换支持的类型，忽略其他类型
            assertThat(result).isInstanceOf(List.class);
            List<?> resultList = (List<?>) result;
            assertThat(resultList).hasSize(3);
            assertThat(resultList.get(0)).isInstanceOf(String.class); // 1L 被转换
            assertThat(resultList.get(1)).isEqualTo("test");        // String 保持不变
            assertThat(resultList.get(2)).isInstanceOf(String.class); // 2 被转换
        }
    }
}