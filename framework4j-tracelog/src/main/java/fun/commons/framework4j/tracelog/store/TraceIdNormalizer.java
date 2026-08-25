package fun.commons.framework4j.tracelog.store;

import java.util.Locale;

/**
 * TraceId 标准化器：统一为 32 位小写 hex（OTel W3C 格式）。
 * <p>
 * 不同 Tracing 实现的 traceId 长度差异巨大：
 * <ul>
 *   <li>W3C Trace Context（OTel）：32 hex（128bit）</li>
 *   <li>Zipkin B3：16 或 32 hex</li>
 *   <li>自定义：不定</li>
 * </ul>
 *
 * <p>统一规范：
 * <ul>
 *   <li>恰好 32 位 hex → 直接转小写</li>
 *   <li>不足 32 位 → 左补 0</li>
 *   <li>超过 32 位 → 截断右段</li>
 *   <li>非 hex 输入 → 返回 null（调用方决定降级）</li>
 * </ul>
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §3.1.5</a>
 */
public final class TraceIdNormalizer {

    private static final int STANDARD_LENGTH = 32;

    private TraceIdNormalizer() {}

    /**
     * 标准化为 32 位小写 hex。
     *
     * @param raw 原始 traceId（可为 null）
     * @return 标准化后的 traceId；非 hex 或 null 返回 null
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;

        // 去除可能的连字符（B3 格式 16hex-8hex-...）
        String hex = trimmed.replace("-", "").toLowerCase(Locale.ROOT);

        // 仅接受纯 hex 字符
        if (!isHexString(hex)) return null;

        if (hex.length() == STANDARD_LENGTH) {
            return hex;
        }
        if (hex.length() < STANDARD_LENGTH) {
            // 左补 0
            StringBuilder sb = new StringBuilder(STANDARD_LENGTH);
            for (int i = 0; i < STANDARD_LENGTH - hex.length(); i++) sb.append('0');
            sb.append(hex);
            return sb.toString();
        }
        // 超过 32 位截断右段（罕见，OTel 不应超过 32 hex）
        return hex.substring(hex.length() - STANDARD_LENGTH);
    }

    /**
     * 判定是否为合法 32 位 hex。
     */
    public static boolean isValidHex32(String s) {
        if (s == null || s.length() != STANDARD_LENGTH) return false;
        return isHexString(s);
    }

    private static boolean isHexString(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!ok) return false;
        }
        return true;
    }
}