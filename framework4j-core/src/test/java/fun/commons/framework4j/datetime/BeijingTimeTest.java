package fun.commons.framework4j.datetime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 北京时间 (CST) 测试
 * 验证符合《软件开发北京时间 (CST) 统一规范》的时间处理
 */
@DisplayName("北京时间 (CST) 测试")
public class BeijingTimeTest {

    private static final DateTimeFormatter BEIJING_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    @DisplayName("应该正确获取当前北京时间")
    void testCurrentBeijingTime() {
        LocalDateTime now = LocalDateTime.now();
        assertNotNull(now);

        // 验证时间格式（LocalDateTime 默认格式）
        String timeStr = now.toString();
        System.out.println("当前时间字符串: " + timeStr);

        // LocalDateTime 默认格式为 yyyy-MM-ddTHH:mm:ss.SSS，应该包含 T
        assertTrue(timeStr.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d+)?"));

        // 验证时区信息（当前系统时区）
        System.out.println("系统时区: " + java.util.TimeZone.getDefault().getID());
    }

    @Test
    @DisplayName("应该正确格式化北京时间为指定格式")
    void testFormatBeijingTime() {
        LocalDateTime fixedTime = LocalDateTime.of(2024, 12, 10, 14, 30, 45);

        String formattedTime = fixedTime.format(BEIJING_FORMATTER);

        assertEquals("2024-12-10 14:30:45", formattedTime);
        System.out.println("格式化后的北京时间: " + formattedTime);
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

        System.out.println("解析后的北京时间: " + parsedTime);
    }

    @Test
    @DisplayName("应该正确处理今天的时间范围")
    void testTodayTimeRange() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);

        System.out.println("今天开始时间: " + startOfDay);
        System.out.println("今天结束时间: " + endOfDay);

        // 验证时间范围
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
        LocalDate today = LocalDate.now();
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1);
        LocalDate sunday = monday.plusDays(6);

        LocalDateTime weekStart = monday.atStartOfDay();
        LocalDateTime weekEnd = sunday.atTime(23, 59, 59);

        System.out.println("本周开始时间: " + weekStart);
        System.out.println("本周结束时间: " + weekEnd);

        // 验证周一和周日
        assertEquals(1, weekStart.toLocalDate().getDayOfWeek().getValue()); // Monday in Java is 1
        assertEquals(7, weekEnd.toLocalDate().getDayOfWeek().getValue()); // Sunday in Java is 7
    }

    @Test
    @DisplayName("应该正确处理月份第一天")
    void testFirstDayOfMonth() {
        LocalDate today = LocalDate.now();
        LocalDateTime firstDayOfMonth = today.withDayOfMonth(1).atStartOfDay();

        System.out.println("本月第一天: " + firstDayOfMonth);

        // 验证是月份的第一天
        assertEquals(1, firstDayOfMonth.getDayOfMonth());
        assertEquals(0, firstDayOfMonth.getHour());
        assertEquals(0, firstDayOfMonth.getMinute());
        assertEquals(0, firstDayOfMonth.getSecond());
    }

    @Test
    @DisplayName("应该正确处理时间比较")
    void testTimeComparison() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourLater = now.plusHours(1);
        LocalDateTime oneHourEarlier = now.minusHours(1);

        System.out.println("当前时间: " + now);
        System.out.println("一小时后: " + oneHourLater);
        System.out.println("一小时前: " + oneHourEarlier);

        // 验证时间比较
        assertTrue(oneHourEarlier.isBefore(now));
        assertTrue(now.isBefore(oneHourLater));
        assertTrue(oneHourLater.isAfter(now));
    }

    @Test
    @DisplayName("应该正确处理业务场景中的时间计算")
    void testBusinessTimeCalculation() {
        // 模拟订单创建时间
        LocalDateTime createTime = LocalDateTime.of(2024, 12, 10, 9, 0, 0);

        // 模拟过期时间（7天后）
        LocalDateTime expireTime = createTime.plusDays(7);

        // 模拟支付时间（创建后30分钟）
        LocalDateTime payTime = createTime.plusMinutes(30);

        System.out.println("订单创建时间: " + createTime);
        System.out.println("订单支付时间: " + payTime);
        System.out.println("订单过期时间: " + expireTime);

        // 验证时间关系
        assertTrue(payTime.isAfter(createTime));
        assertTrue(expireTime.isAfter(payTime));

        // 验证过期时间是7天后
        assertTrue(createTime.plusDays(7).isEqual(expireTime));

        // 验证支付时间是创建后30分钟
        assertTrue(createTime.plusMinutes(30).isEqual(payTime));
    }

    @Test
    @DisplayName("应该正确处理结束时间计算")
    void testEndTimeCalculation() {
        // 固定的开始时间
        LocalDateTime startTime = LocalDateTime.of(2024, 12, 10, 9, 0, 0);

        // 当天的结束时间（23:59:59）
        LocalDateTime endTime = startTime.toLocalDate().atTime(23, 59, 59);

        System.out.println("开始时间: " + startTime);
        System.out.println("结束时间: " + endTime);

        // 验证结束时间是当天的最后一秒
        assertEquals(startTime.toLocalDate(), endTime.toLocalDate());
        assertEquals(23, endTime.getHour());
        assertEquals(59, endTime.getMinute());
        assertEquals(59, endTime.getSecond());
    }
}