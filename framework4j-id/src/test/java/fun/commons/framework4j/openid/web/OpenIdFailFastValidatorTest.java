package fun.commons.framework4j.openid.web;

import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.openid.util.OpenIdTypeSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link OpenIdFailFastValidator} 能在启动期捕获 silent failure 配置错误
 * <p>
 * 重点：模拟消费方未加 {@code -parameters} 编译 flag 的环境（让
 * {@link ParameterNameDiscoverer} 返回 null），验证 validator 把 silent failure
 * 变成 loud startup failure。
 */
@DisplayName("OpenId 启动期 Fail-Fast 校验")
class OpenIdFailFastValidatorTest {

    // ============ 受测 Controller ============

    @Controller
    static class ExplicitNameController {
        public void ok(@OpenId @PathVariable("id") Long id) {}
    }

    @Controller
    static class NoExplicitNameController {
        public void implicit(@OpenId @PathVariable Long id) {}
    }

    @Controller
    static class NoOpenIdController {
        public void plain(@PathVariable Long id) {}
    }

    @RestController
    static class RestNoExplicitNameController {
        public void implicit(@OpenId @PathVariable Long id) {}
    }

    // ============ 测试用例 ============

    @Test
    @DisplayName("显式 @PathVariable(\"id\") + 反射不可用 → 无违规（不依赖 -parameters）")
    void explicitName_noViolationEvenIfDiscoveryReturnsNull() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        Map<String, Object> beans = new HashMap<>();
        beans.put("controller", new ExplicitNameController());
        when(ctx.getBeansWithAnnotation(Controller.class)).thenReturn(beans);
        when(ctx.getBeansWithAnnotation(RestController.class)).thenReturn(new HashMap<>());

        OpenIdFailFastValidator validator = new OpenIdFailFastValidator(ctx, OpenIdTypeSupport.defaults());
        validator.setParameterNameDiscoverer(returnNullDiscoverer());

