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
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(IdSdkProperties.class)
@ConditionalOnProperty(name = "framework4j.id.enabled", havingValue = "true", matchIfMissing = true)
public class IdSdkAutoConfiguration {

    @Value("${spring.application.name:}")
    private String appName;

    /**
     * 自动配置 WorkerID 策略
     * <p>
     * v2.1 修复：原 default-app 兜底会让多服务争抢同一 Redis 租约坑位。改为缺失时抛 IllegalStateException。
     *
     * 根据 idsdk.worker.strategy 配置选择策略:
     * - redis: Redis 租约模式
     * - ip: IP Hash 模式
     */
    @Bean
    @ConditionalOnMissingBean
    public WorkerIdStrategy workerIdStrategy(
            ApplicationContext applicationContext,
            IdSdkProperties properties) {

        // v2.1: 用枚举校验 strategy，未知值抛 IllegalArgumentException（防止拼写错误静默走 Redis）
        fun.commons.framework4j.id.enums.WorkerStrategy strategyEnum =
                fun.commons.framework4j.id.enums.WorkerStrategy.fromString(properties.getWorker().getStrategy());

        // v2.1: 强制要求 spring.application.name（Redis 模式必需，IP 模式不强制但建议配）
        if (strategyEnum == fun.commons.framework4j.id.enums.WorkerStrategy.REDIS
                && !org.springframework.util.StringUtils.hasText(appName)) {
            throw new IllegalStateException(
                    "framework4j.id.worker.strategy=redis 时必须配置 spring.application.name，"
                            + "否则多实例会争抢同一 Redis WorkerId 租约坑位");
        }

        if (strategyEnum == fun.commons.framework4j.id.enums.WorkerStrategy.IP) {
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
            log.error("【ID-SDK】Redis Bean '{}' not found! Please check 'framework4j.id.worker.redis-name' or use 'framework4j.id.worker.strategy=ip'", redisBeanName);
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
     * 开关: framework4j.id.mybatis.enabled (默认 true)
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(IdentifierGenerator.class)
    @ConditionalOnProperty(name = "framework4j.id.mybatis.enabled", havingValue = "true", matchIfMissing = true)
    public IdentifierGenerator mpIdGenerator(SnowflakeDistributor snowflake) {
        MpIdGenerator generator = new MpIdGenerator(snowflake);
        log.info("【ID-SDK】mpIdGenerator，MyBatis-Plus ID生成器，自动为@TableId(type=ASSIGN_ID)字段生成雪花ID");
        return generator;
    }
}
