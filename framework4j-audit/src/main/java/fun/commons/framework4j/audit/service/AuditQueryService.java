package fun.commons.framework4j.audit.service;

import java.time.Instant;
import java.util.List;

/**
 * 审计日志查询 API
 * <p>
 * v2.1 功能增强：提供查询 + 链校验接口。
 * <p>
 * 使用方需自行实现（基于 DB / ES / Kafka 等存储）。
 *
 * @since 2.1.0
 */
public interface AuditQueryService {

    /**
     * 分页查询审计记录
     *
     * @param actor 操作人（可空）
     * @param action 操作类型（可空）
     * @param start 开始时间（可空）
     * @param end 结束时间（可空）
     * @param page 页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 查询结果
     */
    AuditPage query(String actor, String action, Instant start, Instant end, int page, int pageSize);

    /**
     * 校验 hash chain 完整性（指定时间范围）
     *
     * @param start 开始时间（可空）
     * @param end 结束时间（可空）
     * @return 校验结果（含断链位置）
     */
    ChainVerifyResult verifyChain(Instant start, Instant end);

    /** 分页查询结果 */
    record AuditPage(List<AuditRecord> records, long total, int page, int pageSize) {}

    /** 链校验结果 */
    record ChainVerifyResult(boolean valid, int totalChecked, List<String> brokenAt) {}
}
