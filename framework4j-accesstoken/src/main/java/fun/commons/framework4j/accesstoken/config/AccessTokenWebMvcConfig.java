package fun.commons.framework4j.accesstoken.config;

import fun.commons.framework4j.accesstoken.interceptor.TokenInterceptor;
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
        var registration = registry.addInterceptor(tokenInterceptor)
                .addPathPatterns(properties.getPathPatterns());
        if (!CollectionUtils.isEmpty(properties.getExcludePathPatterns())) {
            registration.excludePathPatterns(properties.getExcludePathPatterns());
        }
    }
}
