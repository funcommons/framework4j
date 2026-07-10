package fun.commons.framework4j.openid.util;

import fun.commons.framework4j.id.util.IdObfuscator;
import java.text.ParseException;
import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OpenID 类型处理工具类
 * <p>
 * 统一处理所有 OpenID 相关的类型检查、转换和验证逻辑
 * 支持：Long/long, Integer/int, List/Set/Array 及其组合
 */
public final class OpenIdTypeUtils {

    // 支持的单一ID类型
    private static final Set<Class<?>> SUPPORTED_SINGLE_TYPES = Set.of(
        Long.class, long.class,
        Integer.class, int.class
    );

    // 支持的集合类型
    private static final Set<Class<?>> SUPPORTED_COLLECTION_TYPES = Set.of(
        List.class, Set.class
    );

    // 支持的数组类型
    private static final Map<Class<?>, Class<?>> ARRAY_TYPE_MAP = Map.of(
        long[].class, Long.class,
        Long[].class, Long.class,
        int[].class, Integer.class,
        Integer[].class, Integer.class
    );

    private OpenIdTypeUtils() {}

    /**
     * 检查是否为支持的单一类型
     */
    public static boolean isSupportedSingleType(Class<?> type) {
        return SUPPORTED_SINGLE_TYPES.contains(type);
    }

    /**
     * 检查是否为支持的集合类型
     */
    public static boolean isSupportedCollectionType(Class<?> type) {
        return SUPPORTED_COLLECTION_TYPES.contains(type);
    }

    /**
     * 检查是否为支持的数组类型
     */
    public static boolean isSupportedArrayType(Class<?> type) {
        return ARRAY_TYPE_MAP.containsKey(type);
    }

    /**
     * 获取数组元素的类型
     */
    public static Class<?> getArrayElementType(Class<?> arrayType) {
        return ARRAY_TYPE_MAP.get(arrayType);
    }

    /**
     * 验证值是否为有效ID（非负数检查）
     */
    public static void validateId(long value) throws IllegalArgumentException {
        if (value < 0) {
            throw new IllegalArgumentException("ID cannot be negative: " + value);
        }
    }

    /**
     * 转换为OpenID格式（序列化）
     */
    public static Object convertToOpenId(Object value) {
        if (value == null) {
            return null;
        }

        Class<?> valueType = value.getClass();

        // 单一值类型
        if (isSupportedSingleType(valueType)) {
            return convertSingleToOpenId(value);
        }

        // List 类型
        if (value instanceof List) {
            return convertListToOpenId((List<?>) value);
        }

        // Set 类型
        if (value instanceof Set) {
            return convertSetToOpenId((Set<?>) value);
        }

        // 数组类型
        if (valueType.isArray()) {
            return convertArrayToOpenId(value);
        }

        return value;
    }

    /**
     * 从OpenID格式转换（反序列化）
     */
    public static Object convertFromOpenId(String text, Class<?> targetType) throws ParseException {
        if (text == null || text.isEmpty()) {
            return null;
        }

        long decodedValue;
        try {
            if (text.matches("^-?\\d+$")) {
                // 纯数字兼容性
                decodedValue = Long.parseLong(text);
            } else {
                // OpenID 解码
                decodedValue = IdObfuscator.fromOpenId(text);
            }
        } catch (Exception e) {
            throw new ParseException("Invalid OpenID format: " + text, 0);
        }

        try {
            // 负数检查
            validateId(decodedValue);
        } catch (IllegalArgumentException e) {
            throw new ParseException("ID cannot be negative: " + decodedValue, 0);
        }

        // 根据目标类型转换
        if (targetType == Integer.class || targetType == int.class) {
            if (decodedValue > Integer.MAX_VALUE) {
                throw new ParseException("Value too large for Integer: " + decodedValue, 0);
            }
            return (int) decodedValue;
        }

        return decodedValue;
    }

    // 私有转换方法
    private static String convertSingleToOpenId(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            long longValue = ((Number) value).longValue();
            validateId(longValue);
            return IdObfuscator.toOpenId(longValue);
        }
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> convertListToOpenId(List<?> list) {
        if (list.isEmpty()) {
            return Collections.emptyList();
        }

        // 检查是否包含支持的类型
        if (!containsSupportedType(list)) {
            return (List<String>) list; // 不支持的类型直接返回
        }

        return list.stream()
                .map(OpenIdTypeUtils::convertSingleToOpenId)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static Set<String> convertSetToOpenId(Set<?> set) {
        if (set.isEmpty()) {
            return Collections.emptySet();
        }

        // 检查是否包含支持的类型
        if (!containsSupportedType(set)) {
            return (Set<String>) set; // 不支持的类型直接返回
        }

        return set.stream()
                .map(OpenIdTypeUtils::convertSingleToOpenId)
                .collect(Collectors.toSet());
    }

    private static Object convertArrayToOpenId(Object array) {
        int length = Array.getLength(array);
        if (length == 0) {
            return array;
        }

        Class<?> componentType = array.getClass().getComponentType();
        Class<?> elementType = getArrayElementType(array.getClass());

        if (elementType == null) {
            return array; // 不支持的数组类型
        }

        // 创建字符串数组
        String[] result = new String[length];
        for (int i = 0; i < length; i++) {
            Object element = Array.get(array, i);
            if (element != null) {
                result[i] = convertSingleToOpenId(element);
            }
        }
        return result;
    }

    /**
     * 检查集合是否包含支持的类型
     */
    private static boolean containsSupportedType(Collection<?> collection) {
        if (collection.isEmpty()) {
            return false;
        }

        return collection.stream()
                .filter(Objects::nonNull)
                .anyMatch(item -> isSupportedSingleType(item.getClass()));
    }
}