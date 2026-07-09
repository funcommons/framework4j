package fun.commons.framework4j.sensitive.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 脱敏 + 加密配置
 *
 * @since 2.1.0
 */
@Data
@ConfigurationProperties(prefix = "framework4j.sensitive")
public class SensitiveProperties {

    private boolean enabled = true;

    /** AES-256-GCM 密钥（任意字符串，内部 SHA-256 派生为 32 字节）；生产应从 KMS 取 */
    private String encryptionKey;

    /** GCM IV 长度（字节），默认 12 */
    private int ivLength = 12;

    /** GCM Tag 长度（位），默认 128 */
    private int tagBits = 128;
}
