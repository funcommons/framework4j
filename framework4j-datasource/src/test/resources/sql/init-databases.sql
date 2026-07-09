-- ============================================
-- 初始化多数据库脚本
-- 用于多数据源测试
-- 执行方式: psql -U postgres -f init-databases.sql
-- ============================================

-- 注意：创建数据库的语句不能在事务中执行，需要以超级用户身份运行

-- 删除已存在的数据库（如果存在）
DROP DATABASE IF EXISTS testdb1;
DROP DATABASE IF EXISTS testdb2;
DROP DATABASE IF EXISTS testdb3;
DROP DATABASE IF EXISTS testdb4;

-- 创建测试数据库
CREATE DATABASE testdb1
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'C'
    LC_CTYPE = 'C'
    TEMPLATE = template0;

CREATE DATABASE testdb2
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'C'
    LC_CTYPE = 'C'
    TEMPLATE = template0;

CREATE DATABASE testdb3
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'C'
    LC_CTYPE = 'C'
    TEMPLATE = template0;

CREATE DATABASE testdb4
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'C'
    LC_CTYPE = 'C'
    TEMPLATE = template0;

-- 设置数据库注释
COMMENT ON DATABASE testdb1 IS '默认数据源测试库';
COMMENT ON DATABASE testdb2 IS '业务数据源测试库 (business/order/product)';
COMMENT ON DATABASE testdb3 IS '日志数据源测试库 (log)';
COMMENT ON DATABASE testdb4 IS '报表数据源测试库 (report)';

-- 授权（如果需要使用其他用户）
-- GRANT ALL PRIVILEGES ON DATABASE testdb1 TO your_user;
-- GRANT ALL PRIVILEGES ON DATABASE testdb2 TO your_user;
-- GRANT ALL PRIVILEGES ON DATABASE testdb3 TO your_user;
-- GRANT ALL PRIVILEGES ON DATABASE testdb4 TO your_user;