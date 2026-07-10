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
        return new CacheService(cacheStringRedisTemplate, properties, singleFlightService, appName);
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

    /**
     * v2.2 P1: 跨实例 L1 失效广播订阅。
     * <p>本进程 evict 时向 Redis Pub/Sub 发 {@code <keyPrefix>:invalidate} 消息；
     * 所有实例（包括自己）订阅收到后清各自 L1。
     * <p>关闭方式：{@code framework4j.cache.l1.broadcast-evict=false}（仅清本进程 L1，多实例可能短暂读到旧值）。
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "framework4j.cache.l1", name = "broadcast-evict", havingValue = "true", matchIfMissing = true)
    public org.springframework.data.redis.listener.RedisMessageListenerContainer cacheInvalidateListener(
            StringRedisTemplate cacheStringRedisTemplate,
            CacheProperties properties,
            CacheService cacheService) {
        // P2-1: channel 含 appName 隔离（防多应用共 Redis 时 A 的 L1 失效触发 B 回调）
        String channel = properties.getKeyPrefix() + ":" + appName + properties.getL1().getBroadcastChannelSuffix();
        org.springframework.data.redis.listener.RedisMessageListenerContainer container =
                new org.springframework.data.redis.listener.RedisMessageListenerContainer();
        container.setConnectionFactory(cacheStringRedisTemplate.getRequiredConnectionFactory());
        org.springframework.data.redis.listener.Topic topic = new org.springframework.data.redis.listener.ChannelTopic(channel);
        org.springframework.data.redis.connection.MessageListener listener = new org.springframework.data.redis.connection.MessageListener() {
            @Override
            public void onMessage(org.springframework.data.redis.connection.Message message, byte[] pattern) {
                String fullKey = new String(message.getBody());
                int colon = fullKey.indexOf(':');
                if (colon < 0) {
                    log.warn("[Cache] invalid invalidate message: {}", fullKey);
                    return;
                }
                String prefix = fullKey.substring(0, colon);
                String key = fullKey.substring(colon + 1);
                // 收到广播后清本实例 L1（不删 L2 — L2 已由发布方删除）
                try {
                    cacheService.evictLocalL1Only(prefix, key);
                } catch (Exception e) {
                    log.warn("[Cache] L1 evict from broadcast failed: {}", e.getMessage());
                }
            }
        };
        container.addMessageListener(listener, topic);
        log.info("【Cache】订阅失效广播 channel={}（跨实例 L1 一致性）", channel);
        return container;
    }
}
