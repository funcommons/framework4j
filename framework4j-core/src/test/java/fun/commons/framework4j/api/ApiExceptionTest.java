package fun.commons.framework4j.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiException 业务异常类测试
 *
 * @author LDX2T
 * @since 1.0.0
 */
@DisplayName("ApiException 业务异常类测试")
class ApiExceptionTest {

    @Test
    @DisplayName("测试构造函数 - 仅ApiCode")
    void testConstructorWithApiCodeOnly() {
        ApiException exception = new ApiException(ApiCode.PARAM_ERROR);

        assertEquals(10100, exception.getCode());
        assertEquals("请求参数错误", exception.getMessage());
        assertNull(exception.getErrorDetail());
    }

    @Test
    @DisplayName("测试构造函数 - ApiCode + 自定义消息")
    void testConstructorWithApiCodeAndMessage() {
        ApiException exception = new ApiException(ApiCode.RESOURCE_NOT_FOUND, "用户ID 999 不存在");

        assertEquals(10400, exception.getCode());
        assertEquals("用户ID 999 不存在", exception.getMessage());
        assertNull(exception.getErrorDetail());
    }

    @Test
    @DisplayName("测试构造函数 - ApiCode + 错误详情")
    void testConstructorWithApiCodeAndErrorDetail() {
        Map<String, String> errorDetail = new HashMap<>();
        errorDetail.put("field", "email");
        errorDetail.put("message", "邮箱格式不正确");

        ApiException exception = new ApiException(ApiCode.PARAM_ERROR, errorDetail);

        assertEquals(10100, exception.getCode());
        assertEquals("请求参数错误", exception.getMessage());
        assertNotNull(exception.getErrorDetail());
        assertEquals(errorDetail, exception.getErrorDetail());
    }

    @Test
    @DisplayName("测试构造函数 - ApiCode + 自定义消息 + 错误详情")
    void testConstructorWithApiCodeMessageAndErrorDetail() {
        Map<String, String> errorDetail = new HashMap<>();
        errorDetail.put("field", "age");
        errorDetail.put("value", "-1");

        ApiException exception = new ApiException(ApiCode.BUSINESS_RULE_ERROR, "年龄不能为负数", errorDetail);

        assertEquals(10106, exception.getCode());
        assertEquals("年龄不能为负数", exception.getMessage());
        assertNotNull(exception.getErrorDetail());
        assertEquals(errorDetail, exception.getErrorDetail());
    }

    @Test
    @DisplayName("测试构造函数 - 自定义错误码和消息")
    void testConstructorWithCustomCodeAndMessage() {
        ApiException exception = new ApiException(20001, "业务模块自定义错误");

        assertEquals(20001, exception.getCode());
        assertEquals("业务模块自定义错误", exception.getMessage());
        assertNull(exception.getErrorDetail());
    }

    @Test
    @DisplayName("测试异常继承关系")
    void testExceptionInheritance() {
        ApiException exception = new ApiException(ApiCode.SYSTEM_BUSY);

        assertTrue(exception instanceof RuntimeException);
        assertTrue(exception instanceof Exception);
        assertTrue(exception instanceof Throwable);
    }

    @Test
    @DisplayName("测试异常可抛出性")
    void testExceptionThrowable() {
        assertThrows(ApiException.class, () -> {
            throw new ApiException(ApiCode.UNAUTHORIZED);
        });
    }

    @Test
    @DisplayName("测试异常getMessage()方法")
    void testGetMessage() {
        ApiException exception1 = new ApiException(ApiCode.FORBIDDEN);
        assertEquals("无权限访问", exception1.getMessage());

        ApiException exception2 = new ApiException(ApiCode.FORBIDDEN, "无权访问订单详情");
        assertEquals("无权访问订单详情", exception2.getMessage());
    }

    @Test
    @DisplayName("测试不同错误码的异常")
    void testDifferentErrorCodes() {
        ApiException e1 = new ApiException(ApiCode.PARAM_MISSING);
        ApiException e2 = new ApiException(ApiCode.TOKEN_EXPIRED);
        ApiException e3 = new ApiException(ApiCode.DATA_EXISTS);

        assertNotEquals(e1.getCode(), e2.getCode());
        assertNotEquals(e2.getCode(), e3.getCode());
        assertNotEquals(e1.getCode(), e3.getCode());
    }

    @Test
    @DisplayName("测试错误详情的类型灵活性")
    void testErrorDetailFlexibility() {
        // 错误详情可以是 Map
        Map<String, String> mapDetail = new HashMap<>();
        mapDetail.put("key", "value");
        ApiException e1 = new ApiException(ApiCode.PARAM_ERROR, mapDetail);
        assertTrue(e1.getErrorDetail() instanceof Map);

        // 错误详情可以是 String (使用4参数构造函数)
        ApiException e2 = new ApiException(ApiCode.PARAM_ERROR, "参数错误", "这是一个字符串错误详情");
        assertTrue(e2.getErrorDetail() instanceof String);

        // 错误详情可以是任何 Object
        ApiException e3 = new ApiException(ApiCode.PARAM_ERROR, 12345);
        assertTrue(e3.getErrorDetail() instanceof Integer);
    }
}
