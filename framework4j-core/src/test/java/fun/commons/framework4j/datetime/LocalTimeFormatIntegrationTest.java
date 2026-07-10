package fun.commons.framework4j.datetime;

import fun.commons.framework4j.api.GlobalExceptionHandler;
import fun.commons.framework4j.core.config.WebConfig;
import fun.commons.framework4j.id.config.IdSdkAutoConfiguration;
import fun.commons.framework4j.openid.config.OpenIdSwaggerConfig;
import fun.commons.framework4j.openid.config.OpenIdSwaggerModelConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @LocalTimeFormat 注解 Spring MVC 集成测试
 */
@DisplayName("@LocalTimeFormat 注解集成测试")
public class LocalTimeFormatIntegrationTest {

    @Nested
    @DisplayName("方法级别注解测试")
    @SpringBootTest(classes = {
        LocalTimeFormatIntegrationTest.TestConfig.class,
        LocalTimeFormatTestController.class
    })
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
        "ldx2t.commons.core.config.enabled=true",
        "ldx2t.commons.datetime.enabled=true",
        // 禁用不需要的自动配置
        "spring.data.redis.repositories.enabled=false",
        "spring.datasource.url=",
        "spring.datasource.driver-class-name=",
        "management.metrics.enable.all=false",
        "management.health.defaults.enabled=false"
    })
    class MethodLevelAnnotationTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("方法级别 @LocalTimeFormat 注解应该格式化为本地时间格式")
        void testMethodLevelAnnotation() throws Exception {
            mockMvc.perform(get("/api/time-test/method-level")
                    .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("测试订单"))
                    // 验证本地时间格式：yyyy-MM-dd HH:mm:ss
                    .andExpect(jsonPath("$.data.createTime").value("2025-12-10 10:30:00"))
                    .andExpect(jsonPath("$.data.deliveryTime").value("2025-12-15 14:20:00"))
                    // 验证集合中的时间也被格式化
                    .andExpect(jsonPath("$.data.eventTimes[0]").value("2025-12-10 09:00:00"))
                    .andExpect(jsonPath("$.data.eventTimes[1]").value("2025-12-10 18:00:00"))
                    // 验证不包含 ISO-8601 格式的特殊字符
                    .andExpect(jsonPath("$.data.createTime", not(containsString("+08:00"))))
                    .andExpect(jsonPath("$.data.createTime", not(containsString("T"))))
                    .andExpect(jsonPath("$.data.deliveryTime", not(containsString("+08:00"))))
                    .andExpect(jsonPath("$.data.deliveryTime", not(containsString("T"))));
        }

        @Test
        @DisplayName("批量数据应该全部被格式化")
        void testBatchDataFormatting() throws Exception {
            mockMvc.perform(get("/api/time-test/batch")
                    .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(3))
                    // 验证第一个订单
                    .andExpect(jsonPath("$.data[0].id").value(4))
                    .andExpect(jsonPath("$.data[0].createTime").value("2025-09-10 10:00:00"))
                    .andExpect(jsonPath("$.data[0].deliveryTime").value("2025-09-15 14:00:00"))
                    // 验证第二个订单
                    .andExpect(jsonPath("$.data[1].id").value(5))
                    .andExpect(jsonPath("$.data[1].createTime").value("2025-09-11 11:30:00"))
                    .andExpect(jsonPath("$.data[1].deliveryTime").value("2025-09-16 15:30:00"))
                    // 验证第三个订单
                    .andExpect(jsonPath("$.data[2].id").value(6))
                    .andExpect(jsonPath("$.data[2].createTime").value("2025-09-12 09:45:00"))
                    .andExpect(jsonPath("$.data[2].deliveryTime").value("2025-09-17 12:45:00"));
        }

        @Test
        @DisplayName("复杂嵌套对象中的时间应该被正确格式化")
        void testNestedObjectFormatting() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/time-test/nested")
                    .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(7))
                    // 验证主订单时间格式化
                    .andExpect(jsonPath("$.data.createTime").value("2025-08-05 13:00:00"))
                    .andExpect(jsonPath("$.data.deliveryTime").value("2025-08-10 17:00:00"))
                    // 验证子订单时间格式化
                    .andExpect(jsonPath("$.data.subOrders").isArray())
                    .andExpect(jsonPath("$.data.subOrders.length()").value(2))
                    .andExpect(jsonPath("$.data.subOrders[0].createTime").value("2025-08-06 09:00:00"))
                    .andExpect(jsonPath("$.data.subOrders[0].deliveryTime").value("2025-08-11 13:00:00"))
                    .andExpect(jsonPath("$.data.subOrders[1].createTime").value("2025-08-07 14:30:00"))
                    .andExpect(jsonPath("$.data.subOrders[1].deliveryTime").value("2025-08-12 18:30:00"))
                    // 验证不包含 ISO-8601 格式
                    .andExpect(jsonPath("$.data.createTime", not(containsString("+08:00"))))
                    .andExpect(jsonPath("$.data.createTime", not(containsString("T"))))
                    .andReturn();

            // 额外验证：检查 JSON 响应内容
            String jsonResponse = result.getResponse().getContentAsString();
            assertThat(jsonResponse).contains("2025-08-05 13:00:00");
            assertThat(jsonResponse).doesNotContain("2025-08-05T13:00:00+08:00");
        }
    }

    @Nested
    @DisplayName("类级别注解测试")
    @SpringBootTest(classes = {
        LocalTimeFormatIntegrationTest.TestConfig.class,
        ClassLevelAnnotationTestController.class
    })
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
        "ldx2t.commons.core.config.enabled=true",
        "ldx2t.commons.datetime.enabled=true",
        // 禁用不需要的自动配置
        "spring.data.redis.repositories.enabled=false",
        "spring.datasource.url=",
        "spring.datasource.driver-class-name=",
        "management.metrics.enable.all=false",
        "management.health.defaults.enabled=false"
    })
    class ClassLevelAnnotationTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("类级别 @LocalTimeFormat 注解应该对所有方法生效")
        void testClassLevelAnnotationMethod1() throws Exception {
            mockMvc.perform(get("/api/time-test-class/method1")
                    .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(10))
                    .andExpect(jsonPath("$.data.name").value("类级别方法1"))
                    // 验证本地时间格式
                    .andExpect(jsonPath("$.data.createTime").value("2025-07-10 10:00:00"))
                    .andExpect(jsonPath("$.data.createTime", not(containsString("+08:00"))))
                    .andExpect(jsonPath("$.data.createTime", not(containsString("T"))));
        }

        @Test
        @DisplayName("类级别 @LocalTimeFormat 注解应该对另一个方法也生效")
        void testClassLevelAnnotationMethod2() throws Exception {
            mockMvc.perform(get("/api/time-test-class/method2")
                    .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(11))
                    .andExpect(jsonPath("$.data.name").value("类级别方法2"))
                    // 验证本地时间格式
                    .andExpect(jsonPath("$.data.createTime").value("2025-07-11 15:30:00"))
                    .andExpect(jsonPath("$.data.createTime", not(containsString("+08:00"))))
                    .andExpect(jsonPath("$.data.createTime", not(containsString("T"))));
        }
    }

    @Nested
    @DisplayName("无注解对比测试")
    @SpringBootTest(classes = {
        LocalTimeFormatIntegrationTest.TestConfig.class,
        LocalTimeFormatTestController.class
    })
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
        "ldx2t.commons.core.config.enabled=true",
        "ldx2t.commons.datetime.enabled=true",
        // 禁用不需要的自动配置
        "spring.data.redis.repositories.enabled=false",
        "spring.datasource.url=",
        "spring.datasource.driver-class-name=",
        "management.metrics.enable.all=false",
        "management.health.defaults.enabled=false"
    })
    class NoAnnotationTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("无注解方法应该返回 ISO-8601 格式")
        void testNoAnnotationKeepsIsoFormat() throws Exception {
            mockMvc.perform(get("/api/time-test/no-annotation")
                    .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(3))
                    .andExpect(jsonPath("$.data.name").value("无注解测试订单"))
                    // 验证保留 ISO-8601 格式，包含时区信息
                    .andExpect(jsonPath("$.data.createTime").value("2025-10-15T12:00:00+08:00"))
                    .andExpect(jsonPath("$.data.deliveryTime").value("2025-10-20T16:30:00+08:00"))
                    // 验证集合中也保留 ISO-8601 格式
                    .andExpect(jsonPath("$.data.eventTimes[0]").value("2025-10-15T08:00:00+08:00"))
                    .andExpect(jsonPath("$.data.eventTimes[1]").value("2025-10-15T19:00:00+08:00"))
                    // 验证包含 ISO-8601 格式的特殊字符
                    .andExpect(jsonPath("$.data.createTime", containsString("T")))
                    .andExpect(jsonPath("$.data.createTime", containsString("+08:00")));
        }

        @Test
        @DisplayName("同一个 Controller 中不同注解方法应该有不同的行为")
        void testMixedAnnotationBehavior() throws Exception {
            // 先测试无注解方法
            MvcResult noAnnotationResult = mockMvc.perform(get("/api/time-test/no-annotation")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.createTime", containsString("T")))
                    .andReturn();

            // 再测试有注解方法
            MvcResult annotatedResult = mockMvc.perform(get("/api/time-test/method-level")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.createTime", not(containsString("T"))))
                    .andReturn();

            // 验证响应确实不同
            String noAnnotationJson = noAnnotationResult.getResponse().getContentAsString();
            String annotatedJson = annotatedResult.getResponse().getContentAsString();

            // 验证两个响应确实不相同
            assertThat(noAnnotationJson).isNotEqualTo(annotatedJson);

            // 验证有注解响应包含本地时间格式
            assertThat(annotatedJson).contains("2025-12-10 10:30:00");
            assertThat(annotatedJson).contains("2025-12-15 14:20:00");

            // 验证无注解响应包含 ISO-8601 格式
            assertThat(noAnnotationJson).contains("2025-10-15T12:00:00+08:00");
            assertThat(noAnnotationJson).contains("2025-10-20T16:30:00+08:00");

            // 验证时间格式不同：注解响应使用本地格式，无注解响应使用 ISO-8601 格式
            // 通过验证注解响应包含本地格式特征，无注解响应包含 ISO-8601 特征
            assertThat(annotatedJson).containsPattern("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
            assertThat(noAnnotationJson).containsPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+08:00");
        }
    }

    @Nested
    @DisplayName("TimeContext 隔离测试")
    @SpringBootTest(classes = {
        LocalTimeFormatIntegrationTest.TestConfig.class,
        LocalTimeFormatTestController.class
    })
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
        "ldx2t.commons.core.config.enabled=true",
        "ldx2t.commons.datetime.enabled=true",
        // 禁用不需要的自动配置
        "spring.data.redis.repositories.enabled=false",
        "spring.datasource.url=",
        "spring.datasource.driver-class-name=",
        "management.metrics.enable.all=false",
        "management.health.defaults.enabled=false"
    })
    class TimeContextIsolationTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("ThreadLocal 应该在不同请求间正确隔离")
        void testThreadLocalIsolation() throws Exception {
            // 执行多个请求，验证每个请求的行为一致
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(get("/api/time-test/no-annotation")
                        .accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(0))
                        // 无注解方法总是返回 ISO-8601 格式
                        .andExpect(jsonPath("$.data.createTime", containsString("T")))
                        .andExpect(jsonPath("$.data.createTime", containsString("+08:00")));

                mockMvc.perform(get("/api/time-test/method-level")
                        .accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(0))
                        // 有注解方法总是返回本地格式
                        .andExpect(jsonPath("$.data.createTime", not(containsString("T"))))
                        .andExpect(jsonPath("$.data.createTime", not(containsString("+08:00"))));
            }
        }
    }

    /**
     * 测试配置
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
    static class TestConfig implements WebMvcConfigurer {

        @Bean
        public TimeFormatInterceptor timeFormatInterceptor() {
            return new TimeFormatInterceptor();
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(timeFormatInterceptor())
                    .addPathPatterns("/api/**");
        }
    }
}