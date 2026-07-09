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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Slf4j
@AutoConfiguration
@ConditionalOnClass({StringRedisTemplate.class, MultiRedisManager.class})
@EnableConfigurationProperties(AccessTokenProperties.class)
@ConditionalOnProperty(prefix = "framework4j.access-token", name = "enabled", havingValue = "true")
@Validated
public class AccessTokenAutoConfiguration {

    @Value("${spring.application.name:}")
    private String appName;

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