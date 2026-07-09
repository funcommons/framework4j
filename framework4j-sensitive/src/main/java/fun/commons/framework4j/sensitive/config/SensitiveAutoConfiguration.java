package fun.commons.framework4j.sensitive.config;

import fun.commons.framework4j.sensitive.typehandler.EncryptedFieldTypeHandler;
import fun.commons.framework4j.sensitive.util.AesGcmCryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * framework4j-sensitive 自动装配
 *
 * @since 2.1.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({fun.commons.framework4j.sensitive.serializer.SensitiveJsonSerializer.class})
@ConditionalOnProperty(prefix = "framework4j.sensitive", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SensitiveProperties.class)
public class SensitiveAutoConfiguration {

    /**
     * AES-256-GCM 密钥（单例 byte[] Bean）
     * <p>仅当 encryption-key 已配置时注册
     */
    @Bean(name = "sensitiveAesKeyBytes")
    @ConditionalOnMissingBean(name = "sensitiveAesKeyBytes")
    @ConditionalOnProperty(prefix = "framework4j.sensitive", name = "encryption-key")
    public byte[] sensitiveAesKeyBytes(SensitiveProperties properties) {
        log.info("【Sensitive】encryption-key 已配置，AES-256-GCM 加密 TypeHandler 已启用");
        return AesGcmCryptoUtil.deriveKey(properties.getEncryptionKey());
    }

    /**
     * 字段加密 TypeHandler
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "framework4j.sensitive", name = "encryption-key")
    public EncryptedFieldTypeHandler encryptedFieldTypeHandler(
            @org.springframework.beans.factory.annotation.Qualifier("sensitiveAesKeyBytes")
            byte[] sensitiveAesKeyBytes) {
        return new EncryptedFieldTypeHandler(sensitiveAesKeyBytes);
    }
}
