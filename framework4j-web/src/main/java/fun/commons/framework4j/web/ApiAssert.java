package fun.commons.framework4j.web;

import fun.commons.framework4j.api.ApiCode;

import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * API 业务断言工具类
 * <p>
 * 替代 if-throw 写法，使业务代码更简洁
 *
 * @since 1.0.0
 */
public class ApiAssert {

    private static final ApiAssert INSTANCE = new ApiAssert();

    private ApiAssert() {
        // 工具类禁止实例化
    }

    /**
     * 断言表达式为真，否则抛出异常
     *
     * @param expression 布尔表达式
     * @param apiCode    错误码
     */
    public static ApiAssert isTrue(boolean expression, ApiCode apiCode) {
        if (!expression) {
            throw new ApiException(apiCode);
        }
        return INSTANCE;
    }

    /**
     * 断言表达式为真，否则抛出异常（自定义消息）
     *
     * @param expression 布尔表达式
     * @param apiCode    错误码
     * @param message    自定义消息
     */
    public static ApiAssert isTrue(boolean expression, ApiCode apiCode, String message) {
        if (!expression) {
            throw new ApiException(apiCode, message);
        }
        return INSTANCE;
    }

    /**
     * 断言表达式为假，否则抛出异常
     *
     * @param expression 布尔表达式
     * @param apiCode    错误码
     */
    public static ApiAssert isFalse(boolean expression, ApiCode apiCode) {
        if (expression) {
            throw new ApiException(apiCode);
        }
        return INSTANCE;
    }

    /**
     * 断言表达式为假，否则抛出异常（自定义消息）
     *
     * @param expression 布尔表达式
     * @param apiCode    错误码
     * @param message    自定义消息
     */
    public static ApiAssert isFalse(boolean expression, ApiCode apiCode, String message) {
        if (expression) {
            throw new ApiException(apiCode, message);
        }
        return INSTANCE;
    }

    /**
     * 断言对象不为空
     *
     * @param object  对象
     * @param apiCode 错误码
     */
    public static ApiAssert notNull(@Nullable Object object, ApiCode apiCode) {
        if (object == null) {
            throw new ApiException(apiCode);
        }
        return INSTANCE;
    }

    /**
     * 断言对象不为空（自定义消息）
     *
     * @param object  对象
     * @param apiCode 错误码
     * @param message 自定义消息
     */
    public static ApiAssert notNull(@Nullable Object object, ApiCode apiCode, String message) {
        if (object == null) {
            throw new ApiException(apiCode, message);
        }
        return INSTANCE;
    }

    /**
     * 断言对象为空
     *
     * @param object  对象
     * @param apiCode 错误码
     */
    public static ApiAssert isNull(@Nullable Object object, ApiCode apiCode) {
        if (object != null) {
            throw new ApiException(apiCode);
        }
        return INSTANCE;
    }

    /**
     * 断言字符串不为空
     *
     * @param text    字符串
     * @param apiCode 错误码
     */
    public static ApiAssert notEmpty(@Nullable String text, ApiCode apiCode) {
        if (ObjectUtils.isEmpty(text)) {
            throw new ApiException(apiCode);
        }
        return INSTANCE;
    }

    /**
     * 断言字符串不为空（自定义消息）
     *
     * @param text    字符串
     * @param apiCode 错误码
     * @param message 自定义消息
     */
    public static ApiAssert notEmpty(@Nullable String text, ApiCode apiCode, String message) {
        if (ObjectUtils.isEmpty(text)) {
            throw new ApiException(apiCode, message);
        }
        return INSTANCE;
    }

    /**
     * 断言集合不为空
     *
     * @param collection 集合
     * @param apiCode    错误码
     */
    public static ApiAssert notEmpty(@Nullable Collection<?> collection, ApiCode apiCode) {
        if (CollectionUtils.isEmpty(collection)) {
            throw new ApiException(apiCode);
        }
        return INSTANCE;
    }

    /**
     * 断言集合不为空（自定义消息）
     *
     * @param collection 集合
     * @param apiCode    错误码
     * @param message    自定义消息
     */
    public static ApiAssert notEmpty(@Nullable Collection<?> collection, ApiCode apiCode, String message) {
        if (CollectionUtils.isEmpty(collection)) {
            throw new ApiException(apiCode, message);
        }
        return INSTANCE;
    }

