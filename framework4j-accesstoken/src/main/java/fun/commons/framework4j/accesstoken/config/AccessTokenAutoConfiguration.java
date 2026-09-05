package fun.commons.framework4j.accesstoken.config;


import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.accesstoken.core.RefreshTokenService;
import fun.commons.framework4j.accesstoken.interceptor.TokenInterceptor;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * AccessToken 自动配置。
 * <p>
 * v1.2.8 修复（下游 benefit4j 排查："claims → TokenContext 链路问题"，实际根因是注册缺失）：
 * {@code @Import} {@link AccessTokenWebMvcConfig}。此前该注册类有 {@code @Configuration}
 * 但既不在 {@code AutoConfiguration.imports}、也未被本类引入（与 idempotency v1.2.5 修复
 * 同构的孤儿类问题）—— {@code TokenInterceptor} Bean 创建了但永不进 MVC 拦截链，
 * {@code @RequiresToken} 不生效、{@code TokenContext} 永不填充（所有 {@code getClaim} 返回 null）。
 * claims → Redis → TokenContext 链路本身无故障（{@code WebIntegrationTest} 早已证明，
 * 只是测试自建了拦截器注册）。消费方不要自行注册 {@code TokenInterceptor}。
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({StringRedisTemplate.class, MultiRedisManager.class})
@EnableConfigurationProperties(AccessTokenProperties.class)
@ConditionalOnProperty(prefix = "framework4j.access-token", name = "enabled", havingValue = "true")
@Import(AccessTokenWebMvcConfig.class)
@Validated
public class AccessTokenAutoConfiguration {

    @Value("${spring.application.name:}")
    private String appName;

    /**
     * v1.6.0(Issue #20):配置完整性启动期 fail-fast —— secretKey/hashSalt/policies/key 语义
     * 全部在此校验,不再延迟到首次 generateToken 运行时才发现。
     */
    @Bean
    public AccessTokenConfigValidator.Marker accessTokenConfigValidator(AccessTokenProperties properties) {
        AccessTokenConfigValidator.validate(properties);
        log.info("【AccessToken】配置校验通过(policies 型别: {})",
                properties.getPolicies() == null ? "[]" : properties.getPolicies().keySet());
        return new AccessTokenConfigValidator.Marker();
    }

    /**
     * v2.1 P1 修复：抽 StringRedisTemplate 单例 Bean，避免三个 Bean 各自调 getStringRedisTemplate。
     * <p>原 accessTokenGenerator / refreshTokenService / tokenInterceptor 各自走 MultiRedisManager 解析链，
     * 同一 redisName 解析三次。改为单例后只解析一次，且保证三个组件使用同一实例。
     */
    @Bean
    @ConditionalOnMissingBean(name = "accessTokenStringRedisTemplate")
    public StringRedisTemplate accessTokenStringRedisTemplate(
            AccessTokenProperties properties, MultiRedisManager redisManager) {
        if (!StringUtils.hasText(appName)) {
            throw new IllegalStateException("framework4j.access-token.enabled=true 时必须配置 spring.application.name，"
                    + "用作 Redis key 前缀 + JWT iss claim");
        }
        String redisName = properties.getRedisName();
        log.info("【AccessToken】使用 Redis 数据源: {}", redisName);
        return redisManager.getStringRedisTemplate(redisName);
    }

    @Bean
    @ConditionalOnMissingBean
    public AccessTokenGenerator accessTokenGenerator(
            AccessTokenProperties properties,
            StringRedisTemplate accessTokenStringRedisTemplate) {
        AccessTokenGenerator generator = new AccessTokenGenerator(properties, accessTokenStringRedisTemplate, appName);
        log.info("【AccessToken】accessTokenGenerator，JWT+Redis双重验证的Token生成器，appName={}",
                appName);
        return generator;
    }

    @Bean
    @ConditionalOnMissingBean
    public RefreshTokenService refreshTokenService(
            AccessTokenProperties properties,
            AccessTokenGenerator generator,
            StringRedisTemplate accessTokenStringRedisTemplate) {
        RefreshTokenService service = new RefreshTokenService(properties, accessTokenStringRedisTemplate, generator, appName);
        log.info("【AccessToken】refreshTokenService，Refresh token family 管理器，appName={}", appName);
        return service;
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenInterceptor tokenInterceptor(
            AccessTokenGenerator generator,
            AccessTokenProperties properties,
            StringRedisTemplate accessTokenStringRedisTemplate) {
        TokenInterceptor interceptor = new TokenInterceptor(generator, properties, accessTokenStringRedisTemplate);
        log.info("【AccessToken】tokenInterceptor，Token验证拦截器，自动校验请求头中的AccessToken");
        return interceptor;
    }
}