package fun.commons.framework4j.openid.web;

import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.openid.util.OpenIdTypeSupport;
import fun.commons.framework4j.openid.util.OpenIdValueCodec;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * 处理 {@code @OpenId @PathVariable} 的参数解析器(v1.3 三开关:受理类型由 {@link OpenIdTypeSupport} 决定)。
 * <p>
 * v2.2:替代依赖 {@code OpenIdFormatterFactory} 的 ConversionService 路径,绕过 Spring
 * {@code MethodParameter.getParameterName()} 反射(要求 javac {@code -parameters} flag)。
 * 直接从 {@link HandlerMapping#URI_TEMPLATE_VARIABLES_ATTRIBUTE} 读 path 变量值。
 * <p>
 * 解码经 {@link OpenIdValueCodec} 以 Long 为枢轴:text → Long → 目标类型(Long/Integer/String)。
 *
 * @since 2.2.0(v1.3 接入三开关)
 */
public class OpenIdPathVariableArgumentResolver implements HandlerMethodArgumentResolver {

    private final OpenIdTypeSupport typeSupport;

    public OpenIdPathVariableArgumentResolver(OpenIdTypeSupport typeSupport) {
        this.typeSupport = typeSupport;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(OpenId.class)
                && parameter.hasParameterAnnotation(PathVariable.class)
                && typeSupport.supportsScalar(parameter.getParameterType());
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
                throw new ServletRequestBindingException("Missing path variable '" + varName + "'");
            }
            return null;
        }

        Class<?> targetType = parameter.getParameterType();
        try {
            Long pivoted = OpenIdValueCodec.decodeTextToLong(rawValue, typeSupport.isAcceptNumericFallback());
            if (pivoted == null) {
                return null;
            }
            return OpenIdValueCodec.convertLongToTarget(pivoted, targetType);
        } catch (RuntimeException e) {
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
