package fun.commons.framework4j.openid.web;

import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.openid.util.OpenIdTypeSupport;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 启动期扫描所有 Controller 的 {@code @OpenId} 用法，发现可解析性/误用问题立即 fail-fast。
 * <p>
 * 触发条件（任一���：
 * <ul>
 *   <li>{@code @PathVariable Long id}（未显式 name）+ {@code -parameters} 缺失 → 启动失败</li>
 *   <li>v1.3：{@code @RequestBody} DTO(含嵌套) 的 {@code @OpenId} 标在<b>未受理类型</b>上
 *       （如开关关闭时的 {@code Integer}/{@code String}、{@code Map}、任意对象）→ 启动失败。
 *       把"标了 @OpenId 却静默不生效"变成 loud failure，防迁移期手抖。</li>
 * </ul>
 * <p>受理类型由 {@link OpenIdTypeSupport} 决定(三开关)。默认开启（{@code framework4j.openid.fail-fast=true}）。
 *
 * @since 2.2.0（v1.3 加 body 字段误用扫描 + 信息打磨）
 */
@Slf4j
@ConditionalOnClass({org.springframework.web.method.HandlerMethod.class})
@ConditionalOnProperty(prefix = "framework4j.openid", name = "fail-fast",
        havingValue = "true", matchIfMissing = true)
public class OpenIdFailFastValidator implements ApplicationListener<ContextRefreshedEvent> {

    private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    private final ApplicationContext applicationContext;
    private final OpenIdTypeSupport typeSupport;

    @Autowired
    public OpenIdFailFastValidator(ApplicationContext applicationContext, OpenIdTypeSupport typeSupport) {
        this.applicationContext = applicationContext;
        this.typeSupport = typeSupport;
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
            log.info("【OpenID】Fail-fast 校验通过（{} 个 Controller 已扫描，受理标量: {}）",
                    controllers.size(), typeSupport.scalarTypes());
            return;
        }

        StringBuilder sb = new StringBuilder(
                "【OpenID】Fail-fast 校验未通过，共 " + violations.size() + " 项问题：\n");
        for (int i = 0; i < violations.size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append(violations.get(i)).append('\n');
        }
        sb.append("\n修复方案：")
                .append("(a) 缺 name：pom.xml 的 maven-compiler-plugin 加 <configuration><parameters>true</parameters></configuration>，")
                .append("或显式 @PathVariable(\"name\")；")
                .append("(b) 类型误用：@OpenId 仅受理 ")
                .append(typeSupport.scalarTypes())
                .append("（及其集合/数组）；把字段改 Long，或按需开启 support-integer / support-string 开关。");
        throw new IllegalStateException(sb.toString());
    }

    private void collectViolations(Method method, List<String> violations) {
        int paramCount = method.getParameterCount();
        if (paramCount == 0) {
            return;
        }
        String location = method.getDeclaringClass().getName() + "#" + method.getName();
        for (int i = 0; i < paramCount; i++) {
            MethodParameter mp = new MethodParameter(method, i);
            OpenId openId = mp.getParameterAnnotation(OpenId.class);
            // (1) @OpenId @PathVariable 缺 name（既有校验）
            if (openId != null && mp.getParameterAnnotation(PathVariable.class) != null) {
                collectPathVariableNameViolation(method, mp, i, location, violations);
            }
            // (2) v1.3：@RequestBody DTO 字段误用
            if (mp.getParameterAnnotation(RequestBody.class) != null) {
                scanBodyType(mp.getParameterType(), mp.getGenericParameterType(),
                        new HashSet<>(), location, violations);
            }
        }
    }

    private void collectPathVariableNameViolation(Method method, MethodParameter mp, int i,
                                                   String location, List<String> violations) {
        PathVariable pathVariable = mp.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null && pathVariable.value() != null && !pathVariable.value().isEmpty()) {
            return;
        }
        String[] names = parameterNameDiscoverer.getParameterNames(method);
        if (names == null || i >= names.length || names[i] == null || names[i].isEmpty()) {
            violations.add(String.format(
                    "%s 参数 %d：@PathVariable 未指定 name 且无法通过反射获取参数名。"
                            + "建议显式指定 @PathVariable(\"name\") 或启用 -parameters 编译选项。",
                    location, i));
        }
    }

    // ==================== v1.3 body 字段误用扫描 ====================

    private void scanBodyType(Class<?> rawType, Type genericType, Set<Class<?>> visited,
                              String location, List<String> violations) {
        if (rawType == null || !isBean(rawType) || !visited.add(rawType)) {
            return;
        }
        for (Field field : rawType.getDeclaredFields()) {
            if (field.isAnnotationPresent(OpenId.class) && !isSupportedFieldType(field.getGenericType())) {
                violations.add(String.format(
                        "%s @RequestBody %s 字段 '%s'：@OpenId 标在未受理类型 %s 上，将静默不生效。"
                                + "改为受支持标量(其集合/数组)，或开启对应开关。",
                        location, rawType.getName(), field.getName(), field.getGenericType().getTypeName()));
            }
            // 递归进入嵌套 bean / 集合元素的 bean 类型
            Class<?> nested = beanTypeToRecurse(field.getGenericType());
            if (nested != null) {
                scanBodyType(nested, nested, visited, location, violations);
            }
        }
    }

    /**
     * 字段(泛型感知)是否为受理的 @OpenId 类型:受支持标量,或其集合/数组。
     */
    private boolean isSupportedFieldType(Type t) {
        if (t instanceof Class<?> c) {
            if (c.isArray()) {
                return typeSupport.supportsScalar(c.getComponentType());
            }
            return typeSupport.supportsScalar(c);
        }
        if (t instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> raw) {
            if (Collection.class.isAssignableFrom(raw) && pt.getActualTypeArguments().length == 1
                    && pt.getActualTypeArguments()[0] instanceof Class<?> elem) {
                return typeSupport.supportsScalar(elem);
            }
        }
        if (t instanceof GenericArrayType gat) {
            // 罕见：泛型数组，保守视为不支持
            return false;
        }
        return false;
    }

    /**
     * 该字段类型若为可递归的 bean（嵌套对象，或集合/数组的元素是 bean），返回该 bean Class；否则 null。
     */
    private Class<?> beanTypeToRecurse(Type t) {
        if (t instanceof Class<?> c) {
            if (c.isArray()) {
                return isBean(c.getComponentType()) ? c.getComponentType() : null;
            }
            return isBean(c) ? c : null;
        }
        if (t instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> raw
                && Collection.class.isAssignableFrom(raw)
                && pt.getActualTypeArguments().length == 1
                && pt.getActualTypeArguments()[0] instanceof Class<?> elem) {
            return isBean(elem) ? elem : null;
        }
        return null;
    }

    private boolean isBean(Class<?> c) {
        if (c == null || c.isPrimitive() || c.isArray() || c.isEnum()) {
            return false;
        }
        if (Collection.class.isAssignableFrom(c) || Map.class.isAssignableFrom(c)) {
            return false;
        }
        if (c == String.class || c == Object.class
                || Number.class.isAssignableFrom(c) || c == Boolean.class || c == Character.class) {
            return false;
        }
        if (c.getName().startsWith("java.")) {
            return false;
        }
        return true;
    }
}
