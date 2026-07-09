package fun.commons.framework4j.datetime;

/**
 * 时间上下文管理器
 * <p>
 * 向后兼容的时间格式化上下文管理器。
 * 内部委托给 TimeFormatStateHolder，提供与旧版本完全兼容的 API。
 *
 * <p><strong>重要说明：</strong></p>
 * <ul>
 *   <li>此类已标记为 @Deprecated，建议使用 {@link TimeFormatStateHolder}</li>
 *   <li>保持所有公共 API 的向后兼容性</li>
 *   <li>内部实现已重构为使用新的状态管理机制</li>
 * </ul>
 *
 * <p>当setUseLocal(true)或检测到@LocalTimeFormat注解时，时间将格式化为本地格式（yyyy-MM-dd HH:mm:ss）；
 * 否则使用ISO-8601格式（带时区信息）。</p>
 *
 * @since 1.0.0
 * @deprecated 请使用 {@link TimeFormatStateHolder} 替代，此类仅用于向后兼容
 */
@Deprecated
public class TimeContext {

    /**
     * 设置是否使用本地时间格式
     *
     * @param useLocal true使用本地格式，false使用ISO-8601格式
     */
    public static void setUseLocal(boolean useLocal) {
        TimeFormatStateHolder.setUseLocal(useLocal);
    }

    /**
     * 检查当前线程是否使用本地时间格式
     *
     * @return true如果使用本地格式，false如果使用ISO-8601格式
     */
    public static boolean isUseLocal() {
        return TimeFormatStateHolder.isUseLocal();
    }

    /**
     * 获取当前正在处理的类名
     *
     * @return 当前处理的类名，如果未设置则返回null
     */
    public static String getCurrentProcessingClass() {
        TimeFormatState state = TimeFormatStateHolder.getState();
        return state != null && !state.isDefault() ? state.getSourceClass() : null;
    }

    /**
     * 设置当前正在处理的类名
     *
     * @param className 类名
     */
    public static void setCurrentProcessingClass(String className) {
        // 为了向后兼容，创建一个手动状态
        TimeFormatState currentState = TimeFormatStateHolder.getState();
        if (currentState != null && currentState.isUseLocalFormat()) {
            TimeFormatStateHolder.setState(TimeFormatState.manualLocal(className));
        } else {
            TimeFormatStateHolder.setState(TimeFormatState.manualDefault(className));
        }
    }

    /**
     * 检查是否检测到@LocalTimeFormat注解
     *
     * @return true如果检测到注解，false否则
     */
    public static boolean isAnnotationDetected() {
        TimeFormatState state = TimeFormatStateHolder.getState();
        return state != null && state.isFromAnnotation();
    }

    /**
     * 设置注解检测状态
     *
     * @param detected true如果检测到@LocalTimeFormat注解
     */
    public static void setAnnotationDetected(boolean detected) {
        // 这个方法在新架构中不太适用，但为了兼容性保留
        // 实际的注解检测由 TimeFormatInterceptor 处理
        if (detected) {
            TimeFormatState currentState = TimeFormatStateHolder.getState();
            String sourceClass = currentState != null ? currentState.getSourceClass() : "unknown";
            TimeFormatStateHolder.setState(TimeFormatState.annotationLocal(sourceClass));
        }
    }

    /**
     * 清除当前线程的时间格式设置
     * <p>
     * 在请求处理完成后调用，避免内存泄漏。
     * </p>
     */
    public static void clear() {
        TimeFormatStateHolder.clear();
    }

    // ==================== 向后兼容的便利方法 ====================

    /**
     * 获取当前状态（仅用于调试）
     *
     * @return 当前的时间格式化状态
     */
    public static TimeFormatState getCurrentState() {
        return TimeFormatStateHolder.getState();
    }

    /**
     * 设置时间格式化状态（高级用法）
     *
     * @param state 时间格式化状态
     */
    public static void setState(TimeFormatState state) {
        TimeFormatStateHolder.setState(state);
    }
}