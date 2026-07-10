package fun.commons.framework4j.datetime;

import com.alibaba.fastjson2.filter.ValueFilter;

import java.lang.reflect.Array;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FastJSON2 过滤器，用于动态格式化 OffsetDateTime 及其集合类型
 *
 * <p>优化后的时间格式化过滤器，职责清晰：
 * <ul>
 *   <li>根据 TimeFormatStateHolder 的状态决定是否格式化</li>
 *   <li>支持单个 OffsetDateTime、List、Set、数组等集合类型</li>
 *   <li>移除重复的注解检测逻辑，提高性能</li>
 * </ul></p>
 *
 * <p>注解检测逻辑已迁移到 TimeFormatInterceptor 中，本过滤器只负责格式化操作。</p>
 *
 * @author LDX2T
 * @since 1.0.0
 */
public class DynamicTimeFilter implements ValueFilter {
    private static final DateTimeFormatter LOCAL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Object apply(Object object, String name, Object value) {
        // Null 安全检查
        if (value == null) {
            return null;
        }

        // 在 Spring MVC 环境中，TimeFormatStateHolder 应该总是有正确的状态
        // 如果状态明确设置为使用本地格式，则应用格式化
        if (TimeFormatStateHolder.shouldUseLocalFormat()) {
            return formatWithLocalTime(value);
        }

        // 如果状态明确设置为不使用本地格式（默认状态），则不应用格式化
        TimeFormatState currentState = TimeFormatStateHolder.getState();
        if (currentState != null && !currentState.isDefault()) {
            // 状态被明确设置过了，但不是本地格式，返回原值
            return value;
        }

        // 只有在状态完全未设置的情况下（非Spring MVC环境），才进行注解检测
        // 这是为了向后兼容直接调用 JSON.toJSONString() 的情况
        if (currentState.isDefault() && hasLocalTimeFormatAnnotation(object)) {
            return formatWithLocalTime(value);
        }

        // 返回原值（ISO格式）
        return value;
    }

    /**
     * 应用本地时间格式化
     * 优化的类型检测和格式化逻辑
     */
    private Object formatWithLocalTime(Object value) {
        // 优化的类型检测，按常见类型排序
        if (value instanceof OffsetDateTime) {
            return formatSingle((OffsetDateTime) value);
        }

        if (value instanceof List<?> list && !list.isEmpty()) {
            return formatList(list);
        }

        if (value instanceof Set<?> set && !set.isEmpty()) {
            return formatSet(set);
        }

        if (value.getClass().isArray()) {
            return formatArray(value);
        }

        if (value instanceof Collection<?> collection && !collection.isEmpty()) {
            return formatCollection(collection);
        }

        // 其他类型，返回原值
        return value;
    }

    /**
     * 格式化单个 OffsetDateTime
     * @param dateTime 待格式化的日期时间对象
     * @return 格式化后的字符串 "yyyy-MM-dd HH:mm:ss"
     */
    private String formatSingle(OffsetDateTime dateTime) {
        return dateTime.format(LOCAL_FORMATTER);
    }

    /**
     * 格式化 List<OffsetDateTime>
     * 优化的批量处理和短路检测
     * @param list 待处理的列表
     * @return 格式化后的列表或原列表
     */
    private Object formatList(List<?> list) {
        // 快速检测：检查前几个元素是否包含 OffsetDateTime
        if (!quickContainsOffsetDateTime(list, Math.min(3, list.size()))) {
            return list; // 不是 OffsetDateTime 集合，直接返回
        }

        // 批量格式化
        return list.stream()
                .map(item -> item instanceof OffsetDateTime
                    ? formatSingle((OffsetDateTime) item)
                    : item)
                .collect(Collectors.toList());
    }

