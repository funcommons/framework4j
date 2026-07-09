# 多数据源测试数据库初始化指南

## 概述

本目录包含用于多数据源功能测试的PostgreSQL数据库初始化脚本。

## 数据库规划

| 数据库名 | 用途 | 数据源名称 | 端口 |
|---------|------|-----------|------|
| testdb1 | 默认数据源 | default | 5432 |
| testdb2 | 业务数据源 | business, order, product | 5432 |
| testdb3 | 日志数据源 | log | 5432 |
| testdb4 | 报表数据源 | report | 5432 |

## 快速开始

### Windows环境

```bash
cd framework4j-test\src\test\resources\sql
init-all-schemas.bat
```

### Linux/Mac环境

```bash
cd framework4j-test/src/test/resources/sql
chmod +x init-all-schemas.sh
./init-all-schemas.sh
```

## 手动初始化步骤

如果自动脚本执行失败，可以手动执行以下步骤：

### 1. 创建数据库

```bash
psql -U postgres -f init-databases.sql
```

### 2. 初始化各个数据库的表结构

```bash
# testdb1 - 默认数据源
psql -U postgres -d testdb1 -f schema-business.sql
psql -U postgres -d testdb1 -f data-business.sql

# testdb2 - 业务数据源
psql -U postgres -d testdb2 -f schema-business.sql
psql -U postgres -d testdb2 -f data-business.sql

# testdb3 - 日志数据源
psql -U postgres -d testdb3 -f schema-log.sql
psql -U postgres -d testdb3 -f data-log.sql

# testdb4 - 报表数据源
psql -U postgres -d testdb4 -f schema-report.sql
psql -U postgres -d testdb4 -f data-report.sql
```

## 脚本说明

### init-databases.sql
创建4个测试数据库（testdb1-4）的SQL脚本。

### init-all-schemas.sh / init-all-schemas.bat
一键初始化所有数据库和表结构的脚本（Linux/Windows版本）。

### schema-*.sql
各数据源的表结构定义：
- `schema-business.sql`: 业务表（用户、订单、商品）
- `schema-log.sql`: 日志表（审计日志）
- `schema-report.sql`: 报表表（销售统计、用户统计）

### data-*.sql
各数据源的测试数据：
- `data-business.sql`: 业务测试数据
- `data-log.sql`: 日志测试数据
- `data-report.sql`: 报表测试数据

## 前提条件

1. 已安装 PostgreSQL 12+
2. PostgreSQL 服务已启动
3. 已配置环境变量（能直接使用 `psql` 命令）
4. 默认用户名/密码: postgres/postgres

## 修改数据库连接信息

如果需要修改数据库连接信息，请编辑以下文件中的变量：

**Windows (init-all-schemas.bat):**
```bat
set DB_HOST=localhost
set DB_PORT=5432
set DB_USER=postgres
set DB_PASSWORD=postgres
```

**Linux/Mac (init-all-schemas.sh):**
```bash
DB_HOST="localhost"
DB_PORT="5432"
DB_USER="postgres"
DB_PASSWORD="postgres"
```

## 清理数据库

如果需要重新初始化，可以直接重新运行 `init-all-schemas` 脚本，脚本会自动删除并重建数据库。

或者手动删除：

```sql
DROP DATABASE IF EXISTS testdb1;
DROP DATABASE IF EXISTS testdb2;
DROP DATABASE IF EXISTS testdb3;
DROP DATABASE IF EXISTS testdb4;
```

## 验证数据库

初始化完成后，可以通过以下命令验证：

```bash
# 查看所有数据库
psql -U postgres -c "\l"

# 查看 testdb2 的表
psql -U postgres -d testdb2 -c "\dt"

# 查看 testdb2 的用户数据
psql -U postgres -d testdb2 -c "SELECT * FROM t_user;"
```

## 故障排查

### 1. psql 命令不存在
确保 PostgreSQL 的 bin 目录已添加到 PATH 环境变量中。

**Windows:**
```
C:\Program Files\PostgreSQL\15\bin
```

**Linux:**
```bash
export PATH=/usr/lib/postgresql/15/bin:$PATH
```

### 2. 密码验证失败
修改脚本中的 `DB_PASSWORD` 变量，或者配置 PostgreSQL 的 pg_hba.conf 允许本地信任连接。

### 3. 数据库已存在
脚本会自动删除已存在的数据库，如果遇到问题，可以手动删除后重试。

## 配置文件位置

测试配置文件位于：
```
framework4j-datasource/src/test/resources/application-pgsql-test.yml
```

该配置文件已更新为指向不同的数据库：
- default → testdb1
- business → testdb2
- log → testdb3
- report → testdb4