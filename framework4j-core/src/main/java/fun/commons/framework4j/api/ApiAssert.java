package fun.commons.framework4j.api;

import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.Collection;
import java.util.Map;

/**
 * API 业务断言工具类
 * <p>
 * 替代 if-throw 写法，使业务代码更简洁
 *
 * @author LDX2T
 * @since 1.0.0
 */
public class ApiAssert {

    private ApiAssert() {
        // 工具类禁止实例化
    }

    /**
     * 断言表达式为真，否则抛出异常
     *
     * @param expression 布尔表达式
     * @param apiCode    错误码
     */
    public static void isTrue(boolean expression, ApiCode apiCode) {
        if (!expression) {
            throw new ApiException(apiCode);
        }
    }

    /**
     * 断言表达式为真，否则抛出异常（自定义消息）
     *
     * @param expression 布尔表达式
     * @param apiCode    错误码
     * @param message    自定义消息
     */
    public static void isTrue(boolean expression, ApiCode apiCode, String message) {
        if (!expression) {
            throw new ApiException(apiCode, message);
        }
    }

    /**
     * 断言表达式为假，否则抛出异常
     *
     * @param expression 布尔表达式
     * @param apiCode    错误码
     */
    public static void isFalse(boolean expression, ApiCode apiCode) {
        if (expression) {
            throw new ApiException(apiCode);
        }
    }

    /**
     * 断言表达式为假，否则抛出异常（自定义消息）
     *
     * @param expression 布尔表达式
     * @param apiCode    错误码
     * @param message    自定义消息
     */
    public static void isFalse(boolean expression, ApiCode apiCode, String message) {
        if (expression) {
            throw new ApiException(apiCode, message);
        }
    }

    /**
     * 断言对象不为空
     *
     * @param object  对象
     * @param apiCode 错误码
     */
    public static void notNull(@Nullable Object object, ApiCode apiCode) {
        if (object == null) {
            throw new ApiException(apiCode);
        }
    }

    /**
     * 断言对象不为空（自定义消息）
     *
     * @param object  对象
     * @param apiCode 错误码
     * @param message 自定义消息
     */
    public static void notNull(@Nullable Object object, ApiCode apiCode, String message) {
        if (object == null) {
            throw new ApiException(apiCode, message);
        }
    }

    /**
     * 断言对象为空
     *
     * @param object  对象
     * @param apiCode 错误码
     */
    public static void isNull(@Nullable Object object, ApiCode apiCode) {
        if (object != null) {
            throw new ApiException(apiCode);
        }
    }

    /**
     * 断言字符串不为空
     *
     * @param text    字符串
     * @param apiCode 错误码
     */
    public static void notEmpty(@Nullable String text, ApiCode apiCode) {
        if (ObjectUtils.isEmpty(text)) {
            throw new ApiException(apiCode);
        }
    }

    /**
     * 断言字符串不为空（自定义消息）
     *
     * @param text    字符串
     * @param apiCode 错误码
     * @param message 自定义消息
     */
    public static void notEmpty(@Nullable String text, ApiCode apiCode, String message) {
        if (ObjectUtils.isEmpty(text)) {
            throw new ApiException(apiCode, message);
        }
    }

    /**
     * 断言集合不为空
     *
     * @param collection 集合
     * @param apiCode    错误码
     */
    public static void notEmpty(@Nullable Collection<?> collection, ApiCode apiCode) {
        if (CollectionUtils.isEmpty(collection)) {
            throw new ApiException(apiCode);
        }
    }

    /**
     * 断言集合不为空（自定义消息）
     *
     * @param collection 集合
     * @param apiCode    错误码
     * @param message    自定义消息
     */
    public static void notEmpty(@Nullable Collection<?> collection, ApiCode apiCode, String message) {
        if (CollectionUtils.isEmpty(collection)) {
            throw new ApiException(apiCode, message);
        }
    }

    /**
     * 断言 Map 不为空
     *
     * @param map     Map
     * @param apiCode 错误码
     */
    public static void notEmpty(@Nullable Map<?, ?> map, ApiCode apiCode) {
        if (CollectionUtils.isEmpty(map)) {
            throw new ApiException(apiCode);
        }
    }

    /**
     * 断言 Map 不为空（自定义消息）
     *
     * @param map     Map
     * @param apiCode 错误码
     * @param message 自定义消息
     */
    public static void notEmpty(@Nullable Map<?, ?> map, ApiCode apiCode, String message) {
        if (CollectionUtils.isEmpty(map)) {
            throw new ApiException(apiCode, message);
        }
    }

    /**
     * 直接抛出异常
     *
     * @param apiCode 错误码
     */
    public static void failure(ApiCode apiCode) {
        throw new ApiException(apiCode);
    }

    /**
     * 直接抛出异常（自定义消息）
     *
     * @param apiCode 错误码
     * @param message 自定义消息
     */
    public static void failure(ApiCode apiCode, String message) {
        throw new ApiException(apiCode, message);
    }
}
