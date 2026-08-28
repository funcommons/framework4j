package fun.commons.framework4j.tenant.ddl;

import com.baomidou.mybatisplus.annotation.TableName;
import fun.commons.framework4j.tenant.config.Framework4jTenantProperties;
import fun.commons.framework4j.tenant.entity.TenantEntity;
import fun.commons.framework4j.tenant.enums.DdlMode;
import fun.commons.framework4j.tenant.schema.TenantSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DDL 初始化器(H2 真库):幂等建表 / 缺列补列 / PROVIDED 只输出 / SPI 一致性 fail-fast。
 * PG 真库链路由 tenant-tck 与 benefit4j 接入(P2)覆盖。
 */
class TenantDdlInitializerTest {

    private static final AtomicInteger DB_SEQ = new AtomicInteger();

    /** SPI 桩:子类实体,表名与 properties 前缀一致 */
    @TableName(value = "demo_tenant", autoResultMap = true)
    static class DemoTenant extends TenantEntity {
    }

    private static TenantSchema schema() {
        return () -> DemoTenant.class;
    }

    private static Framework4jTenantProperties props() {
        Framework4jTenantProperties p = new Framework4jTenantProperties();
        p.setTablePrefix("demo_");
        return p;
    }

    /** H2 命名内存库(DB_CLOSE_DELAY=-1):同库跑两次验证幂等 */
    private static DataSource h2() {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:tenantddl_" + DB_SEQ.incrementAndGet() + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    @Test
    @DisplayName("AUTO:建表列齐;重复执行幂等不炸")
    void auto_createsTableIdempotently() throws Exception {
        DataSource ds = h2();
        run(props(), schema(), ds);
        run(props(), schema(), ds);   // 二次执行 = 重启幂等

        try (Connection c = ds.getConnection()) {
            assertThat(tables(c)).contains("demo_tenant");
            assertThat(columns(c)).containsAll(Set.of("id", "tenant_secret", "tenant_secret_prev",
                    "tenant_secret_prev_at", "privileges", "config", "oem", "ext",
                    "channel", "email", "status", "created_at", "is_deleted"));
        }
    }

    @Test
    @DisplayName("AUTO:表已存在但缺列(项目存量表)→ 缺列补齐,已有列不动")
    void auto_backfillsMissingColumns() throws Exception {
        DataSource ds = h2();
        // 模拟项目存量表:老结构,只有 8 列(benefit4j P2 的典型形态)
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE demo_tenant (id BIGINT PRIMARY KEY, name VARCHAR(64), "
                    + "tenant_secret VARCHAR(256) NOT NULL, status VARCHAR(16), ext TEXT, "
                    + "created_at TIMESTAMP WITH TIME ZONE, updated_at TIMESTAMP WITH TIME ZONE, "
                    + "is_deleted SMALLINT DEFAULT 0)");
        }
        run(props(), schema(), ds);

        try (Connection c = ds.getConnection()) {
            Set<String> cols = columns(c);
            // 原有 8 列(id/name/tenant_secret/status/ext/created_at/updated_at/is_deleted)
            // + 契约补 10 列(description/email/channel/tenant_secret_prev/tenant_secret_prev_at/
            //   privileges/config/oem/create_by/update_by) = 18
            assertThat(cols).contains("privileges", "config", "oem", "channel", "email",
                    "tenant_secret_prev", "tenant_secret_prev_at", "description",
                    "create_by", "update_by");
            assertThat(cols).as("原有 8 列保留 + 补 10 列").hasSize(18);
        }
    }

    @Test
    @DisplayName("AUTO 无 DataSource:WARN 跳过不炸(纯 PROVIDED 项目可无库)")
    void autoWithoutDataSource_skips() throws Exception {
        TenantDdlInitializer init = new TenantDdlInitializer(props(), schema(), null);
        init.afterPropertiesSet();   // 不抛即过
    }

    @Test
    @DisplayName("PROVIDED:不执行任何 DDL,只输出模板")
    void provided_outputsOnly() throws Exception {
        Framework4jTenantProperties p = props();
        p.setDdlMode(DdlMode.PROVIDED);
        DataSource ds = h2();
        run(p, schema(), ds);

        try (Connection c = ds.getConnection()) {
            assertThat(tables(c)).as("PROVIDED 不得建表").doesNotContain("demo_tenant");
        }
    }

    @Test
    @DisplayName("SPI fail-fast:实体表名 ≠ {table-prefix}tenant → 启动失败")
    void tableNameMismatch_failsFast() {
        @TableName(value = "other_tenant", autoResultMap = true)
        class WrongTenant extends TenantEntity {
        }
        TenantDdlInitializer init = new TenantDdlInitializer(props(), () -> WrongTenant.class, h2());
        assertThatThrownBy(init::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demo_tenant")
                .hasMessageContaining("other_tenant");
    }

    @Test
    @DisplayName("SPI fail-fast:autoResultMap=false(密文读不回明文)→ 启动失败并给指引")
    void autoResultMapMissing_failsFast() {
        @TableName("demo_tenant")
        class NoResultMapTenant extends TenantEntity {
        }
        TenantDdlInitializer init = new TenantDdlInitializer(props(), () -> NoResultMapTenant.class, h2());
        assertThatThrownBy(init::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("autoResultMap = true");
    }

    private static void run(Framework4jTenantProperties p, TenantSchema s, DataSource ds) throws Exception {
        TenantDdlInitializer init = new TenantDdlInitializer(p, s, ds);
        init.afterPropertiesSet();
    }

    private static Set<String> tables(Connection c) throws SQLException {
        Set<String> out = new HashSet<>();
        try (ResultSet rs = c.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                out.add(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static Set<String> columns(Connection c) throws SQLException {
        Set<String> out = new HashSet<>();
        for (String variant : new String[]{"demo_tenant", "DEMO_TENANT"}) {   // H2 大写存储,双查
            try (ResultSet rs = c.getMetaData().getColumns(null, null, variant, "%")) {
                while (rs.next()) {
                    out.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        return out;
    }
}
