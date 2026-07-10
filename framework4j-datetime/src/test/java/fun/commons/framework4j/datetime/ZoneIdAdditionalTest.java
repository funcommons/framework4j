package fun.commons.framework4j.datetime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ZoneId 补充测试")
class ZoneIdAdditionalTest {
    @Test @DisplayName("常用时区")
    void commonZones() {
        assertThat(ZoneId.of("Asia/Shanghai")).isNotNull();
        assertThat(ZoneId.of("UTC")).isNotNull();
        assertThat(ZoneId.of("America/New_York")).isNotNull();
    }
    @Test @DisplayName("UTC 等价 GMT+0")
    void utcEqualsGmt() {
        assertThat(ZoneId.of("UTC").getRules().getOffset(Instant.EPOCH))
                .isEqualTo(ZoneOffset.ofHours(0));
    }
    @Test @DisplayName("跨月减1天")
    void crossMonth() {
        OffsetDateTime t = OffsetDateTime.of(2024, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        assertThat(t.minusDays(1).getMonthValue()).isEqualTo(6);
    }
    @Test @DisplayName("时间戳 → OffsetDateTime → 时间戳 往返")
    void roundTrip() {
        long ts = 1718496000000L;
        OffsetDateTime odt = Instant.ofEpochMilli(ts).atOffset(ZoneOffset.UTC);
        assertThat(odt.toInstant().toEpochMilli()).isEqualTo(ts);
    }
    @Test @DisplayName("Month 长度")
    void monthLength() {
        assertThat(Month.APRIL.length(true)).isEqualTo(30);
        assertThat(Month.APRIL.length(false)).isEqualTo(30);
        assertThat(Month.JANUARY.length(true)).isEqualTo(31);
    }
    @Test @DisplayName("DayOfWeek +1 循环")
    void dayOfWeekPlus() {
        assertThat(DayOfWeek.SUNDAY.plus(1)).isEqualTo(DayOfWeek.MONDAY);
        assertThat(DayOfWeek.MONDAY.plus(6)).isEqualTo(DayOfWeek.SUNDAY);
    }
    @Test @DisplayName("Year isLeap")
    void yearIsLeap() {
        assertThat(Year.of(2000).isLeap()).isTrue();
        assertThat(Year.of(2100).isLeap()).isFalse();
    }
}
