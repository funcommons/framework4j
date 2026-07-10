package fun.commons.framework4j.datetime;

import org.springframework.web.method.HandlerMethod;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 时间格式化状态持有者
 *
 * <p>管理线程本地的时间格式化状态，使用单一的 ThreadLocal 变量替代原有的多个变量。
 * 提供高性能的注解检测缓存，避免重复的反射操作。</p>
 *
 * <p>主要功能：
 * <ul>
 *   <li>线程本地状态管理</li>
 *   <li>高性能注解检测缓存</li>
 *   <li>自动内存清理</li>
 * </ul></p>
 *
 * @author LDX2T
 * @since 1.0.0
 */
public class TimeFormatStateHolder {

    /**
     * 线程本地状态存储
     * 使用单一的 ThreadLocal 替代原有的多个 ThreadLocal 变量
     */
    private static final ThreadLocal<TimeFormatState> THREAD_LOCAL_STATE = new ThreadLocal<>();

    /**
     * 类级别注解检测缓存
     * 使用 ConcurrentHashMap 和定时清理机制
     */
    private static final ConcurrentHashMap<Class<?>, CacheEntry> CLASS_CACHE = new ConcurrentHashMap<>();

    /**
     * 方法级别注解检测缓存
     * 用于缓存方法级别的注解检测结果
     */
    private static final ConcurrentHashMap<String, CacheEntry> METHOD_CACHE = new ConcurrentHashMap<>();

