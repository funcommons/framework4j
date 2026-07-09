package fun.commons.framework4j.cache.config;

import fun.commons.framework4j.cache.service.CacheService;
import fun.commons.framework4j.cache.service.SingleFlightService;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * framework4j-cache 自动装配
 *
 * @since 2.1.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({StringRedisTemplate.class, MultiRedisManager.class,
        com.github.benmanes.caffeine.cache.Cache.class})
@ConditionalOnProperty(prefix = "framework4j.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CacheProperties.class)
public class CacheAutoConfiguration {

    @Value("${spring.application.name:}")
    private String appName;

    @Bean
    @ConditionalOnMissingBean(name = "cacheStringRedisTemplate")
    public StringRedisTemplate cacheStringRedisTemplate(
            CacheProperties properties, MultiRedisManager redisManager) {
        if (!StringUtils.hasText(appName)) {
            throw new IllegalStateException("framework4j.cache.enabled=true 时必须配置 spring.application.name");
        }
        log.info("【Cache】使用 Redis 数据源: {}", properties.getRedisName());
        return redisManager.getStringRedisTemplate(properties.getRedisName());
    }

    @Bean
    @ConditionalOnMissingBean
    public SingleFlightService singleFlightService(
            StringRedisTemplate cacheStringRedisTemplate, CacheProperties properties) {
        return new SingleFlightService(cacheStringRedisTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheService cacheService(
            StringRedisTemplate cacheStringRedisTemplate,
            CacheProperties properties,
            SingleFlightService singleFlightService) {
        log.info("【Cache】CacheService 已启用（L1={}, 单飞={}, 防穿透空值 TTL={}s）",
                properties.getL1().isEnabled(),
                properties.getSingleFlight().isEnabled(),
                properties.getNullTtlSeconds());
        return new CacheService(cacheStringRedisTemplate, properties, singleFlightService);
    }

    /**
     * v2.1 P0: 缓存注解 AOP 切面（@CacheableGet/Put/Evict）
     * <p>需 spring-boot-starter-aop 在类路径上（与 audit 模块一致）
     */
    @Bean
    @ConditionalOnMissingBean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(
            name = "org.aspectj.lang.ProceedingJoinPoint")
    public fun.commons.framework4j.cache.aspect.CacheableAspect cacheableAspect(CacheService cacheService) {
        log.info("【Cache】CacheableAspect 已注册（注解驱动 @CacheableGet/Put/Evict）");
        return new fun.commons.framework4j.cache.aspect.CacheableAspect(cacheService);
    }
}
