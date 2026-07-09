#!/bin/bash
# ============================================
# 初始化所有数据库的表结构脚本
# 执行方式: bash init-all-schemas.sh
# ============================================

set -e  # 遇到错误立即退出

# 数据库连接信息
DB_HOST="localhost"
DB_PORT="5432"
DB_USER="postgres"
DB_PASSWORD="postgres"

# 设置环境变量（避免密码提示）
export PGPASSWORD="$DB_PASSWORD"

echo "=========================================="
echo "开始初始化多数据源测试数据库"
echo "=========================================="

# 1. 创建数据库
echo ""
echo "[1/5] 创建数据库..."
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -f init-databases.sql
echo "✓ 数据库创建完成"

# 2. 初始化 testdb1 (默认数据源) - 创建所有表
echo ""
echo "[2/5] 初始化 testdb1 (默认数据源)..."
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d testdb1 -f schema-business.sql
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d testdb1 -f data-business.sql
echo "✓ testdb1 初始化完成"

# 3. 初始化 testdb2 (业务数据源)
echo ""
echo "[3/5] 初始化 testdb2 (业务数据源)..."
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d testdb2 -f schema-business.sql
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d testdb2 -f data-business.sql
echo "✓ testdb2 初始化完成"

# 4. 初始化 testdb3 (日志数据源)
echo ""
echo "[4/5] 初始化 testdb3 (日志数据源)..."
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d testdb3 -f schema-log.sql
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d testdb3 -f data-log.sql
echo "✓ testdb3 初始化完成"

# 5. 初始化 testdb4 (报表数据源)
echo ""
echo "[5/5] 初始化 testdb4 (报表数据源)..."
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d testdb4 -f schema-report.sql
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d testdb4 -f data-report.sql
echo "✓ testdb4 初始化完成"

# 清除密码环境变量
unset PGPASSWORD

echo ""
echo "=========================================="
echo "✓ 所有数据库初始化完成！"
echo "=========================================="
echo ""
echo "数据库清单:"
echo "  - testdb1 (default)   -> jdbc:postgresql://localhost:5432/testdb1"
echo "  - testdb2 (business)  -> jdbc:postgresql://localhost:5432/testdb2"
echo "  - testdb3 (log)       -> jdbc:postgresql://localhost:5432/testdb3"
echo "  - testdb4 (report)    -> jdbc:postgresql://localhost:5432/testdb4"
echo ""