-- ============================================
-- 报表数据源 (report) - 表结构
-- ============================================

-- 删除表 (如果存在)
DROP TABLE IF EXISTS t_daily_report;

-- 日报表
CREATE TABLE t_daily_report (
    id BIGSERIAL PRIMARY KEY,
    report_date DATE NOT NULL,
    total_users INT DEFAULT 0,
    new_users INT DEFAULT 0,
    total_orders INT DEFAULT 0,
    total_amount DECIMAL(15,2) DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_daily_report_date ON t_daily_report(report_date);

COMMENT ON TABLE t_daily_report IS '日统计报表';
COMMENT ON COLUMN t_daily_report.report_date IS '报表日期';
COMMENT ON COLUMN t_daily_report.total_users IS '总用户数';
COMMENT ON COLUMN t_daily_report.new_users IS '新增用户数';
COMMENT ON COLUMN t_daily_report.total_orders IS '总订单数';
COMMENT ON COLUMN t_daily_report.total_amount IS '总金额';
