package fun.commons.framework4j.audit.service;

import fun.commons.framework4j.audit.config.AuditProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;

/**
 * JDBC 实现的 {@link AuditSink} — append-only 写入到 {@code framework4j.audit.table-name}。
 *
 * <p>v2.2 新增：此前仅 {@link InMemoryAuditSink}，重启即丢，与 README「企业合规刚需」承诺严重不符。
 * <p>业务方只需在 application.yml 配置 DataSource + audit.table-name，AuditAutoConfiguration
 * 会自动装配本 sink 并把 append-only INSERT 路由到 DB。
 *
 * <h3>建表 DDL</h3>
 * <pre>
 * CREATE TABLE audit_log (
 *     id BIGSERIAL PRIMARY KEY,
 *     action VARCHAR(64) NOT NULL,
 *     target_type VARCHAR(64) NOT NULL,
 *     target_id VARCHAR(64),
 *     actor VARCHAR(64),
 *     result VARCHAR(16) NOT NULL,
 *     error_message TEXT,
 *     args_json TEXT,
 *     result_json TEXT,
 *     ip VARCHAR(45),
 *     user_agent VARCHAR(255),
 *     trace_id VARCHAR(64),
 *     timestamp TIMESTAMPTZ NOT NULL,
 *     prev_hash VARCHAR(128) NOT NULL,
 *     hash VARCHAR(128) NOT NULL UNIQUE
 * );
 * CREATE INDEX idx_audit_target ON audit_log (target_type, target_id);
 * CREATE INDEX idx_audit_actor_time ON audit_log (actor, timestamp DESC);
 * CREATE INDEX idx_audit_time ON audit_log (timestamp DESC);
 * </pre>
 *
 * @since 2.2.0
 */
@Slf4j
public class JdbcAuditSink implements AuditSink {

    private final JdbcTemplate jdbc;
    private final String tableName;

    public JdbcAuditSink(DataSource dataSource, AuditProperties properties) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.tableName = sanitizeTableName(properties.getTableName());
    }

    /**
     * append-only INSERT。{@code hash} 字段加了 UNIQUE 约束，重复 hash 写入会抛
     * {@link org.springframework.dao.DuplicateKeyException} — 这是 hash chain 检测到冲突的信号。
     */
    @Override
    public void write(AuditRecord record) {
        String sql = "INSERT INTO " + tableName + " ("
                + "action, target_type, target_id, actor, result, error_message, "
                + "args_json, result_json, ip, user_agent, trace_id, "
                + "timestamp, prev_hash, hash"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbc.update(sql,
                record.getAction(),
                record.getTargetType(),
                record.getTargetId(),
                record.getActor(),
                record.getResult(),
                record.getErrorMessage(),
                record.getArgsJson(),
                record.getResultJson(),
                record.getIp(),
                truncate(record.getUserAgent(), 255),
                record.getTraceId(),
                Timestamp.from(record.getTimestamp()),
                record.getPrevHash(),
                record.getHash());
    }

    /**
     * 启动时取最后一条 hash 用于初始化 {@link HashChainService}。
     * 表为空时返回 null（AuditService 会 fallback 到 GENESIS）。
     */
    @Override
    public String loadLastHash() {
        String sql = "SELECT hash FROM " + tableName + " ORDER BY id DESC LIMIT 1";
        try {
            String last = jdbc.queryForObject(sql, String.class);
            if (last == null || last.isEmpty()) {
                return "GENESIS";
            }
            log.info("[Audit-JDBC] loaded last hash from {}: hash={}", tableName, last);
            return last;
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return "GENESIS";
        } catch (Exception e) {
            // 表不存在或其他异常 → 兜底返回 GENESIS（不阻塞启动）
            log.warn("[Audit-JDBC] loadLastHash failed (table={}): {} → fallback GENESIS", tableName, e.getMessage());
            return "GENESIS";
        }
    }

    /**
     * 表名做白名单防 SQL injection — 只允许 [a-zA-Z0-9_]，且 ≤ 64 字符。
     */
    private static String sanitizeTableName(String name) {
        if (name == null || !name.matches("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$")) {
            throw new IllegalStateException("Invalid audit table-name: " + name
                    + " (must match [a-zA-Z_][a-zA-Z0-9_]{0,63})");
        }
        return name;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}