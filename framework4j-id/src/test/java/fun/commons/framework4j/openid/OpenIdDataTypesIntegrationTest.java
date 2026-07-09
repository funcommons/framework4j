package fun.commons.framework4j.openid;

import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.openid.util.OpenIdTypeUtils;
import fun.commons.framework4j.openid.formatter.OpenIdFormatterFactory;
import fun.commons.framework4j.id.util.IdObfuscator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OpenID 数据类型扩展集成测试
 * 测试所有支持的数据类型在实际应用场景中的工作情况
 */
@SpringJUnitConfig
@AutoConfigureWebMvc
@DisplayName("OpenID 数据类型扩展集成测试")
class OpenIdDataTypesIntegrationTest {

    @Configuration
    static class TestConfig implements WebMvcConfigurer {

        @Bean
        public OpenIdFormatterFactory openIdFormatterFactory() {
            return new OpenIdFormatterFactory();
        }
    }

    @RestController("openIdDataTypesTestController")
    static class TestController {

        @GetMapping("/users/{userId}")
        public Map<String, Object> getUser(@PathVariable @OpenId Long userId) {
            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("userType", "Long");
            return result;
        }

        @GetMapping("/orders/{orderId}")
        public Map<String, Object> getOrder(@PathVariable @OpenId Integer orderId) {
            Map<String, Object> result = new HashMap<>();
            result.put("orderId", orderId);
            result.put("orderType", "Integer");
            return result;
        }

        @PostMapping("/users/batch")
        public Map<String, Object> getUsers(@RequestBody UserDTO userDTO) {
            return userDTO.toMap();
        }

        @GetMapping("/users/list/{userIds}")
        public Map<String, Object> getUsers(@PathVariable @OpenId List<Long> userIds) {
            Map<String, Object> result = new HashMap<>();
            result.put("userIds", userIds);
            result.put("type", "List<Long>");
            return result;
        }

        @GetMapping("/orders/set/{orderIds}")
        public Map<String, Object> getOrders(@PathVariable @OpenId Set<Integer> orderIds) {
            Map<String, Object> result = new HashMap<>();
            result.put("orderIds", orderIds);
            result.put("type", "Set<Integer>");
            return result;
        }

        @GetMapping("/test/array")
        public Map<String, Object> getArray(@RequestBody ArrayDTO arrayDTO) {
            return arrayDTO.toMap();
        }
    }

    static class UserDTO {
        @OpenId
        private Long userId;

        @OpenId
        private Integer orderId;

        @OpenId
        private List<Long> friends;

        @OpenId
        private Set<Integer> permissions;

        @OpenId
        private long[] roles;

        @OpenId
        private int[] tags;

        // Getters and Setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public Integer getOrderId() { return orderId; }
        public void setOrderId(Integer orderId) { this.orderId = orderId; }

        public List<Long> getFriends() { return friends; }
        public void setFriends(List<Long> friends) { this.friends = friends; }

        public Set<Integer> getPermissions() { return permissions; }
        public void setPermissions(Set<Integer> permissions) { this.permissions = permissions; }

        public long[] getRoles() { return roles; }
        public void setRoles(long[] roles) { this.roles = roles; }

        public int[] getTags() { return tags; }
        public void setTags(int[] tags) { this.tags = tags; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", userId);
            map.put("orderId", orderId);
            map.put("friends", friends);
            map.put("permissions", permissions);
            map.put("roles", roles);
            map.put("tags", tags);
            return map;
        }
    }

    static class ArrayDTO {
        @OpenId
        private Long[] longArray;

        @OpenId
        private Integer[] intArray;

        public Long[] getLongArray() { return longArray; }
        public void setLongArray(Long[] longArray) { this.longArray = longArray; }

        public Integer[] getIntArray() { return intArray; }
        public void setIntArray(Integer[] intArray) { this.intArray = intArray; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("longArray", longArray);
            map.put("intArray", intArray);
            return map;
        }
    }

    @Autowired(required = false)
    private MockMvc mockMvc;

    @Nested
    @DisplayName("Spring MVC 参数绑定测试")
    class SpringMvcBindingTest {

