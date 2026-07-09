package fun.commons.framework4j.redis.factory;

import fun.commons.framework4j.redis.properties.RedisDataSourceProperties;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * v2.1 抽出顶级类：统一 Lettuce + Redisson 构造逻辑。
 * <p>
 * 原本 {@link fun.commons.framework4j.redis.manager.MultiRedisManager} 和
 * {@link fun.commons.framework4j.redis.config.MultiRedisAutoConfiguration} 各有一份
 * createConnectionFactory / createRedissonClient 实现，逻辑几乎逐行重复。
 *
 * @since 2.1.0
 */
public final class RedisComponentFactory {

    private RedisComponentFactory() {}

    /**
     * 创建 Lettuce 连接工厂（含连接池配置）
     */
    public static LettuceConnectionFactory createConnectionFactory(RedisDataSourceProperties config) {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(config.getHost());
        redisConfig.setPort(config.getPort());
        redisConfig.setDatabase(config.getDatabase());
        if (StringUtils.hasText(config.getPassword())) {
            redisConfig.setPassword(RedisPassword.of(config.getPassword()));
        }

        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        RedisDataSourceProperties.PoolConfig sourcePool = config.getLettuce().getPool();
        poolConfig.setMaxTotal(sourcePool.getMaxActive());
        poolConfig.setMaxIdle(sourcePool.getMaxIdle());
        poolConfig.setMinIdle(sourcePool.getMinIdle());
        poolConfig.setMaxWait(Duration.ofMillis(sourcePool.getMaxWait()));

        LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .commandTimeout(config.getTimeout())
                .poolConfig(poolConfig)
                .build();

        return new LettuceConnectionFactory(redisConfig, clientConfig);
    }

    /**
     * 创建 Redisson 客户端
     */
    public static RedissonClient createRedissonClient(RedisDataSourceProperties config) {
        Config redissonConfig = new Config();
        String address = "redis://" + config.getHost() + ":" + config.getPort();
        redissonConfig.useSingleServer()
                .setAddress(address)
                .setDatabase(config.getDatabase())
                .setPassword(StringUtils.hasText(config.getPassword()) ? config.getPassword() : null)
                .setTimeout((int) config.getTimeout().toMillis())
                .setConnectionMinimumIdleSize(config.getLettuce().getPool().getMinIdle())
                .setConnectionPoolSize(config.getLettuce().getPool().getMaxActive());
        return Redisson.create(redissonConfig);
    }

    /**
     * 创建连接池配置（用于 BeanDefinition 注册场景）
     */
    public static GenericObjectPoolConfig<?> createPoolConfig(RedisDataSourceProperties config) {
        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        RedisDataSourceProperties.PoolConfig sourcePool = config.getLettuce().getPool();
        poolConfig.setMaxTotal(sourcePool.getMaxActive());
        poolConfig.setMaxIdle(sourcePool.getMaxIdle());
        poolConfig.setMinIdle(sourcePool.getMinIdle());
        poolConfig.setMaxWait(Duration.ofMillis(sourcePool.getMaxWait()));
        return poolConfig;
    }

    /**
     * 创建 RedisStandaloneConfiguration（用于 BeanDefinition 注册场景）
     */
    public static RedisStandaloneConfiguration createStandaloneConfig(RedisDataSourceProperties config) {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(config.getHost());
        redisConfig.setPort(config.getPort());
        redisConfig.setDatabase(config.getDatabase());
        if (StringUtils.hasText(config.getPassword())) {
            redisConfig.setPassword(RedisPassword.of(config.getPassword()));
        }
        return redisConfig;
    }
}
