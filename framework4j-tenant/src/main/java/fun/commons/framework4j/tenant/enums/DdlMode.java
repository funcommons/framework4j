package fun.commons.framework4j.tenant.enums;

/**
 * 租户表 DDL 管理模式(framework4j-tenant模块设计 v1.1 D-1)。
 */
public enum DdlMode {

    /**
     * 框架在启动时按配置表名({table-prefix}tenant)执行幂等 DDL:
     * CREATE TABLE IF NOT EXISTS + 缺列补列。零迁移工具的项目直接可用。
     */
    AUTO,

    /**
     * 框架仅输出 SQL 模板(日志/文档),由项目迁移工具(Flyway 等)自行管理。
     * 结构契约仍由 TenantEntity 基类 + tenant-tck 结构断言守护。
     */
    PROVIDED
}
