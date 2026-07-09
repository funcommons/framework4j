-- ============================================
-- 业务数据源 (business) - Mock 数据
-- ============================================

-- 清空数据
TRUNCATE TABLE t_order RESTART IDENTITY CASCADE;
TRUNCATE TABLE t_product RESTART IDENTITY CASCADE;
TRUNCATE TABLE t_user RESTART IDENTITY CASCADE;

-- 用户数据
INSERT INTO t_user (username, email, phone, status, create_time) VALUES
('admin', 'admin@test.com', '13800000001', 1, '2024-01-01 10:00:00'),
('zhangsan', 'zhangsan@test.com', '13800000002', 1, '2024-01-01 11:00:00'),
('lisi', 'lisi@test.com', '13800000003', 1, '2024-01-02 09:00:00'),
('wangwu', 'wangwu@test.com', '13800000004', 0, '2024-01-02 14:00:00'),
('test_user', 'test@test.com', '13800000005', 1, '2024-01-03 08:00:00');

-- 订单数据
INSERT INTO t_order (order_no, user_id, amount, status, remark, create_time) VALUES
('ORD20240101001', 1, 199.00, 1, '测试订单1', '2024-01-01 10:30:00'),
('ORD20240101002', 2, 299.00, 1, '测试订单2', '2024-01-01 14:20:00'),
('ORD20240101003', 2, 99.50, 0, '待支付订单', '2024-01-02 09:15:00'),
('ORD20240101004', 3, 1299.00, 2, '已取消订单', '2024-01-02 16:00:00'),
('ORD20240101005', 1, 599.00, 1, '测试订单5', '2024-01-03 11:30:00');

-- 商品数据
INSERT INTO t_product (name, price, stock, category, status, create_time) VALUES
('iPhone 15', 6999.00, 100, '手机', 1, '2024-01-01 00:00:00'),
('MacBook Pro', 12999.00, 50, '电脑', 1, '2024-01-01 00:00:00'),
('AirPods Pro', 1999.00, 200, '配件', 1, '2024-01-01 00:00:00'),
('旧款手机', 999.00, 10, '手机', 0, '2024-01-01 00:00:00'),
('iPad Pro', 8999.00, 80, '平板', 1, '2024-01-01 00:00:00');
