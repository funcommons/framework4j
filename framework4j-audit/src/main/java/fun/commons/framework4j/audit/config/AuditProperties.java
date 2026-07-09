package fun.commons.framework4j.audit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 审计日志配置
 *
 * @since 2.1.0
 */
@Data
@ConfigurationProperties(prefix = "framework4j.audit")
public class AuditProperties {

    private boolean enabled = true;

    /** 表名 */
    private String tableName = "audit_log";

    /** 是否启用 hash chain 防篡改 */
    private boolean hashChainEnabled = true;

    /** Hash chain 算法（SHA-256 / SHA-512） */
    private String hashAlgorithm = "SHA-256";

    /** 操作人来源 Header 名（如 X-User-Id） */
    private String actorHeader = "X-User-Id";

    /** 操作人为空时兜底值 */
    private String actorFallback = "anonymous";

    /** IP 来源 Header 名（X-Forwarded-For 优先） */
    private String ipHeader = "X-Forwarded-For";
}
