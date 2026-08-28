package fun.commons.framework4j.tenant.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 租户域标注(中间件中台租户设计 §4/§5.3):标注的 Controller <strong>仅真实租户身份可达</strong>
 * —— token claim {@code tenant_id > 0}。
 * <p>
 * 平台身份(tenant_id==0)是管理面,不是记账主体:放行则资金类接口会把账记到
 * tenant_id=0 幽灵租户名下(§6.2 L1 语义)。平台身份访问得 403。
 * <p>
 * 同一 Controller 不得同时标注 {@code @PlatformDomain} 与 {@code @TenantDomain}(校验拦截,返回 403)。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantDomain {
}
