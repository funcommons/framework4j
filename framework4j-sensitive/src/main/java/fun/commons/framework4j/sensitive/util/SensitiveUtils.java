package fun.commons.framework4j.sensitive.util;

import fun.commons.framework4j.sensitive.annotation.SensitiveRule;

/**
 * 脱敏工具（按规则脱敏字符串）
 *
 * @since 2.1.0
 */
public final class SensitiveUtils {

    private SensitiveUtils() {}

    /**
     * 按规则脱敏
     */
    public static String desensitize(String value, SensitiveRule rule) {
        return desensitize(value, rule, null);
    }

    /**
     * v2.1 功能增强：按规则脱敏 + 自定义 pattern
     *
     * @param pattern 仅 rule=CUSTOM 时生效，格式 "前保留,后保留,星号数"
     */
    public static String desensitize(String value, SensitiveRule rule, String pattern) {
        if (value == null || value.isEmpty()) return value;
        if (rule == SensitiveRule.CUSTOM) {
            return maskCustom(value, pattern);
        }
        switch (rule) {
            case PHONE:
                return maskPhone(value);
            case ID_CARD:
                return maskIdCard(value);
            case BANK_CARD:
                return maskBankCard(value);
            case EMAIL:
                return maskEmail(value);
            case NAME:
                return maskName(value);
            case ADDRESS:
                return maskAddress(value);
            case ALL:
            default:
                return "******";
        }
    }

    /** 手机号：138****1234（保留前 3 + 后 4） */
    static String maskPhone(String phone) {
        if (phone.length() < 7) return "******";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 身份证：110101********1234（保留前 6 + 后 4） */
    static String maskIdCard(String id) {
        if (id.length() < 10) return "******";
        return id.substring(0, 6) + "********" + id.substring(id.length() - 4);
    }

    /** 银行卡：6228******5678（保留前 4 + 后 4） */
    static String maskBankCard(String card) {
        if (card.length() < 8) return "******";
        return card.substring(0, 4) + "******" + card.substring(card.length() - 4);
    }

    /** 邮箱：a***@example.com（首字符 + *** + @域名） */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return "******";
        String prefix = email.substring(0, at);
        String domain = email.substring(at);
        if (prefix.length() <= 1) return prefix + "***" + domain;
        return prefix.charAt(0) + "***" + domain;
    }

    /** 姓名：张*（保留首字符） */
    static String maskName(String name) {
        if (name.length() <= 1) return "*";
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }

    /** 地址：保留前 6 位 + *** */
    static String maskAddress(String addr) {
        if (addr.length() <= 6) return "***";
        return addr.substring(0, 6) + "***";
    }

    /** v2.1 功能增强：自定义脱敏 pattern "前保留,后保留,星号数" → Ab********cd */
    static String maskCustom(String value, String pattern) {
        if (pattern == null || pattern.isEmpty()) return "******";
        String[] parts = pattern.split(",");
        int keepStart = parts.length > 0 ? Math.max(0, Integer.parseInt(parts[0].trim())) : 0;
        int keepEnd = parts.length > 1 ? Math.max(0, Integer.parseInt(parts[1].trim())) : 0;
        int stars = parts.length > 2 ? Math.max(1, Integer.parseInt(parts[2].trim())) : 4;

        if (value.length() <= keepStart + keepEnd) return "*".repeat(stars);
        String prefix = value.substring(0, keepStart);
        String suffix = value.substring(value.length() - keepEnd);
        return prefix + "*".repeat(stars) + suffix;
    }
}
