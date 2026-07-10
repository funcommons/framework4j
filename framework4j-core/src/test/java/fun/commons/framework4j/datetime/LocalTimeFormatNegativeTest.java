package fun.commons.framework4j.datetime;

import fun.commons.framework4j.api.ApiResponse;
import fun.commons.framework4j.api.GlobalExceptionHandler;
import fun.commons.framework4j.core.config.WebConfig;
import fun.commons.framework4j.id.config.IdSdkAutoConfiguration;
import fun.commons.framework4j.openid.config.OpenIdSwaggerConfig;
import fun.commons.framework4j.openid.config.OpenIdSwaggerModelConfig;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @LocalTimeFormat 注解反例测试（验证错误用法不生效）
 *
 * <p>测试场景：
 * <ul>
 *   <li>❌ 注解加在 VO/DTO 字段上（应该无效）</li>
 *   <li>❌ 注解加在 Service 层（应该无效）</li>
 *   <li>❌ 注解加在普通类上（应该无效）</li>
 *   <li>✅ null 值处理</li>
 *   <li>✅ 空集合处理</li>
 *   <li>✅ 混合类型集合处理</li>
 * </ul></p>
 *
 * @author LDX2T
 * @since 1.0.0
 */
@DisplayName("@LocalTimeFormat 注解反例测试")
public class LocalTimeFormatNegativeTest {

