package fun.commons.framework4j.openid.fastjson2;

import com.alibaba.fastjson2.filter.BeanContext;
import com.alibaba.fastjson2.filter.ContextValueFilter;
import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.openid.util.OpenIdTypeUtils;

/**
 * OpenID 出参混淆过滤器 (FastJson2)
 * <p>
 * 作用:
 * 拦截 JSON 序列化过程，检测字段上的 @OpenId 注解。
 * 自动将支持的所有 ID 类型转换为混淆后的字符串。
 * 支持类型：Long/long, Integer/int, List/Set/Array 及其组合
 */
public class OpenIdAnnotationFilter implements ContextValueFilter {

    @Override
    public Object process(BeanContext context, Object object, String name, Object value) {
        if (value == null) {
            return null;
        }

        // 检查字段或 getter 方法上是否有 @OpenId 注解
        OpenId annotation = context.getAnnotation(OpenId.class);
        if (annotation == null) {
            return value;
        }

        // 使用统一工具类进行转换
        return OpenIdTypeUtils.convertToOpenId(value);
    }
}