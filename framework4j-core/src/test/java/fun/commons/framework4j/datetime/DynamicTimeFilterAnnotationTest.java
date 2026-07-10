package fun.commons.framework4j.datetime;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DynamicTimeFilter 注解驱动功能测试
 */
public class DynamicTimeFilterAnnotationTest {

    @BeforeEach
    void setUp() {
        TimeContext.clear();
    }

    @LocalTimeFormat
    public static class LocalTimeDto {
        private OffsetDateTime time;
        private List<OffsetDateTime> timeList;

        public LocalTimeDto(OffsetDateTime time) {
            this.time = time;
            this.timeList = Arrays.asList(time, time.plusHours(1));
        }

        public OffsetDateTime getTime() { return time; }
        public void setTime(OffsetDateTime time) { this.time = time; }
        public List<OffsetDateTime> getTimeList() { return timeList; }
        public void setTimeList(List<OffsetDateTime> timeList) { this.timeList = timeList; }
    }

    public static class IsoTimeDto {
        private OffsetDateTime time;

        public IsoTimeDto(OffsetDateTime time) {
            this.time = time;
        }

        public OffsetDateTime getTime() { return time; }
        public void setTime(OffsetDateTime time) { this.time = time; }
    }

    @Test
    void testLocalTimeAnnotationWorks() {
        DynamicTimeFilter filter = new DynamicTimeFilter();
        OffsetDateTime testTime = OffsetDateTime.now();
        LocalTimeDto dto = new LocalTimeDto(testTime);

        // 测试单个时间字段
        Object result = filter.apply(dto, "time", testTime);
        assertNotNull(result);
        assertTrue(result instanceof String);
        String timeStr = (String) result;
        assertTrue(timeStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    void testNoAnnotationKeepsIsoFormat() {
        DynamicTimeFilter filter = new DynamicTimeFilter();
        OffsetDateTime testTime = OffsetDateTime.now();
        IsoTimeDto dto = new IsoTimeDto(testTime);

        // 测试没有注解的类保持原格式
        Object result = filter.apply(dto, "time", testTime);
        assertNotNull(result);
        assertSame(testTime, result);
    }

    @Test
    void testTimeContextCompatibility() {
        DynamicTimeFilter filter = new DynamicTimeFilter();
        OffsetDateTime testTime = OffsetDateTime.now();
        IsoTimeDto dto = new IsoTimeDto(testTime);

        // 设置TimeContext
        TimeContext.setUseLocal(true);
        try {
            Object result = filter.apply(dto, "time", testTime);
            assertNotNull(result);
            assertTrue(result instanceof String);
            String timeStr = (String) result;
            assertTrue(timeStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        } finally {
            TimeContext.clear();
        }
    }

    @Test
    void testNullValueHandling() {
        DynamicTimeFilter filter = new DynamicTimeFilter();
        LocalTimeDto dto = new LocalTimeDto(OffsetDateTime.now());

        Object result = filter.apply(dto, "time", null);
        assertNull(result);
    }

    @Test
    void testFastJson2Integration() {
        OffsetDateTime testTime = OffsetDateTime.now();
        LocalTimeDto dto = new LocalTimeDto(testTime);

        String json = JSON.toJSONString(dto, new DynamicTimeFilter());

        assertNotNull(json);
        // 检查包含格式化的时间字符串
        assertTrue(json.contains("time"));
        // 简单验证格式化结果（不包含ISO格式的特殊字符）
        assertFalse(json.contains("+08:00") || json.contains("Z"));
    }
}