package fun.commons.framework4j.sensitive.annotation;

/**
 * 脱敏规则枚举
 *
 * @since 2.1.0
 */
public enum SensitiveRule {
    PHONE,
    ID_CARD,
    BANK_CARD,
    EMAIL,
    NAME,
    ADDRESS,
    ALL,
    /** v2.1 功能增强：自定义脱敏（配合 @Sensitive(pattern="前,后,星号数")） */
    CUSTOM
}
