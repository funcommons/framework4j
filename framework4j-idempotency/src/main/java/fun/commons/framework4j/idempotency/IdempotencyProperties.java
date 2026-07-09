package fun.commons.framework4j.idempotency;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Idempotency-Key 拦截器配置
 *
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "framework4j.idempotency")
public class IdempotencyProperties {

    /**
     * 是否启用幂等拦截
     */
    private boolean enabled = false;

    /**
     * Header 名称（默认 Idempotency-Key，遵循 mc-api-spec v1.6 §227）
     */
    private String headerName = "Idempotency-Key";

    /**
     * Redis key 前缀（默认 idem）
     */
    private String keyPrefix = "idem";

    /**
     * 缓存 TTL（秒，默认 48 小时 = 172800）
     */
    private long ttlSeconds = 172800L;

    /**
     * 是否对 body 哈希后才允许重放（防止 key 被误用成不同 body）
     */
    private boolean bodyHashRequired = true;

    /**
     * 用于存储幂等状态的 Redis 数据源名称（从 MultiRedisManager 取）
     */
    private String redisName = "default";

    /**
     * v2.1: 拦截器路径模式（默认 /api/**，原硬编码）
     */
    private List<String> pathPatterns = List.of("/api/**");

    /**
     * v2.1: 拦截器排除路径模式
     */
    private List<String> excludePathPatterns = List.of();
}
