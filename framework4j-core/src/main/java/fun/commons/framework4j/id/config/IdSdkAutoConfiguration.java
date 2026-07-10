package fun.commons.framework4j.id.config;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import fun.commons.framework4j.id.generator.MpIdGenerator;
import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import fun.commons.framework4j.id.properties.IdSdkProperties;
import fun.commons.framework4j.id.strategy.IpHashWorkerIdStrategy;
import fun.commons.framework4j.id.strategy.RedisWorkerIdStrategy;
import fun.commons.framework4j.id.strategy.WorkerIdStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * ID SDK 自动配置类
 * <p>
 * 功能:
 * <ul>
 *   <li>自动配置 WorkerIdStrategy (Redis/IP)</li>
 *   <li>自动配置 SnowflakeDistributor</li>
 *   <li>自动注册 MyBatis-Plus IdentifierGenerator</li>
 * </ul>
 *
 * @author LDX2T
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(IdSdkProperties.class)
@ConditionalOnProperty(name = "ldx2t.commons.id.enabled", havingValue = "true", matchIfMissing = true)
public class IdSdkAutoConfiguration {

    @Value("${spring.application.name:default-app}")
    private String appName;

    /**
     * 自动配置 WorkerID 策略
     * <p>
     * 根据 idsdk.worker.strategy 配置选择策略:
     * - redis: Redis 租约模式
     * - ip: IP Hash 模式
     */
    @Bean
    @ConditionalOnMissingBean
    public WorkerIdStrategy workerIdStrategy(
            ApplicationContext applicationContext,
            IdSdkProperties properties) {

        String strategy = properties.getWorker().getStrategy();

        if ("ip".equalsIgnoreCase(strategy)) {
            IpHashWorkerIdStrategy workerIdStrategy = new IpHashWorkerIdStrategy();
            log.info("【ID-SDK】workerIdStrategy，WorkerID分配策略=IP-Hash模式");
            return workerIdStrategy;
        }

        // 默认: Redis 策略
        String redisBeanName = properties.getWorker().getRedisName();
        try {
            StringRedisTemplate redisTemplate = applicationContext.getBean(redisBeanName, StringRedisTemplate.class);
            RedisWorkerIdStrategy workerIdStrategy = new RedisWorkerIdStrategy(redisTemplate, appName);
            log.info("【ID-SDK】workerIdStrategy，WorkerID分配策略=Redis租约模式，redisBean={}", redisBeanName);
            return workerIdStrategy;
        } catch (NoSuchBeanDefinitionException e) {
            log.error("【ID-SDK】Redis Bean '{}' not found! Please check 'ldx2t.commons.id.worker.redis-name' or use 'ldx2t.commons.id.worker.strategy=ip'", redisBeanName);
            throw e;
        }
    }

    /**
     * 自动配置雪花算法核心
     */
    @Bean
    @ConditionalOnMissingBean
    public SnowflakeDistributor snowflakeDistributor(WorkerIdStrategy strategy) {
        long workerId = strategy.getWorkerId();
        SnowflakeDistributor distributor = new SnowflakeDistributor(workerId);
        log.info("【ID-SDK】snowflakeDistributor，Snowflake分布式ID生成器，workerId={}", workerId);
        return distributor;
    }

    /**
     * 自动注册 MyBatis-Plus ID 生成器
     * <p>
     * 开关: ldx2t.commons.id.mybatis.enabled (默认 true)
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(IdentifierGenerator.class)
    @ConditionalOnProperty(name = "ldx2t.commons.id.mybatis.enabled", havingValue = "true", matchIfMissing = true)
    public IdentifierGenerator mpIdGenerator(SnowflakeDistributor snowflake) {
        MpIdGenerator generator = new MpIdGenerator(snowflake);
        log.info("【ID-SDK】mpIdGenerator，MyBatis-Plus ID生成器，自动为@TableId(type=ASSIGN_ID)字段生成雪花ID");
        return generator;
    }
}
