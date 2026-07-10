package fun.commons.framework4j.datetime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.*;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.*;

@DisplayName("时间格式化补充测试")
class TimeFormatAdditionalTest {

    @Test
    @DisplayName("OffsetDateTime 序列化为 ISO-8601")
    void offsetDateTimeToIso8601() {
        OffsetDateTime t = OffsetDateTime.of(2024, 12, 10, 14, 30, 45, 0, ZoneOffset.ofHours(8));
        String formatted = t.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        assertThat(formatted).contains("2024-12-10T14:30:45+08:00");
    }

    @Test
    @DisplayName("OffsetDateTime UTC 时区")
    void offsetDateTimeUtc() {
        OffsetDateTime t = OffsetDateTime.of(2024, 12, 10, 6, 0, 0, 0, ZoneOffset.UTC);
        assertThat(t.getHour()).isEqualTo(6);
        assertThat(t.getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("OffsetDateTime 时区转换")
    void offsetDateTimeZoneConversion() {
        OffsetDateTime utc = OffsetDateTime.of(2024, 12, 10, 6, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime beijing = utc.withOffsetSameInstant(ZoneOffset.ofHours(8));
        assertThat(beijing.getHour()).isEqualTo(14);
    }

    @Test
    @DisplayName("Long.MAX_VALUE 转 String（防精度丢失）")
    void longMaxToString() {
        long max = Long.MAX_VALUE;
        String str = String.valueOf(max);
        assertThat(str).isEqualTo("9223372036854775807");
        assertThat(Long.parseLong(str)).isEqualTo(max);
    }

    @Test
    @DisplayName("时间戳毫秒 → OffsetDateTime 转换")
    void millisToOffsetDateTime() {
        long millis = 1718660400000L; // 2024-06-18T01:00:00Z
        Instant instant = Instant.ofEpochMilli(millis);
        OffsetDateTime odt = instant.atOffset(ZoneOffset.UTC);
        assertThat(odt.getYear()).isEqualTo(2024);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-12-10T14:30:45+08:00", "2024-12-10 14:30:45", "2024-12-10T14:30:45Z"})
    @DisplayName("多种时间格式解析")
    void parseMultipleFormats(String input) {
        assertThatCode(() -> {
            if (input.contains("T")) {
                OffsetDateTime.parse(input);
            } else {
                LocalDateTime.parse(input, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Duration 解析：1m / 1h / 1s")
    void durationParse() {
        assertThat(Duration.parse("PT1M").getSeconds()).isEqualTo(60);
        assertThat(Duration.parse("PT1H").getSeconds()).isEqualTo(3600);
        assertThat(Duration.parse("PT1S").getSeconds()).isEqualTo(1);
    }

    @Test
    @DisplayName("Duration 解析：2h30m")
    void durationComplex() {
        Duration d = Duration.parse("PT2H30M");
        assertThat(d.toHours()).isEqualTo(2);
        assertThat(d.toMinutes()).isEqualTo(150);
    }

    @Test
    @DisplayName("ZoneId 常用时区")
    void zoneIdCommon() {
        assertThat(ZoneId.of("Asia/Shanghai").getId()).isEqualTo("Asia/Shanghai");
        assertThat(ZoneId.of("UTC").getId()).isEqualTo("UTC");
        assertThat(ZoneOffset.ofHours(8).getTotalSeconds()).isEqualTo(28800);
    }

    @Test
    @DisplayName("OffsetDateTime 比较")
    void offsetDateTimeComparison() {
        OffsetDateTime t1 = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime t2 = OffsetDateTime.of(2024, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);
        assertThat(t1.isBefore(t2)).isTrue();
        assertThat(t2.isAfter(t1)).isTrue();
        assertThat(t1.isEqual(t1)).isTrue();
    }

    @Test
    @DisplayName("OffsetDateTime 加减")
    void offsetDateTimePlusMinus() {
        OffsetDateTime t = OffsetDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC);
        assertThat(t.plusDays(1).getDayOfMonth()).isEqualTo(16);
        assertThat(t.minusHours(2).getHour()).isEqualTo(10);
        assertThat(t.plusWeeks(1).getDayOfMonth()).isEqualTo(22);
    }

    @Test
    @DisplayName("LocalDateTime ↔ OffsetDateTime 转换")
    void localToOffset() {
        LocalDateTime ldt = LocalDateTime.of(2024, 6, 15, 12, 0, 0);
        OffsetDateTime odt = ldt.atOffset(ZoneOffset.ofHours(8));
        assertThat(odt.getHour()).isEqualTo(12);
        assertThat(odt.getOffset().getTotalSeconds()).isEqualTo(28800);
    }

    @Test
    @DisplayName("Instant ↔ OffsetDateTime 转换")
    void instantToOffset() {
        Instant now = Instant.now();
        OffsetDateTime odt1 = now.atOffset(ZoneOffset.UTC);
        OffsetDateTime odt2 = now.atOffset(ZoneOffset.ofHours(8));
        assertThat(odt1.toInstant()).isEqualTo(odt2.toInstant());
    }

    @Test
    @DisplayName("时间戳格式化（毫秒 → yyyy-MM-dd HH:mm:ss）")
    void timestampFormatting() {
        OffsetDateTime t = OffsetDateTime.of(2024, 12, 10, 14, 30, 45, 0, ZoneOffset.ofHours(8));
        String formatted = t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        assertThat(formatted).isEqualTo("2024-12-10 14:30:45");
    }

    @Test
    @DisplayName("时区偏移量列表")
    void zoneOffsets() {
        assertThat(ZoneOffset.ofHours(0).getId()).isEqualTo("Z");
        assertThat(ZoneOffset.ofHours(8).getId()).isEqualTo("+08:00");
        assertThat(ZoneOffset.ofHours(-5).getId()).isEqualTo("-05:00");
    }

    @Test
    @DisplayName("Duration.ZERO")
    void durationZero() {
        assertThat(Duration.ZERO.getSeconds()).isZero();
        assertThat(Duration.ZERO.isZero()).isTrue();
        assertThat(Duration.ZERO.isNegative()).isFalse();
    }

    @Test
    @DisplayName("闰年判断（2024 闰 / 2023 平）")
    void leapYear() {
        assertThat(Year.of(2024).isLeap()).isTrue();
        assertThat(Year.of(2023).isLeap()).isFalse();
        assertThat(Year.of(2000).isLeap()).isTrue();
        assertThat(Year.of(1900).isLeap()).isFalse();
    }

    @Test
    @DisplayName("MonthDay 常用")
    void monthDay() {
        MonthDay md = MonthDay.of(12, 25);
        assertThat(md.getMonth()).isEqualTo(Month.DECEMBER);
        assertThat(md.getDayOfMonth()).isEqualTo(25);
    }

    @Test
    @DisplayName("Period 计算")
    void periodCalc() {
        Period p = Period.between(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        assertThat(p.getMonths()).isEqualTo(11);
        assertThat(p.getDays()).isEqualTo(30);
    }
}
