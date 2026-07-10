package fun.commons.framework4j.datetime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OffsetDateTime 转换器补充测试")
class OffsetDateTimeConverterTest {

    @ParameterizedTest
    @CsvSource({
        "2024-01-01T00:00:00+08:00, 2023-12-31T16:00:00Z",
        "2024-06-15T12:00:00+08:00, 2024-06-15T04:00:00Z",
        "2024-12-31T23:59:59+08:00, 2024-12-31T15:59:59Z",
    })
    @DisplayName("CST → UTC 时区转换正确")
    void cstToUtc(String cst, String utc) {
        OffsetDateTime cstTime = OffsetDateTime.parse(cst);
        OffsetDateTime utcTime = cstTime.withOffsetSameInstant(ZoneOffset.UTC);
        assertThat(utcTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).isEqualTo(utc);
    }

    @Test @DisplayName("四季时间范围")
    void fourSeasons() {
        LocalDate spring = LocalDate.of(2024, 3, 20);
        LocalDate summer = LocalDate.of(2024, 6, 21);
        LocalDate autumn = LocalDate.of(2024, 9, 23);
        LocalDate winter = LocalDate.of(2024, 12, 22);
        assertThat(spring.isBefore(summer)).isTrue();
        assertThat(summer.isBefore(autumn)).isTrue();
        assertThat(autumn.isBefore(winter)).isTrue();
    }

    @Test @DisplayName("时区偏移 ±14 小时范围")
    void timezoneRange() {
        assertThat(ZoneOffset.ofHours(14).getTotalSeconds()).isEqualTo(50400);
        assertThat(ZoneOffset.ofHours(-12).getTotalSeconds()).isEqualTo(-43200);
        assertThat(ZoneOffset.of("+05:30").getTotalSeconds()).isEqualTo(19800);
        assertThat(ZoneOffset.of("-09:30").getTotalSeconds()).isEqualTo(-34200);
    }

    @Test @DisplayName("同一天不同时区")
    void sameDayDifferentZone() {
        LocalDate date = LocalDate.of(2024, 6, 15);
        LocalTime time = LocalTime.of(12, 0, 0);
        OffsetDateTime utc = OffsetDateTime.of(date, time, ZoneOffset.UTC);
        OffsetDateTime cst = OffsetDateTime.of(date, time, ZoneOffset.ofHours(8));
        assertThat(utc.toInstant()).isNotEqualTo(cst.toInstant());
        assertThat(ChronoUnit.HOURS.between(utc, cst)).isEqualTo(-8);
    }

    @Test @DisplayName("新年跨秒")
    void newYearTransition() {
        OffsetDateTime before = OffsetDateTime.of(2023, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);
        OffsetDateTime after = before.plusSeconds(1);
        assertThat(after.getYear()).isEqualTo(2024);
        assertThat(after.getDayOfYear()).isEqualTo(1);
    }

    @Test @DisplayName("2月29日闰年特殊")
    void leapDay() {
        LocalDate leap = LocalDate.of(2024, 2, 29);
        assertThat(leap.plusYears(1)).isEqualTo(LocalDate.of(2025, 2, 28));
        assertThat(leap.plusYears(4)).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test @DisplayName("Timestamp 0 → 1970-01-01")
    void epochZero() {
        Instant epoch = Instant.ofEpochMilli(0);
        OffsetDateTime odt = epoch.atOffset(ZoneOffset.UTC);
        assertThat(odt.getYear()).isEqualTo(1970);
        assertThat(odt.getMonthValue()).isEqualTo(1);
        assertThat(odt.getDayOfMonth()).isEqualTo(1);
    }

    @Test @DisplayName("负时间戳 → 1969")
    void negativeTimestamp() {
        Instant past = Instant.ofEpochMilli(-1);
        OffsetDateTime odt = past.atOffset(ZoneOffset.UTC);
        assertThat(odt.getYear()).isEqualTo(1969);
    }
}
