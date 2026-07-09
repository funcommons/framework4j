package fun.commons.framework4j.openid.formatter;

import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.openid.util.OpenIdTypeUtils;
import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Parser;
import org.springframework.format.Printer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * OpenID 入参转换工厂 (Spring MVC)
 * <p>
 * 作用:
 * 拦截带有 @OpenId 注解的 Controller 参数，实现 String -> Long/Integer 的自动还原。
 * 兼容纯数字输入 (以支持旧接口过渡)。
 * 支持类型：Long, long, Integer, int
 */
public class OpenIdFormatterFactory implements AnnotationFormatterFactory<OpenId> {

    // v2.1: 不可变 Set 单例（原每次 new HashSet + add）
    private static final Set<Class<?>> FIELD_TYPES = Set.of(Long.class, long.class, Integer.class, int.class);

    @Override
    public Set<Class<?>> getFieldTypes() {
        return FIELD_TYPES;
    }

    @Override
    public Printer<?> getPrinter(OpenId annotation, Class<?> fieldType) {
        // 用于服务端渲染 (如 Thymeleaf) 或 MvcUriComponentsBuilder
        return (object, locale) -> {
            if (object == null) return null;
            // 使用统一工具类进行转换
            Object converted = OpenIdTypeUtils.convertToOpenId(object);
            return converted != null ? converted.toString() : null;
        };
    }

    @Override
    public Parser<?> getParser(OpenId annotation, Class<?> fieldType) {
        // 用于接口入参绑定 (String -> Long/Integer)
        return (text, locale) -> {
            try {
                // 使用统一工具类进行转换
                return OpenIdTypeUtils.convertFromOpenId(text, fieldType);
            } catch (java.text.ParseException e) {
                // 重新抛出原始的ParseException，保留详细错误消息
                throw e;
            } catch (Exception e) {
                // 对于其他异常，抛出通用的ParseException
                throw new java.text.ParseException("Invalid OpenID format: " + text, 0);
            }
        };
    }
}