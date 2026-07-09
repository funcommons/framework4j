package fun.commons.framework4j.datetime;

import org.springframework.core.convert.converter.Converter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 全能时间转换器（手写多格式识别）
 * 自动识别：时间戳（毫秒/秒）、yyyy-MM-dd HH:mm:ss（GMT+8）、ISO-8601
 *
 * @since 2.0.0（从 fastjson2 迁移到手写解析，避免 JSON 库耦合）
 */
public class StringToOffsetDateTimeConverter implements Converter<String, OffsetDateTime> {

    private static final DateTimeFormatter LOCAL_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.ofHours(8));
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    // v2.1: 正则预编译（原 s.matches 每次重新编译）
    private static final Pattern DIGITS_PATTERN = Pattern.compile("\\d+");

    @Override
    public OffsetDateTime convert(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String s = source.trim();

        // 1. 纯数字 → 时间戳（毫秒或秒）
        if (DIGITS_PATTERN.matcher(s).matches()) {
            long ts = Long.parseLong(s);
            // 13 位以下视为秒
            if (s.length() <= 10) {
                ts = ts * 1000;
            }
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault());
        }

        // 2. ISO-8601
        try {
            return OffsetDateTime.parse(s, ISO_FORMATTER);
        } catch (DateTimeParseException ignore) {
        }

        // 3. 本地格式 yyyy-MM-dd HH:mm:ss（GMT+8）
        try {
            return OffsetDateTime.parse(s, LOCAL_FORMATTER);
        } catch (DateTimeParseException ignore) {
        }

        throw new IllegalArgumentException("无法解析时间字符串: " + source);
    }
}
