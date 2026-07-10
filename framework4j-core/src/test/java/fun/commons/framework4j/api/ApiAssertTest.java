package fun.commons.framework4j.api;

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
 * @author LDX2T
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
        assertDoesNotThrow(() -> ApiAssert.isNull(null, ApiCode.DATA_EXISTS));
    }

    @Test
    @DisplayName("测试 isNull - 对象不为空抛出异常")
    void testIsNullFailure() {
        ApiException exception = assertThrows(ApiException.class,
                () -> ApiAssert.isNull("test", ApiCode.DATA_EXISTS));
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
}