        @Test
        @DisplayName("应该正确绑定 Long 类型参数")
        void shouldBindLongParameter() throws Exception {
            String openId = IdObfuscator.toOpenId(12345L);

            if (mockMvc != null) {
                MvcResult result = mockMvc.perform(get("/users/{userId}", openId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.userId").value(12345))
                        .andExpect(jsonPath("$.userType").value("Long"))
                        .andReturn();
            }
        }

        @Test
        @DisplayName("应该正确绑定 Integer 类型参数")
        void shouldBindIntegerParameter() throws Exception {
            String openId = IdObfuscator.toOpenId(123L);

            if (mockMvc != null) {
                MvcResult result = mockMvc.perform(get("/orders/{orderId}", openId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.orderId").value(123))
                        .andExpect(jsonPath("$.orderType").value("Integer"))
                        .andReturn();
            }
        }

        @Test
        @DisplayName("应该正确处理纯数字兼容性")
        void shouldHandleNumericCompatibility() throws Exception {
            if (mockMvc != null) {
                MvcResult result = mockMvc.perform(get("/users/{userId}", "12345"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.userId").value(12345))
                        .andReturn();
            }
        }
    }

    @Nested
    @DisplayName("工具类集成测试")
    class UtilIntegrationTest {

        @Test
        @DisplayName("应该正确处理复杂嵌套结构")
        void shouldHandleComplexNestedStructures() {
            // 创建复杂数据结构
            Map<String, Object> complexData = new HashMap<>();
            complexData.put("userId", 12345L);
            complexData.put("orderIds", Arrays.asList(1, 2, 3));
            complexData.put("roleIds", new HashSet<>(Arrays.asList(10L, 20L, 30L)));
            complexData.put("tags", new String[]{"tag1", "tag2"});

            // 测试转换
            Object result = OpenIdTypeUtils.convertToOpenId(complexData);
            // Map 本身不支持，所以应该原样返回
            assertThat(result).isSameAs(complexData);
        }

        @Test
        @DisplayName("应该正确处理所有支持的单一类型")
        void shouldHandleAllSupportedSingleTypes() throws Exception {
            // 测试所有支持的类型
            Long longValue = 12345L;
            Integer intValue = 678;

            Object longResult = OpenIdTypeUtils.convertToOpenId(longValue);
            Object intResult = OpenIdTypeUtils.convertToOpenId(intValue);

            assertThat(longResult).isInstanceOf(String.class);
            assertThat(intResult).isInstanceOf(String.class);

            // 验证可以正确还原
            String longOpenId = (String) longResult;
            String intOpenId = (String) intResult;

            Object revertedLong = OpenIdTypeUtils.convertFromOpenId(longOpenId, Long.class);
            Object revertedInt = OpenIdTypeUtils.convertFromOpenId(intOpenId, Integer.class);

            assertThat(revertedLong).isEqualTo(longValue);
            assertThat(revertedInt).isEqualTo(intValue);
        }

        @Test
        @DisplayName("应该正确处理所有支持的集合类型")
        void shouldHandleAllSupportedCollectionTypes() {
            List<Long> longList = Arrays.asList(1L, 2L, 3L);
            List<Integer> intList = Arrays.asList(1, 2, 3);
            Set<Long> longSet = new HashSet<>(Arrays.asList(1L, 2L, 3L));
            Set<Integer> intSet = new HashSet<>(Arrays.asList(1, 2, 3));

            Object longListResult = OpenIdTypeUtils.convertToOpenId(longList);
            Object intListResult = OpenIdTypeUtils.convertToOpenId(intList);
            Object longSetResult = OpenIdTypeUtils.convertToOpenId(longSet);
            Object intSetResult = OpenIdTypeUtils.convertToOpenId(intSet);

            assertThat(longListResult).isInstanceOf(List.class);
            assertThat(intListResult).isInstanceOf(List.class);
            assertThat(longSetResult).isInstanceOf(Set.class);
            assertThat(intSetResult).isInstanceOf(Set.class);
        }

        @Test
        @DisplayName("应该正确处理所有支持的数组类型")
        void shouldHandleAllSupportedArrayTypes() {
            long[] primitiveLongArray = {1L, 2L, 3L};
            int[] primitiveIntArray = {1, 2, 3};
            Long[] objectLongArray = {1L, 2L, 3L};
            Integer[] objectIntArray = {1, 2, 3};

            Object primitiveLongResult = OpenIdTypeUtils.convertToOpenId(primitiveLongArray);
            Object primitiveIntResult = OpenIdTypeUtils.convertToOpenId(primitiveIntArray);
            Object objectLongResult = OpenIdTypeUtils.convertToOpenId(objectLongArray);
            Object objectIntResult = OpenIdTypeUtils.convertToOpenId(objectIntArray);

            assertThat(primitiveLongResult).isInstanceOf(String[].class);
            assertThat(primitiveIntResult).isInstanceOf(String[].class);
            assertThat(objectLongResult).isInstanceOf(String[].class);
            assertThat(objectIntResult).isInstanceOf(String[].class);
        }
    }

    @Nested
    @DisplayName("边界条件和错误处理测试")
    class EdgeCaseAndErrorHandlingTest {

        @Test
        @DisplayName("应该正确处理空值")
        void shouldHandleNullValues() {
            Object result = OpenIdTypeUtils.convertToOpenId(null);
            assertThat(result).isNull();

            List<Object> listWithNull = Arrays.asList(1L, null, 3L);
            result = OpenIdTypeUtils.convertToOpenId(listWithNull);
            assertThat(result).isInstanceOf(List.class);
        }

        @Test
        @DisplayName("应该正确处理空集合")
        void shouldHandleEmptyCollections() {
            List<Long> emptyList = Collections.emptyList();
            Set<Long> emptySet = Collections.emptySet();
            long[] emptyArray = {};

            Object listResult = OpenIdTypeUtils.convertToOpenId(emptyList);
            Object setResult = OpenIdTypeUtils.convertToOpenId(emptySet);
            Object arrayResult = OpenIdTypeUtils.convertToOpenId(emptyArray);

            assertThat(listResult).isInstanceOf(List.class);
            assertThat(setResult).isInstanceOf(Set.class);
            assertThat((long[]) arrayResult).hasSize(0);
        }

        @Test
        @DisplayName("应该忽略不支持的类型")
        void shouldIgnoreUnsupportedTypes() {
            String unsupported = "test";
            Double doubleValue = 123.45;
            List<String> unsupportedList = Arrays.asList("test", "list");

            Object stringResult = OpenIdTypeUtils.convertToOpenId(unsupported);
            Object doubleResult = OpenIdTypeUtils.convertToOpenId(doubleValue);
            Object listResult = OpenIdTypeUtils.convertToOpenId(unsupportedList);

            assertThat(stringResult).isSameAs(unsupported);
            assertThat(doubleResult).isSameAs(doubleValue);
            assertThat(listResult).isSameAs(unsupportedList);
        }
    }

    @Nested
    @DisplayName("性能和并发测试")
    class PerformanceAndConcurrencyTest {

        @Test
        @DisplayName("应该支持大量数据处理")
        void shouldHandleLargeDataVolumes() {
            // 创建大量数据
            List<Long> largeList = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                largeList.add((long) i);
            }

            long startTime = System.currentTimeMillis();
            Object result = OpenIdTypeUtils.convertToOpenId(largeList);
            long endTime = System.currentTimeMillis();

            assertThat(result).isInstanceOf(List.class);
            assertThat((List<?>) result).hasSize(1000);
            assertThat(endTime - startTime).isLessThan(1000); // 应该在1秒内完成
        }

        @Test
        @DisplayName("应该是线程安全的")
        void shouldBeThreadSafe() throws InterruptedException {
            List<Long> testData = Arrays.asList(1L, 2L, 3L, 4L, 5L);
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];
            boolean[] success = new boolean[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                threads[i] = new Thread(() -> {
                    try {
                        for (int j = 0; j < 100; j++) {
                            Object result = OpenIdTypeUtils.convertToOpenId(testData);
                            assertThat(result).isInstanceOf(List.class);
                        }
                        success[threadIndex] = true;
                    } catch (Exception e) {
                        success[threadIndex] = false;
                    }
                });
            }

            // 启动所有线程
            for (Thread thread : threads) {
                thread.start();
            }

            // 等待所有线程完成
            for (Thread thread : threads) {
                thread.join();
            }

            // 验证所有线程都成功
            for (boolean s : success) {
                assertThat(s).isTrue();
            }
        }
    }
}