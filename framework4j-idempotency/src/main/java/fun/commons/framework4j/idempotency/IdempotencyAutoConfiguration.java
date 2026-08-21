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
import org.springframework.context.annotation.Import;
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
 * <p>
 * v1.2.5 修复（GitHub Issue：下游 benefit4j 排查报告 #5）：
 * {@code @Import} {@link IdempotencyWebMvcConfig}。此前该注册类既不在
 * {@code AutoConfiguration.imports}、也未被本类引入 —— v2.1 从 @Component 扫描
 * 迁移到显式 @Bean 时被漏挂，导致拦截器 Bean 创建了但永不进 MVC 拦截链，
 * 幂等校验形同虚设。现与同库 RateLimit 的 rateLimitWebMvcConfig 对齐。
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({StringRedisTemplate.class, ObjectMapper.class})
@EnableConfigurationProperties(IdempotencyProperties.class)
@ConditionalOnProperty(prefix = "framework4j.idempotency", name = "enabled", havingValue = "true")
@Import(IdempotencyWebMvcConfig.class)
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
