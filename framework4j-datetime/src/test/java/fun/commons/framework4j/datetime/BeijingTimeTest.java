package fun.commons.framework4j.datetime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 北京时间 (CST) 测试
 * <p>v2.1 P1 修复：
 * <ul>
 *   <li>固定 Asia/Shanghai 时区，避免 CI 在 UTC 跑假阳性</li>
 *   <li>修正正则 {@code .} → {@code \.}（原 . 是任意字符）</li>
 *   <li>删除 System.out.println 调试输出</li>
 *   <li>加 ZonedDateTime 时区断言</li>
 * </ul>
 */
@DisplayName("北京时间 (CST) 测试")
public class BeijingTimeTest {

    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter BEIJING_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    @DisplayName("应该正确获取当前北京时间（Asia/Shanghai 时区）")
    void testCurrentBeijingTime() {
        ZonedDateTime beijingNow = ZonedDateTime.now(BEIJING_ZONE);
        assertNotNull(beijingNow);

        // 验证时区
        assertEquals(BEIJING_ZONE, beijingNow.getZone());

        // 验证时间字符串格式（ZonedDateTime.toString 含时区后缀 +08:00[Asia/Shanghai]）
        String timeStr = beijingNow.toString();
        assertTrue(timeStr.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\+08:00.*"),
                "时间字符串应匹配 ISO + Asia/Shanghai 格式，实际: " + timeStr);

        // 北京时间 UTC+8
        assertEquals(8, beijingNow.getOffset().getTotalSeconds() / 3600);
    }

    @Test
    @DisplayName("应该正确格式化北京时间为指定格式")
    void testFormatBeijingTime() {
        LocalDateTime fixedTime = LocalDateTime.of(2024, 12, 10, 14, 30, 45);
        String formattedTime = fixedTime.format(BEIJING_FORMATTER);
        assertEquals("2024-12-10 14:30:45", formattedTime);
    }

    @Test
    @DisplayName("应该正确解析北京时间字符串")
    void testParseBeijingTime() {
        String timeStr = "2024-12-10 14:30:45";
        LocalDateTime parsedTime = LocalDateTime.parse(timeStr, BEIJING_FORMATTER);

        assertEquals(2024, parsedTime.getYear());
        assertEquals(12, parsedTime.getMonthValue());
        assertEquals(10, parsedTime.getDayOfMonth());
        assertEquals(14, parsedTime.getHour());
        assertEquals(30, parsedTime.getMinute());
        assertEquals(45, parsedTime.getSecond());
    }

    @Test
    @DisplayName("应该正确处理今天的时间范围")
    void testTodayTimeRange() {
        LocalDate today = LocalDate.now(BEIJING_ZONE);
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);

        assertTrue(startOfDay.isBefore(endOfDay));
        assertEquals(0, startOfDay.getHour());
        assertEquals(0, startOfDay.getMinute());
        assertEquals(0, startOfDay.getSecond());

        assertEquals(23, endOfDay.getHour());
        assertEquals(59, endOfDay.getMinute());
        assertEquals(59, endOfDay.getSecond());
    }

    @Test
    @DisplayName("应该正确处理本周的时间范围")
    void testWeekTimeRange() {
        LocalDate today = LocalDate.now(BEIJING_ZONE);
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1);
        LocalDate sunday = monday.plusDays(6);

        LocalDateTime weekStart = monday.atStartOfDay();
        LocalDateTime weekEnd = sunday.atTime(23, 59, 59);

        // 验证周一和周日
        assertEquals(1, weekStart.toLocalDate().getDayOfWeek().getValue()); // Monday in Java is 1
        assertEquals(7, weekEnd.toLocalDate().getDayOfWeek().getValue()); // Sunday in Java is 7
    }

    @Test
    @DisplayName("应该正确处理月份第一天")
    void testFirstDayOfMonth() {
        LocalDate today = LocalDate.now(BEIJING_ZONE);
        LocalDateTime firstDayOfMonth = today.withDayOfMonth(1).atStartOfDay();

        assertEquals(1, firstDayOfMonth.getDayOfMonth());
        assertEquals(0, firstDayOfMonth.getHour());
        assertEquals(0, firstDayOfMonth.getMinute());
        assertEquals(0, firstDayOfMonth.getSecond());
    }

    @Test
    @DisplayName("北京时间与 UTC 转换正确（UTC+8）")
    void testBeijingUtcConversion() {
        // UTC 2024-12-10T06:00:00 = 北京 2024-12-10T14:00:00
        ZonedDateTime utc = ZonedDateTime.of(2024, 12, 10, 6, 0, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime beijing = utc.withZoneSameInstant(BEIJING_ZONE);

        assertEquals(14, beijing.getHour());
        assertEquals(2024, beijing.getYear());
        assertEquals(12, beijing.getMonthValue());
        assertEquals(10, beijing.getDayOfMonth());
    }
}
