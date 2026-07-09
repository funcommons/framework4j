package fun.commons.framework4j.signature.config;

import fun.commons.framework4j.redis.manager.MultiRedisManager;
import fun.commons.framework4j.signature.interceptor.SignatureInterceptor;
import fun.commons.framework4j.signature.service.InMemorySecretProvider;
import fun.commons.framework4j.signature.service.SecretProvider;
import fun.commons.framework4j.signature.service.SignatureService;
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
 * framework4j-signature 自动装配
 *
 * @since 2.1.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({StringRedisTemplate.class, MultiRedisManager.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "framework4j.signature", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SignatureProperties.class)
public class SignatureAutoConfiguration {

    @Value("${spring.application.name:}")
    private String appName;

    /**
     * 共享 StringRedisTemplate Bean（与 AccessTokenAutoConfiguration 模式一致）
     */
    @Bean
    @ConditionalOnMissingBean(name = "signatureStringRedisTemplate")
    public StringRedisTemplate signatureStringRedisTemplate(
            SignatureProperties properties, MultiRedisManager redisManager) {
        if (!StringUtils.hasText(appName)) {
            throw new IllegalStateException("framework4j.signature.enabled=true 时必须配置 spring.application.name");
        }
        String redisName = properties.getRedisName();
        log.info("【Signature】使用 Redis 数据源: {}", redisName);
        return redisManager.getStringRedisTemplate(redisName);
    }

    /**
     * 默认 SecretProvider（InMemory，开发/测试用）
     */
    @Bean
    @ConditionalOnMissingBean
    public SecretProvider secretProvider() {
        log.info("【Signature】使用默认 InMemorySecretProvider（生产环境应替换为 DB/配置中心版本）");
        return new InMemorySecretProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public SignatureService signatureService(
            StringRedisTemplate signatureStringRedisTemplate,
            SignatureProperties properties,
            SecretProvider secretProvider) {
        return new SignatureService(signatureStringRedisTemplate, properties, secretProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public SignatureInterceptor signatureInterceptor(
            SignatureService signatureService, SignatureProperties properties) {
        return new SignatureInterceptor(signatureService, properties);
    }

    /**
     * 拦截器注册（WebMvcConfigurer）
     */
    @Bean
    @ConditionalOnMissingBean(name = "signatureWebMvcConfig")
    public WebMvcConfigurer signatureWebMvcConfig(
            SignatureInterceptor signatureInterceptor, SignatureProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                if (properties.getPathPatterns() == null || properties.getPathPatterns().isEmpty()) {
                    log.info("【Signature】未配置 path-patterns，签名拦截器不拦截任何路径");
                    return;
                }
                registry.addInterceptor(signatureInterceptor)
                        .addPathPatterns(properties.getPathPatterns().toArray(new String[0]))
                        .excludePathPatterns(properties.getExcludePathPatterns().toArray(new String[0]));
                log.info("【Signature】注册拦截器 path={} exclude={}",
                        properties.getPathPatterns(), properties.getExcludePathPatterns());
            }
        };
    }
}
