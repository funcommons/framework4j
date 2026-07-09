package fun.commons.framework4j.ratelimit.config;

import fun.commons.framework4j.ratelimit.interceptor.RateLimitInterceptor;
import fun.commons.framework4j.ratelimit.service.RateLimitKeyResolver;
import fun.commons.framework4j.ratelimit.service.RateLimitService;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * framework4j-rate-limit 自动装配
 *
 * @since 2.1.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({StringRedisTemplate.class, MultiRedisManager.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "framework4j.rate-limit", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitAutoConfiguration {

    @Value("${spring.application.name:}")
    private String appName;

    @Bean
    @ConditionalOnMissingBean(name = "rateLimitStringRedisTemplate")
    public StringRedisTemplate rateLimitStringRedisTemplate(
            RateLimitProperties properties, MultiRedisManager redisManager) {
        if (!StringUtils.hasText(appName)) {
            throw new IllegalStateException("framework4j.rate-limit.enabled=true 时必须配置 spring.application.name");
        }
        log.info("【RateLimit】使用 Redis 数据源: {}", properties.getRedisName());
        return redisManager.getStringRedisTemplate(properties.getRedisName());
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitService rateLimitService(StringRedisTemplate rateLimitStringRedisTemplate) {
        return new RateLimitService(rateLimitStringRedisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitKeyResolver rateLimitKeyResolver(RateLimitProperties properties) {
        return new RateLimitKeyResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitInterceptor rateLimitInterceptor(
            RateLimitService rateLimitService,
            RateLimitKeyResolver keyResolver,
            RateLimitProperties properties) {
        return new RateLimitInterceptor(rateLimitService, keyResolver, properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "rateLimitWebMvcConfig")
    public WebMvcConfigurer rateLimitWebMvcConfig(
            RateLimitInterceptor rateLimitInterceptor, RateLimitProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                if (properties.getPathPatterns() == null || properties.getPathPatterns().isEmpty()) {
                    log.info("【RateLimit】未配置 path-patterns，限流拦截器不拦截任何路径（仅 @RateLimit 注解触发）");
                    registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/**");
                    return;
                }
                registry.addInterceptor(rateLimitInterceptor)
                        .addPathPatterns(properties.getPathPatterns().toArray(new String[0]))
                        .excludePathPatterns(properties.getExcludePathPatterns().toArray(new String[0]));
                log.info("【RateLimit】注册拦截器 path={} exclude={}",
                        properties.getPathPatterns(), properties.getExcludePathPatterns());
            }
        };
    }
}
