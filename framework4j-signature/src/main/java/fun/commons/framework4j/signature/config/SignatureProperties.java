package fun.commons.framework4j.signature.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 签名校验配置
 * <p>
 * 对齐 mc-java-security §6（接口签名防重放）：
 * <ul>
 *   <li>HMAC-SHA256 签名算法</li>
 *   <li>timestamp ±5min 容忍</li>
 *   <li>nonce Redis SETNX 10min 防重放</li>
 *   <li>MessageDigest.isEqual 常量时间比较</li>
 * </ul>
 *
 * @since 2.1.0
 */
@Data
@ConfigurationProperties(prefix = "framework4j.signature")
public class SignatureProperties {

    /** 是否启用 */
    private boolean enabled = true;

    /** timestamp 容忍度（毫秒），默认 ±5min */
    private long timestampToleranceMs = 300_000L;

    /** nonce 防重放 TTL（秒），默认 10min */
    private long nonceTtlSeconds = 600L;

    /** 拦截路径（Ant 风格），默认空表示不拦截 */
    private List<String> pathPatterns = List.of();

    /** 排除路径（Ant 风格） */
    private List<String> excludePathPatterns = List.of();

    /** nonce Redis Key 前缀 */
    private String nonceKeyPrefix = "signature:nonce";

    /** 使用的 Redis 数据源名（MultiRedisManager） */
    private String redisName = "default";

    /** Header 名称（可定制） */
    private HeaderNames headerNames = new HeaderNames();

    @Data
    public static class HeaderNames {
        private String accessKey = "X-Access-Key";
        private String timestamp = "X-Timestamp";
        private String nonce = "X-Nonce";
        private String signature = "X-Signature";
    }
}
