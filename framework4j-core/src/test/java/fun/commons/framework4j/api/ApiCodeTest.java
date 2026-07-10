package fun.commons.framework4j.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiCode 错误码枚举测试
 * <p>
 * 覆盖所有31个错误码的结构和语义
 *
 * @author LDX2T
 * @since 1.0.0
 */
@DisplayName("ApiCode 错误码枚举测试")
class ApiCodeTest {

    @Test
    @DisplayName("测试成功码 - code=0")
    void testSuccessCode() {
        assertEquals(0, ApiCode.SUCCESS.getCode());
        assertEquals("操作成功", ApiCode.SUCCESS.getMessage());
    }

    @Test
    @DisplayName("测试系统与基础设施类错误码 (10xxx)")
    void testSystemErrorCodes() {
        // 10001-10005
        assertEquals(10001, ApiCode.SYSTEM_BUSY.getCode());
        assertEquals(10002, ApiCode.SERVICE_MAINTENANCE.getCode());
        assertEquals(10003, ApiCode.SERVICE_TIMEOUT.getCode());
        assertEquals(10004, ApiCode.THIRD_PARTY_ERROR.getCode());
        assertEquals(10005, ApiCode.MIDDLEWARE_ERROR.getCode());

        // 验证错误码格式：第1位为1，第2位为0
        assertTrue(ApiCode.SYSTEM_BUSY.getCode() >= 10000 && ApiCode.SYSTEM_BUSY.getCode() < 10100);
    }

    @Test
    @DisplayName("测试请求与参数校验类错误码 (101xx)")
    void testParamErrorCodes() {
        // 10100-10106
        assertEquals(10100, ApiCode.PARAM_ERROR.getCode());
        assertEquals(10101, ApiCode.PARAM_MISSING.getCode());
        assertEquals(10102, ApiCode.PARAM_FORMAT_ERROR.getCode());
        assertEquals(10103, ApiCode.BODY_FORMAT_ERROR.getCode());
        assertEquals(10104, ApiCode.METHOD_NOT_SUPPORTED.getCode());
        assertEquals(10105, ApiCode.MEDIA_TYPE_NOT_SUPPORTED.getCode());
        assertEquals(10106, ApiCode.BUSINESS_RULE_ERROR.getCode());

        // 验证错误码格式：第1位为1，第2位为1
        assertTrue(ApiCode.PARAM_ERROR.getCode() >= 10100 && ApiCode.PARAM_ERROR.getCode() < 10200);
    }

    @Test
    @DisplayName("测试认证与账号类错误码 (102xx)")
    void testAuthErrorCodes() {
        // 10200-10206
        assertEquals(10200, ApiCode.UNAUTHORIZED.getCode());
        assertEquals(10201, ApiCode.TOKEN_EXPIRED.getCode());
        assertEquals(10202, ApiCode.TOKEN_INVALID.getCode());
        assertEquals(10203, ApiCode.CREDENTIAL_ERROR.getCode());
        assertEquals(10204, ApiCode.ACCOUNT_DISABLED.getCode());
        assertEquals(10205, ApiCode.ACCOUNT_KICKOUT.getCode());
        assertEquals(10206, ApiCode.CAPTCHA_ERROR.getCode());

        // 验证错误码格式：第1位为1，第2位为2
        assertTrue(ApiCode.UNAUTHORIZED.getCode() >= 10200 && ApiCode.UNAUTHORIZED.getCode() < 10300);
    }

    @Test
    @DisplayName("测试权限与授权类错误码 (103xx)")
    void testPermissionErrorCodes() {
        // 10300-10303
        assertEquals(10300, ApiCode.FORBIDDEN.getCode());
        assertEquals(10301, ApiCode.DATA_PERMISSION_DENIED.getCode());
        assertEquals(10302, ApiCode.SIGNATURE_ERROR.getCode());
        assertEquals(10303, ApiCode.IP_RESTRICTED.getCode());

        // 验证错误码格式：第1位为1，第2位为3
        assertTrue(ApiCode.FORBIDDEN.getCode() >= 10300 && ApiCode.FORBIDDEN.getCode() < 10400);
    }

    @Test
    @DisplayName("测试资源与数据类错误码 (104xx)")
    void testResourceErrorCodes() {
        // 10400-10404
        assertEquals(10400, ApiCode.RESOURCE_NOT_FOUND.getCode());
        assertEquals(10401, ApiCode.DATA_EXISTS.getCode());
        assertEquals(10402, ApiCode.STATE_CONFLICT.getCode());
        assertEquals(10403, ApiCode.RESOURCE_LOCKED.getCode());
        assertEquals(10404, ApiCode.INTEGRITY_VIOLATION.getCode());

        // 验证错误码格式：第1位为1，第2位为4
        assertTrue(ApiCode.RESOURCE_NOT_FOUND.getCode() >= 10400 && ApiCode.RESOURCE_NOT_FOUND.getCode() < 10500);
    }

