package fun.commons.framework4j.web;

import fun.commons.framework4j.api.ApiCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiResponse 类单元测试
 *
 * @since 1.0.0
 */
@DisplayName("ApiResponse 统一响应结构测试")
class ApiResponseTest {

    @Test
    @DisplayName("测试成功响应 - 无数据")
    void testSuccessWithoutData() {
        ApiResponse<Void> response = ApiResponse.success();

        assertEquals(0, response.getCode());
        assertEquals("操作成功", response.getMessage());
        assertNull(response.getData());
        assertNull(response.getError());
        assertTrue(response.isSuccess());
        assertFalse(response.isFail());
    }

    @Test
    @DisplayName("测试成功响应 - 带数据")
    void testSuccessWithData() {
        String data = "Hello World";
        ApiResponse<String> response = ApiResponse.success(data);

        assertEquals(0, response.getCode());
        assertEquals("操作成功", response.getMessage());
        assertEquals(data, response.getData());
        assertTrue(response.isSuccess());
    }

    @Test
    @DisplayName("测试成功响应 - 自定义消息")
    void testSuccessWithCustomMessage() {
        String data = "test data";
        ApiResponse<String> response = ApiResponse.success(data, "自定义成功消息");

        assertEquals(0, response.getCode());
        assertEquals("自定义成功消息", response.getMessage());
        assertEquals(data, response.getData());
        assertTrue(response.isSuccess());
    }

    @Test
    @DisplayName("测试失败响应 - 使用 ApiCode")
    void testFailWithApiCode() {
        ApiResponse<Void> response = ApiResponse.fail(ApiCode.PARAM_ERROR);

        assertEquals(10100, response.getCode());
        assertEquals("请求参数错误", response.getMessage());
        assertNull(response.getData());
        assertNull(response.getError());
        assertFalse(response.isSuccess());
        assertTrue(response.isFail());
    }

    @Test
    @DisplayName("测试失败响应 - 自定义错误消息")
    void testFailWithCustomMessage() {
        ApiResponse<Void> response = ApiResponse.fail(ApiCode.NOT_FOUND, "用户 ID 999 不存在");

        assertEquals(10400, response.getCode());
        assertEquals("用户 ID 999 不存在", response.getMessage());
        assertFalse(response.isSuccess());
    }

    @Test
    @DisplayName("测试失败响应 - 带错误详情")
    void testFailWithErrorDetail() throws Exception {
        Map<String, String> errorDetail = new HashMap<>();
        errorDetail.put("field", "email");
        errorDetail.put("message", "邮箱格式不正确");

        ApiResponse<Void> response = ApiResponse.fail(ApiCode.PARAM_ERROR, errorDetail);

        assertEquals(10100, response.getCode());
        assertNotNull(response.getError());
        assertTrue(response.isFail());
    }

    @Test
    @DisplayName("测试 TraceId 设置")
    void testWithTraceId() throws Exception {
        ApiResponse<String> response = ApiResponse.<String>success("test").withTraceId("trace-123");

        assertEquals("trace-123", response.getTraceId());
    }

    @Test
    @DisplayName("测试 JSON 序列化 - 成功响应")
    void testJsonSerializationSuccess() throws Exception {
        ApiResponse<String> response = ApiResponse.success("test data");
        String json = new ObjectMapper().writeValueAsString(response);

        assertNotNull(json);
        assertTrue(json.contains("\"code\":0"));
        assertTrue(json.contains("\"message\":\"操作成功\""));
        assertTrue(json.contains("\"data\":\"test data\""));
    }

    @Test
    @DisplayName("测试 JSON 序列化 - 失败响应")
    void testJsonSerializationFail() throws Exception {
        ApiResponse<Void> response = ApiResponse.fail(ApiCode.UNAUTHORIZED);
        // 使用 WriteNulls 特性确保null值被序列化
        String json = new ObjectMapper().writeValueAsString(response);

        assertNotNull(json);
        assertTrue(json.contains("\"code\":10200"));
        assertTrue(json.contains("\"message\":\"用户未登录\""));
        assertTrue(json.contains("\"data\":null"));
    }

    @Test
    @DisplayName("测试 JSON 反序列化")
    void testJsonDeserialization() throws Exception {
        String json = "{\"code\":0,\"message\":\"操作成功\",\"data\":\"test\",\"trace_id\":\"abc123\"}";
        ApiResponse<?> response = new ObjectMapper().readValue(json, ApiResponse.class);

        assertNotNull(response);
        assertEquals(0, response.getCode());
        assertEquals("操作成功", response.getMessage());
        assertEquals("test", response.getData());
        assertEquals("abc123", response.getTraceId());
    }

    @Test
    @DisplayName("测试复杂对象数据")
    void testComplexObjectData() throws Exception {
        UserDTO user = new UserDTO(1L, "张三", "zhangsan@example.com");
        ApiResponse<UserDTO> response = ApiResponse.success(user);

        assertEquals(0, response.getCode());
        assertNotNull(response.getData());
        assertEquals("张三", response.getData().getName());

        String json = new ObjectMapper().writeValueAsString(response);
        assertTrue(json.contains("\"name\":\"张三\""));
    }

    @Test
    @DisplayName("测试 ApiCode.fromCode 方法")
    void testApiCodeFromCode() {
        ApiCode code = ApiCode.fromCode(10400);
        assertEquals(ApiCode.NOT_FOUND, code);

        // 测试未找到的情况
        ApiCode unknownCode = ApiCode.fromCode(99999);
        assertNull(unknownCode);
    }

    // 测试用 DTO
    private static class UserDTO {
        private Long id;
        private String name;
        private String email;

        public UserDTO() {
        }

        public UserDTO(Long id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}