    /**
     * 断言 Map 不为空
     *
     * @param map     Map
     * @param apiCode 错误码
     */
    public static ApiAssert notEmpty(@Nullable Map<?, ?> map, ApiCode apiCode) {
        if (CollectionUtils.isEmpty(map)) {
            throw new ApiException(apiCode);
        }
        return INSTANCE;
    }

    /**
     * 断言 Map 不为空（自定义消息）
     *
     * @param map     Map
     * @param apiCode 错误码
     * @param message 自定义消息
     */
    public static ApiAssert notEmpty(@Nullable Map<?, ?> map, ApiCode apiCode, String message) {
        if (CollectionUtils.isEmpty(map)) {
            throw new ApiException(apiCode, message);
        }
        return INSTANCE;
    }

    /**
     * 直接抛出异常
     *
     * @param apiCode 错误码
     */
    public static ApiAssert failure(ApiCode apiCode) {
        throw new ApiException(apiCode);
        // unreachable, but signature requires return for chainable API
    }

    /**
     * 直接抛出异常（自定义消息）
     *
     * @param apiCode 错误码
     * @param message 自定义消息
     */
    public static ApiAssert failure(ApiCode apiCode, String message) {
        throw new ApiException(apiCode, message);
        // unreachable, but signature requires return for chainable API
    }

    // ==================== v2.1 新增断言 ====================

    /**
     * 断言两个对象相等（用 {@link Objects#equals}）
     */
    public static ApiAssert equals(@Nullable Object expected, @Nullable Object actual, ApiCode apiCode) {
        if (!Objects.equals(expected, actual)) {
            throw new ApiException(apiCode);
        }
        return INSTANCE;
    }

    public static ApiAssert equals(@Nullable Object expected, @Nullable Object actual, ApiCode apiCode, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new ApiException(apiCode, message);
        }
        return INSTANCE;
    }

    /**
     * 断言两个对象不相等
     */
    public static ApiAssert notEquals(@Nullable Object expected, @Nullable Object actual, ApiCode apiCode) {
        if (Objects.equals(expected, actual)) {
            throw new ApiException(apiCode);
        }
        return INSTANCE;
    }

    /**
     * 断言数字为正数（&gt; 0）。支持 Integer / Long / Double 等 Number 子类。
     */
    public static ApiAssert isPositive(@Nullable Number number, ApiCode apiCode) {
        if (number == null || number.doubleValue() <= 0) {
            throw new ApiException(apiCode);
        }
        return INSTANCE;
    }

    public static ApiAssert isPositive(@Nullable Number number, ApiCode apiCode, String message) {
        if (number == null || number.doubleValue() <= 0) {
            throw new ApiException(apiCode, message);
        }
        return INSTANCE;
    }

    /**
     * v2.1 P1 修复：正则预编译缓存。
     * <p>原 {@code Pattern.matches(regex, text)} 每次断言重新编译正则，热路径开销大。
     * <p>此处用 ConcurrentHashMap 缓存 Pattern，相同 regex 仅编译一次。
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Pattern> REGEX_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static Pattern compileRegex(String regex) {
        Pattern cached = REGEX_CACHE.get(regex);
        if (cached != null) return cached;
        Pattern compiled = Pattern.compile(regex);
        Pattern prev = REGEX_CACHE.putIfAbsent(regex, compiled);
        return prev != null ? prev : compiled;
    }

    /**
     * 断言字符串匹配正则
     */
    public static ApiAssert matches(@Nullable String text, String regex, ApiCode apiCode) {
        if (text == null || !compileRegex(regex).matcher(text).matches()) {
            throw new ApiException(apiCode);
        }
        return INSTANCE;
    }

    public static ApiAssert matches(@Nullable String text, String regex, ApiCode apiCode, String message) {
        if (text == null || !compileRegex(regex).matcher(text).matches()) {
            throw new ApiException(apiCode, message);
        }
        return INSTANCE;
    }

    /**
     * 断言集合包含指定元素
     */
    public static ApiAssert contains(@Nullable Collection<?> collection, Object element, ApiCode apiCode) {
        if (collection == null || !collection.contains(element)) {
            throw new ApiException(apiCode);
        }
        return INSTANCE;
    }
}
