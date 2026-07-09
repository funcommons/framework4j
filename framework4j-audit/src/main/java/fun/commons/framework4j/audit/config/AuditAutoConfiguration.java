package fun.commons.framework4j.audit.config;

import fun.commons.framework4j.audit.aspect.AuditAspect;
import fun.commons.framework4j.audit.service.AuditService;
import fun.commons.framework4j.audit.service.AuditSink;
import fun.commons.framework4j.audit.service.HashChainService;
import fun.commons.framework4j.audit.service.InMemoryAuditSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * framework4j-audit 自动装配
 *
 * @since 2.1.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({AuditService.class, AuditAspect.class})
@ConditionalOnProperty(prefix = "framework4j.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AuditProperties.class)
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HashChainService hashChainService(AuditProperties properties) {
        return new HashChainService(properties);
    }

    /**
     * 默认 InMemoryAuditSink（开发/测试用）
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditSink auditSink() {
        log.info("【Audit】使用默认 InMemoryAuditSink（生产环境应替换为 DB/Kafka 实现）");
        return new InMemoryAuditSink();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditService auditService(AuditProperties properties,
                                     HashChainService hashChainService,
                                     AuditSink auditSink) {
        log.info("【Audit】AuditService 已启用（hashChain={}, algorithm={}）",
                properties.isHashChainEnabled(), properties.getHashAlgorithm());
        return new AuditService(properties, hashChainService, auditSink);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditAspect auditAspect(AuditService auditService) {
        return new AuditAspect(auditService);
    }
}
