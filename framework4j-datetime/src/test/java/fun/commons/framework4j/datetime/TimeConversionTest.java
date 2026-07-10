package fun.commons.framework4j.datetime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.*;

@DisplayName("时间转换补充测试")
class TimeConversionTest {

    @Test @DisplayName("LocalDate → OffsetDateTime（一天的开始）")
    void localDateToStartOfDay() {
        LocalDate date = LocalDate.of(2024, 6, 15);
        OffsetDateTime start = date.atStartOfDay(ZoneOffset.ofHours(8)).toOffsetDateTime();
        assertThat(start.getHour()).isZero();
        assertThat(start.getMinute()).isZero();
    }

    @Test @DisplayName("LocalDate → 一天的结束（23:59:59）")
    void localDateToEndOfDay() {
        LocalDate date = LocalDate.of(2024, 6, 15);
        OffsetDateTime end = date.atTime(23, 59, 59).atOffset(ZoneOffset.ofHours(8));
        assertThat(end.getHour()).isEqualTo(23);
        assertThat(end.getSecond()).isEqualTo(59);
    }

    @Test @DisplayName("本周一 → 本周日")
    void weekRange() {
        LocalDate wed = LocalDate.of(2024, 6, 19); // 周三
        LocalDate monday = wed.minusDays(wed.getDayOfWeek().getValue() - 1);
        LocalDate sunday = monday.plusDays(6);
        assertThat(monday.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(sunday.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
    }

    @Test @DisplayName("月份第一天")
    void firstDayOfMonth() {
        LocalDate date = LocalDate.of(2024, 6, 15);
        LocalDate first = date.withDayOfMonth(1);
        assertThat(first.getDayOfMonth()).isEqualTo(1);
    }

    @Test @DisplayName("月份最后一天")
    void lastDayOfMonth() {
        LocalDate feb = LocalDate.of(2024, 2, 15);
        LocalDate last = feb.withDayOfMonth(feb.lengthOfMonth());
        assertThat(last.getDayOfMonth()).isEqualTo(29); // 2024 闰年
    }

    @Test @DisplayName("两个时间差（天）")
    void daysBetween() {
        LocalDate d1 = LocalDate.of(2024, 1, 1);
        LocalDate d2 = LocalDate.of(2024, 12, 31);
        long days = ChronoUnit.DAYS.between(d1, d2);
        assertThat(days).isEqualTo(365); // 2024 闰年
    }

    @Test @DisplayName("两个时间差（小时）")
    void hoursBetween() {
        OffsetDateTime t1 = OffsetDateTime.of(2024, 6, 15, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime t2 = OffsetDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC);
        long hours = ChronoUnit.HOURS.between(t1, t2);
        assertThat(hours).isEqualTo(12);
    }

    @Test @DisplayName("Unix 时间戳 → LocalDate")
    void timestampToLocalDate() {
        long ts = 1718496000000L; // 2024-06-16 UTC
        Instant instant = Instant.ofEpochMilli(ts);
        LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
        assertThat(date.getYear()).isEqualTo(2024);
        assertThat(date.getMonthValue()).isEqualTo(6);
    }

    @Test @DisplayName("LocalDate → Unix 时间戳")
    void localDateToTimestamp() {
        LocalDate date = LocalDate.of(2024, 1, 1);
        long ts = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        assertThat(ts).isPositive();
    }

    @Test @DisplayName("格式化 yyyy-MM-dd")
    void formatDate() {
        LocalDate date = LocalDate.of(2024, 12, 10);
        String formatted = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        assertThat(formatted).isEqualTo("2024-12-10");
    }

    @Test @DisplayName("格式化 yyyy/MM/dd HH:mm:ss")
    void formatCustomPattern() {
        LocalDateTime dt = LocalDateTime.of(2024, 12, 10, 14, 30, 45);
        String formatted = dt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
        assertThat(formatted).isEqualTo("2024/12/10 14:30:45");
    }

    @Test @DisplayName("解析 yyyy-MM-dd HH:mm:ss")
    void parseCustomPattern() {
        String input = "2024-06-15 08:30:00";
        LocalDateTime dt = LocalDateTime.parse(input, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        assertThat(dt.getYear()).isEqualTo(2024);
        assertThat(dt.getMonthValue()).isEqualTo(6);
        assertThat(dt.getHour()).isEqualTo(8);
    }

    @Test @DisplayName("不同时区同一时刻")
    void sameInstantDifferentZone() {
        Instant now = Instant.now();
        OffsetDateTime utc = now.atOffset(ZoneOffset.UTC);
        OffsetDateTime cst = now.atOffset(ZoneOffset.ofHours(8));
        assertThat(utc.toInstant()).isEqualTo(cst.toInstant());
        assertThat(cst.getHour()).isEqualTo(utc.getHour() + 8);
    }

    @Test @DisplayName("OffsetDateTime isAfter / isBefore 边界")
    void offsetDateTimeBoundary() {
        OffsetDateTime base = OffsetDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime same = OffsetDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime before = base.minusNanos(1);
        OffsetDateTime after = base.plusNanos(1);
        assertThat(base.isEqual(same)).isTrue();
        assertThat(base.isAfter(before)).isTrue();
        assertThat(base.isBefore(after)).isTrue();
        assertThat(base.isAfter(same)).isFalse();
        assertThat(base.isBefore(same)).isFalse();
    }

    @Test @DisplayName("ZonedDateTime → OffsetDateTime")
    void zonedToOffset() {
        ZonedDateTime zdt = ZonedDateTime.of(2024, 6, 15, 14, 0, 0, 0, ZoneId.of("Asia/Shanghai"));
        OffsetDateTime odt = zdt.toOffsetDateTime();
        assertThat(odt.getOffset().getTotalSeconds()).isEqualTo(28800);
    }

    @Test @DisplayName("Duration of minutes/seconds/hours")
    void durationFactories() {
        assertThat(Duration.ofMinutes(30).getSeconds()).isEqualTo(1800);
        assertThat(Duration.ofHours(2).getSeconds()).isEqualTo(7200);
        assertThat(Duration.ofSeconds(90).toMinutes()).isEqualTo(1);
    }

    @Test @DisplayName("Duration 加减")
    void durationArithmetic() {
        Duration d1 = Duration.ofHours(2);
        Duration d2 = Duration.ofMinutes(30);
        assertThat(d1.plus(d2).toMinutes()).isEqualTo(150);
        assertThat(d1.minus(d2).toMinutes()).isEqualTo(90);
    }

    @Test @DisplayName("DayOfWeek 枚举")
    void dayOfWeek() {
        assertThat(DayOfWeek.MONDAY.getValue()).isEqualTo(1);
        assertThat(DayOfWeek.SUNDAY.getValue()).isEqualTo(7);
        assertThat(DayOfWeek.FRIDAY.plus(3)).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test @DisplayName("Month 枚举")
    void monthEnum() {
        assertThat(Month.JANUARY.getValue()).isEqualTo(1);
        assertThat(Month.DECEMBER.getValue()).isEqualTo(12);
        assertThat(Month.FEBRUARY.length(false)).isEqualTo(28);
        assertThat(Month.FEBRUARY.length(true)).isEqualTo(29);
    }

    @Test @DisplayName("YearMonth 天数")
    void yearMonthDays() {
        assertThat(YearMonth.of(2024, 2).lengthOfMonth()).isEqualTo(29);
        assertThat(YearMonth.of(2023, 2).lengthOfMonth()).isEqualTo(28);
        assertThat(YearMonth.of(2024, 1).lengthOfMonth()).isEqualTo(31);
        assertThat(YearMonth.of(2024, 4).lengthOfMonth()).isEqualTo(30);
    }

    @Test @DisplayName("时间戳转秒")
    void timestampToSeconds() {
        long millis = System.currentTimeMillis();
        long seconds = millis / 1000;
        assertThat(seconds).isPositive();
        assertThat(Instant.ofEpochSecond(seconds).toEpochMilli() / 1000).isEqualTo(seconds);
    }

    @Test @DisplayName("OffsetDateTime → epochMilli → OffsetDateTime 往返")
    void epochMilliRoundTrip() {
        OffsetDateTime original = OffsetDateTime.of(2024, 6, 15, 14, 30, 45, 123456789, ZoneOffset.UTC);
        long millis = original.toInstant().toEpochMilli();
        OffsetDateTime restored = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC);
        assertThat(restored.getYear()).isEqualTo(2024);
        assertThat(restored.getHour()).isEqualTo(14);
    }

    @Test @DisplayName("LocalTime 范围")
    void localTimeRange() {
        LocalTime midnight = LocalTime.MIDNIGHT;
        LocalTime noon = LocalTime.NOON;
        LocalTime max = LocalTime.MAX;
        assertThat(midnight.getHour()).isZero();
        assertThat(noon.getHour()).isEqualTo(12);
        assertThat(max.getHour()).isEqualTo(23);
        assertThat(max.getSecond()).isEqualTo(59);
        assertThat(max.getNano()).isEqualTo(999999999);
    }

    @Test @DisplayName("TemporalAdjusters 下个月第一天")
    void temporalAdjusters() {
        LocalDate date = LocalDate.of(2024, 6, 15);
        LocalDate firstNextMonth = date.with(java.time.temporal.TemporalAdjusters.firstDayOfNextMonth());
        assertThat(firstNextMonth.getMonth()).isEqualTo(Month.JULY);
        assertThat(firstNextMonth.getDayOfMonth()).isEqualTo(1);
    }
}