    @Test
    @DisplayName("测试流量控制类错误码 (105xx)")
    void testTrafficControlErrorCodes() {
        // 10500-10502
        assertEquals(10500, ApiCode.TOO_MANY_REQUESTS.getCode());
        assertEquals(10501, ApiCode.DUPLICATE_SUBMIT.getCode());
        assertEquals(10502, ApiCode.SERVICE_DEGRADE.getCode());

        // 验证错误码格式：第1位为1，第2位为5
        assertTrue(ApiCode.TOO_MANY_REQUESTS.getCode() >= 10500 && ApiCode.TOO_MANY_REQUESTS.getCode() < 10600);
    }

    @Test
    @DisplayName("测试文件与上传类错误码 (106xx)")
    void testFileErrorCodes() {
        // 10600-10603
        assertEquals(10600, ApiCode.UPLOAD_FAILED.getCode());
        assertEquals(10601, ApiCode.FILE_TYPE_ERROR.getCode());
        assertEquals(10602, ApiCode.FILE_SIZE_EXCEED.getCode());
        assertEquals(10603, ApiCode.FILE_EMPTY.getCode());

        // 验证错误码格式：第1位为1，第2位为6
        assertTrue(ApiCode.UPLOAD_FAILED.getCode() >= 10600 && ApiCode.UPLOAD_FAILED.getCode() < 10700);
    }

    @Test
    @DisplayName("测试 fromCode 方法 - 成功查找")
    void testFromCodeSuccess() {
        assertEquals(ApiCode.SUCCESS, ApiCode.fromCode(0));
        assertEquals(ApiCode.PARAM_ERROR, ApiCode.fromCode(10100));
        assertEquals(ApiCode.UNAUTHORIZED, ApiCode.fromCode(10200));
        assertEquals(ApiCode.RESOURCE_NOT_FOUND, ApiCode.fromCode(10400));
    }

    @Test
    @DisplayName("测试 fromCode 方法 - 未找到返回默认值")
    void testFromCodeNotFound() {
        // 不存在的错误码应该返回 SYSTEM_BUSY
        assertEquals(ApiCode.SYSTEM_BUSY, ApiCode.fromCode(99999));
        assertEquals(ApiCode.SYSTEM_BUSY, ApiCode.fromCode(-1));
        assertEquals(ApiCode.SYSTEM_BUSY, ApiCode.fromCode(20000));
    }

    @Test
    @DisplayName("测试所有错误码的消息非空")
    void testAllErrorCodeMessagesNotEmpty() {
        for (ApiCode code : ApiCode.values()) {
            assertNotNull(code.getMessage(), "错误码 " + code.getCode() + " 的消息不能为空");
            assertFalse(code.getMessage().isEmpty(), "错误码 " + code.getCode() + " 的消息不能为空字符串");
        }
    }

    @Test
    @DisplayName("测试错误码唯一性")
    void testErrorCodeUniqueness() {
        ApiCode[] codes = ApiCode.values();
        for (int i = 0; i < codes.length; i++) {
            for (int j = i + 1; j < codes.length; j++) {
                assertNotEquals(codes[i].getCode(), codes[j].getCode(),
                        "错误码重复: " + codes[i].name() + " 和 " + codes[j].name());
            }
        }
    }

    @Test
    @DisplayName("测试错误码格式规范 - ABCCC 格式")
    void testErrorCodeFormat() {
        for (ApiCode code : ApiCode.values()) {
            if (code == ApiCode.SUCCESS) {
                assertEquals(0, code.getCode());
                continue;
            }

            // 所有非成功码应该是5位数字
            assertTrue(code.getCode() >= 10000 && code.getCode() <= 99999,
                    "错误码 " + code.name() + " 不符合5位数字格式");

            // 第一位应该是1（系统/通用）
            int firstDigit = code.getCode() / 10000;
            assertEquals(1, firstDigit, "错误码 " + code.name() + " 第一位应该是1");
        }
    }

    @Test
    @DisplayName("测试错误码数量")
    void testErrorCodeCount() {
        // 应该有36个错误码（1个成功码 + 35个失败码）
        assertEquals(36, ApiCode.values().length);
    }

    @Test
    @DisplayName("测试常用错误码的语义正确性")
    void testCommonErrorCodeSemantics() {
        // 参数错误
        assertTrue(ApiCode.PARAM_ERROR.getMessage().contains("参数"));

        // 未授权
        assertTrue(ApiCode.UNAUTHORIZED.getMessage().contains("登录") ||
                ApiCode.UNAUTHORIZED.getMessage().contains("未登录"));

        // 无权限
        assertTrue(ApiCode.FORBIDDEN.getMessage().contains("权限"));

        // 资源不存在
        assertTrue(ApiCode.RESOURCE_NOT_FOUND.getMessage().contains("不存在") ||
                ApiCode.RESOURCE_NOT_FOUND.getMessage().contains("资源"));

        // 限流
        assertTrue(ApiCode.TOO_MANY_REQUESTS.getMessage().contains("频繁") ||
                ApiCode.TOO_MANY_REQUESTS.getMessage().contains("请求"));
    }
}