    /**
     * 格式化 Set<OffsetDateTime>
     * 优化的批量处理和短路检测
     * @param set 待处理的集合
     * @return 格式化后的集合或原集合
     */
    private Object formatSet(Set<?> set) {
        // 快速检测：检查前几个元素是否包含 OffsetDateTime
        if (!quickContainsOffsetDateTime(set, Math.min(3, set.size()))) {
            return set; // 不是 OffsetDateTime 集合，直接返回
        }

        // 批量格式化
        return set.stream()
                .map(item -> item instanceof OffsetDateTime
                    ? formatSingle((OffsetDateTime) item)
                    : item)
                .collect(Collectors.toSet());
    }

    /**
     * 格式化 OffsetDateTime 数组
     * 如果数组包含 OffsetDateTime 则返回 String[]，否则返回原值
     * @param array 待处理的数组
     * @return 格式化后的数组或原数组
     */
    private Object formatArray(Object array) {
        int length = Array.getLength(array);

        if (length == 0) {
            return array; // 空数组 - 保持类型
        }

        // 检查组件类型
        Class<?> componentType = array.getClass().getComponentType();
        if (!OffsetDateTime.class.equals(componentType)) {
            return array; // 不是 OffsetDateTime 数组
        }

        // 转换为 String[]
        String[] result = new String[length];
        for (int i = 0; i < length; i++) {
            Object element = Array.get(array, i);
            if (element instanceof OffsetDateTime) {
                result[i] = formatSingle((OffsetDateTime) element);
            } else {
                result[i] = null; // 保持 null 元素
            }
        }
        return result;
    }

    /**
     * 格式化其他 Collection 子类（如 Queue、Deque）
     * 优化的快速检测和批量处理
     * @param collection 待处理的集合
     * @return 格式化后的集合或原集合
     */
    private Object formatCollection(Collection<?> collection) {
        // 快速检测：检查前几个元素
        if (!quickContainsOffsetDateTime(collection, Math.min(3, collection.size()))) {
            return collection; // 不是 OffsetDateTime 集合，直接返回
        }

        // 批量格式化
        return collection.stream()
                .map(item -> item instanceof OffsetDateTime
                    ? formatSingle((OffsetDateTime) item)
                    : item)
                .collect(Collectors.toList());
    }

    /**
     * 快速检测集合是否包含 OffsetDateTime 元素（短路检测）
     * @param collection 待检查的集合
     * @param checkCount 检查的元素数量限制
     * @return true 如果集合包含至少一个 OffsetDateTime 元素
     */
    private boolean quickContainsOffsetDateTime(Collection<?> collection, int checkCount) {
        if (collection.isEmpty()) {
            return false;
        }

        int checked = 0;
        for (Object item : collection) {
            if (item instanceof OffsetDateTime) {
                return true; // 短路：找到目标元素立即返回
            }
            if (++checked >= checkCount) {
                break; // 达到检查数量限制
            }
        }
        return false;
    }

    /**
     * 检查集合是否包含 OffsetDateTime 元素（完整检测）
     * @param collection 待检查的集合
     * @return true 如果集合包含至少一个 OffsetDateTime 元素
     */
    private boolean containsOffsetDateTime(Collection<?> collection) {
        return collection.stream()
                .filter(Objects::nonNull)
                .anyMatch(item -> item instanceof OffsetDateTime);
    }

    /**
     * 检查对象或其类型是否带有 @LocalTimeFormat 注解
     * 提供向后兼容的直接注解检测功能
     *
     * @param object 待检查的对象
     * @return true 如果检测到 @LocalTimeFormat 注解
     */
    private boolean hasLocalTimeFormatAnnotation(Object object) {
        if (object == null) {
            return false;
        }

        Class<?> clazz = object.getClass();

        // 只检查类级别的直接注解，避免继承和其他干扰
        try {
            java.lang.annotation.Annotation annotation = clazz.getDeclaredAnnotation(LocalTimeFormat.class);
            if (annotation != null) {
                return true;
            }
        } catch (Exception e) {
            // 忽略反射异常
        }

        return false;
    }
}