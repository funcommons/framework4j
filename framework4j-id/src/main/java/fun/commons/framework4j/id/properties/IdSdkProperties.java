package fun.commons.framework4j.id.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ID SDK 配置属性
 * <p>
 * 配置前缀: framework4j.id
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "framework4j.id")
public class IdSdkProperties {

    /**
     * WorkerID 策略配置
     */
    private WorkerConfig worker = new WorkerConfig();

    /**
     * MyBatis-Plus 集成配置
     */
    private MybatisConfig mybatis = new MybatisConfig();

    public WorkerConfig getWorker() {
        return worker;
    }

    public void setWorker(WorkerConfig worker) {
        this.worker = worker;
    }

    public MybatisConfig getMybatis() {
        return mybatis;
    }

    public void setMybatis(MybatisConfig mybatis) {
        this.mybatis = mybatis;
    }

    /**
     * WorkerID 策略配置
     */
    public static class WorkerConfig {

        /**
         * 策略类型: redis / ip
         * <p>
         * redis: Redis 租约模式 (推荐，适合 K8s 环境)
         * ip: IP Hash 模式 (无 Redis 时的降级方案)
         */
        private String strategy = "redis";

        /**
         * Redis Bean 名称 (多源 Redis 时指定)
         * <p>
         * 默认: stringRedisTemplate
         */
        private String redisName = "stringRedisTemplate";

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public String getRedisName() {
            return redisName;
        }

        public void setRedisName(String redisName) {
            this.redisName = redisName;
        }
    }

    /**
     * MyBatis-Plus 集成配置
     */
    public static class MybatisConfig {

        /**
         * 是否启用 MyBatis-Plus ID 生成器自动注册
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