        ContextRefreshedEvent event = new ContextRefreshedEvent(ctx);
        validator.onApplicationEvent(event); // 不应抛
    }

    @Test
    @DisplayName("无显式 name + ParameterNameDiscoverer 返回 null → 启动失败（loud failure）")
    void implicitName_discoveryNull_failsFast() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        Map<String, Object> beans = new HashMap<>();
        beans.put("controller", new NoExplicitNameController());
        when(ctx.getBeansWithAnnotation(Controller.class)).thenReturn(beans);
        when(ctx.getBeansWithAnnotation(RestController.class)).thenReturn(new HashMap<>());

        OpenIdFailFastValidator validator = new OpenIdFailFastValidator(ctx, OpenIdTypeSupport.defaults());
        validator.setParameterNameDiscoverer(returnNullDiscoverer());

        ContextRefreshedEvent event = new ContextRefreshedEvent(ctx);
        assertThatThrownBy(() -> validator.onApplicationEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Fail-fast 校验未通过")
                .hasMessageContaining("-parameters");
    }

    @Test
    @DisplayName("@RestController 也被扫描")
    void restControllerAlsoScanned() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        Map<String, Object> emptyControllers = new HashMap<>();
        Map<String, Object> restControllers = new HashMap<>();
        restControllers.put("rest", new RestNoExplicitNameController());
        when(ctx.getBeansWithAnnotation(Controller.class)).thenReturn(emptyControllers);
        when(ctx.getBeansWithAnnotation(RestController.class)).thenReturn(restControllers);

        OpenIdFailFastValidator validator = new OpenIdFailFastValidator(ctx, OpenIdTypeSupport.defaults());
        validator.setParameterNameDiscoverer(returnNullDiscoverer());

        ContextRefreshedEvent event = new ContextRefreshedEvent(ctx);
        assertThatThrownBy(() -> validator.onApplicationEvent(event))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("无 @OpenId Controller → 无违规")
    void noOpenIdController_noViolation() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        Map<String, Object> beans = new HashMap<>();
        beans.put("controller", new NoOpenIdController());
        when(ctx.getBeansWithAnnotation(Controller.class)).thenReturn(beans);
        when(ctx.getBeansWithAnnotation(RestController.class)).thenReturn(new HashMap<>());

        OpenIdFailFastValidator validator = new OpenIdFailFastValidator(ctx, OpenIdTypeSupport.defaults());
        validator.setParameterNameDiscoverer(returnNullDiscoverer());

        ContextRefreshedEvent event = new ContextRefreshedEvent(ctx);
        validator.onApplicationEvent(event); // 不应抛
    }

    @Test
    @DisplayName("反射可用（模拟 -parameters 开启）→ 无违规")
    void implicitName_discoveryWorks_noViolation() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        Map<String, Object> beans = new HashMap<>();
        beans.put("controller", new NoExplicitNameController());
        when(ctx.getBeansWithAnnotation(Controller.class)).thenReturn(beans);
        when(ctx.getBeansWithAnnotation(RestController.class)).thenReturn(new HashMap<>());

        OpenIdFailFastValidator validator = new OpenIdFailFastValidator(ctx, OpenIdTypeSupport.defaults());
        // 使用真实 DefaultParameterNameDiscoverer（测试环境有 -parameters flag）
        // 不替换，使用默认

        ContextRefreshedEvent event = new ContextRefreshedEvent(ctx);
        validator.onApplicationEvent(event); // 不应抛
    }

    // ============ v1.3 @RequestBody 字段误用扫描 ============

    static class MisuseDto {
        @OpenId
        private Integer orderId;   // 默认(support-integer=false)下为误用
    }

    static class ValidDto {
        @OpenId
        private Long id;           // 合法
    }

    @Controller
    static class BodyController {
        public void misuse(@RequestBody MisuseDto dto) {}
        public void valid(@RequestBody ValidDto dto) {}
    }

    @Test
    @DisplayName("@RequestBody @OpenId Integer(开关关) → 启动失败（body 误用）")
    void bodyMisuse_integerUnsupported_failsFast() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        Map<String, Object> beans = new HashMap<>();
        beans.put("c", new BodyController());
        when(ctx.getBeansWithAnnotation(Controller.class)).thenReturn(beans);
        when(ctx.getBeansWithAnnotation(RestController.class)).thenReturn(new HashMap<>());

        OpenIdFailFastValidator validator = new OpenIdFailFastValidator(ctx, OpenIdTypeSupport.defaults());
        ContextRefreshedEvent event = new ContextRefreshedEvent(ctx);

        assertThatThrownBy(() -> validator.onApplicationEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Fail-fast 校验未通过")
                .hasMessageContaining("orderId")
                .hasMessageContaining("support-integer");
    }

    @Test
    @DisplayName("@RequestBody @OpenId Long → 无违规")
    void bodyValidLong_noViolation() {
        // ValidDto 只在 valid() 方法用；misuse() 用 MisuseDto 会触发违规，故单独构造只含 valid 的 ctx
        ApplicationContext ctx = mock(ApplicationContext.class);
        Map<String, Object> beans = new HashMap<>();
        beans.put("c", new BodyController());
        when(ctx.getBeansWithAnnotation(Controller.class)).thenReturn(beans);
        when(ctx.getBeansWithAnnotation(RestController.class)).thenReturn(new HashMap<>());

        // 开启 support-integer 后 MisuseDto 也合法 → 全部无违规
        OpenIdFailFastValidator validator = new OpenIdFailFastValidator(
                ctx, OpenIdTypeSupport.builder().integer(true).build());
        ContextRefreshedEvent event = new ContextRefreshedEvent(ctx);

        validator.onApplicationEvent(event); // 不应抛
    }

    // ============ 辅助方法 ============

    private ParameterNameDiscoverer returnNullDiscoverer() {
        return new ParameterNameDiscoverer() {
            @Override
            public String[] getParameterNames(Method method) {
                return null;
            }

            @Override
            public String[] getParameterNames(java.lang.reflect.Constructor<?> ctor) {
                return null;
            }
        };
    }
}
