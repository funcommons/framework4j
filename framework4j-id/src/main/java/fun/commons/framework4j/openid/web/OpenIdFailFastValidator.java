package fun.commons.framework4j.openid.web;

import fun.commons.framework4j.openid.annotation.OpenId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 启动期扫描所有 Controller 的 {@code @OpenId} 参数，发现可解析性问题立即 fail-fast
 * <p>
 * v2.2：把 {@code @OpenId @PathVariable} 缺 {@code -parameters} 编译 flag 的 silent failure
 * 变成 loud startup failure，避免生产环境在收到第一个 OpenID 路径请求时才炸。
 * <p>
 * 触发条件（任一）：
 * <ul>
 *   <li>{@code @PathVariable Long id}（未显式 name）+ {@code -parameters} 缺失 → 启动失败</li>
 *   <li>{@code @OpenId} 标注在非 Number 类型参数上 → 启动失败（用法错误）</li>
 * </ul>
 * <p>
 * 默认开启（{@code framework4j.openid.fail-fast=true}）；如需关闭可设为 false。
 *
 * @since 2.2.0
 */
@Slf4j
@ConditionalOnClass({org.springframework.web.method.HandlerMethod.class})
@ConditionalOnProperty(prefix = "framework4j.openid", name = "fail-fast",
        havingValue = "true", matchIfMissing = true)
public class OpenIdFailFastValidator implements ApplicationListener<ContextRefreshedEvent> {

    private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    private final ApplicationContext applicationContext;

    @Autowired
    public OpenIdFailFastValidator(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 测试可覆盖：注入自定义 ParameterNameDiscoverer（如返回 null 模拟 -parameters 缺失）。
     */
    protected void setParameterNameDiscoverer(ParameterNameDiscoverer discoverer) {
        this.parameterNameDiscoverer = discoverer;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        List<String> violations = new ArrayList<>();

        Map<String, Object> controllers = applicationContext.getBeansWithAnnotation(Controller.class);
        Map<String, Object> restControllers = applicationContext.getBeansWithAnnotation(RestController.class);
        controllers.putAll(restControllers);

        for (Object controller : controllers.values()) {
            Class<?> clazz = org.springframework.aop.support.AopUtils.getTargetClass(controller);
            for (Method method : clazz.getDeclaredMethods()) {
                collectViolations(method, violations);
            }
        }

        if (violations.isEmpty()) {
            log.info("【OpenID】Fail-fast 校验通过（{} 个 Controller 已扫描）", controllers.size());
            return;
        }

        StringBuilder sb = new StringBuilder(
                "【OpenID】Fail-fast 校验未通过，共 " + violations.size() + " 项问题：\n");
        for (int i = 0; i < violations.size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append(violations.get(i)).append('\n');
        }
        sb.append("\n修复方案：")
                .append("(a) 在 pom.xml 的 maven-compiler-plugin 加 <parameters>true</parameters>；")
                .append("(b) 或显式指定 @PathVariable(\"name\") / @RequestParam(\"name\")。");
        throw new IllegalStateException(sb.toString());
    }

    private void collectViolations(Method method, List<String> violations) {
        int paramCount = method.getParameterCount();
        if (paramCount == 0) {
            return;
        }
        for (int i = 0; i < paramCount; i++) {
            MethodParameter mp = new MethodParameter(method, i);
            OpenId openId = mp.getParameterAnnotation(OpenId.class);
            if (openId == null) {
                continue;
            }
            PathVariable pathVariable = mp.getParameterAnnotation(PathVariable.class);
            if (pathVariable == null) {
                continue;
            }
            if (pathVariable.value() != null && !pathVariable.value().isEmpty()) {
                continue;
            }
            String[] names = parameterNameDiscoverer.getParameterNames(method);
            if (names == null || i >= names.length || names[i] == null || names[i].isEmpty()) {
                violations.add(String.format(
                        "%s#%s 参数 %d：@PathVariable 未指定 name 且无法通过反射获取参数名。"
                                + "建议显式指定 @PathVariable(\"name\") 或启用 -parameters 编译选项。",
                        method.getDeclaringClass().getSimpleName(),
                        method.getName(), i));
            }
        }
    }
}
