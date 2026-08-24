package fun.commons.framework4j.sensitive.config;

import fun.commons.framework4j.sensitive.context.SpringContextHolder;
import fun.commons.framework4j.sensitive.typehandler.EncryptedFieldTypeHandler;
import fun.commons.framework4j.sensitive.util.AesGcmCryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * framework4j-sensitive 自动装配
 * <p>
 * v2.2 BUG FIX: 不再将 {@link EncryptedFieldTypeHandler} 注册为 Spring Bean。
 * MyBatis-Plus 会通过 Spring 容器扫描所有 {@code BaseTypeHandler<T>} Bean 并按类型擦除
 * 自动注册为 {@code T} 的全局 handler，导致所有 String 字段（JSONB、email、nickname 等）
 * 被默认加密。
 * <p>
 * 正确用法：在实体字段上显式标注
 * {@code @TableField(typeHandler = EncryptedFieldTypeHandler.class)}，
 * 由 MyBatis-Plus 的 mapper 字段级 typeHandler 机制精确应用。
 * <p>
 * 仍保留 {@code sensitiveAesKeyBytes} bean — 业务方可通过它自行构造 handler 实例。
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
     * <p>仅当 encryption-key 已配置时注册。
     * <p>业务方可通过注入此 bean 自行构造 {@link EncryptedFieldTypeHandler} 实例并注册到 MyBatis Configuration。
     */
    @Bean(name = "sensitiveAesKeyBytes")
    @ConditionalOnProperty(prefix = "framework4j.sensitive", name = "encryption-key")
    public byte[] sensitiveAesKeyBytes(SensitiveProperties properties) {
        // P0-1: 启动期硬校验 encryption-key 长度 ≥ 32 字符
        String raw = properties.getEncryptionKey();
        if (raw == null || raw.length() < 32) {
            throw new IllegalStateException(
                "framework4j.sensitive.encryption-key 必须 ≥ 32 字符（实际 "
                + (raw == null ? 0 : raw.length()) + "）。生产环境必须从 KMS 取强密钥。");
        }
        log.info("【Sensitive】encryption-key 已配置（长度={}），AES-256-GCM 密钥派生完成", raw.length());
        return AesGcmCryptoUtil.deriveKey(raw);
    }

    /**
     * Spring 容器静态访问器（供 {@link fun.commons.framework4j.sensitive.typehandler.LazyEncryptedFieldTypeHandler}
     * 等 MyBatis 反射实例化的 TypeHandler 运行时取 key Bean）。
     * <p>与 sensitiveAesKeyBytes 同条件（配了 encryption-key 才需要 lazy handler）。
     */
    @Bean
    @ConditionalOnProperty(prefix = "framework4j.sensitive", name = "encryption-key")
    public SpringContextHolder framework4jSensitiveContextHolder() {
        return new SpringContextHolder();
    }
}
