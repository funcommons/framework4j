package fun.commons.framework4j.openid.web;

import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.openid.util.OpenIdTypeUtils;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

import java.text.ParseException;
import java.util.Map;

/**
 * 处理 {@code @OpenId @PathVariable} 的参数解析器
 * <p>
 * v2.2：替代依赖 {@code OpenIdFormatterFactory} 的 ConversionService 路径，绕过 Spring
 * {@code MethodParameter.getParameterName()} 反射 —— 该反射要求 javac {@code -parameters}
 * 编译 flag，否则抛 {@code IllegalArgumentException}，被 framework4j-web 包成 10106 静默失败。
 * <p>
 * 本解析器直接从 {@link HandlerMapping#URI_TEMPLATE_VARIABLES_ATTRIBUTE} 读 path 变量值，
 * 仅在 {@code @PathVariable} 没显式 name（如 {@code @PathVariable Long id} 而非
 * {@code @PathVariable("id") Long id}）时才回退到 parameter name 反射 —— 此时显式抛清晰异常，
 * 指引用户加 {@code -parameters} flag 或显式 name。
 * <p>
 * 仅处理 {@code @OpenId @PathVariable}，{@code @OpenId @RequestParam} 等仍走
 * {@link fun.commons.framework4j.openid.formatter.OpenIdFormatterFactory}。
 *
 * @since 2.2.0
 */
public class OpenIdPathVariableArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(OpenId.class)
                && parameter.hasParameterAnnotation(PathVariable.class)
                && OpenIdTypeUtils.isSupportedSingleType(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        boolean required = pathVariable == null || pathVariable.required();

        String varName = resolveVariableName(parameter, pathVariable);

        String rawValue = lookupUriVariable(webRequest, varName);
        if (rawValue == null) {
            if (required) {
                throw new ServletRequestBindingException(
                        "Missing path variable '" + varName + "'");
            }
            return null;
        }

        try {
            return OpenIdTypeUtils.convertFromOpenId(rawValue, parameter.getParameterType());
        } catch (ParseException e) {
            throw new IllegalArgumentException(
                    "Invalid @OpenId path variable '" + varName + "' value: " + rawValue
                            + " (expected 12-char OpenID or numeric string)", e);
        }
    }

    private String resolveVariableName(MethodParameter parameter, PathVariable pathVariable) {
        if (pathVariable != null && !pathVariable.value().isEmpty()) {
            return pathVariable.value();
        }
        String reflectedName;
        try {
            reflectedName = parameter.getParameterName();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Cannot resolve @PathVariable name. Either declare it explicitly "
                            + "(e.g. @PathVariable(\"id\")) or enable the '-parameters' javac flag "
                            + "(<parameters>true</parameters> in maven-compiler-plugin).", e);
        }
        if (reflectedName == null) {
            throw new IllegalStateException(
                    "Cannot resolve @PathVariable name (parameter name not available). "
                            + "Add <parameters>true</parameters> to maven-compiler-plugin "
                            + "or specify @PathVariable(\"name\") explicitly.");
        }
        return reflectedName;
    }

    @SuppressWarnings("unchecked")
    private String lookupUriVariable(NativeWebRequest webRequest, String name) {
        Object attr = webRequest.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST);
        if (attr == null) {
            return null;
        }
        Map<String, String> uriVars = (Map<String, String>) attr;
        return uriVars.get(name);
    }
}
