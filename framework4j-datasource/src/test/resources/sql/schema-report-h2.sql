-- ============================================
-- 报表数据源 (report) - 表结构 (H2数据库版本)
-- ============================================

-- 删除表 (如果存在)
DROP TABLE IF EXISTS t_daily_report;

-- 日报表
CREATE TABLE t_daily_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_date DATE NOT NULL,
    total_users INT DEFAULT 0,
    new_users INT DEFAULT 0,
    total_orders INT DEFAULT 0,
    total_amount DECIMAL(15,2) DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_daily_report_date ON t_daily_report(report_date);