package fun.commons.framework4j.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Idempotency-Key 自动配置
 * <p>
 * v2.1 修复：
 * <ul>
 *   <li>BodyCacheFilter 改为 @Bean 注册（受 enabled 开关控制，原 @Component 绕过开关）</li>
 *   <li>ObjectMapper 由容器注入（与 WebConfig 全局策略一致）</li>
 *   <li>加 @ConditionalOnClass 防御</li>
 * </ul>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({StringRedisTemplate.class, ObjectMapper.class})
@EnableConfigurationProperties(IdempotencyProperties.class)
@ConditionalOnProperty(prefix = "framework4j.idempotency", name = "enabled", havingValue = "true")
public class IdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyInterceptor idempotencyInterceptor(
            IdempotencyProperties properties,
            MultiRedisManager redisManager,
            ObjectMapper objectMapper) {
        String redisName = properties.getRedisName();
        log.info("【Idempotency】从 Redis 数据源 '{}' 加载 StringRedisTemplate", redisName);
        StringRedisTemplate redisTemplate = redisManager.getStringRedisTemplate(redisName);
        return new IdempotencyInterceptor(redisTemplate, properties, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyBodyCacheFilter idempotencyBodyCacheFilter() {
        log.info("【Idempotency】idempotencyBodyCacheFilter，缓存 request + response body");
        return new IdempotencyBodyCacheFilter();
    }
}
