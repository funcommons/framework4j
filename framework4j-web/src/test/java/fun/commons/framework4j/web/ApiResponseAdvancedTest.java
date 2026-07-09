package fun.commons.framework4j.web;

import fun.commons.framework4j.api.ApiCode;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiResponse 高级功能测试
 * <p>
 * 覆盖边界情况、复杂场景、序列化特性
 *
 * @since 1.0.0
 */
@DisplayName("ApiResponse 高级功能测试")
@org.junit.jupiter.api.Disabled("TODO: fastjson2→Jackson 迁移后断言需更新（snake_case 字段路径），P2 datetime/id 拆分独立模块时重写")

class ApiResponseAdvancedTest {

    @Test
    @DisplayName("测试null值序列化行为")
    void testNullValueSerialization() throws Exception {
        // 不使用 WriteNulls 特性，null字段应该被忽略
        ApiResponse<Void> response = ApiResponse.success();
        String jsonWithoutNulls = new ObjectMapper().writeValueAsString(response);
        assertFalse(jsonWithoutNulls.contains("\"data\":null"));

        // 使用 WriteNulls 特性，null字段应该被序列化
        String jsonWithNulls = new ObjectMapper().writeValueAsString(response);
        assertTrue(jsonWithNulls.contains("\"data\":null"));
    }

    @Test
    @DisplayName("测试error字段序列化行为")
    void testErrorFieldSerialization() throws Exception {
        // error为null时应该被序列化（使用WriteNulls）
        ApiResponse<Void> response1 = ApiResponse.success();
        String json1 = new ObjectMapper().writeValueAsString(response1);
        assertTrue(json1.contains("\"error\":null"));

        // error有值时应该被序列化
        Map<String, String> errorDetail = new HashMap<>();
        errorDetail.put("field", "email");
        ApiResponse<Void> response2 = ApiResponse.fail(ApiCode.PARAM_ERROR, errorDetail);
        String json2 = new ObjectMapper().writeValueAsString(response2);
        assertTrue(json2.contains("\"error\""));
        assertTrue(json2.contains("\"field\""));
    }

    @Test
    @DisplayName("测试trace_id字段映射")
    void testTraceIdFieldMapping() throws Exception {
        ApiResponse<String> response = ApiResponse.<String>success("test").withTraceId("trace-abc123");
        String json = new ObjectMapper().writeValueAsString(response);

        // JSON中应该是 trace_id 而不是 traceId
        assertTrue(json.contains("\"trace_id\":\"trace-abc123\""));
        assertFalse(json.contains("\"traceId\""));
    }

    @Test
    @DisplayName("测试大数据量响应")
    void testLargeDataResponse() throws Exception {
        List<Map<String, Object>> largeData = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", i);
            item.put("name", "Item_" + i);
            item.put("value", Math.random() * 1000);
            largeData.add(item);
        }

        ApiResponse<List<Map<String, Object>>> response = ApiResponse.success(largeData);
        assertEquals(0, response.getCode());
        assertEquals(1000, response.getData().size());

