package fun.commons.framework4j.tenant.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 极简 JSON 工具(模块内用,不外暴)。注册码元数据等内部结构的序列化/反序列化。
 */
public final class Jsons {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Jsons() {
    }

    public static String write(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    public static Map<String, Object> readMap(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