    /**
     * 定时清理任务
     */
    private static final ScheduledExecutorService CLEANUP_EXECUTOR =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TimeFormatStateHolder-Cleanup");
            t.setDaemon(true);
            return t;
        });

    /**
     * 缓存条目，包含状态和过期时间
     */
    private static class CacheEntry {
        final TimeFormatState state;
        final long expireTime;

        CacheEntry(TimeFormatState state, long ttl) {
            this.state = state;
            this.expireTime = System.currentTimeMillis() + ttl;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }

    // 初始化定时清理任务
    static {
        // 每30分钟清理一次过期缓存
        CLEANUP_EXECUTOR.scheduleAtFixedRate(TimeFormatStateHolder::cleanupExpiredEntries,
                30, 30, TimeUnit.MINUTES);
    }

    /**
     * 简单的统计信息
     */
    private static final ConcurrentHashMap<String, Long> STATS = new ConcurrentHashMap<>();

    // ==================== 状态管理方法 ====================

    /**
     * 设置当前线程的时间格式化状态
     *
     * @param state 时间格式化状态
     */
    public static void setState(TimeFormatState state) {
        if (state == null) {
            clear();
            return;
        }
        THREAD_LOCAL_STATE.set(state);
        updateStats("set_state");
    }

    /**
     * 获取当前线程的时间格式化状态
     *
     * @return 当前状态，如果未设置则返回默认状态
     */
    public static TimeFormatState getState() {
        TimeFormatState state = THREAD_LOCAL_STATE.get();
        return state != null ? state : TimeFormatState.DEFAULT;
    }

    /**
     * 判断是否应该使用本地时间格式
     *
     * @return true 如果应该使用本地时间格式
     */
    public static boolean shouldUseLocalFormat() {
        return getState().shouldFormat();
    }

    /**
     * 清理当前线程的状态
     * 必须在请求处理完成后调用，避免内存泄漏
     */
    public static void clear() {
        THREAD_LOCAL_STATE.remove();
        updateStats("clear_state");
    }

    // ==================== 注解检测方法 ====================

    /**
     * 检测 HandlerMethod 的时间格式化状态
     *
     * @param handlerMethod Spring HandlerMethod
     * @return 时间格式化状态
     */
    public static TimeFormatState detectTimeFormatState(HandlerMethod handlerMethod) {
        if (handlerMethod == null) {
            return TimeFormatState.DEFAULT;
        }

        Class<?> beanType = handlerMethod.getBeanType();
        String methodKey = buildMethodKey(handlerMethod);

        // 先检查方法级别的缓存
        CacheEntry cachedMethodEntry = METHOD_CACHE.get(methodKey);
        if (cachedMethodEntry != null && !cachedMethodEntry.isExpired()) {
            updateStats("method_cache_hit");
            return cachedMethodEntry.state;
        }

        // 再检查类级别的缓存
        CacheEntry cachedClassEntry = CLASS_CACHE.get(beanType);
        if (cachedClassEntry != null && !cachedClassEntry.isExpired()) {
            // 如果类级别有注解，检查方法级别是否有覆盖
            TimeFormatState methodState = detectMethodAnnotation(handlerMethod, cachedClassEntry.state);
            METHOD_CACHE.put(methodKey, new CacheEntry(methodState, 30 * 60 * 1000L)); // 30分钟
            updateStats("method_cache_miss_class_hit");
            return methodState;
        }

        // 执行完整的注解检测
        TimeFormatState state = performAnnotationDetection(handlerMethod);

        // 更新缓存
        CLASS_CACHE.put(beanType, new CacheEntry(state, 30 * 60 * 1000L)); // 30分钟
        METHOD_CACHE.put(methodKey, new CacheEntry(state, 30 * 60 * 1000L)); // 30分钟

        updateStats("annotation_detection");
        return state;
    }

    /**
     * 手动设置本地时间格式
     * 向后兼容方法
     *
     * @param useLocal 是否使用本地格式
     * @deprecated 推荐使用 setState(TimeFormatState) 方法
     */
    @Deprecated
    public static void setUseLocal(boolean useLocal) {
        setState(useLocal ? TimeFormatState.MANUAL_LOCAL : TimeFormatState.DEFAULT);
    }

    /**
     * 检查是否手动设置为本地格式
     * 向后兼容方法
     *
     * @return true 如果手动设置为本地格式
     * @deprecated 推荐使用 shouldUseLocalFormat() 方法
     */
    @Deprecated
    public static boolean isUseLocal() {
        return shouldUseLocalFormat();
    }

    // ==================== 私有方法 ====================

    /**
     * 执行完整的注解检测
     *
     * @param handlerMethod HandlerMethod
     * @return 检测结果
     */
    private static TimeFormatState performAnnotationDetection(HandlerMethod handlerMethod) {
        Class<?> beanType = handlerMethod.getBeanType();

        // 检查方法级别的注解
        if (handlerMethod.hasMethodAnnotation(LocalTimeFormat.class)) {
            return TimeFormatState.annotationLocal(beanType.getName());
        }

        // 检查类级别的注解
        if (beanType.isAnnotationPresent(LocalTimeFormat.class)) {
            return TimeFormatState.annotationLocal(beanType.getName());
        }

        // 没有注解，使用默认状态
        return TimeFormatState.annotationDefault(beanType.getName());
    }

    /**
     * 检测方法级别的注解，考虑类级别注解的覆盖
     *
     * @param handlerMethod HandlerMethod
     * @param classState 类级别的状态
     * @return 方法级别的最终状态
     */
    private static TimeFormatState detectMethodAnnotation(HandlerMethod handlerMethod, TimeFormatState classState) {
        // 如果方法有注解，方法级别优先
        if (handlerMethod.hasMethodAnnotation(LocalTimeFormat.class)) {
            return TimeFormatState.annotationLocal(handlerMethod.getBeanType().getName());
        }

        // 否则使用类级别的状态
        return classState;
    }

    /**
     * 构建方法的缓存键
     *
     * @param handlerMethod HandlerMethod
     * @return 缓存键
     */
    private static String buildMethodKey(HandlerMethod handlerMethod) {
        return handlerMethod.getBeanType().getName() + "#" + handlerMethod.getMethod().getName();
    }

    /**
     * 更新统计信息
     *
     * @param key 统计键
     */
    private static void updateStats(String key) {
        STATS.merge(key, 1L, Long::sum);
    }

    // ==================== 监控和统计方法 ====================

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计
     */
    public static CacheStats getCacheStats() {
        return new CacheStats(
            CLASS_CACHE.size(),
            METHOD_CACHE.size()
        );
    }

    /**
     * 获取操作统计信息
     *
     * @return 操作统计的副本
     */
    public static ConcurrentHashMap<String, Long> getOperationStats() {
        return new ConcurrentHashMap<>(STATS);
    }

    /**
     * 清理缓存
     */
    public static void clearCaches() {
        CLASS_CACHE.clear();
        METHOD_CACHE.clear();
        STATS.clear();
        updateStats("clear_caches");
    }

    /**
     * 清理过期的缓存条目
     */
    private static void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();

        // 清理类级别缓存
        CLASS_CACHE.entrySet().removeIf(entry -> {
            CacheEntry cacheEntry = entry.getValue();
            return cacheEntry.isExpired();
        });

        // 清理方法级别缓存
        METHOD_CACHE.entrySet().removeIf(entry -> {
            CacheEntry cacheEntry = entry.getValue();
            return cacheEntry.isExpired();
        });

        updateStats("cleanup_expired_entries");
    }

    /**
     * 预热缓存
     *
     * @param handlerMethods 需要预热的 HandlerMethod 列表
     */
    public static void warmupCaches(Iterable<HandlerMethod> handlerMethods) {
        for (HandlerMethod handlerMethod : handlerMethods) {
            detectTimeFormatState(handlerMethod);
        }
        updateStats("warmup_caches");
    }

    // ==================== 内部类 ====================

    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final long classCacheSize;
        private final long methodCacheSize;

        public CacheStats(long classCacheSize, long methodCacheSize) {
            this.classCacheSize = classCacheSize;
            this.methodCacheSize = methodCacheSize;
        }

        public long getClassCacheSize() {
            return classCacheSize;
        }

        public long getMethodCacheSize() {
            return methodCacheSize;
        }

        public long getTotalCacheSize() {
            return classCacheSize + methodCacheSize;
        }

        @Override
        public String toString() {
            return String.format(
                "CacheStats{classCacheSize=%d, methodCacheSize=%d, totalSize=%d}",
                classCacheSize, methodCacheSize, getTotalCacheSize()
            );
        }
    }
}