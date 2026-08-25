package fun.commons.framework4j.tracelog.switcher;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 动态日志提权开关规则。
 * <p>
 * 一个规则对应一条 Redis 中的 {@code log_switch:{type}:{value}} 记录。
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §3.3</a>
 */
@Data
@NoArgsConstructor
public class SwitchRule {

    /** 维度类型: user / trace / url / order */
    private String type;

    /** 维度值: userId / traceId / url pattern / orderId */
    private String value;

    /** 提权目标级别: DEBUG / TRACE */
    private String level;

    /** Redis 中创建时间（用于本地缓存 TTL 与 Redis 同步） */
    private Instant createdAt;

    public SwitchRule(String type, String value, String level) {
        this.type = type;
        this.value = value;
        this.level = level;
        this.createdAt = Instant.now();
    }

    /**
     * Redis Key 格式: {@code log_switch:{type}:{value}}
     */
    public String redisKey() {
        return "log_switch:id:" + type + ":" + value;
    }

    /**
     * Pub/Sub payload（JSON 序列化）。
     */
    public String pubSubPayload() {
        // 简单手写避免引入额外 Jackson 依赖
        return String.format("{\"type\":\"%s\",\"value\":\"%s\",\"level\":\"%s\"}",
                escape(type), escape(value), escape(level));
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 反序列化 Pub/Sub payload。
     */
    public static SwitchRule fromPayload(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            // 极简解析（避免业务方引入 Jackson 时的循环依赖）
            String type = extract(json, "type");
            String value = extract(json, "value");
            String level = extract(json, "level");
            if (type == null || value == null || level == null) return null;
            return new SwitchRule(type, value, level);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 JSON 中按 key 提取字符串值，正确处理转义引号。
     */
    private static String extract(String json, String key) {
        String pattern = "\"" + key + "\":";
        int i = json.indexOf(pattern);
        if (i < 0) return null;
        int start = i + pattern.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length() || json.charAt(start) != '"') return null;
        start++; // 跳过开引号
        // 逐字符扫描，正确处理转义
        StringBuilder sb = new StringBuilder();
        while (start < json.length()) {
            char c = json.charAt(start);
            if (c == '\\' && start + 1 < json.length()) {
                char next = json.charAt(start + 1);
                if (next == '"') { sb.append('"'); start += 2; continue; }
                if (next == '\\') { sb.append('\\'); start += 2; continue; }
                if (next == 'n') { sb.append('\n'); start += 2; continue; }
                if (next == 'r') { sb.append('\r'); start += 2; continue; }
                if (next == 't') { sb.append('\t'); start += 2; continue; }
            }
            if (c == '"') break;
            sb.append(c);
            start++;
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return type + ":" + value + "->" + level;
    }
}