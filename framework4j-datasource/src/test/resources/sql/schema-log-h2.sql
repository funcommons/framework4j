-- ============================================
-- 日志数据源 (log) - 表结构 (H2数据库版本)
-- ============================================

-- 删除表 (如果存在)
DROP TABLE IF EXISTS t_audit_log;

-- 审计日志表
CREATE TABLE t_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    operation VARCHAR(50) NOT NULL,
    table_name VARCHAR(100),
    record_id BIGINT,
    old_value TEXT,
    new_value TEXT,
    user_id BIGINT,
    user_name VARCHAR(100),
    ip_address VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_user_id ON t_audit_log(user_id);
CREATE INDEX idx_audit_log_table_name ON t_audit_log(table_name);
CREATE INDEX idx_audit_log_create_time ON t_audit_log(create_time);