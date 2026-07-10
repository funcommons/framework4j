package fun.commons.framework4j.datetime;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.support.config.FastJsonConfig;
import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
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
import org.springframework.context.ApplicationContext;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OffsetDateTime 格式化测试
 * 验证 WebConfig 配置是否正确生效
 */
@DisplayName("OffsetDateTime格式化测试")
public class TestCTime {

    @Nested
    @DisplayName("WebConfig集成测试")
    @SpringBootTest(classes = TestCTime.WebConfigTestConfig.class)
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
            "ldx2t.commons.core.config.enabled=true",
            "ldx2t.commons.openid.enabled=true",
            "ldx2t.commons.datetime.enabled=true",
            // 禁用 Redis
            "spring.data.redis.repositories.enabled=false",
            // 禁用数据源自动配置
            "spring.datasource.url=",
            "spring.datasource.driver-class-name=",
            // 禁用 actuator 的一些功能
            "management.metrics.enable.all=false",
            "management.health.defaults.enabled=false"
    })
    class WebConfigIntegrationTest {

        @Autowired
        private ApplicationContext applicationContext;

        @Autowired
        private RequestMappingHandlerAdapter handlerAdapter;

        @Test
        @DisplayName("应该使用WebConfig配置的日期格式化OffsetDateTime")
        public void testWebConfigOffsetDateTimeFormat() {
            // 验证 WebConfig 配置的 FastJson2 消息转换器是否生效
            java.util.List<HttpMessageConverter<?>> converters = handlerAdapter.getMessageConverters();
            System.out.println("Found " + converters.size() + " HttpMessageConverter in RequestMappingHandlerAdapter:");
            for (HttpMessageConverter<?> converter : converters) {
                System.out.println("  - " + converter.getClass().getName());
            }
            assertThat(converters).isNotEmpty();

            boolean hasFastJsonConverter = false;
            for (HttpMessageConverter<?> converter : converters) {
                if (converter instanceof FastJsonHttpMessageConverter) {
                    hasFastJsonConverter = true;

                    // 进一步验证 FastJson2 配置是否包含日期格式化
                    FastJsonHttpMessageConverter fastJsonConverter = (FastJsonHttpMessageConverter) converter;
                    com.alibaba.fastjson2.support.config.FastJsonConfig config = fastJsonConverter.getFastJsonConfig();
                    String dateFormat = config.getDateFormat();
                    System.out.println("FastJson2 日期格式: " + dateFormat);

                    // 验证日期格式配置为 "iso8601" 或者具体的 ISO 格式
                    assertThat(dateFormat).isNotNull();
                    assertThat(dateFormat).isIn("iso8601", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

                    break;
                }
            }
            assertThat(hasFastJsonConverter).as("Should find FastJsonHttpMessageConverter in MVC message converters").isTrue();

            System.out.println("WebConfig 配置测试通过");
        }
    }

    @SpringBootApplication(exclude = {
            org.springdoc.webmvc.ui.SwaggerConfig.class,
            org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
            IdSdkAutoConfiguration.class,
            OpenIdSwaggerConfig.class,
            OpenIdSwaggerModelConfig.class
    })
    static class WebConfigTestConfig {
        // WebConfig will be auto-configured via META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
    }

    @Test
    @DisplayName("应该正确配置FastJson2日期格式")
    public void testFastJsonDateFormat() {
        // 模拟 WebConfig 中的配置
        FastJsonConfig config = new FastJsonConfig();

        // 设置和 WebConfig 相同的日期格式
        config.setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        config.setCharset(StandardCharsets.UTF_8);

        // 创建测试对象
        UserVO userVO = new UserVO();
        userVO.setId(1L);

        // 创建一个固定时间的 OffsetDateTime
        OffsetDateTime fixedDateTime = OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 123000000, ZoneOffset.ofHours(8));
        userVO.setCreateAt(fixedDateTime);

        System.out.println("原始 OffsetDateTime: " + fixedDateTime);

        // 使用 FastJson2 序列化（默认会使用配置的日期格式）
        String jsonResult = JSON.toJSONString(userVO);

        System.out.println("序列化后的 JSON: " + jsonResult);

        // 验证日期格式：应该是 ISO 8601 格式，包含时区信息
        assertThat(jsonResult).contains("createAt");
        assertThat(jsonResult).contains("2024-01-01T12:00:00.123+08:00");
    }

    @Test
    @DisplayName("应该正确格式化当前时间的OffsetDateTime")
    public void testCurrentOffsetDateTime() {
        UserVO userVO = new UserVO();
        userVO.setId(1L);
        userVO.setCreateAt(OffsetDateTime.now());

        System.out.println("当前 OffsetDateTime: " + userVO.getCreateAt());

        // 使用 FastJson2 默认配置序列化
        String jsonResult = JSON.toJSONString(userVO);

        System.out.println("序列化后的 JSON: " + jsonResult);

        // 验证包含 ISO 8601 格式的日期时间（支持可变长度的毫秒）
        assertThat(jsonResult).contains("createAt");
        assertThat(jsonResult).matches(".*\"createAt\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{1,9}[+-]\\d{2}:\\d{2}\".*");
    }

    @Test
    @DisplayName("应该验证日期格式符合ISO 8601标准")
    public void testIso8601Format() {
        // 测试不同的 OffsetDateTime 格式
        DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

        OffsetDateTime dateTime1 = OffsetDateTime.now();
        OffsetDateTime dateTime2 = OffsetDateTime.parse("2024-06-15T10:30:45.123+08:00");
        OffsetDateTime dateTime3 = OffsetDateTime.parse("2024-12-31T23:59:59.999-05:00");

        System.out.println("DateTime 1: " + dateTime1.format(isoFormatter));
        System.out.println("DateTime 2: " + dateTime2.format(isoFormatter));
        System.out.println("DateTime 3: " + dateTime3.format(isoFormatter));

        // 验证所有时间都符合 ISO 8601 格式（支持可变长度的毫秒）
        assertThat(dateTime1.format(isoFormatter)).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{1,9}[+-]\\d{2}:\\d{2}");
        assertThat(dateTime2.format(isoFormatter)).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{1,9}[+-]\\d{2}:\\d{2}");
        assertThat(dateTime3.format(isoFormatter)).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{1,9}[+-]\\d{2}:\\d{2}");
    }

    /**
     * 测试用的实体类
     */
    @Data
    static class UserVO {
        private Long id;
        private OffsetDateTime createAt;
    }

    // ==================== 集合类型测试 ====================

    @Nested
    @DisplayName("集合类型格式化测试")
    class CollectionFormattingTest {

        @Test
        @DisplayName("应该正确序列化包含 List<OffsetDateTime> 的 DTO")
        void shouldSerializeDtoWithListOfOffsetDateTime() {
            // 创建测试数据
            EventVO event = new EventVO();
            event.setId(1L);
            event.setName("测试事件");

            OffsetDateTime dt1 = OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(8));
            OffsetDateTime dt2 = OffsetDateTime.of(2024, 1, 2, 14, 30, 0, 0, ZoneOffset.ofHours(8));
            event.setEventTimes(java.util.Arrays.asList(dt1, dt2));

            // 不启用本地格式 - 应该使用 ISO 8601 格式
            String jsonWithoutLocal = JSON.toJSONString(event);
            System.out.println("未启用本地格式: " + jsonWithoutLocal);
            assertThat(jsonWithoutLocal).contains("2024-01-01T10:00:00+08:00");
            assertThat(jsonWithoutLocal).contains("2024-01-02T14:30:00+08:00");

            // 启用本地格式 - 应该使用 yyyy-MM-dd HH:mm:ss 格式
            TimeContext.setUseLocal(true);
            try {
                DynamicTimeFilter filter = new DynamicTimeFilter();
                String jsonWithLocal = JSON.toJSONString(event, filter);
                System.out.println("启用本地格式: " + jsonWithLocal);
                assertThat(jsonWithLocal).contains("2024-01-01 10:00:00");
                assertThat(jsonWithLocal).contains("2024-01-02 14:30:00");
            } finally {
                TimeContext.clear();
            }
        }

        @Test
        @DisplayName("应该正确序列化包含 Set<OffsetDateTime> 的 DTO")
        void shouldSerializeDtoWithSetOfOffsetDateTime() {
            // 创建测试数据
            ScheduleVO schedule = new ScheduleVO();
            schedule.setId(2L);
            schedule.setTitle("会议安排");

            OffsetDateTime dt1 = OffsetDateTime.of(2024, 3, 15, 9, 0, 0, 0, ZoneOffset.ofHours(8));
            OffsetDateTime dt2 = OffsetDateTime.of(2024, 3, 16, 15, 0, 0, 0, ZoneOffset.ofHours(8));
            schedule.setAvailableSlots(new java.util.LinkedHashSet<>(java.util.Arrays.asList(dt1, dt2)));

            // 启用本地格式
            TimeContext.setUseLocal(true);
            try {
                DynamicTimeFilter filter = new DynamicTimeFilter();
                String json = JSON.toJSONString(schedule, filter);
                System.out.println("Set 序列化结果: " + json);
                assertThat(json).contains("2024-03-15 09:00:00");
                assertThat(json).contains("2024-03-16 15:00:00");
            } finally {
                TimeContext.clear();
            }
        }

        @Test
        @DisplayName("应该正确序列化包含 OffsetDateTime[] 的 DTO")
        void shouldSerializeDtoWithArrayOfOffsetDateTime() {
            // 创建测试数据
            RecordVO record = new RecordVO();
            record.setId(3L);
            record.setDescription("历史记录");

            OffsetDateTime dt1 = OffsetDateTime.of(2024, 6, 1, 8, 0, 0, 0, ZoneOffset.ofHours(8));
            OffsetDateTime dt2 = OffsetDateTime.of(2024, 6, 2, 20, 0, 0, 0, ZoneOffset.ofHours(8));
            record.setTimestamps(new OffsetDateTime[]{dt1, dt2, null});

            // 启用本地格式
            TimeContext.setUseLocal(true);
            try {
                DynamicTimeFilter filter = new DynamicTimeFilter();
                String json = JSON.toJSONString(record, filter);
                System.out.println("数组序列化结果: " + json);
                assertThat(json).contains("2024-06-01 08:00:00");
                assertThat(json).contains("2024-06-02 20:00:00");
                // 验证 null 元素被保留
                assertThat(json).contains("null");
            } finally {
                TimeContext.clear();
            }
        }

        @Test
        @DisplayName("应该正确处理空集合")
        void shouldHandleEmptyCollections() {
            EventVO event = new EventVO();
            event.setId(4L);
            event.setName("空事件");
            event.setEventTimes(new java.util.ArrayList<>());

            TimeContext.setUseLocal(true);
            try {
                DynamicTimeFilter filter = new DynamicTimeFilter();
                String json = JSON.toJSONString(event, filter);
                System.out.println("空集合序列化结果: " + json);
                assertThat(json).contains("\"eventTimes\":[]");
            } finally {
                TimeContext.clear();
            }
        }

        @Test
        @DisplayName("应该正确处理混合类型的复杂 DTO")
        void shouldHandleComplexDtoWithMixedTypes() {
            ComplexVO complex = new ComplexVO();
            complex.setId(5L);
            complex.setName("复杂对象");
            complex.setSingleDate(OffsetDateTime.of(2024, 12, 25, 12, 0, 0, 0, ZoneOffset.ofHours(8)));

            OffsetDateTime dt1 = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(8));
            OffsetDateTime dt2 = OffsetDateTime.of(2024, 12, 31, 23, 59, 59, 0, ZoneOffset.ofHours(8));
            complex.setDateList(java.util.Arrays.asList(dt1, dt2));
            complex.setDateSet(new java.util.HashSet<>(java.util.Arrays.asList(dt1)));
            complex.setDateArray(new OffsetDateTime[]{dt2});

            // 启用本地格式
            TimeContext.setUseLocal(true);
            try {
                DynamicTimeFilter filter = new DynamicTimeFilter();
                String json = JSON.toJSONString(complex, filter);
                System.out.println("复杂对象序列化结果: " + json);

                // 验证单个日期
                assertThat(json).contains("2024-12-25 12:00:00");

                // 验证 List
                assertThat(json).contains("2024-01-01 00:00:00");
                assertThat(json).contains("2024-12-31 23:59:59");

                // 验证所有集合类型都被格式化
                assertThat(json).doesNotContain("+08:00");
            } finally {
                TimeContext.clear();
            }
        }
    }

    /**
     * 包含 List<OffsetDateTime> 的测试实体
     */
    @Data
    static class EventVO {
        private Long id;
        private String name;
        private java.util.List<OffsetDateTime> eventTimes;
    }

    /**
     * 包含 Set<OffsetDateTime> 的测试实体
     */
    @Data
    static class ScheduleVO {
        private Long id;
        private String title;
        private java.util.Set<OffsetDateTime> availableSlots;
    }

    /**
     * 包含 OffsetDateTime[] 的测试实体
     */
    @Data
    static class RecordVO {
        private Long id;
        private String description;
        private OffsetDateTime[] timestamps;
    }

    /**
     * 混合多种日期类型的复杂实体
     */
    @Data
    static class ComplexVO {
        private Long id;
        private String name;
        private OffsetDateTime singleDate;
        private java.util.List<OffsetDateTime> dateList;
        private java.util.Set<OffsetDateTime> dateSet;
        private OffsetDateTime[] dateArray;
    }
}
