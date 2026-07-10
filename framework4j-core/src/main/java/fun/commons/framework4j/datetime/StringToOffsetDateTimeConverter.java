package fun.commons.framework4j.datetime;

import com.alibaba.fastjson2.JSON;
import org.springframework.core.convert.converter.Converter;

import java.time.OffsetDateTime;

/**
     * 全能时间转换器 (基于 FastJSON2)
     * 自动识别：时间戳、yyyy-MM-dd HH:mm:ss (GMT+8)、ISO-8601
     */
    public class StringToOffsetDateTimeConverter implements Converter<String, OffsetDateTime> {

        @Override
        public OffsetDateTime convert(String source) {
            if (source == null || source.isBlank()) {
                return null;
            }
            // 利用 FastJSON2 强大的智能解析能力简化代码 解析 (FastJSON2 会自动处理数字时间戳、ISO格式及普通时间格式)
            return JSON.parseObject(JSON.toJSONString(source), OffsetDateTime.class);
        }
    }