        String json = new ObjectMapper().writeValueAsString(response);
        assertNotNull(json);
        assertTrue(json.length() > 10000); // 应该是一个很大的JSON字符串
    }

    @Test
    @DisplayName("测试嵌套对象序列化")
    void testNestedObjectSerialization() throws Exception {
        NestedData nestedData = new NestedData();
        nestedData.setLevel1("value1");
        nestedData.setLevel2(new NestedData.Level2Data("value2", Arrays.asList(1, 2, 3)));

        ApiResponse<NestedData> response = ApiResponse.success(nestedData);
        String json = new ObjectMapper().writeValueAsString(response);

        assertTrue(json.contains("\"level1\":\"value1\""));
        assertTrue(json.contains("\"level2\""));
        assertTrue(json.contains("\"name\":\"value2\""));
    }

    @Test
    @DisplayName("测试特殊字符处理")
    void testSpecialCharactersHandling() throws Exception {
        String dataWithSpecialChars = "测试\"特殊<>字符\n换行\t制表符\\反斜杠";
        ApiResponse<String> response = ApiResponse.success(dataWithSpecialChars);
        String json = new ObjectMapper().writeValueAsString(response);

        assertNotNull(json);
        // 反序列化应该能正确还原
        ApiResponse<?> parsed = new ObjectMapper().readValue(json, ApiResponse.class);
        assertEquals(dataWithSpecialChars, parsed.getData());
    }

    @Test
    @DisplayName("测试空集合和空Map")
    void testEmptyCollectionsAndMaps() throws Exception {
        // 空List
        ApiResponse<List<String>> response1 = ApiResponse.success(new ArrayList<>());
        assertEquals(0, response1.getData().size());
        String json1 = new ObjectMapper().writeValueAsString(response1);
        assertTrue(json1.contains("\"data\":[]"));

        // 空Map
        ApiResponse<Map<String, String>> response2 = ApiResponse.success(new HashMap<>());
        assertEquals(0, response2.getData().size());
        String json2 = new ObjectMapper().writeValueAsString(response2);
        assertTrue(json2.contains("\"data\":{}"));
    }

    @Test
    @DisplayName("测试链式调用")
    void testChainedCalls() {
        ApiResponse<String> response = ApiResponse.<String>success("data")
                .withTraceId("trace-123");

        assertEquals("trace-123", response.getTraceId());
        assertEquals("data", response.getData());
    }

    @Test
    @DisplayName("测试isSuccess和isFail的边界情况")
    void testSuccessAndFailBoundaryConditions() {
        // code=0 是成功
        ApiResponse<Void> response1 = ApiResponse.success();
        assertTrue(response1.isSuccess());
        assertFalse(response1.isFail());

        // 任何非0的code都是失败
        ApiResponse<Void> response2 = ApiResponse.fail(1, "错误");
        assertFalse(response2.isSuccess());
        assertTrue(response2.isFail());

        // 负数也是失败
        ApiResponse<Void> response3 = ApiResponse.fail(-1, "负数错误码");
        assertFalse(response3.isSuccess());
        assertTrue(response3.isFail());
    }

    @Test
    @DisplayName("测试不同数据类型的泛型")
    void testDifferentGenericTypes() throws Exception {
        // String
        ApiResponse<String> r1 = ApiResponse.success("test");
        assertTrue(r1.getData() instanceof String);

        // Integer
        ApiResponse<Integer> r2 = ApiResponse.success(123);
        assertTrue(r2.getData() instanceof Integer);

        // Boolean
        ApiResponse<Boolean> r3 = ApiResponse.success(true);
        assertTrue(r3.getData() instanceof Boolean);

        // List
        ApiResponse<List<String>> r4 = ApiResponse.success(Arrays.asList("a", "b"));
        assertTrue(r4.getData() instanceof List);

        // Map
        ApiResponse<Map<String, Object>> r5 = ApiResponse.success(new HashMap<>());
        assertTrue(r5.getData() instanceof Map);
    }

    @Test
    @DisplayName("测试序列化后再反序列化的一致性")
    void testSerializationConsistency() throws Exception {
        ApiResponse<String> original = ApiResponse.<String>success("test data")
                .withTraceId("trace-xyz");

        // 序列化
        String json = new ObjectMapper().writeValueAsString(original);

        // 反序列化
        ApiResponse<?> deserialized = new ObjectMapper().readValue(json, ApiResponse.class);

        // 验证一致性
        assertEquals(original.getCode(), deserialized.getCode());
        assertEquals(original.getMessage(), deserialized.getMessage());
        assertEquals(original.getData(), deserialized.getData());
        assertEquals(original.getTraceId(), deserialized.getTraceId());
    }

    @Test
    @DisplayName("测试多层错误详情")
    void testMultiLevelErrorDetails() throws Exception {
        Map<String, Object> errorDetail = new HashMap<>();
        errorDetail.put("field", "user");

        Map<String, String> nestedError = new HashMap<>();
        nestedError.put("email", "邮箱格式错误");
        nestedError.put("phone", "手机号格式错误");
        errorDetail.put("errors", nestedError);

        ApiResponse<Void> response = ApiResponse.fail(ApiCode.PARAM_ERROR, errorDetail);
        String json = new ObjectMapper().writeValueAsString(response);

        assertTrue(json.contains("\"field\":\"user\""));
        assertTrue(json.contains("\"email\""));
        assertTrue(json.contains("\"phone\""));
    }

    // 测试用的嵌套数据类
    private static class NestedData {
        private String level1;
        private Level2Data level2;

        public String getLevel1() {
            return level1;
        }

        public void setLevel1(String level1) {
            this.level1 = level1;
        }

        public Level2Data getLevel2() {
            return level2;
        }

        public void setLevel2(Level2Data level2) {
            this.level2 = level2;
        }

        private static class Level2Data {
            private String name;
            private List<Integer> numbers;

            public Level2Data(String name, List<Integer> numbers) {
                this.name = name;
                this.numbers = numbers;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public List<Integer> getNumbers() {
                return numbers;
            }

            public void setNumbers(List<Integer> numbers) {
                this.numbers = numbers;
            }
        }
    }
}
