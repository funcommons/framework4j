package fun.commons.framework4j.tenant.config;

import fun.commons.framework4j.tenant.enums.DdlMode;
import fun.commons.framework4j.tenant.enums.RlsMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自动装配行为契约:
 *   ① 默认关闭(引入依赖不等于生效)
 *   ② 显式开启 + 合法 table-prefix → 激活且配置绑定
 *   ③ 开启但缺/坏 table-prefix → 启动即失败(fail-fast,防幽灵表名)
 *   ④ expire-seconds 超 12h 上限(文档 §5.2)→ 启动即失败
 */
class TenantAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantAutoConfiguration.class));

    @Test
    @DisplayName("默认关闭:不配 enabled 时不装配")
    void disabledByDefault() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(TenantAutoConfiguration.class));
    }

    @Test
    @DisplayName("显式 enabled=false 同样不装配")
    void explicitlyDisabled() {
        runner.withPropertyValues("framework4j.tenant.enabled=false").run(ctx ->
                assertThat(ctx).doesNotHaveBean(TenantAutoConfiguration.class));
    }

    @Test
    @DisplayName("enabled=true + table-prefix → 激活,嵌套配置逐项绑定")
    void enabledWithPrefix_bindsAllLevels() {
        runner.withPropertyValues(
                        "framework4j.tenant.enabled=true",
                        "framework4j.tenant.table-prefix=ubma_",
                        "framework4j.tenant.ddl-mode=PROVIDED",
                        "framework4j.tenant.auth.token-type=APP",
                        "framework4j.tenant.auth.expire-seconds=7200",
                        "framework4j.tenant.secret.grace-hours=48",
                        "framework4j.tenant.registration-key.enabled=true",
                        "framework4j.tenant.rls.mode=POLICY")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TenantAutoConfiguration.class);
                    Framework4jTenantProperties p = ctx.getBean(Framework4jTenantProperties.class);
                    assertThat(p.getTablePrefix()).isEqualTo("ubma_");
                    assertThat(p.tenantTableName()).isEqualTo("ubma_tenant");
                    assertThat(p.getDdlMode()).isEqualTo(DdlMode.PROVIDED);
                    assertThat(p.getAuth().getTokenType()).isEqualTo("APP");          // benefit4j 兼容开关
                    assertThat(p.getAuth().getExpireSeconds()).isEqualTo(7200);
                    assertThat(p.getSecret().getGraceHours()).isEqualTo(48);
                    assertThat(p.getRegistrationKey().isEnabled()).isTrue();
                    assertThat(p.getRls().getMode()).isEqualTo(RlsMode.POLICY);
                });
    }

    @Test
    @DisplayName("enabled=true 但缺 table-prefix → 启动失败(校验信息含指引)")
    void missingPrefix_failsFast() {
        runner.withPropertyValues("framework4j.tenant.enabled=true").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(flatMessages(ctx.getStartupFailure())).contains("table-prefix");
        });
    }

    @Test
    @DisplayName("table-prefix 不守项目简码规范(大写/缺下划线)→ 启动失败")
    void badPrefix_failsFast() {
        runner.withPropertyValues(
                        "framework4j.tenant.enabled=true",
                        "framework4j.tenant.table-prefix=UBMA")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(flatMessages(ctx.getStartupFailure())).contains("ubma_");
                });
    }

    @Test
    @DisplayName("expire-seconds 超 12h 上限 → 启动失败(文档 §5.2 硬上限)")
    void expireSecondsOverLimit_failsFast() {
        runner.withPropertyValues(
                        "framework4j.tenant.enabled=true",
                        "framework4j.tenant.table-prefix=ubma_",
                        "framework4j.tenant.auth.expire-seconds=50000")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(flatMessages(ctx.getStartupFailure())).contains("43200");
                });
    }

    /** 拼接异常链全部 message,避免断言落在包装层 */
    private static String flatMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        while (t != null) {
            if (t.getMessage() != null) {
                sb.append(t.getMessage()).append(" | ");
            }
            t = t.getCause();
        }
        return sb.toString();
    }
}
