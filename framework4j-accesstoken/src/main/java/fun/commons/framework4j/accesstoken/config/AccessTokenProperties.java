package fun.commons.framework4j.accesstoken.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;
import java.util.Map;

/**
 * AccessToken 配置属性
 * 对应 YAML 前缀: framework4j.access-token
 */
@Data
@ConfigurationProperties(prefix = "framework4j.access-token")
public class AccessTokenProperties {

    /**
     * 是否启用
     */
    private boolean enabled = false;

    /**
     * JWT 签名密钥 (HS256，必须 ≥ 256 位 / 32 字符)
     */
    @NotBlank(message = "secret-key 不能为空")
    @Size(min = 32, message = "secret-key 至少 32 字符（256 位）")
    private String secretKey;

    /**
     * 哈希加盐 (防止 Redis 泄露后反查 Key)
     */
    @NotBlank(message = "hash-salt 不能为空")
    private String hashSalt = "";

    /**
     * 全局默认过期时间 (秒) — mc-java-security v1.0 铁律 3: access_token ≤ 2h
     */
    private long expireTime = 7200;

    /**
     * Redis 数据源 Bean 名称 (用于 @RedisOn 注解)
     */
    private String redisName = "default";

    /**
     * 拦截器拦截路径模式（默认 /**，由注解决定是否真鉴权）
     */
    private List<String> pathPatterns = List.of("/**");

    /**
     * 拦截器排除路径模式
     */
    private List<String> excludePathPatterns = List.of();

    /**
     * 策略配置 Map <TokenType, Policy>
     */
    private Map<String, Policy> policies;

    @Getter
    @Setter
    public static class Policy {
        /**
         * 互斥键字段名 (必填) —— <strong>这是 claims 必需字段名列表,不是签名密钥</strong>(Issue #20)。
         * <p>
         * 语义:该型别 generateToken 时 claims 必须包含的字段名(如 [tenant_id] / [uid]),
         * 决定会话 key 的互斥维度(同 key 值再次签发互斥/覆盖)。
         * 签名密钥请配置 framework4j.access-token.secret-key,两者不要混淆。
         * 支持单个字段 "uid" 或联合主键 ["uid", "deviceId"]
         */
        private List<String> key;

        /**
         * 覆盖全局过期时间 (秒)
         */
        private Long expireTime;

        /**
         * 最大使用次数 (Fail-Secure)
         */
        private Integer maxUsage;

        /**
         * 激活时间限制 (秒)
         */
        private Long activationTimeLimit;

        /**
         * 是否自动续期
         */
        private Boolean autoRenew;

        /**
         * 续期步长 (秒)
         */
        private Long renewIncrement;

        /**
         * Refresh Token 过期时间 (秒，默认 30 天 = 2592000)
         */
        private Long refreshExpireTime = 2592000L;

        /**
         * 家族最大轮转次数 (默认 20，超过后强制重新登录)
         */
        private Integer maxRotations = 20;

        // v2.1: getter/setter 由 Lombok @Getter/@Setter 生成。setKeyFromString 保留自定义逻辑（支持单字段 String 配置）。
        /**
         * 便捷 setter:单字段场景(等价 key=[singleKey])。
         * 参数是 <strong>claims 字段名</strong>(如 "tenant_id"),不是签名密钥。
         */
        public void setKeyFromString(String singleKey) {
            if (singleKey != null && !singleKey.isEmpty()) {
                this.key = List.of(singleKey);
            }
        }
    }

    // v2.1: getter/setter 由 Lombok @Data 生成。
}