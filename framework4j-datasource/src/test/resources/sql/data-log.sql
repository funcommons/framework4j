-- ============================================
-- 日志数据源 (log) - Mock 数据
-- ============================================

-- 清空数据
TRUNCATE TABLE t_audit_log RESTART IDENTITY CASCADE;

-- 审计日志数据
INSERT INTO t_audit_log (operation, table_name, record_id, old_value, new_value, user_id, user_name, ip_address, create_time) VALUES
('INSERT', 't_user', 1, NULL, '{"username":"admin","email":"admin@test.com"}', 0, 'system', '127.0.0.1', '2024-01-01 10:00:00'),
('INSERT', 't_order', 1, NULL, '{"order_no":"ORD20240101001","amount":199.00}', 1, 'admin', '192.168.1.100', '2024-01-01 10:30:00'),
('UPDATE', 't_order', 1, '{"status":0}', '{"status":1}', 1, 'admin', '192.168.1.100', '2024-01-01 10:35:00'),
('INSERT', 't_product', 1, NULL, '{"name":"iPhone 15","price":6999.00}', 1, 'admin', '192.168.1.100', '2024-01-01 00:00:00'),
('DELETE', 't_user', 10, '{"username":"deleted_user"}', NULL, 1, 'admin', '192.168.1.100', '2024-01-02 15:00:00');
