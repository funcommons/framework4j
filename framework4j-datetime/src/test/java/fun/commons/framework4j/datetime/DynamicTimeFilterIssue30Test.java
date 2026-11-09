package fun.commons.framework4j.datetime;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.datetime.config.DateTimeAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #30 回归（v1.10）：DynamicTimeFilter 两个序列化缺陷。
 * <ul>
 *   <li><b>①默认态变长 ISO</b>：{@code toString()} 秒与纳秒为 0 时省略秒段，wire 形态
 *       随数据漂移——改定长 {@code yyyy-MM-dd'T'HH:mm:ssXXX}</li>
 *   <li><b>②本地格式分支零时区换算</b>：javadoc 宣称 GMT+8 但代码按值自带 offset 排墙钟，
 *       UTC 值错 8 小时——格式化前 {@code atZoneSameInstant} 换算，目标时区可配
 *       （{@code framework4j.datetime.local-zone}，缺省 Asia/Shanghai）</li>
 * </ul>
 */
@DisplayName("issue #30：DynamicTimeFilter 定长 ISO + 本地分支时区换算")
class DynamicTimeFilterIssue30Test {

    /** 定长 ISO：秒段必须存在，offset 为 Z 或 ±HH:MM */
    private static final Pattern FIXED_ISO =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(Z|[+-]\\d{2}:\\d{2})");

    @BeforeEach
    void resetState() {
        TimeFormatStateHolder.clear();
    }

    @AfterEach
    void cleanState() {
        TimeFormatStateHolder.clear();
    }

    @Nested
    @DisplayName("缺陷①定长 ISO（wire 形态稳定）")
    class FixedIsoTest {

        @Test
        @DisplayName("默认态：秒为 0 的时间不再省略秒段（此前 toString() 概率漂移的根因）")
        void defaultStateKeepsZeroSeconds() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                    .addSerializer(OffsetDateTime.class, new DynamicTimeFilter()));

            // 秒与纳秒恰为 0 —— 此前输出 "2026-11-09T14:30+08:00"（变长）
            OffsetDateTime zeroSec = OffsetDateTime.of(2026, 11, 9, 14, 30, 0, 0, ZoneOffset.of("+08:00"));
            String json = mapper.writeValueAsString(zeroSec);

            assertThat(json).isEqualTo("\"2026-11-09T14:30:00+08:00\"")
                    .matches("\"" + FIXED_ISO.pattern() + "\"");
        }

        @Test
        @DisplayName("默认态：带秒值同样定长，两种形态统一")
        void defaultStateWithSecondsSameShape() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                    .addSerializer(OffsetDateTime.class, new DynamicTimeFilter()));

            String json = mapper.writeValueAsString(
                    OffsetDateTime.of(2026, 11, 9, 14, 30, 5, 0, ZoneOffset.of("+08:00")));

            assertThat(json).isEqualTo("\"2026-11-09T14:30:05+08:00\"");
        }

        @Test
        @DisplayName("注解 ISO 态（state 非 default）同样定长")
        void annotationIsoStateAlsoFixedLength() throws Exception {
            TimeFormatStateHolder.setState(new TimeFormatState(false, "test-source", true));

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                    .addSerializer(OffsetDateTime.class, new DynamicTimeFilter()));

            String json = mapper.writeValueAsString(
                    OffsetDateTime.of(2026, 11, 9, 14, 30, 0, 0, ZoneOffset.of("+08:00")));

            assertThat(json).isEqualTo("\"2026-11-09T14:30:00+08:00\"");
        }

        @Test
        @DisplayName("UTC offset 值保留自身 offset（ISO 分支不换算，只补定长秒段）")
        void utcOffsetPreservedInIsoBranch() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                    .addSerializer(OffsetDateTime.class, new DynamicTimeFilter()));

            String json = mapper.writeValueAsString(OffsetDateTime.of(2026, 11, 9, 14, 0, 0, 0, ZoneOffset.UTC));

            assertThat(json).isEqualTo("\"2026-11-09T14:00:00Z\"");
        }
    }

    @Nested
    @DisplayName("缺陷②本地格式分支时区换算")
    class LocalZoneConversionTest {

        @Test
        @DisplayName("缺省 Asia/Shanghai：UTC 14:00 → 北京墙钟 22:00（此前错排 14:00）")
        void utcValueConvertedToShanghaiWallClock() throws Exception {
            TimeFormatStateHolder.setUseLocal(true);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                    .addSerializer(OffsetDateTime.class, new DynamicTimeFilter()));

            // UTC 14:00 的北京墙钟是 22:00（此前直接排 "2026-11-09 14:00:00"，错 8 小时）
            String json = mapper.writeValueAsString(OffsetDateTime.of(2026, 11, 9, 14, 0, 0, 0, ZoneOffset.UTC));

            assertThat(json).isEqualTo("\"2026-11-09 22:00:00\"");
        }

        @Test
        @DisplayName("构造器指定 UTC：本地分支输出 UTC 墙钟")
        void customZoneHonored() throws Exception {
            TimeFormatStateHolder.setUseLocal(true);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                    .addSerializer(OffsetDateTime.class, new DynamicTimeFilter(ZoneId.of("UTC"))));

            String json = mapper.writeValueAsString(OffsetDateTime.of(2026, 11, 9, 14, 0, 0, 0, ZoneOffset.UTC));

            assertThat(json).isEqualTo("\"2026-11-09 14:00:00\"");
        }

        @Test
        @DisplayName("+08:00 值在缺省时区下墙钟不变（换算幂等）")
        void sameOffsetValueUnchanged() throws Exception {
            TimeFormatStateHolder.setUseLocal(true);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                    .addSerializer(OffsetDateTime.class, new DynamicTimeFilter()));

            String json = mapper.writeValueAsString(
                    OffsetDateTime.of(2026, 11, 9, 14, 30, 5, 0, ZoneOffset.of("+08:00")));

            assertThat(json).isEqualTo("\"2026-11-09 14:30:05\"");
        }
    }

    @Nested
    @DisplayName("容器装配（local-zone 可配 + fail-fast）")
    class ContainerWiringTest {

        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DateTimeAutoConfiguration.class, JacksonAutoConfiguration.class));

        @Test
        @DisplayName("framework4j.datetime.local-zone=UTC：本地分支按 UTC 墙钟输出")
        void localZonePropertyHonored() {
            TimeFormatStateHolder.setUseLocal(true);
            try {
                runner.withPropertyValues("framework4j.datetime.local-zone=UTC").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("framework4jDateTimeJacksonCustomizer");
                });
            } finally {
                TimeFormatStateHolder.clear();
            }
        }

        @Test
        @DisplayName("非法时区 → 启动失败（fail-fast）")
        void invalidZoneFailsFast() {
            runner.withPropertyValues("framework4j.datetime.local-zone=Not/AZone")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .hasStackTraceContaining("framework4j.datetime.local-zone 非法时区");
                    });
        }

        @Test
        @DisplayName("缺省 local-zone：容器 ObjectMapper 走定长 ISO（默认态 wire 契约）")
        void defaultContainerMapperEmitsFixedIso() throws Exception {
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                ObjectMapper mapper = context.getBean(ObjectMapper.class);
                // 默认态（无注解状态）→ 定长 ISO
                String json = mapper.writeValueAsString(
                        OffsetDateTime.of(2026, 11, 9, 14, 30, 0, 0, ZoneOffset.of("+08:00")));
                assertThat(json).isEqualTo("\"2026-11-09T14:30:00+08:00\"");
            });
        }
    }
}
