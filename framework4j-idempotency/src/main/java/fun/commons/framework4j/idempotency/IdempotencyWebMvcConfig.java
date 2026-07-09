package fun.commons.framework4j.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Idempotency-Key 拦截器注册
 * <p>
 * v2.1: 路径模式可配置（原硬编码 /api/**）。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "framework4j.idempotency", name = "enabled", havingValue = "true")
public class IdempotencyWebMvcConfig implements WebMvcConfigurer {

    private final IdempotencyInterceptor idempotencyInterceptor;
    private final IdempotencyProperties properties;

    public IdempotencyWebMvcConfig(IdempotencyInterceptor idempotencyInterceptor, IdempotencyProperties properties) {
        this.idempotencyInterceptor = idempotencyInterceptor;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        var registration = registry.addInterceptor(idempotencyInterceptor)
                .addPathPatterns(properties.getPathPatterns());
        if (!CollectionUtils.isEmpty(properties.getExcludePathPatterns())) {
            registration.excludePathPatterns(properties.getExcludePathPatterns());
        }
        log.info("【Idempotency】注册 Idempotency-Key 拦截器，路径 {}", properties.getPathPatterns());
    }
}
