package fun.commons.framework4j.datetime;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * OffsetDateTime 集合转换器
 * 支持 String[] → List<OffsetDateTime>, Set<OffsetDateTime>, OffsetDateTime[]
 *
 * <p>使用场景:</p>
 * <ul>
 *   <li>@RequestParam 重复参数: ?times=2024-01-01T10:00:00+08:00&times=2024-01-02T14:30:00+08:00</li>
 *   <li>Spring MVC 自动将重复参数收集为 String[]</li>
 *   <li>本转换器将 String[] 转换为目标集合类型</li>
 * </ul>
 *
 * <p>支持的时间格式（通过 Jackson 智能解析）:</p>
 * <ul>
 *   <li>时间戳: 1704081600000</li>
 *   <li>ISO-8601: 2024-01-01T10:00:00+08:00</li>
 *   <li>本地格式: 2024-01-01 10:00:00 (GMT+8)</li>
 * </ul>
 *
 * @see StringToOffsetDateTimeConverter
 */
public class StringArrayToOffsetDateTimeCollectionConverter implements GenericConverter {

    // v2.1: 复用单例（原 convertSingleElement 每次 new）
    private static final StringToOffsetDateTimeConverter DELEGATE = new StringToOffsetDateTimeConverter();


    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
        Set<ConvertiblePair> pairs = new HashSet<>();
        // String[] → List<OffsetDateTime>
        pairs.add(new ConvertiblePair(String[].class, List.class));
        // String[] → Set<OffsetDateTime>
        pairs.add(new ConvertiblePair(String[].class, Set.class));
        // String[] → OffsetDateTime[]
        pairs.add(new ConvertiblePair(String[].class, OffsetDateTime[].class));
        return pairs;
    }

    @Override
    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        // Null 安全检查
        if (source == null) {
            return null;
        }

        String[] sourceArray = (String[]) source;

        // 类型检查：只处理 OffsetDateTime 集合
        if (!isOffsetDateTimeCollection(targetType)) {
            return null; // 返回 null，让其他转换器处理
        }

        // 转换每个元素
        List<OffsetDateTime> convertedList = new ArrayList<>();
        for (String element : sourceArray) {
            OffsetDateTime converted = convertSingleElement(element);
            convertedList.add(converted);
        }

        // 根据目标类型返回相应集合
        return convertToTargetType(convertedList, targetType);
    }

    /**
     * 检查目标类型是否为 OffsetDateTime 集合
     *
     * @param targetType 目标类型描述符
     * @return true 如果目标类型为 OffsetDateTime 集合，否则 false
     */
    private boolean isOffsetDateTimeCollection(TypeDescriptor targetType) {
        // 检查数组类型
        if (targetType.isArray()) {
            TypeDescriptor elementType = targetType.getElementTypeDescriptor();
            return elementType != null && OffsetDateTime.class.equals(elementType.getType());
        }

        // 检查集合类型 (List, Set, etc.)
        if (targetType.isCollection()) {
            TypeDescriptor elementType = targetType.getElementTypeDescriptor();
            return elementType != null && OffsetDateTime.class.equals(elementType.getType());
        }

        return false;
    }

    /**
     * 转换单个元素（复用 StringToOffsetDateTimeConverter 逻辑）
     *
     * @param source 源字符串
     * @return 转换后的 OffsetDateTime，如果源为 null 或空则返回 null
     */
    private OffsetDateTime convertSingleElement(String source) {
        // v2.1: 复用单例 DELEGATE
        return DELEGATE.convert(source);
    }

    /**
     * 将转换后的列表转换为目标集合类型
     *
     * @param convertedList 已转换的 OffsetDateTime 列表
     * @param targetType 目标类型描述符
     * @return 目标类型的集合对象
     */
    private Object convertToTargetType(List<OffsetDateTime> convertedList, TypeDescriptor targetType) {
        // 数组类型
        if (targetType.isArray()) {
            return convertedList.toArray(new OffsetDateTime[0]);
        }

        Class<?> collectionType = targetType.getType();

        // List 类型
        if (List.class.isAssignableFrom(collectionType)) {
            return new ArrayList<>(convertedList);
        }

        // Set 类型（使用 LinkedHashSet 保持插入顺序）
        if (Set.class.isAssignableFrom(collectionType)) {
            return new LinkedHashSet<>(convertedList);
        }

        // 默认返回 List
        return convertedList;
    }
}
