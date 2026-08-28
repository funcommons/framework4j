package fun.commons.framework4j.tenant.config;

import fun.commons.framework4j.tenant.enums.DdlMode;
import fun.commons.framework4j.tenant.enums.RlsMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配置默认值契约 —— 与「framework4j-tenant模块设计 v1.1 §4」逐项对应。
 * 默认值即契约:改动任何一项都意味着消费方升级行为变化,须过设计文档。
 */
class Framework4jTenantPropertiesTest {

    @Test
    @DisplayName("§4 默认值全表:零配置可用,关键安全项全部安全默认")
    void defaults_matchDesign() {
        Framework4jTenantProperties p = new Framework4jTenantProperties();

        // 总开关:默认关闭(含 DDL/端点注册,必须显式开启)
        assertThat(p.isEnabled()).isFalse();
        assertThat(p.getTablePrefix()).isEmpty();
        assertThat(p.getDdlMode()).isEqualTo(DdlMode.AUTO);

        // 认证端点
        assertThat(p.getAuth().isEnabled()).isTrue();
        assertThat(p.getAuth().getPath()).isEqualTo("/api/v1/auth/token");
        assertThat(p.getAuth().getMaxFail()).isEqualTo(5);
        assertThat(p.getAuth().getLockMinutes()).isEqualTo(15);
        assertThat(p.getAuth().getTokenType()).isEqualTo("TENANT");
        assertThat(p.getAuth().getExpireSeconds()).isEqualTo(28800);   // 8h(文档 §5.2)

        // 平台合成租户(tenant_id=0)
        assertThat(p.getPlatform().getClientId()).isEqualTo("PLATFORM");
        assertThat(p.getPlatform().getClientSecret()).isEmpty();

        // 密钥宽限期
        assertThat(p.getSecret().getGraceHours()).isEqualTo(24);

        // 注册码通道:默认关(通道 B,按需开)
        assertThat(p.getRegistrationKey().isEnabled()).isFalse();
        assertThat(p.getRegistrationKey().getDefaultUses()).isEqualTo(1);
        assertThat(p.getRegistrationKey().getDefaultTtlHours()).isEqualTo(24);

        // RLS:默认不干预
        assertThat(p.getRls().getMode()).isEqualTo(RlsMode.OFF);
    }

    @Test
    @DisplayName("租户表名 = {table-prefix}tenant(D-1 B 方案单一 SSOT)")
    void tenantTableName_composesPrefix() {
        Framework4jTenantProperties p = new Framework4jTenantProperties();
        p.setTablePrefix("ubma_");
        assertThat(p.tenantTableName()).isEqualTo("ubma_tenant");

        p.setTablePrefix("demo_");
        assertThat(p.tenantTableName()).isEqualTo("demo_tenant");
    }
}
