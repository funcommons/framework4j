-- ============================================
-- 业务数据源 (business) - 表结构
-- ============================================

-- 删除表 (如果存在)
DROP TABLE IF EXISTS t_order;
DROP TABLE IF EXISTS t_product;
DROP TABLE IF EXISTS t_user;

-- 用户表
CREATE TABLE t_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(200),
    phone VARCHAR(20),
    status INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_user IS '用户表';
COMMENT ON COLUMN t_user.id IS '用户ID';
COMMENT ON COLUMN t_user.username IS '用户名';
COMMENT ON COLUMN t_user.email IS '邮箱';
COMMENT ON COLUMN t_user.phone IS '手机号';
COMMENT ON COLUMN t_user.status IS '状态: 0-禁用, 1-正常';

-- 订单表
CREATE TABLE t_order (
    id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status INT DEFAULT 0,
    remark VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_user_id ON t_order(user_id);
CREATE INDEX idx_order_create_time ON t_order(create_time);

COMMENT ON TABLE t_order IS '订单表';
COMMENT ON COLUMN t_order.order_no IS '订单号';
COMMENT ON COLUMN t_order.user_id IS '用户ID';
COMMENT ON COLUMN t_order.amount IS '订单金额';
COMMENT ON COLUMN t_order.status IS '状态: 0-待支付, 1-已支付, 2-已取消';

-- 商品表
CREATE TABLE t_product (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    stock INT DEFAULT 0,
    category VARCHAR(100),
    status INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_product IS '商品表';
COMMENT ON COLUMN t_product.name IS '商品名称';
COMMENT ON COLUMN t_product.price IS '价格';
COMMENT ON COLUMN t_product.stock IS '库存';
COMMENT ON COLUMN t_product.category IS '分类';
COMMENT ON COLUMN t_product.status IS '状态: 0-下架, 1-上架';
