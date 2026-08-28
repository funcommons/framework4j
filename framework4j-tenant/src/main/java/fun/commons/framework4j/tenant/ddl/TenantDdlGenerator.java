package fun.commons.framework4j.tenant.ddl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户表 DDL 生成器 —— 列定义单一 SSOT(与 {@link fun.commons.framework4j.tenant.entity.TenantEntity} 字段一一对应)。
 * <ul>
 *   <li>PG:正式形态(租户设计 §3.1)—— JSONB / TIMESTAMPTZ / GIN / 部分唯一索引</li>
 *   <li>H2:demo/test 兼容降级 —— JSONB→TEXT、无 GIN/部分索引;<strong>列集与 PG 完全一致</strong></li>
 * </ul>
 * 仅静态方法,不连库;执行由 {@link TenantDdlInitializer} 负责。
 */
public final class TenantDdlGenerator {

    private TenantDdlGenerator() {
    }

    /** 列名 → 两方言定义(顺序即建表列序,id 打头,逻辑删除收尾) */
    private static final Map<String, String[]> COLUMNS = new LinkedHashMap<>();

    static {
        // {PG 定义, H2 定义}
        col("id", "BIGINT PRIMARY KEY", "BIGINT PRIMARY KEY");
        col("name", "VARCHAR(64) NOT NULL", "VARCHAR(64) NOT NULL");
        col("description", "VARCHAR(255)", "VARCHAR(255)");
        col("email", "VARCHAR(128)", "VARCHAR(128)");
        col("channel", "VARCHAR(16) NOT NULL DEFAULT 'OPS'", "VARCHAR(16) NOT NULL DEFAULT 'OPS'");
        col("status", "VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'", "VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'");
        col("tenant_secret", "VARCHAR(256) NOT NULL", "VARCHAR(256) NOT NULL");
        col("tenant_secret_prev", "VARCHAR(256)", "VARCHAR(256)");
        col("tenant_secret_prev_at", "TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE");
        col("privileges", "JSONB NOT NULL DEFAULT '{}'", "TEXT NOT NULL DEFAULT '{}'");
        col("config", "JSONB NOT NULL DEFAULT '{}'", "TEXT NOT NULL DEFAULT '{}'");
        col("oem", "JSONB NOT NULL DEFAULT '{}'", "TEXT NOT NULL DEFAULT '{}'");
        col("ext", "JSONB NOT NULL DEFAULT '{}'", "TEXT NOT NULL DEFAULT '{}'");
        col("created_at", "TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP",
                "TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP");
        col("updated_at", "TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP",
                "TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP");
        col("create_by", "VARCHAR(64)", "VARCHAR(64)");
        col("update_by", "VARCHAR(64)", "VARCHAR(64)");
        col("is_deleted", "SMALLINT NOT NULL DEFAULT 0", "SMALLINT NOT NULL DEFAULT 0");
    }

    private static void col(String name, String pg, String h2) {
        COLUMNS.put(name, new String[]{pg, h2});
    }

    /**
     * 幂等建表语句(单元素列表,整段执行)。
     */
    public static List<String> createTable(String table, boolean postgres) {
        StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(table).append(" (\n");
        int i = 0;
        for (Map.Entry<String, String[]> e : COLUMNS.entrySet()) {
            sb.append("    ").append(e.getKey()).append(" ").append(e.getValue()[postgres ? 0 : 1]);
            if (++i < COLUMNS.size()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(")");
        return List.of(sb.toString());
    }

    /**
     * 索引语句:PG 建全部(ext GIN + email 部分唯一);H2 不支持 GIN/部分索引,返回空。
     */
    public static List<String> indexes(String table, boolean postgres) {
        if (!postgres) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        out.add("CREATE INDEX IF NOT EXISTS idx_" + table + "_ext ON " + table + " USING GIN (ext)");
        out.add("CREATE UNIQUE INDEX IF NOT EXISTS uk_" + table + "_email ON " + table + " (email) "
                + "WHERE is_deleted = 0 AND email IS NOT NULL");
        return out;
    }

    /**
     * 缺列补列语句(只在列缺失时使用;两方言同形:ADD COLUMN IF NOT EXISTS)。
     */
    public static String addColumn(String table, String column, boolean postgres) {
        String[] def = COLUMNS.get(column);
        if (def == null) {
            throw new IllegalArgumentException("非契约列: " + column);
        }
        return "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + column + " " + def[postgres ? 0 : 1];
    }

    /**
     * 全部契约列名(补列比对用)。
     */
    public static List<String> contractColumns() {
        return List.copyOf(COLUMNS.keySet());
    }

    /**
     * PROVIDED 模式 SQL 模板:建表 + 索引 + 补列示例,可整段贴进项目迁移工具(Flyway 等)。
     */
    public static String providedTemplate(String table) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- framework4j-tenant 租户表 DDL 模板(表 ").append(table).append(",契约层 §3.1)\n");
        for (String s : createTable(table, true)) {
            sb.append(s).append(";\n");
        }
        for (String s : indexes(table, true)) {
            sb.append(s).append(";\n");
        }
        sb.append("-- 存量表补列(按需执行,幂等):\n");
        for (String c : COLUMNS.keySet()) {
            sb.append(addColumn(table, c, true)).append(";\n");
        }
        return sb.toString();
    }
}
