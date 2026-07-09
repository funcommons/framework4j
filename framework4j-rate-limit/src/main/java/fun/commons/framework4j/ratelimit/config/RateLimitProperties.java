package fun.commons.framework4j.ratelimit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 限流配置
 * <p>
 * 对齐 mc-api-spec §8.5：
 * <ul>
 *   <li>限流响应头：Retry-After / X-RateLimit-Limit / X-RateLimit-Remaining / X-RateLimit-Reset</li>
 *   <li>信封 code 10500</li>
 * </ul>
 *
 * @since 2.1.0
 */
@Data
@ConfigurationProperties(prefix = "framework4j.rate-limit")
public class RateLimitProperties {

    /** 是否启用 */
    private boolean enabled = true;

    /** 拦截路径 */
    private List<String> pathPatterns = List.of();

    /** 排除路径 */
    private List<String> excludePathPatterns = List.of();

    /** Redis 数据源名 */
    private String redisName = "default";

    /** 默认 limit（每个 key 在 window 内的请求上限） */
    private int defaultLimit = 100;

    /** 默认 window（Duration 解析格式：1s/1m/1h） */
    private String defaultWindow = "1m";

    /** 默认算法：sliding_window（Lua ZSET）/ token_bucket（Redisson） */
    private String defaultAlgorithm = "sliding_window";

    /** 默认 scope：ip / user / app / global */
    private String defaultScope = "ip";

    /** 是否设置响应头（X-RateLimit-*） */
    private boolean includeHeaders = true;

    /** key 前缀 */
    private String keyPrefix = "ratelimit";

    /** v2.1 功能增强：白名单路径（完全跳过限流，如 /actuator/**） */
    private List<String> whitelistPaths = List.of("/actuator/**", "/health/**", "/v1/auth/login");

    /** v2.1 功能增强：白名单 IP（内部服务调用豁免） */
    private List<String> whitelistIps = List.of("127.0.0.1", "::1");
}
