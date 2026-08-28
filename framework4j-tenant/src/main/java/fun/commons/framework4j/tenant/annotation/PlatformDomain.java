package fun.commons.framework4j.tenant.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 平台域标注(中间件中台租户设计 §4/§5.3):标注的 Controller <strong>仅平台身份可达</strong>
 * —— token claim {@code tenant_id == 0}(平台合成租户,平台密钥直签)。
 * <p>
 * 用于租户管理、平台配置等管理面接口。真实租户身份(tenant_id&gt;0)访问得 403。
 * <p>
 * 注意:注解只声明规则,校验由 {@code DomainGuardInterceptor} 执行 ——
 * 模块已自动按路径注册拦截器,消费方<strong>不要</strong>自行注册(benefit4j 踩坑:注解≠自动触发)。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PlatformDomain {
}
