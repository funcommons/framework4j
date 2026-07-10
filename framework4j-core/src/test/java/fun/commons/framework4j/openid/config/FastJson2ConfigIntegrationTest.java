package fun.commons.framework4j.openid.config;

import com.alibaba.fastjson2.JSON;
import fun.commons.framework4j.core.config.WebConfig;
import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.id.config.IdSdkAutoConfiguration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WebConfig 独立集成测试
 * 验证 OffsetDateTime 格式化功能
 */
@SpringBootTest(classes = FastJson2ConfigIntegrationTest.TestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("WebConfig 独立集成测试")
public class FastJson2ConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("应该正确序列化OffsetDateTime为ISO 8601格式")
    public void shouldSerializeOffsetDateTimeToIso8601Format() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/user/offset-datetime"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        String jsonContent = result.getResponse().getContentAsString();
        System.out.println("OffsetDateTime JSON: " + jsonContent);

        // 验证包含 ISO 8601 格式的日期时间
        assertThat(jsonContent).contains("createAt");
        assertThat(jsonContent).contains("updateAt");

        // 验证格式：yyyy-MM-dd'T'HH:mm:ss.SSSXXX
        // 支持可变长度的毫秒部分（1-9位）
        assertThat(jsonContent).matches(".*\"createAt\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{1,9}[+-]\\d{2}:\\d{2}\".*");
        assertThat(jsonContent).matches(".*\"updateAt\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{1,9}[+-]\\d{2}:\\d{2}\".*");
    }

    @Test
    @DisplayName("应该正确处理固定时间的OffsetDateTime")
    public void shouldSerializeFixedOffsetDateTime() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/user/fixed-time"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        String jsonContent = result.getResponse().getContentAsString();
        System.out.println("固定时间 JSON: " + jsonContent);

        // 验证包含指定的固定时间
        assertThat(jsonContent).contains("\"2024-01-01T12:00:00.123+08:00\"");
    }

    @Test
    @DisplayName("应该正确应用@OpenId注解进行类型转换")
    public void shouldApplyOpenIdAnnotation() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/user/with-openid"))
                .andExpect(status().isOk())
                .andReturn();

        String jsonContent = result.getResponse().getContentAsString();
        System.out.println("@OpenId JSON: " + jsonContent);

        // 验证 @OpenId 注解生效，Long id 被转换为 String
        // 注意：混淆后的 ID 不会是 "1001"，而是一个混淆字符串
        assertThat(jsonContent).contains("\"id\":");
        // 验证 id 的值是字符串类型（被引号包围）
        assertThat(jsonContent).matches(".*\"id\":\"[^\"]+\".*");
        // 验证不包含数字类型的 id（没有 "id":1001）
        assertThat(jsonContent).doesNotContain("\"id\":1001");
        // 验证包含 createAt 字段
        assertThat(jsonContent).contains("createAt");
    }

    @Test
    @DisplayName("应该正确处理不同的时区偏移")
    public void shouldHandleDifferentTimezoneOffsets() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/user/different-timezones"))
                .andExpect(status().isOk())
                .andReturn();

        String jsonContent = result.getResponse().getContentAsString();
        System.out.println("不同时区 JSON: " + jsonContent);

        // 验证包含不同的时区偏移
        assertThat(jsonContent).contains("+08:00");  // UTC+8
        assertThat(jsonContent).contains("-05:00");  // UTC-5
        assertThat(jsonContent).contains("Z");      // UTC+0
    }

    @Test
    @DisplayName("应该正确处理不同的时区偏移")
    public void shouldHandleDifferentLocalDatetime() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/user/different-local-datetime"))
                .andExpect(status().isOk())
                .andReturn();

        String jsonContent = result.getResponse().getContentAsString();
        System.out.println("不同时区 JSON: " + jsonContent);


    }

    @Test
    @DisplayName("应该正确处理POST请求中的OffsetDateTime")
    public void shouldHandleOffsetDateTimeInPostRequest() throws Exception {
        String requestBody = "{\"name\":\"Test User\",\"createAt\":\"2024-06-15T10:30:45.123+08:00\"}";

        MvcResult result = mockMvc.perform(post("/api/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        String jsonContent = result.getResponse().getContentAsString();
        System.out.println("POST 响应 JSON: " + jsonContent);

        // 验证响应包含正确的日期时间格式
        assertThat(jsonContent).matches(".*\"createAt\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{1,9}[+-]\\d{2}:\\d{2}\".*");
    }

    // ==========================================
    // 测试配置与辅助类
    // ==========================================

    @SpringBootApplication
    @Import({
            IdSdkAutoConfiguration.class, // 核心 SDK
            WebConfig.class,              // Web 配置 (含 FastJson2 + OpenId)
            TestController.class          // 必须显式导入inner class
    })
    static class TestConfig {
    }

    /**
     * 测试控制器
     */
    @RestController("fastJson2TestController")
    @RequestMapping("/api")
    static class TestController {

        @GetMapping("/user/offset-datetime")
        public UserVO getUserWithCurrentOffsetDateTime() {
            UserVO user = new UserVO();
            user.setId(1L);
            user.setName("Current Time User");
            user.setCreateAt(OffsetDateTime.now());
            user.setUpdateAt(OffsetDateTime.now().plusHours(1));
            return user;
        }

        @GetMapping("/user/fixed-time")
        public UserVO getUserWithFixedTime() {
            UserVO user = new UserVO();
            user.setId(2L);
            user.setName("Fixed Time User");
            user.setCreateAt(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 123000000, ZoneOffset.ofHours(8)));
            user.setUpdateAt(OffsetDateTime.of(2024, 1, 1, 13, 0, 0, 456000000, ZoneOffset.ofHours(8)));
            user.setOrderTime(LocalDateTime.of(2024, 1, 1, 14, 0, 0, 456000000));
            return user;
        }

        @GetMapping("/user/with-openid")
        public UserVO getUserWithOpenId() {
            UserVO user = new UserVO();
            user.setId(1001L); // 这个会被 @OpenId 注解转换为 String
            user.setName("OpenId User");
            user.setCreateAt(OffsetDateTime.now());
            return user;
        }

        @GetMapping("/user/different-timezones")
        public TimezoneVO getDifferentTimezones() {
            TimezoneVO timezoneVO = new TimezoneVO();
            timezoneVO.setBeijingTime(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.ofHours(8)));
            timezoneVO.setNewYorkTime(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.ofHours(-5)));
            timezoneVO.setUtcTime(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC));
            return timezoneVO;
        }

        @GetMapping("/user/different-local-datetime")
        public LocalTimeVO getDifferentLocalDatetime() {
            LocalTimeVO vo = new LocalTimeVO();
            vo.setBeijingTime(LocalDateTime.of(2024, 1, 1, 12, 0, 0, 0));
            vo.setNewYorkTime(LocalDateTime.of(2024, 1, 1, 12, 0, 0, 0));
            vo.setUtcTime(LocalDateTime.of(2024, 1, 1, 12, 0, 0, 0));
            return vo;
        }


        @PostMapping("/user")
        public UserVO createUser(@RequestBody UserVO user) {
            // 模拟创建用户，设置创建时间
            user.setId(System.currentTimeMillis());
            user.setUpdateAt(OffsetDateTime.now());
            System.out.println(JSON.toJSONString(user));
            return user;
        }
    }

    /**
     * 测试用的用户VO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class UserVO {
        @OpenId
        private Long id;
        private String name;
        private OffsetDateTime createAt;
        private OffsetDateTime updateAt;
        private LocalDateTime orderTime = LocalDateTime.now();
    }

    /**
     * 测试用的时区VO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TimezoneVO {
        private OffsetDateTime beijingTime;
        private OffsetDateTime newYorkTime;
        private OffsetDateTime utcTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class LocalTimeVO {
        private LocalDateTime beijingTime;
        private LocalDateTime newYorkTime;
        private LocalDateTime utcTime;
    }
}