    @Nested
    @DisplayName("错误用法1：注解加在字段上（应该无效）")
    @SpringBootTest(classes = {
        LocalTimeFormatNegativeTest.TestConfig.class,
        WrongFieldAnnotationController.class
    })
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
        "ldx2t.commons.core.config.enabled=true",
        "ldx2t.commons.datetime.enabled=true",
        "spring.data.redis.repositories.enabled=false",
        "spring.datasource.url=",
        "spring.datasource.driver-class-name=",
        "management.metrics.enable.all=false",
        "management.health.defaults.enabled=false"
    })
    class WrongFieldAnnotationTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("验证：Controller 方法无注解时返回 ISO-8601 格式（VO 类无需注解）")
        void testFieldAnnotationIgnored() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/negative/field-annotation")
                    .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(1))
                    // 验证：Controller 方法无注解，应该保留 ISO-8601 格式
                    .andExpect(jsonPath("$.data.createTime", containsString("T")))
                    .andExpect(jsonPath("$.data.createTime", containsString("+08:00")))
                    .andReturn();

            String jsonResponse = result.getResponse().getContentAsString();
            assertThat(jsonResponse).contains("2025-12-10T10:00:00+08:00");
            assertThat(jsonResponse).doesNotContain("2025-12-10 10:00:00");

            // 说明：@LocalTimeFormat 注解的 @Target 不支持 FIELD
            // 因此无法在字段上添加该注解（编译器会报错）
            // 这验证了"注解只能加在 Controller 方法/类上"的设计
        }
    }

    @Nested
    @DisplayName("错误用法2：注解加在 Service 层（应该无效）")
    @SpringBootTest(classes = {
        LocalTimeFormatNegativeTest.TestConfig.class,
        WrongServiceAnnotationController.class,
        WrongAnnotatedService.class
    })
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
        "ldx2t.commons.core.config.enabled=true",
        "ldx2t.commons.datetime.enabled=true",
        "spring.data.redis.repositories.enabled=false",
        "spring.datasource.url=",
        "spring.datasource.driver-class-name=",
        "management.metrics.enable.all=false",
        "management.health.defaults.enabled=false"
    })
    class WrongServiceAnnotationTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("Service 层注解应该被忽略，返回 ISO-8601 格式")
        void testServiceAnnotationIgnored() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/negative/service-annotation")
                    .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(2))
                    // 验证：Service 层注解无效，应该保留 ISO-8601 格式
                    .andExpect(jsonPath("$.data.createTime", containsString("T")))
                    .andExpect(jsonPath("$.data.createTime", containsString("+08:00")))
                    .andReturn();

            String jsonResponse = result.getResponse().getContentAsString();
            assertThat(jsonResponse).contains("2025-12-11T11:00:00+08:00");
            assertThat(jsonResponse).doesNotContain("2025-12-11 11:00:00");
        }
    }

    @Nested
    @DisplayName("边界情况测试：null 和空集合")
    @SpringBootTest(classes = {
        LocalTimeFormatNegativeTest.TestConfig.class,
        BoundaryTestController.class
    })
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
        "ldx2t.commons.core.config.enabled=true",
        "ldx2t.commons.datetime.enabled=true",
        "spring.data.redis.repositories.enabled=false",
        "spring.datasource.url=",
        "spring.datasource.driver-class-name=",
        "management.metrics.enable.all=false",
        "management.health.defaults.enabled=false"
    })
    class BoundaryTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("null 值应该保持为 null，不会抛出异常")
        void testNullValues() throws Exception {
            mockMvc.perform(get("/api/boundary/null-values")
                    .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(3))
                    // 验证：null 值保持为 null
                    .andExpect(jsonPath("$.data.createTime").doesNotExist())
                    .andExpect(jsonPath("$.data.deliveryTime").doesNotExist())
                    .andExpect(jsonPath("$.data.eventTimes").doesNotExist());
        }

        @Test
        @DisplayName("空集合应该返回空数组，不会抛出异常")
        void testEmptyCollection() throws Exception {
            mockMvc.perform(get("/api/boundary/empty-collection")
                    .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(4))
                    .andExpect(jsonPath("$.data.eventTimes").isArray())
                    .andExpect(jsonPath("$.data.eventTimes.length()").value(0));
        }

        @Test
        @DisplayName("混合 null 和非 null 的集合应该正确处理")
        void testMixedNullCollection() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/boundary/mixed-null-collection")
                    .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(5))
                    .andExpect(jsonPath("$.data.eventTimes").isArray())
                    .andExpect(jsonPath("$.data.eventTimes.length()").value(3))
                    // 第一个元素是格式化的时间
                    .andExpect(jsonPath("$.data.eventTimes[0]").value("2025-12-10 10:00:00"))
                    // 第二个元素是 null
                    .andExpect(jsonPath("$.data.eventTimes[1]").isEmpty())
                    // 第三个元素是格式化的时间
                    .andExpect(jsonPath("$.data.eventTimes[2]").value("2025-12-10 15:00:00"))
                    .andReturn();

            String jsonResponse = result.getResponse().getContentAsString();
            assertThat(jsonResponse).contains("2025-12-10 10:00:00");
            assertThat(jsonResponse).contains("2025-12-10 15:00:00");
        }
    }

    @Nested
    @DisplayName("混合类型集合测试")
    @SpringBootTest(classes = {
        LocalTimeFormatNegativeTest.TestConfig.class,
        BoundaryTestController.class
    })
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
        "ldx2t.commons.core.config.enabled=true",
        "ldx2t.commons.datetime.enabled=true",
        "spring.data.redis.repositories.enabled=false",
        "spring.datasource.url=",
        "spring.datasource.driver-class-name=",
        "management.metrics.enable.all=false",
        "management.health.defaults.enabled=false"
    })
    class MixedTypeCollectionTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("混合类型集合只格式化 OffsetDateTime 类型")
        void testMixedTypeCollection() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/boundary/mixed-type-collection")
                    .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(4))
                    // 字符串元素保持不变
                    .andExpect(jsonPath("$.data[0]").value("字符串元素"))
                    // 数字元素保持不变
                    .andExpect(jsonPath("$.data[1]").value(12345))
                    // OffsetDateTime 元素被格式化
                    .andExpect(jsonPath("$.data[2]").value("2025-12-10 10:00:00"))
                    // 布尔元素保持不变
                    .andExpect(jsonPath("$.data[3]").value(true))
                    .andReturn();

            String jsonResponse = result.getResponse().getContentAsString();
            // 验证只有 OffsetDateTime 被格式化
            assertThat(jsonResponse).contains("字符串元素");
            assertThat(jsonResponse).contains("12345");
            assertThat(jsonResponse).contains("2025-12-10 10:00:00");
            assertThat(jsonResponse).contains("true");
            // 验证不包含 ISO-8601 格式
            assertThat(jsonResponse).doesNotContain("2025-12-10T10:00:00+08:00");
        }
    }

    // ==================== 测试用的 Controller 和 Service ====================

    /**
     * 错误用法1：在 VO 字段上加注解
     */
    @RestController
    @RequestMapping("/api/negative")
    static class WrongFieldAnnotationController {

        @GetMapping("/field-annotation")
        public ApiResponse<WrongFieldAnnotationVO> testFieldAnnotation() {
            WrongFieldAnnotationVO vo = new WrongFieldAnnotationVO();
            vo.setId(1L);
            vo.setName("字段注解测试");
            vo.setCreateTime(OffsetDateTime.parse("2025-12-10T10:00:00+08:00"));
            return ApiResponse.success(vo);
        }
    }

    /**
     * 错误示例：在字段上加注解（无效）
     * 注意：由于 @LocalTimeFormat 的 @Target 不支持 FIELD，
     * 实际上无法在字段上添加该注解（编译器会报错）。
     * 此测试验证：即使VO类本身没有任何注解，
     * 只要Controller方法没有注解，就应该返回ISO-8601格式。
     */
    @Data
    static class WrongFieldAnnotationVO {
        private Long id;
        private String name;
        // 注意：无法在字段上加 @LocalTimeFormat（编译器不允许）
        // 这个测试验证的是"不加注解"的默认行为
        private OffsetDateTime createTime;
    }

    /**
     * 错误用法2：在 Service 层加注解
     */
    @RestController
    @RequestMapping("/api/negative")
    static class WrongServiceAnnotationController {

        @Autowired
        private WrongAnnotatedService service;

        @GetMapping("/service-annotation")
        public ApiResponse<TestOrderVO> testServiceAnnotation() {
            return ApiResponse.success(service.getOrder());
        }
    }

    /**
     * 错误示例：在 Service 层加注解（无效）
     */
    @Service
    @LocalTimeFormat  // ❌ 错误：Service 层注解无效
    static class WrongAnnotatedService {

        public TestOrderVO getOrder() {
            TestOrderVO vo = new TestOrderVO();
            vo.setId(2L);
            vo.setName("Service 注解测试");
            vo.setCreateTime(OffsetDateTime.parse("2025-12-11T11:00:00+08:00"));
            return vo;
        }
    }

    /**
     * 边界情况测试 Controller
     */
    @RestController
    @RequestMapping("/api/boundary")
    @LocalTimeFormat  // 类级别注解，用于测试边界情况
    static class BoundaryTestController {

        @GetMapping("/null-values")
        public ApiResponse<TestOrderVO> testNullValues() {
            TestOrderVO vo = new TestOrderVO();
            vo.setId(3L);
            vo.setName("null 值测试");
            // createTime, deliveryTime, eventTimes 保持为 null
            return ApiResponse.success(vo);
        }

        @GetMapping("/empty-collection")
        public ApiResponse<TestOrderVO> testEmptyCollection() {
            TestOrderVO vo = new TestOrderVO();
            vo.setId(4L);
            vo.setName("空集合测试");
            vo.setCreateTime(OffsetDateTime.parse("2025-12-10T10:00:00+08:00"));
            vo.setEventTimes(Arrays.asList());  // 空集合
            return ApiResponse.success(vo);
        }

        @GetMapping("/mixed-null-collection")
        public ApiResponse<TestOrderVO> testMixedNullCollection() {
            TestOrderVO vo = new TestOrderVO();
            vo.setId(5L);
            vo.setName("混合 null 集合测试");
            vo.setEventTimes(Arrays.asList(
                OffsetDateTime.parse("2025-12-10T10:00:00+08:00"),
                null,  // null 元素
                OffsetDateTime.parse("2025-12-10T15:00:00+08:00")
            ));
            return ApiResponse.success(vo);
        }

        @GetMapping("/mixed-type-collection")
        public ApiResponse<List<Object>> testMixedTypeCollection() {
            List<Object> mixedList = Arrays.asList(
                "字符串元素",
                12345,
                OffsetDateTime.parse("2025-12-10T10:00:00+08:00"),
                true
            );
            return ApiResponse.success(mixedList);
        }
    }

    /**
     * 测试用 VO
     */
    @Data
    static class TestOrderVO {
        private Long id;
        private String name;
        private OffsetDateTime createTime;
        private OffsetDateTime deliveryTime;
        private List<OffsetDateTime> eventTimes;
    }

    /**
     * 测试配置（简化版，依赖 WebConfig 的自动配置）
     */
    @SpringBootApplication(exclude = {
        org.springdoc.webmvc.ui.SwaggerConfig.class,
        org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
        IdSdkAutoConfiguration.class,
        OpenIdSwaggerConfig.class,
        OpenIdSwaggerModelConfig.class
    })
    @Import({
        WebConfig.class,
        GlobalExceptionHandler.class
    })
    static class TestConfig {
        // WebConfig 已自动配置 TimeFormatInterceptor，无需重复定义
    }
}
