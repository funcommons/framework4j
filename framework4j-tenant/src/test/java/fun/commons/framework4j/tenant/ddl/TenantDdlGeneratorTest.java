package fun.commons.framework4j.tenant.ddl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDL 生成方言契约:PG 为正式形态(租户设计 §3.1),H2 为 demo/test 兼容降级。
 */
class TenantDdlGeneratorTest {

    private static final List<String> PG_CREATE = TenantDdlGenerator.createTable("demo_tenant", true);
    private static final String PG_ALL = String.join("\n", PG_CREATE);

    @Test
    @DisplayName("PG:四 JSONB 列 NOT NULL DEFAULT '{}' + GIN(ext) + email 部分唯一索引")
    void postgres_ddl() {
        assertThat(PG_CREATE.get(0)).contains("CREATE TABLE IF NOT EXISTS demo_tenant");
        for (String col : new String[]{"privileges", "config", "oem", "ext"}) {
            assertThat(PG_ALL).contains(col + " JSONB NOT NULL DEFAULT '{}'");
        }
        assertThat(PG_ALL).contains("tenant_secret VARCHAR(256) NOT NULL");
        assertThat(PG_ALL).contains("status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'");
        assertThat(PG_ALL).contains("channel VARCHAR(16) NOT NULL DEFAULT 'OPS'");
        assertThat(PG_ALL).contains("created_at TIMESTAMPTZ");

        List<String> indexes = TenantDdlGenerator.indexes("demo_tenant", true);
        assertThat(String.join(";", indexes))
                .contains("CREATE INDEX IF NOT EXISTS idx_demo_tenant_ext ON demo_tenant USING GIN (ext)")
                .contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_demo_tenant_email ON demo_tenant (email)")
                .contains("WHERE is_deleted = 0 AND email IS NOT NULL");
    }

    @Test
    @DisplayName("H2 降级:JSONB→TEXT,无 GIN/部分索引(建表列集与 PG 完全一致)")
    void h2_ddl() {
        String h2All = String.join("\n", TenantDdlGenerator.createTable("demo_tenant", false));
        assertThat(h2All).contains("privileges TEXT NOT NULL DEFAULT '{}'");
        assertThat(h2All).doesNotContain("JSONB");
        assertThat(h2All).doesNotContain("TIMESTAMPTZ");   // → TIMESTAMP WITH TIME ZONE

        Set<String> pgCols = columnsOf(PG_CREATE);
        Set<String> h2Cols = columnsOf(TenantDdlGenerator.createTable("demo_tenant", false));
        assertThat(h2Cols).as("两方言列集一致(类型降级不丢列)").isEqualTo(pgCols);

        assertThat(TenantDdlGenerator.indexes("demo_tenant", false)).isEmpty();
    }

    @Test
    @DisplayName("PROVIDED 模板:含建表+索引+补列示例,可整段贴进 Flyway 迁移")
    void providedTemplate() {
        String tpl = TenantDdlGenerator.providedTemplate("ubma_tenant");
        assertThat(tpl).contains("CREATE TABLE IF NOT EXISTS ubma_tenant");
        assertThat(tpl).contains("USING GIN (ext)");
        assertThat(tpl).contains("ALTER TABLE ubma_tenant ADD COLUMN IF NOT EXISTS");
    }

    /** 从 CREATE 语句抽取列名集(行首缩进列定义) */
    private static Set<String> columnsOf(List<String> ddl) {
        return ddl.stream()
                .filter(l -> l.trim().matches("^[a-z_]+\\s+(BIGINT|VARCHAR|JSONB|TEXT|SMALLINT|TIMESTAMPTZ|TIMESTAMP).*"))
                .map(l -> l.trim().split("\\s+")[0])
                .collect(Collectors.toSet());
    }
}
