package fun.commons.framework4j.datetime;

import org.junit.jupiter.api.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DynamicTimeFilter 单元测试
 * 测试 OffsetDateTime 及其集合类型的格式化功能
 */
@DisplayName("DynamicTimeFilter 单元测试")
class DynamicTimeFilterTest {

    private DynamicTimeFilter filter;
    private OffsetDateTime testDateTime;
    private String expectedFormattedString;

    @BeforeEach
    void setUp() {
        filter = new DynamicTimeFilter();
        // 创建固定的测试时间: 2024-01-01 12:00:00 +08:00
        testDateTime = OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.ofHours(8));
        expectedFormattedString = "2024-01-01 12:00:00";
    }

    @AfterEach
    void tearDown() {
        // 清理 TimeContext，避免影响其他测试
        TimeContext.clear();
    }

    // ==================== 单个对象测试 ====================

    @Nested
    @DisplayName("单个 OffsetDateTime 测试")
    class SingleOffsetDateTimeTest {

        @Test
        @DisplayName("启用本地格式时应格式化单个 OffsetDateTime")
        void shouldFormatSingleOffsetDateTimeWhenLocalFormatEnabled() {
            TimeContext.setUseLocal(true);

            Object result = filter.apply(null, "createAt", testDateTime);

            assertThat(result).isInstanceOf(String.class);
            assertThat(result).isEqualTo(expectedFormattedString);
        }

        @Test
        @DisplayName("禁用本地格式时应返回原 OffsetDateTime")
        void shouldReturnOriginalOffsetDateTimeWhenLocalFormatDisabled() {
            TimeContext.setUseLocal(false);

            Object result = filter.apply(null, "createAt", testDateTime);

            assertThat(result).isInstanceOf(OffsetDateTime.class);
            assertThat(result).isEqualTo(testDateTime);
        }
    }

    // ==================== List 测试 ====================

    @Nested
    @DisplayName("List<OffsetDateTime> 测试")
    class ListTest {

        @Test
        @DisplayName("应格式化 List<OffsetDateTime>")
        void shouldFormatListOfOffsetDateTime() {
            TimeContext.setUseLocal(true);

            OffsetDateTime dt1 = OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(8));
            OffsetDateTime dt2 = OffsetDateTime.of(2024, 1, 2, 14, 30, 0, 0, ZoneOffset.ofHours(8));
            List<OffsetDateTime> list = Arrays.asList(dt1, dt2);

            Object result = filter.apply(null, "dates", list);

            assertThat(result).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<String> formattedList = (List<String>) result;
            assertThat(formattedList).hasSize(2);
            assertThat(formattedList.get(0)).isEqualTo("2024-01-01 10:00:00");
            assertThat(formattedList.get(1)).isEqualTo("2024-01-02 14:30:00");
        }

        @Test
        @DisplayName("应处理空 List")
        void shouldHandleEmptyList() {
            TimeContext.setUseLocal(true);

            List<OffsetDateTime> emptyList = new ArrayList<>();

            Object result = filter.apply(null, "dates", emptyList);

            assertThat(result).isSameAs(emptyList);
        }

        @Test
        @DisplayName("应处理包含 null 元素的 List")
        void shouldHandleListWithNullElements() {
            TimeContext.setUseLocal(true);

            OffsetDateTime dt1 = OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(8));
            List<OffsetDateTime> list = Arrays.asList(dt1, null, testDateTime);

            Object result = filter.apply(null, "dates", list);

            assertThat(result).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<Object> formattedList = (List<Object>) result;
            assertThat(formattedList).hasSize(3);
            assertThat(formattedList.get(0)).isEqualTo("2024-01-01 10:00:00");
            assertThat(formattedList.get(1)).isNull();
            assertThat(formattedList.get(2)).isEqualTo(expectedFormattedString);
        }

        @Test
        @DisplayName("不应格式化其他类型的 List")
        void shouldNotFormatOtherTypeList() {
            TimeContext.setUseLocal(true);

            List<String> stringList = Arrays.asList("str1", "str2");

            Object result = filter.apply(null, "strings", stringList);

            assertThat(result).isSameAs(stringList);
        }

        @Test
        @DisplayName("禁用本地格式时应返回原 List")
        void shouldReturnOriginalListWhenLocalFormatDisabled() {
            TimeContext.setUseLocal(false);

            List<OffsetDateTime> list = Arrays.asList(testDateTime);

            Object result = filter.apply(null, "dates", list);

            assertThat(result).isSameAs(list);
        }
    }

    // ==================== Set 测试 ====================

    @Nested
    @DisplayName("Set<OffsetDateTime> 测试")
    class SetTest {

        @Test
        @DisplayName("应格式化 Set<OffsetDateTime>")
        void shouldFormatSetOfOffsetDateTime() {
            TimeContext.setUseLocal(true);

            OffsetDateTime dt1 = OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(8));
            OffsetDateTime dt2 = OffsetDateTime.of(2024, 1, 2, 14, 30, 0, 0, ZoneOffset.ofHours(8));
            Set<OffsetDateTime> set = new LinkedHashSet<>(Arrays.asList(dt1, dt2));

            Object result = filter.apply(null, "dates", set);

            assertThat(result).isInstanceOf(Set.class);
            @SuppressWarnings("unchecked")
            Set<String> formattedSet = (Set<String>) result;
            assertThat(formattedSet).hasSize(2);
            assertThat(formattedSet).contains("2024-01-01 10:00:00", "2024-01-02 14:30:00");
        }

        @Test
        @DisplayName("应处理空 Set")
        void shouldHandleEmptySet() {
            TimeContext.setUseLocal(true);

            Set<OffsetDateTime> emptySet = new HashSet<>();

            Object result = filter.apply(null, "dates", emptySet);

            assertThat(result).isSameAs(emptySet);
        }

        @Test
        @DisplayName("不应格式化其他类型的 Set")
        void shouldNotFormatOtherTypeSet() {
            TimeContext.setUseLocal(true);

            Set<String> stringSet = new HashSet<>(Arrays.asList("str1", "str2"));

            Object result = filter.apply(null, "strings", stringSet);

            assertThat(result).isSameAs(stringSet);
        }

        @Test
        @DisplayName("禁用本地格式时应返回原 Set")
        void shouldReturnOriginalSetWhenLocalFormatDisabled() {
            TimeContext.setUseLocal(false);

            Set<OffsetDateTime> set = new HashSet<>(Arrays.asList(testDateTime));

            Object result = filter.apply(null, "dates", set);

            assertThat(result).isSameAs(set);
        }
    }

    // ==================== 数组测试 ====================

    @Nested
    @DisplayName("OffsetDateTime[] 数组测试")
    class ArrayTest {

        @Test
        @DisplayName("应格式化 OffsetDateTime 数组")
        void shouldFormatOffsetDateTimeArray() {
            TimeContext.setUseLocal(true);

            OffsetDateTime dt1 = OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(8));
            OffsetDateTime dt2 = OffsetDateTime.of(2024, 1, 2, 14, 30, 0, 0, ZoneOffset.ofHours(8));
            OffsetDateTime[] array = new OffsetDateTime[]{dt1, dt2};

            Object result = filter.apply(null, "dates", array);

            assertThat(result).isInstanceOf(String[].class);
            String[] formattedArray = (String[]) result;
            assertThat(formattedArray).hasSize(2);
            assertThat(formattedArray[0]).isEqualTo("2024-01-01 10:00:00");
            assertThat(formattedArray[1]).isEqualTo("2024-01-02 14:30:00");
        }

        @Test
        @DisplayName("应处理包含 null 元素的数组")
        void shouldHandleArrayWithNullElements() {
            TimeContext.setUseLocal(true);

            OffsetDateTime dt1 = OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(8));
            OffsetDateTime[] array = new OffsetDateTime[]{dt1, null, testDateTime};

            Object result = filter.apply(null, "dates", array);

            assertThat(result).isInstanceOf(String[].class);
            String[] formattedArray = (String[]) result;
            assertThat(formattedArray).hasSize(3);
            assertThat(formattedArray[0]).isEqualTo("2024-01-01 10:00:00");
            assertThat(formattedArray[1]).isNull();
            assertThat(formattedArray[2]).isEqualTo(expectedFormattedString);
        }

        @Test
        @DisplayName("应处理空数组")
        void shouldHandleEmptyArray() {
            TimeContext.setUseLocal(true);

            OffsetDateTime[] emptyArray = new OffsetDateTime[0];

            Object result = filter.apply(null, "dates", emptyArray);

            assertThat(result).isSameAs(emptyArray);
        }

        @Test
        @DisplayName("不应格式化其他类型的数组")
        void shouldNotFormatOtherTypeArray() {
            TimeContext.setUseLocal(true);

            String[] stringArray = new String[]{"str1", "str2"};

            Object result = filter.apply(null, "strings", stringArray);

            assertThat(result).isSameAs(stringArray);
        }

        @Test
        @DisplayName("禁用本地格式时应返回原数组")
        void shouldReturnOriginalArrayWhenLocalFormatDisabled() {
            TimeContext.setUseLocal(false);

            OffsetDateTime[] array = new OffsetDateTime[]{testDateTime};

            Object result = filter.apply(null, "dates", array);

            assertThat(result).isSameAs(array);
        }
    }

    // ==================== 其他 Collection 子类测试 ====================

    @Nested
    @DisplayName("其他 Collection 子类测试")
    class OtherCollectionTest {

        @Test
        @DisplayName("应格式化 Queue<OffsetDateTime>")
        void shouldFormatQueueOfOffsetDateTime() {
            TimeContext.setUseLocal(true);

            OffsetDateTime dt1 = OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(8));
            OffsetDateTime dt2 = OffsetDateTime.of(2024, 1, 2, 14, 30, 0, 0, ZoneOffset.ofHours(8));
            Queue<OffsetDateTime> queue = new LinkedList<>(Arrays.asList(dt1, dt2));

            Object result = filter.apply(null, "dates", queue);

            assertThat(result).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<String> formattedList = (List<String>) result;
            assertThat(formattedList).hasSize(2);
            assertThat(formattedList.get(0)).isEqualTo("2024-01-01 10:00:00");
            assertThat(formattedList.get(1)).isEqualTo("2024-01-02 14:30:00");
        }

        @Test
        @DisplayName("应格式化 Deque<OffsetDateTime>")
        void shouldFormatDequeOfOffsetDateTime() {
            TimeContext.setUseLocal(true);

            OffsetDateTime dt1 = OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(8));
            Deque<OffsetDateTime> deque = new ArrayDeque<>(Collections.singletonList(dt1));

            Object result = filter.apply(null, "dates", deque);

            assertThat(result).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<String> formattedList = (List<String>) result;
            assertThat(formattedList).hasSize(1);
            assertThat(formattedList.get(0)).isEqualTo("2024-01-01 10:00:00");
        }
    }

    // ==================== Null 安全测试 ====================

    @Nested
    @DisplayName("Null 安全测试")
    class NullSafetyTest {

        @Test
        @DisplayName("应处理 null 值")
        void shouldHandleNullValue() {
            TimeContext.setUseLocal(true);

            Object result = filter.apply(null, "field", null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("应处理全为 null 的 List")
        void shouldHandleListWithAllNulls() {
            TimeContext.setUseLocal(true);

            List<OffsetDateTime> list = Arrays.asList(null, null);

            Object result = filter.apply(null, "dates", list);

            // 全为 null 时无法判断类型，返回原列表
            assertThat(result).isSameAs(list);
        }
    }

    // ==================== TimeContext 测试 ====================

    @Nested
    @DisplayName("TimeContext 上下文测试")
    class TimeContextTest {

        @Test
        @DisplayName("应响应 TimeContext 开关")
        void shouldRespondToTimeContextFlag() {
            // 测试启用
            TimeContext.setUseLocal(true);
            Object resultEnabled = filter.apply(null, "date", testDateTime);
            assertThat(resultEnabled).isEqualTo(expectedFormattedString);

            // 测试禁用
            TimeContext.setUseLocal(false);
            Object resultDisabled = filter.apply(null, "date", testDateTime);
            assertThat(resultDisabled).isEqualTo(testDateTime);
        }

        @Test
        @DisplayName("TimeContext 未设置时应返回原值")
        void shouldReturnOriginalValueWhenTimeContextNotSet() {
            // 不设置 TimeContext，默认为 false

            Object result = filter.apply(null, "date", testDateTime);

            assertThat(result).isEqualTo(testDateTime);
        }
    }

    // ==================== 混合类型测试 ====================

    @Nested
    @DisplayName("混合类型测试")
    class MixedTypeTest {

        @Test
        @DisplayName("应处理混合对象类型（非日期对象）")
        void shouldHandleNonDateTimeObjects() {
            TimeContext.setUseLocal(true);

            String stringValue = "test string";
            Integer intValue = 123;
            Object customObject = new Object();

            assertThat(filter.apply(null, "str", stringValue)).isSameAs(stringValue);
            assertThat(filter.apply(null, "num", intValue)).isSameAs(intValue);
            assertThat(filter.apply(null, "obj", customObject)).isSameAs(customObject);
        }
    }
}
