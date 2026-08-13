package fun.commons.framework4j.openid.web;

import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.openid.util.OpenIdTypeSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 验证 {@link OpenIdPathVariableArgumentResolver} 的解析行为
 * <p>
 * 重点覆盖 GitHub Issue #1 修复：绕过 {@code MethodParameter.getParameterName()} 反射，
 * 让 {@code @PathVariable("id")} 显式 name 的场景不依赖 {@code -parameters} 编译 flag。
 */
@DisplayName("OpenIdPathVariableArgumentResolver 单元测试")
class OpenIdPathVariableArgumentResolverTest {

    private OpenIdPathVariableArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OpenIdPathVariableArgumentResolver(OpenIdTypeSupport.builder().integer(true).build());
    }

    // ============ supportsParameter ============

    @Test
    @DisplayName("supportsParameter: @OpenId + @PathVariable + Long → true")
    void supports_openIdPathVariableLong() throws Exception {
        MethodParameter mp = methodParameter("explicitName", Long.class);
        assertThat(resolver.supportsParameter(mp)).isTrue();
    }

    @Test
    @DisplayName("supportsParameter: @OpenId + @PathVariable + Integer → true")
    void supports_openIdPathVariableInteger() throws Exception {
        MethodParameter mp = methodParameter("integerParam", Integer.class);
        assertThat(resolver.supportsParameter(mp)).isTrue();
    }

    @Test
    @DisplayName("supportsParameter: 仅 @PathVariable 无 @OpenId → false")
    void supports_pathVariableOnly() throws Exception {
        MethodParameter mp = methodParameter("noOpenId", Long.class);
        assertThat(resolver.supportsParameter(mp)).isFalse();
    }

    @Test
    @DisplayName("supportsParameter: @OpenId 无 @PathVariable → false")
    void supports_openIdOnly() throws Exception {
        MethodParameter mp = methodParameter("openIdWithoutPath", Long.class);
        assertThat(resolver.supportsParameter(mp)).isFalse();
    }

    @Test
    @DisplayName("supportsParameter: @OpenId @PathVariable String 类型 → false（非 Number）")
    void supports_stringType() throws Exception {
        MethodParameter mp = methodParameter("stringType", String.class);
        assertThat(resolver.supportsParameter(mp)).isFalse();
    }

    // ============ resolveArgument: 显式 @PathVariable("id") ============

    @Test
    @DisplayName("resolveArgument: @PathVariable(\"id\") + OpenID 字符串 → 还原 Long（不依赖 -parameters）")
    void resolve_explicitName_openIdString_restoredToLong() throws Exception {
        long raw = 123456789L;
        String openId = IdObfuscator.toOpenId(raw);
        MethodParameter mp = methodParameter("explicitName", Long.class);
        NativeWebRequest request = webRequestWithUriVars(Map.of("id", openId));

        Object result = resolver.resolveArgument(mp, mock(ModelAndViewContainer.class),
                request, mock(WebDataBinderFactory.class));

        assertThat(result).isInstanceOf(Long.class).isEqualTo(raw);
    }

    @Test
    @DisplayName("resolveArgument: @PathVariable(\"id\") + 纯数字字符串 → 兼容还原 Long")
    void resolve_explicitName_numericString_compatibleLong() throws Exception {
        MethodParameter mp = methodParameter("explicitName", Long.class);
        NativeWebRequest request = webRequestWithUriVars(Map.of("id", "987654321"));

        Object result = resolver.resolveArgument(mp, mock(ModelAndViewContainer.class),
                request, mock(WebDataBinderFactory.class));

        assertThat(result).isEqualTo(987654321L);
    }

    @Test
    @DisplayName("resolveArgument: @PathVariable(\"id\") + Integer 类型 → 还原为 int")
    void resolve_explicitName_integerType() throws Exception {
        MethodParameter mp = methodParameter("integerParam", Integer.class);
        NativeWebRequest request = webRequestWithUriVars(Map.of("num", "42"));

        Object result = resolver.resolveArgument(mp, mock(ModelAndViewContainer.class),
                request, mock(WebDataBinderFactory.class));

        assertThat(result).isInstanceOf(Integer.class).isEqualTo(42);
    }

    @Test
    @DisplayName("resolveArgument: 非法 OpenID 字符串 → IllegalArgumentException")
    void resolve_invalidOpenId_throwsIAE() throws Exception {
        MethodParameter mp = methodParameter("explicitName", Long.class);
        NativeWebRequest request = webRequestWithUriVars(Map.of("id", "!!!invalid!!!"));

        assertThatThrownBy(() -> resolver.resolveArgument(mp,
                mock(ModelAndViewContainer.class), request, mock(WebDataBinderFactory.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid @OpenId path variable");
    }

    @Test
    @DisplayName("resolveArgument: 缺失 path 变量且 required=true → ServletRequestBindingException")
    void resolve_missingVar_required_throws() throws Exception {
        MethodParameter mp = methodParameter("explicitName", Long.class);
        NativeWebRequest request = webRequestWithUriVars(Map.of());

        assertThatThrownBy(() -> resolver.resolveArgument(mp,
                mock(ModelAndViewContainer.class), request, mock(WebDataBinderFactory.class)))
                .isInstanceOf(org.springframework.web.bind.ServletRequestBindingException.class)
                .hasMessageContaining("Missing path variable");
    }

    @Test
    @DisplayName("resolveArgument: required=false 且缺失 → 返回 null")
    void resolve_optionalMissing_returnsNull() throws Exception {
        MethodParameter mp = methodParameter("optionalParam", Long.class);
        NativeWebRequest request = webRequestWithUriVars(Map.of());

        Object result = resolver.resolveArgument(mp, mock(ModelAndViewContainer.class),
                request, mock(WebDataBinderFactory.class));

        assertThat(result).isNull();
    }

    // ============ 辅助方法 ============

    static class SampleController {
        public void explicitName(@OpenId @PathVariable("id") Long id) {}
        public void integerParam(@OpenId @PathVariable("num") Integer num) {}
        public void optionalParam(@OpenId @PathVariable(value = "opt", required = false) Long opt) {}
        public void noOpenId(@PathVariable("id") Long id) {}
        public void openIdWithoutPath(@OpenId Long body) {}
        public void stringType(@OpenId @PathVariable("id") String id) {}
    }

    private MethodParameter methodParameter(String methodName, Class<?> paramType) throws Exception {
        Method method = SampleController.class.getDeclaredMethod(methodName, paramType);
        return new MethodParameter(method, 0);
    }

    private NativeWebRequest webRequestWithUriVars(Map<String, String> uriVars) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        ServletWebRequest webRequest = new ServletWebRequest(servletRequest);
        webRequest.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriVars,
                RequestAttributes.SCOPE_REQUEST);
        return webRequest;
    }
}
