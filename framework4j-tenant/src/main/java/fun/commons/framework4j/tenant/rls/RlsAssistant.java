package fun.commons.framework4j.tenant.rls;

import fun.commons.framework4j.tenant.config.Framework4jTenantProperties;
import fun.commons.framework4j.tenant.enums.RlsMode;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

/**
 * RLS 助手(渐进:OFF → POLICY 就位不 FORCE → FULL 连接层强制)。
 * <p>
 * POLICY(默认起步):对业务表执行 ENABLE ROW LEVEL SECURITY + tenant_isolation 策略
 * (current_setting('app.tenant_id', true)::bigint = tenant_id),但<strong>不 FORCE</strong> ——
 * 表 owner/superuser 连接零行为变化,应用完全无感(零风险起步形态)。
 * <p>
 * FULL:POLICY 基础上 FORCE + 连接层 {@code set_config('app.tenant_id', tenantId, false)} ——
 * 要求应用连接使用非 owner 角色(否则 FORCE 对 owner 仍不生效)。
 * <p>
 * 幂等:重复执行安全(PG CREATE POLICY IF NOT EXISTS / ALTER TABLE IF EXISTS 语义);
 * 表不存在跳过(新服务可后补)。
 */
@Slf4j
public class RlsAssistant {

    private final Framework4jTenantProperties properties;
    private final DataSource dataSource;

    public RlsAssistant(Framework4jTenantProperties properties, DataSource dataSource) {
        this.properties = properties;
        this.dataSource = dataSource;
    }

    /**
     * 按配置模式执行。OFF → 直接返回;POLICY/FULL → 对业务表批量执行。
     */
    public void apply(List<String> businessTables) {
        RlsMode mode = properties.getRls().getMode();
        if (mode == RlsMode.OFF) {
            log.info("【Tenant】RLS mode=OFF,不干预");
            return;
        }
        if (businessTables == null || businessTables.isEmpty()) {
            log.warn("【Tenant】RLS mode={} 但未配置业务表清单(framework4j.tenant.rls.tables),跳过", mode);
            return;
        }

        String policy = "tenant_isolation";
        String tenantCol = "tenant_id";
        String settingKey = "app.tenant_id";

        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            for (String table : businessTables) {
                if (!tableExists(st, table)) {
                    log.warn("【Tenant】RLS 跳过(表不存在): {}", table);
                    continue;
                }
                st.execute(String.format(
                        "ALTER TABLE %s ENABLE ROW LEVEL SECURITY", table));
                st.execute(String.format(
                        "DROP POLICY IF EXISTS %s ON %s", policy, table));
                st.execute(String.format(
                        "CREATE POLICY %s ON %s USING (%s = current_setting('%s', true)::bigint)",
                        policy, table, tenantCol, settingKey));
                if (mode == RlsMode.FULL) {
                    st.execute(String.format("ALTER TABLE %s FORCE ROW LEVEL SECURITY", table));
                }
            }
            log.info("【Tenant】RLS 就位: mode={}, 表数={}, policy={}, FORCE={}",
                    mode, businessTables.size(), policy, mode == RlsMode.FULL);
        } catch (Exception e) {
            throw new IllegalStateException("【Tenant】RLS 执行失败: mode=" + mode, e);
        }
    }

    private static boolean tableExists(Statement st, String table) throws Exception {
        try (var rs = st.executeQuery(
                "SELECT 1 FROM information_schema.tables WHERE table_name = '" + table + "'")) {
            return rs.next();
        }
    }

    /**
     * FULL 模式连接层绑定(由项目的事务/拦截器在请求入口调用):
     * {@code SET LOCAL app.tenant_id = '...'} 或 {@code set_config(..., false)}。
     * 本类只提供 SQL 模板,绑定时机由项目控制(连接池/事务边界各异)。
     */
    public static String setTenantConfigSql(long tenantId) {
        return String.format("SELECT set_config('app.tenant_id', '%d', false)", tenantId);
    }
}
