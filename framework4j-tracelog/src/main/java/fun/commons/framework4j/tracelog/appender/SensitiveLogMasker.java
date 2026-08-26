package fun.commons.framework4j.tracelog.appender;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 敏感字段脱敏器（按 key 匹配，采集进 Redis 前执行）。
 * <p>
 * 日志场景的脱敏与 {@code framework4j-sensitive} 的按值格式脱敏（手机号/身份证）
 * 互补：日志行是已序列化的字符串，敏感信息以 <b>key→值</b> 形态出现，因此本类按
 * <b>字段名</b> 匹配并将值整体替换为 {@code ******}，零误伤、零跨模块依赖。
 *
 * <p>覆盖两种形态：
 * <ol>
 *   <li><b>JSON 字段</b>：日志体内 {@code "password":"abc"} —— 含 message 字段里
 *       转义嵌套的 {@code \"password\":\"abc\"} 形式（LogstashEncoder 转义后）</li>
 *   <li><b>message 内 kv</b>：自然语言日志 {@code password=abc} / {@code token: abc}</li>
 * </ol>
 *
 * <p>在 Worker 线程 {@code serialize()} 之后执行，业务线程零开销。
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §六 敏感日志泄漏</a>
 */
public final class SensitiveLogMasker {

    public static final String MASK = "******";

    /** 引号 token：可选反斜杠 + 引号 —— 同时匹配普通 JSON 与 message 内转义形式 */
    private static final String Q = "\\\\?\"";

    private final boolean enabled;
    /** JSON 字段形态: "key":"value"（兼容 message 内转义的 \"key\":\"value\"） */
    private final Pattern jsonFieldPattern;
    /** message 内 kv 形态: key=value / key: value */
    private final Pattern kvPattern;

    public SensitiveLogMasker(boolean enabled, List<String> keys) {
        this.enabled = enabled;
        if (!enabled || keys == null || keys.isEmpty()) {
            this.jsonFieldPattern = null;
            this.kvPattern = null;
            return;
        }
        String alt = buildAlternation(keys);
        // 组: (q1)(key)(q2+colon+q3)(值)(q4) —— 引号两侧都兼容转义反斜杠，值内转义安全
        this.jsonFieldPattern = Pattern.compile(
                "(" + Q + ")(" + alt + ")(" + Q + "\\s*:\\s*" + Q + ")((?:\\\\.|[^\"\\\\])*?)(" + Q + ")",
                Pattern.CASE_INSENSITIVE);
        this.kvPattern = Pattern.compile(
                "\\b(" + alt + ")(\\s*[=:]\\s*)([^\\s,;&\"'\\\\]+)",
                Pattern.CASE_INSENSITIVE);
    }

    /**
     * 对整行日志执行脱敏。未启用或无 key 时原样返回。
     */
    public String mask(String line) {
        if (!enabled || jsonFieldPattern == null || line == null || line.isEmpty()) return line;
        String masked = jsonFieldPattern.matcher(line).replaceAll("$1$2$3" + MASK + "$5");
        return kvPattern.matcher(masked).replaceAll("$1$2" + MASK);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** key 转小写 + Pattern.quote（防注入），拼接为交替分支 */
    private static String buildAlternation(List<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            if (k == null || k.isBlank()) continue;
            if (sb.length() > 0) sb.append('|');
            sb.append(Pattern.quote(k.trim().toLowerCase(Locale.ROOT)));
        }
        return sb.length() > 0 ? sb.toString() : "a^"; // 空集合 → 永不匹配
    }
}