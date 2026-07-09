package fun.commons.framework4j.datetime;

import java.util.Objects;

/**
 * 时间格式化状态类
 *
 * <p>用于封装时间格式化的相关状态信息，替代原有的多个 ThreadLocal 变量。
 * 这个类设计为不可变对象，确保线程安全和状态一致性。</p>
 *
 * <p>主要包含：
 * <ul>
 *   <li>是否使用本地时间格式的标志</li>
 *   <li>状态来源类名（用于调试和监控）</li>
 *   <li>是否来自注解的标志</li>
 * </ul></p>
 *
 * @since 1.0.0
 */
public final class TimeFormatState {

    /**
     * 是否使用本地时间格式
     * true: 使用 yyyy-MM-dd HH:mm:ss 格式
     * false: 使用 ISO-8601 格式
     */
    private final boolean useLocalFormat;

    /**
     * 状态来源类名
     * 用于调试和监控，标识这个状态是由哪个类设置的
     */
    private final String sourceClass;

    /**
     * 是否来自注解
     * true: 来自 @LocalTimeFormat 注解
     * false: 来自手动设置或其他方式
     */
    private final boolean fromAnnotation;

    // ==================== 构造函数 ====================

    public TimeFormatState(boolean useLocalFormat, String sourceClass, boolean fromAnnotation) {
        this.useLocalFormat = useLocalFormat;
        this.sourceClass = sourceClass;
        this.fromAnnotation = fromAnnotation;
    }

    // ==================== Getter 方法 ====================

    public boolean isUseLocalFormat() {
        return useLocalFormat;
    }

    public String getSourceClass() {
        return sourceClass;
    }

    public boolean isFromAnnotation() {
        return fromAnnotation;
    }

    // ==================== equals, hashCode, toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeFormatState that = (TimeFormatState) o;
        return useLocalFormat == that.useLocalFormat &&
               fromAnnotation == that.fromAnnotation &&
               Objects.equals(sourceClass, that.sourceClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(useLocalFormat, sourceClass, fromAnnotation);
    }

    @Override
    public String toString() {
        return "TimeFormatState{" +
                "useLocalFormat=" + useLocalFormat +
                ", sourceClass='" + sourceClass + '\'' +
                ", fromAnnotation=" + fromAnnotation +
                '}';
    }

    // ==================== 预定义常用状态 ====================

    /**
     * 默认状态：不使用本地格式
     */
    public static final TimeFormatState DEFAULT = new TimeFormatState(false, null, false);

    /**
     * 手动设置的本地格式状态
     */
    public static final TimeFormatState MANUAL_LOCAL = new TimeFormatState(true, "manual", false);

    /**
     * 创建注解驱动的本地格式状态
     *
     * @param sourceClass 来源类名
     * @return TimeFormatState 实例
     */
    public static TimeFormatState annotationLocal(String sourceClass) {
        return new TimeFormatState(true, sourceClass, true);
    }

    /**
     * 创建注解驱动的默认格式状态
     *
     * @param sourceClass 来源类名
     * @return TimeFormatState 实例
     */
    public static TimeFormatState annotationDefault(String sourceClass) {
        return new TimeFormatState(false, sourceClass, true);
    }

    /**
     * 创建手动设置的本地格式状态
     *
     * @param sourceClass 来源类名
     * @return TimeFormatState 实例
     */
    public static TimeFormatState manualLocal(String sourceClass) {
        return new TimeFormatState(true, sourceClass, false);
    }

    /**
     * 创建手动设置的默认格式状态
     *
     * @param sourceClass 来源类名
     * @return TimeFormatState 实例
     */
    public static TimeFormatState manualDefault(String sourceClass) {
        return new TimeFormatState(false, sourceClass, false);
    }

    // ==================== 便利方法 ====================

    /**
     * 是否需要格式化时间
     *
     * @return true 如果需要格式化为本地时间格式
     */
    public boolean shouldFormat() {
        return useLocalFormat;
    }

    /**
     * 是否为默认状态
     *
     * @return true 如果是默认状态
     */
    public boolean isDefault() {
        return !useLocalFormat && sourceClass == null && !fromAnnotation;
    }

    /**
     * 是否为手动设置的状态
     *
     * @return true 如果来自手动设置
     */
    public boolean isManual() {
        return !fromAnnotation;
    }
}