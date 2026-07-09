package fun.commons.framework4j.redis.properties;

import fun.commons.framework4j.redis.enums.TemplateType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "framework4j.redis")
public class RedisDataSourceProperties {

    /**
     * 数据源名称 (可选，默认使用 Map Key)
     */
    private String name;

    /**
     * [核心修改] 数据源别名列表
     * 这里的别名会注册为 Spring Bean 的 alias，指向同一个 Bean 实例
     */
    private List<String> aliases = new ArrayList<>();

    private String host = "localhost";
    private int port = 6379;
    private int database = 0;
    private String password;

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration timeout = Duration.ofMillis(3000);

    private LettuceConfig lettuce = new LettuceConfig();
    private RedissonConfig redisson = new RedissonConfig();

    /**
     * Redis 模板类型
     * STRING: 生成 StringRedisTemplate（默认）
     * OBJECT: 生成 RedisTemplate&lt;String, Object&gt;，使用 Jackson 序列化
     */
    private TemplateType templateType = TemplateType.STRING;

    @Data
    public static class LettuceConfig {
        private PoolConfig pool = new PoolConfig();
    }

    @Data
    public static class PoolConfig {
        private int maxActive = 8;
        private int maxIdle = 8;
        private int minIdle = 0;
        private int maxWait = -1;
    }

    @Data
    public static class RedissonConfig {
        private boolean enabled = false;
    }
}