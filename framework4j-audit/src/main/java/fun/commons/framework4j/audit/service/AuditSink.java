package fun.commons.framework4j.audit.service;

/**
 * 审计日志持久化抽象
 * <p>
 * 业务方实现：DB append-only / 文件 / ELK / Kafka 等。
 *
 * @since 2.1.0
 */
public interface AuditSink {

    /**
     * 持久化一条审计记录（异步调用，不应阻塞主流程）
     */
    void write(AuditRecord record);

    /**
     * 取最后一条 hash（用于初始化 HashChain 服务）
     *
     * @return last hash；首次启动返回 "GENESIS"
     */
    default String loadLastHash() {
        return "GENESIS";
    }
}
