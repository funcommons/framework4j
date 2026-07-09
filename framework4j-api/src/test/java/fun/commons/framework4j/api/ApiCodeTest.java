package fun.commons.framework4j.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiCode 错误码枚举测试
 * <p>
 * 段位划分对齐 mc-api-spec v1.6 §7 / v1.7 §7.9/§7.10/§7.11
 *
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
    @DisplayName("测试系统与基础设施类错误码 (10xxx, v1.6 §7.4)")
    void testSystemErrorCodes() {
        // 10001-10005
        assertEquals(10001, ApiCode.SYSTEM_BUSY.getCode());
        assertEquals(10002, ApiCode.SERVICE_MAINTENANCE.getCode());
        assertEquals(10003, ApiCode.SERVICE_TIMEOUT.getCode());
        assertEquals(10004, ApiCode.THIRD_PARTY_ERROR.getCode());
        assertEquals(10005, ApiCode.MIDDLEWARE_ERROR.getCode());

        // 段位：10000 ≤ x < 10100
        assertTrue(ApiCode.SYSTEM_BUSY.getCode() >= 10000 && ApiCode.SYSTEM_BUSY.getCode() < 10100);
    }

    @Test
    @DisplayName("测试请求与参数校验类错误码 (101xx, v1.6 §7.5)")
    void testParamErrorCodes() {
        // 10100-10106
        assertEquals(10100, ApiCode.PARAM_ERROR.getCode());
        assertEquals(10101, ApiCode.PARAM_MISSING.getCode());
        assertEquals(10102, ApiCode.PARAM_FORMAT_ERROR.getCode());
        assertEquals(10103, ApiCode.BODY_FORMAT_ERROR.getCode());
        assertEquals(10104, ApiCode.METHOD_NOT_SUPPORTED.getCode());
        assertEquals(10105, ApiCode.MEDIA_TYPE_NOT_SUPPORTED.getCode());
        assertEquals(10106, ApiCode.BUSINESS_RULE_ERROR.getCode());

        assertTrue(ApiCode.PARAM_ERROR.getCode() >= 10100 && ApiCode.PARAM_ERROR.getCode() < 10200);
    }

    @Test
    @DisplayName("测试认证与账号类错误码 (102xx, v1.6 §7.6)")
    void testAuthErrorCodes() {
        // 10200-10206
        assertEquals(10200, ApiCode.UNAUTHORIZED.getCode());
        assertEquals(10201, ApiCode.TOKEN_EXPIRED.getCode());
        assertEquals(10202, ApiCode.TOKEN_INVALID.getCode());
        assertEquals(10203, ApiCode.CREDENTIAL_ERROR.getCode());
        assertEquals(10204, ApiCode.ACCOUNT_DISABLED.getCode());
        assertEquals(10205, ApiCode.KICKED_OUT.getCode());
        assertEquals(10206, ApiCode.CAPTCHA_ERROR.getCode());

        assertTrue(ApiCode.UNAUTHORIZED.getCode() >= 10200 && ApiCode.UNAUTHORIZED.getCode() < 10300);
    }

    @Test
    @DisplayName("测试权限与授权类错误码 (103xx, v1.6 §7.7)")
    void testPermissionErrorCodes() {
        // 10300-10303
        assertEquals(10300, ApiCode.FORBIDDEN.getCode());
        assertEquals(10301, ApiCode.DATA_PERMISSION_DENIED.getCode());
        assertEquals(10302, ApiCode.SIGNATURE_ERROR.getCode());
        assertEquals(10303, ApiCode.IP_RESTRICTED.getCode());

        assertTrue(ApiCode.FORBIDDEN.getCode() >= 10300 && ApiCode.FORBIDDEN.getCode() < 10400);
    }

    @Test
    @DisplayName("测试资源与数据类错误码 (104xx, v1.6 §7.8)")
    void testResourceErrorCodes() {
        // 10400-10404
        assertEquals(10400, ApiCode.NOT_FOUND.getCode());
        assertEquals(10401, ApiCode.UNIQUE_CONFLICT.getCode());
        assertEquals(10402, ApiCode.STATE_CONFLICT.getCode());
        assertEquals(10403, ApiCode.LOCKED.getCode());
        assertEquals(10404, ApiCode.DATA_INTEGRITY_VIOLATION.getCode());

        assertTrue(ApiCode.NOT_FOUND.getCode() >= 10400 && ApiCode.NOT_FOUND.getCode() < 10500);
    }

    @Test
    @DisplayName("测试流量控制 + 文件上传类错误码 (105xx, v1.7 §7.9)")
    void testTrafficAndUploadErrorCodes() {
        // 10500-10502 流量
        assertEquals(10500, ApiCode.TOO_MANY_REQUESTS.getCode());
        assertEquals(10501, ApiCode.DUPLICATE_SUBMIT.getCode());
        assertEquals(10502, ApiCode.SERVICE_DEGRADE.getCode());

        // 10503-10506 文件上传（v1.7 从原 106xx 整合到此）
        assertEquals(10503, ApiCode.UPLOAD_FAILED.getCode());
        assertEquals(10504, ApiCode.FILE_TYPE_ERROR.getCode());
        assertEquals(10505, ApiCode.FILE_SIZE_EXCEED.getCode());
        assertEquals(10506, ApiCode.FILE_EMPTY.getCode());

        assertTrue(ApiCode.TOO_MANY_REQUESTS.getCode() >= 10500 && ApiCode.TOO_MANY_REQUESTS.getCode() < 10600);
    }

    @Test
    @DisplayName("测试部分成功错误码 (10700, v1.6 §7.11)")
    void testPartialSuccess() {
        // 10700
        assertEquals(10700, ApiCode.PARTIAL_SUCCESS.getCode());

        assertTrue(ApiCode.PARTIAL_SUCCESS.getCode() >= 10700 && ApiCode.PARTIAL_SUCCESS.getCode() < 10800);
    }

    @Test
    @DisplayName("测试 fromCode 方法 - 成功查找")
    void testFromCodeSuccess() {
        assertEquals(ApiCode.SUCCESS, ApiCode.fromCode(0));
        assertEquals(ApiCode.SYSTEM_BUSY, ApiCode.fromCode(10001));
        assertEquals(ApiCode.PARAM_ERROR, ApiCode.fromCode(10100));
        assertEquals(ApiCode.UNAUTHORIZED, ApiCode.fromCode(10200));
        assertEquals(ApiCode.NOT_FOUND, ApiCode.fromCode(10400));
        assertEquals(ApiCode.PARTIAL_SUCCESS, ApiCode.fromCode(10700));
        // 新增：Token 格式/撤销细分
        assertEquals(ApiCode.TOKEN_INVALID_FORMAT, ApiCode.fromCode(10207));
        assertEquals(ApiCode.TOKEN_REVOKED, ApiCode.fromCode(10208));
        // 新增：Refresh token 细分
        assertEquals(ApiCode.REFRESH_EXPIRED, ApiCode.fromCode(10210));
        assertEquals(ApiCode.REFRESH_INVALID, ApiCode.fromCode(10211));
        assertEquals(ApiCode.REFRESH_ROTATION_EXCEEDED, ApiCode.fromCode(10212));
    }

    @Test
    @DisplayName("测试 fromCode 方法 - 未找到返回 null")
    void testFromCodeNotFound() {
        // 不存在的错误码返回 null（调用方显式处理）
        assertNull(ApiCode.fromCode(99999));
        assertNull(ApiCode.fromCode(-1));
        assertNull(ApiCode.fromCode(20000));
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
        // 1 个成功码 + 41 个失败码 = 42
        // 段位：10xxx (5) + 101xx (7) + 102xx (12, 含 10207/10208/10210/10211/10212) + 103xx (4) + 104xx (5) + 105xx (7) + 10700 (1) = 41
        // 106xx 段位预留给业务自定义（v1.7 §7.10），本枚举不预定义
        // 10209 预留（未使用）
        assertEquals(42, ApiCode.values().length);
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
        assertTrue(ApiCode.NOT_FOUND.getMessage().contains("不存在") ||
                ApiCode.NOT_FOUND.getMessage().contains("资源"));

        // 限流
        assertTrue(ApiCode.TOO_MANY_REQUESTS.getMessage().contains("频繁") ||
                ApiCode.TOO_MANY_REQUESTS.getMessage().contains("请求"));
    }
}
