package fun.commons.framework4j.tenant.config;

import fun.commons.framework4j.tenant.interceptor.DomainGuardInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 三域守卫的 MVC 注册 —— <strong>注解只定规则,拦截器按 path 注册才生效</strong>
 * (benefit4j 踩坑:守卫逻辑有了但没进拦截链 = 形同虚设,故由模块自动注册)。
 * <p>
 * 顺序:LOWEST_PRECEDENCE,保证排在 accesstoken 的 TokenInterceptor 之后 ——
 * 先完成 token 校验/claims 填充,守卫才读得到 tenant_id。
 * 消费方不要自行注册 {@code DomainGuardInterceptor}(会双跑)。
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@AutoConfiguration(after = fun.commons.framework4j.accesstoken.config.AccessTokenWebMvcConfig.class)
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "framework4j.tenant", name = "enabled", havingValue = "true")
public class TenantWebMvcConfig implements WebMvcConfigurer {

    @Bean
    public DomainGuardInterceptor domainGuardInterceptor() {
        return new DomainGuardInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(domainGuardInterceptor())
                .addPathPatterns("/**")
                .order(Ordered.LOWEST_PRECEDENCE);
    }
}
