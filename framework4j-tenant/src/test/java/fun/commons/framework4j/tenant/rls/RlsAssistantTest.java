package fun.commons.framework4j.tenant.rls;

import fun.commons.framework4j.tenant.config.Framework4jTenantProperties;
import fun.commons.framework4j.tenant.enums.RlsMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RLS 助手(H2 不支持 PG RLS 语法,本测试只验证 OFF/POLICY 的调用面与表不存在跳过;
 * PG 真库链路由 tenant-tck 与 benefit4j 接入覆盖)。
 */
class RlsAssistantTest {

    private static final AtomicInteger DB_SEQ = new AtomicInteger();

    private static Framework4jTenantProperties props(RlsMode mode) {
        Framework4jTenantProperties p = new Framework4jTenantProperties();
        p.setTablePrefix("demo_");
        p.getRls().setMode(mode);
        return p;
    }

    @Test
    @DisplayName("OFF:不执行任何 SQL")
    void off_noop() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:rls_off;DB_CLOSE_DELAY=-1", "sa", "");
        RlsAssistant assistant = new RlsAssistant(props(RlsMode.OFF), ds);
        assistant.apply(List.of("ubmx_account"));   // 不抛即过(OFF 直接返回)
    }

    @Test
    @DisplayName("POLICY:表不存在跳过不炸(H2 无 RLS 语法,存在表才会执行 —— 这里只验证跳过路径)")
    void policy_skipsMissingTable() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:rls_policy;DB_CLOSE_DELAY=-1", "sa", "");
        RlsAssistant assistant = new RlsAssistant(props(RlsMode.POLICY), ds);
        assistant.apply(List.of("nonexistent_table"));   // 表不存在 → WARN 跳过
    }

    @Test
    @DisplayName("POLICY:空清单 WARN 跳过")
    void policy_emptyList() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:rls_empty;DB_CLOSE_DELAY=-1", "sa", "");
        RlsAssistant assistant = new RlsAssistant(props(RlsMode.POLICY), ds);
        assistant.apply(List.of());
    }

    @Test
    @DisplayName("setTenantConfigSql 模板")
    void setTenantConfigSql() {
        assertThat(RlsAssistant.setTenantConfigSql(12345L))
                .isEqualTo("SELECT set_config('app.tenant_id', '12345', false)");
    }
}
