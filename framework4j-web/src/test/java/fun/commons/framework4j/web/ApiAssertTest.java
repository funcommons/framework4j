package fun.commons.framework4j.web;

import fun.commons.framework4j.api.ApiCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiAssert 断言工具类测试
 *
 * @since 1.0.0
 */
@DisplayName("ApiAssert 断言工具类测试")
class ApiAssertTest {

    @Test
    @DisplayName("测试 isTrue - 表达式为真不抛出异常")
    void testIsTrueSuccess() {
        assertDoesNotThrow(() -> ApiAssert.isTrue(true, ApiCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("测试 isTrue - 表达式为假抛出异常")
    void testIsTrueFailure() {
        ApiException exception = assertThrows(ApiException.class,
                () -> ApiAssert.isTrue(false, ApiCode.PARAM_ERROR));
        assertEquals(10100, exception.getCode());
    }

    @Test
    @DisplayName("测试 isTrue - 自定义消息")
    void testIsTrueWithMessage() {
        ApiException exception = assertThrows(ApiException.class,
                () -> ApiAssert.isTrue(false, ApiCode.PARAM_ERROR, "自定义错误"));
        assertEquals("自定义错误", exception.getMessage());
    }

    @Test
    @DisplayName("测试 isFalse - 表达式为假不抛出异常")
    void testIsFalseSuccess() {
        assertDoesNotThrow(() -> ApiAssert.isFalse(false, ApiCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("测试 isFalse - 表达式为真抛出异常")
    void testIsFalseFailure() {
        ApiException exception = assertThrows(ApiException.class,
                () -> ApiAssert.isFalse(true, ApiCode.PARAM_ERROR));
        assertEquals(10100, exception.getCode());
    }

    @Test
    @DisplayName("测试 notNull - 对象不为空不抛出异常")
    void testNotNullSuccess() {
        assertDoesNotThrow(() -> ApiAssert.notNull("test", ApiCode.PARAM_MISSING));
    }

    @Test
    @DisplayName("测试 notNull - 对象为空抛出异常")
    void testNotNullFailure() {
        ApiException exception = assertThrows(ApiException.class,
                () -> ApiAssert.notNull(null, ApiCode.PARAM_MISSING));
        assertEquals(10101, exception.getCode());
    }

    @Test
    @DisplayName("测试 isNull - 对象为空不抛出异常")
    void testIsNullSuccess() {
        assertDoesNotThrow(() -> ApiAssert.isNull(null, ApiCode.UNIQUE_CONFLICT));
    }

    @Test
    @DisplayName("测试 isNull - 对象不为空抛出异常")
    void testIsNullFailure() {
        ApiException exception = assertThrows(ApiException.class,
                () -> ApiAssert.isNull("test", ApiCode.UNIQUE_CONFLICT));
        assertEquals(10401, exception.getCode());
    }

    @Test
    @DisplayName("测试 notEmpty - 字符串不为空不抛出异常")
    void testNotEmptyStringSuccess() {
        assertDoesNotThrow(() -> ApiAssert.notEmpty("test", ApiCode.PARAM_MISSING));
    }

    @Test
    @DisplayName("测试 notEmpty - 字符串为空抛出异常")
    void testNotEmptyStringFailure() {
        ApiException exception = assertThrows(ApiException.class,
                () -> ApiAssert.notEmpty("", ApiCode.PARAM_MISSING));
        assertEquals(10101, exception.getCode());
    }

    @Test
    @DisplayName("测试 notEmpty - 集合不为空不抛出异常")
    void testNotEmptyCollectionSuccess() {
        List<String> list = new ArrayList<>();
        list.add("test");
        assertDoesNotThrow(() -> ApiAssert.notEmpty(list, ApiCode.PARAM_MISSING));
    }

    @Test
    @DisplayName("测试 notEmpty - 集合为空抛出异常")
    void testNotEmptyCollectionFailure() {
        List<String> list = new ArrayList<>();
        ApiException exception = assertThrows(ApiException.class,
                () -> ApiAssert.notEmpty(list, ApiCode.PARAM_MISSING));
        assertEquals(10101, exception.getCode());
    }

    @Test
    @DisplayName("测试 notEmpty - Map 不为空不抛出异常")
    void testNotEmptyMapSuccess() {
        Map<String, String> map = new HashMap<>();
        map.put("key", "value");
        assertDoesNotThrow(() -> ApiAssert.notEmpty(map, ApiCode.PARAM_MISSING));
    }

    @Test
    @DisplayName("测试 notEmpty - Map 为空抛出异常")
    void testNotEmptyMapFailure() {
        Map<String, String> map = new HashMap<>();
        ApiException exception = assertThrows(ApiException.class,
                () -> ApiAssert.notEmpty(map, ApiCode.PARAM_MISSING));
        assertEquals(10101, exception.getCode());
    }

    @Test
    @DisplayName("测试 failure - 直接抛出异常")
    void testFailure() {
        ApiException exception = assertThrows(ApiException.class,
                () -> ApiAssert.failure(ApiCode.SYSTEM_BUSY));
        assertEquals(10001, exception.getCode());
    }

    @Test
    @DisplayName("测试 failure - 直接抛出异常带自定义消息")
    void testFailureWithMessage() {
        ApiException exception = assertThrows(ApiException.class,
                () -> ApiAssert.failure(ApiCode.SYSTEM_BUSY, "系统维护中"));
        assertEquals("系统维护中", exception.getMessage());
    }

    // v2.1 P1: 补 5 个新断言的测试

    @Test
    @DisplayName("测试 equals - 相等不抛异常")
    void testEqualsSuccess() {
        assertDoesNotThrow(() -> ApiAssert.equals("a", "a", ApiCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("测试 equals - 不等抛异常")
    void testEqualsFailure() {
        ApiException exception = assertThrows(ApiException.class,
                () -> ApiAssert.equals("a", "b", ApiCode.PARAM_ERROR));
        assertEquals(10100, exception.getCode());
    }

    @Test
    @DisplayName("测试 equals - null 输入")
    void testEqualsNullInputs() {
        assertDoesNotThrow(() -> ApiAssert.equals(null, null, ApiCode.PARAM_ERROR));
        assertThrows(ApiException.class, () -> ApiAssert.equals(null, "x", ApiCode.PARAM_ERROR));
        assertThrows(ApiException.class, () -> ApiAssert.equals("x", null, ApiCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("测试 notEquals - 不等不抛异常")
    void testNotEqualsSuccess() {
        assertDoesNotThrow(() -> ApiAssert.notEquals("a", "b", ApiCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("测试 notEquals - 相等抛异常")
    void testNotEqualsFailure() {
        ApiException exception = assertThrows(ApiException.class,
                () -> ApiAssert.notEquals("a", "a", ApiCode.PARAM_ERROR));
        assertEquals(10100, exception.getCode());
    }

    @Test
    @DisplayName("测试 isPositive - 正数不抛异常")
    void testIsPositiveSuccess() {
        assertDoesNotThrow(() -> ApiAssert.isPositive(1, ApiCode.PARAM_ERROR));
        assertDoesNotThrow(() -> ApiAssert.isPositive(1L, ApiCode.PARAM_ERROR));
        assertDoesNotThrow(() -> ApiAssert.isPositive(1.5, ApiCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("测试 isPositive - 非正数抛异常")
    void testIsPositiveFailure() {
        assertThrows(ApiException.class, () -> ApiAssert.isPositive(0, ApiCode.PARAM_ERROR));
        assertThrows(ApiException.class, () -> ApiAssert.isPositive(-1, ApiCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("测试 isPositive - null 抛异常")
    void testIsPositiveNull() {
        assertThrows(ApiException.class, () -> ApiAssert.isPositive(null, ApiCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("测试 matches - 匹配正则不抛异常")
    void testMatchesSuccess() {
        assertDoesNotThrow(() -> ApiAssert.matches("abc123", "[a-z]+[0-9]+", ApiCode.PARAM_FORMAT_ERROR));
    }

    @Test
    @DisplayName("测试 matches - 不匹配抛异常")
    void testMatchesFailure() {
        ApiException exception = assertThrows(ApiException.class,
                () -> ApiAssert.matches("ABC", "[a-z]+", ApiCode.PARAM_FORMAT_ERROR));
        assertEquals(10102, exception.getCode());
    }

    @Test
    @DisplayName("测试 matches - null 抛异常")
    void testMatchesNull() {
        assertThrows(ApiException.class,
                () -> ApiAssert.matches(null, "[a-z]+", ApiCode.PARAM_FORMAT_ERROR));
    }

    @Test
    @DisplayName("测试 matches - 正则缓存生效（同 regex 多次调用不抛）")
    void testMatchesRegexCache() {
        // 同一 regex 调多次，验证 REGEX_CACHE 正常工作
        for (int i = 0; i < 100; i++) {
            final int idx = i;
            assertDoesNotThrow(() -> ApiAssert.matches("test" + idx, "test[0-9]+", ApiCode.PARAM_FORMAT_ERROR));
        }
    }

    @Test
    @DisplayName("测试 contains - 集合包含元素不抛异常")
    void testContainsSuccess() {
        List<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        assertDoesNotThrow(() -> ApiAssert.contains(list, "a", ApiCode.NOT_FOUND));
    }

    @Test
    @DisplayName("测试 contains - 集合不包含元素抛异常")
    void testContainsFailure() {
        List<String> list = new ArrayList<>();
        list.add("a");
        ApiException exception = assertThrows(ApiException.class,
                () -> ApiAssert.contains(list, "z", ApiCode.NOT_FOUND));
        assertEquals(10400, exception.getCode());
    }

    @Test
    @DisplayName("测试 contains - null 集合抛异常")
    void testContainsNullCollection() {
        assertThrows(ApiException.class,
                () -> ApiAssert.contains(null, "a", ApiCode.NOT_FOUND));
    }
}
