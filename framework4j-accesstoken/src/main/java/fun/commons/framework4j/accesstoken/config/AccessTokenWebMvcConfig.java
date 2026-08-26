package fun.commons.framework4j.accesstoken.config;

import fun.commons.framework4j.accesstoken.interceptor.TokenInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * v2.1: 加 @ConditionalOnClass + @ConditionalOnWebApplication(SERVLET)，
 * 防止纯 WebFlux 或非 Web 应用引入 starter 时 WebMvcConfigurer 加载失败。
 */
@Slf4j
@Configuration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "framework4j.access-token", name = "enabled", havingValue = "true")
public class AccessTokenWebMvcConfig implements WebMvcConfigurer {

    private final TokenInterceptor tokenInterceptor;
    private final AccessTokenProperties properties;

    public AccessTokenWebMvcConfig(TokenInterceptor tokenInterceptor, AccessTokenProperties properties) {
        this.tokenInterceptor = tokenInterceptor;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // v1.4.1（Issue #17）：显式配置空 path-patterns 时跳过拦截器注册。
        // 原 addPathPatterns(空列表) 的 Spring 语义为拦截 /**（"空=全拦"与直觉相反，
        // 测试环境误配后整站 401）。默认值即 /**，想拦截全部请显式配置或移除该配置项。
        if (CollectionUtils.isEmpty(properties.getPathPatterns())) {
            log.warn("[AccessToken] framework4j.access-token.path-patterns 为空列表，跳过 TokenInterceptor 注册（不拦截任何路径，exclude-path-patterns 亦不生效）。"
                    + "如需拦截全部路径请配置 path-patterns: [\"/**\"]（默认值）或删除该配置；"
                    + "如需关闭模块请使用 framework4j.access-token.enabled=false");
            return;
        }
        var registration = registry.addInterceptor(tokenInterceptor)
                .addPathPatterns(properties.getPathPatterns());
        if (!CollectionUtils.isEmpty(properties.getExcludePathPatterns())) {
            registration.excludePathPatterns(properties.getExcludePathPatterns());
        }
    }
}
