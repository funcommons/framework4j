package fun.commons.framework4j.audit.config;

import fun.commons.framework4j.audit.aspect.AuditAspect;
import fun.commons.framework4j.audit.service.AuditService;
import fun.commons.framework4j.audit.service.AuditSink;
import fun.commons.framework4j.audit.service.HashChainService;
import fun.commons.framework4j.audit.service.InMemoryAuditSink;
import fun.commons.framework4j.audit.service.JdbcAuditSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * framework4j-audit 自动装配
 * <p>
 * v2.2 升级：
 * <ul>
 *   <li>检测到 {@link DataSource} bean 时，自动装配 {@link JdbcAuditSink}（append-only INSERT）</li>
 *   <li>未检测到 DataSource 时，fallback 到 {@link InMemoryAuditSink}（开发 / 单测用）</li>
 *   <li>业务方无需任何代码改动 — 只需配置 DataSource + audit.table-name 即生效</li>
 * </ul>
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
     * 默认 sink 选择：
     * <ol>
     *   <li>有 DataSource bean → JdbcAuditSink（生产推荐）</li>
     *   <li>无 DataSource → InMemoryAuditSink（开发 / 单测）</li>
     * </ol>
     * 业务方自定义 AuditSink bean 优先（@ConditionalOnMissingBean）。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataSource.class)
    public AuditSink jdbcAuditSink(DataSource dataSource, AuditProperties properties) {
        log.info("【Audit】检测到 DataSource — 自动装配 JdbcAuditSink（表={}）", properties.getTableName());
        return new JdbcAuditSink(dataSource, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditSink inMemoryAuditSinkFallback() {
        log.warn("【Audit】未检测到 DataSource — fallback 到 InMemoryAuditSink（仅开发/单测用，重启即丢！）");
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
