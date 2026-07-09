@echo off
REM ============================================
REM 初始化所有数据库的表结构脚本 (Windows)
REM 执行方式: init-all-schemas.bat
REM 前提条件: 已安装 PostgreSQL 并配置了环境变量
REM ============================================

setlocal enabledelayedexpansion

REM 数据库连接信息
set DB_HOST=localhost
set DB_PORT=5432
set DB_USER=postgres
set DB_PASSWORD=postgres

REM 设置环境变量（避免密码提示）
set PGPASSWORD=%DB_PASSWORD%

echo ==========================================
echo 开始初始化多数据源测试数据库
echo ==========================================

REM 1. 创建数据库
echo.
echo [1/5] 创建数据库...
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -f init-databases.sql
if %errorlevel% neq 0 (
    echo ✗ 数据库创建失败
    goto :error
)
echo √ 数据库创建完成

REM 2. 初始化 testdb1 (默认数据源)
echo.
echo [2/5] 初始化 testdb1 (默认数据源)...
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d testdb1 -f schema-business.sql
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d testdb1 -f data-business.sql
if %errorlevel% neq 0 (
    echo ✗ testdb1 初始化失败
    goto :error
)
echo √ testdb1 初始化完成

REM 3. 初始化 testdb2 (业务数据源)
echo.
echo [3/5] 初始化 testdb2 (业务数据源)...
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d testdb2 -f schema-business.sql
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d testdb2 -f data-business.sql
if %errorlevel% neq 0 (
    echo ✗ testdb2 初始化失败
    goto :error
)
echo √ testdb2 初始化完成

REM 4. 初始化 testdb3 (日志数据源)
echo.
echo [4/5] 初始化 testdb3 (日志数据源)...
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d testdb3 -f schema-log.sql
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d testdb3 -f data-log.sql
if %errorlevel% neq 0 (
    echo ✗ testdb3 初始化失败
    goto :error
)
echo √ testdb3 初始化完成

REM 5. 初始化 testdb4 (报表数据源)
echo.
echo [5/5] 初始化 testdb4 (报表数据源)...
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d testdb4 -f schema-report.sql
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d testdb4 -f data-report.sql
if %errorlevel% neq 0 (
    echo ✗ testdb4 初始化失败
    goto :error
)
echo √ testdb4 初始化完成

REM 清除密码环境变量
set PGPASSWORD=

echo.
echo ==========================================
echo √ 所有数据库初始化完成！
echo ==========================================
echo.
echo 数据库清单:
echo   - testdb1 (default)   -^> jdbc:postgresql://localhost:5432/testdb1
echo   - testdb2 (business)  -^> jdbc:postgresql://localhost:5432/testdb2
echo   - testdb3 (log)       -^> jdbc:postgresql://localhost:5432/testdb3
echo   - testdb4 (report)    -^> jdbc:postgresql://localhost:5432/testdb4
echo.
goto :end

:error
set PGPASSWORD=
echo.
echo ✗ 初始化过程中出现错误，请检查日志
exit /b 1

:end
endlocal