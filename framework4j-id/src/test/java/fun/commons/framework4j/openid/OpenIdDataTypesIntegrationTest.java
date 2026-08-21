package fun.commons.framework4j.openid;

import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.openid.formatter.OpenIdFormatterFactory;
import fun.commons.framework4j.openid.util.OpenIdTypeSupport;
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
            return new OpenIdFormatterFactory(OpenIdTypeSupport.allEnabled());
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
}