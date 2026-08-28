package fun.commons.framework4j.tenant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * framework4j-tenant 自动配置(Step 1 骨架)。
 * <p>
 * 实施计划(framework4j-tenant模块设计 v1.1 §7,共 7 步):
 * Step1 骨架(本类)→ Step2 租户表/实体 SPI → Step3 双面守卫注解 → Step4 认证端点
 * → Step5 密钥/注册码 → Step6 UserIdContext/RLS → Step7 tenant-tck。
 * <p>
 * 与多数模块不同:本模块默认关闭({@code framework4j.tenant.enabled=false}),
 * 必须显式开启 —— 启用即含 DDL 执行({@code ddl-mode: AUTO})与认证端点注册。
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(Framework4jTenantProperties.class)
@ConditionalOnProperty(prefix = "framework4j.tenant", name = "enabled", havingValue = "true")
public class TenantAutoConfiguration {

    public TenantAutoConfiguration(Framework4jTenantProperties properties) {
        log.info("【Tenant】framework4j-tenant 已启用 —— 租户表={}, ddl-mode={}, 认证端点={}(型别 {}, {}s), "
                        + "注册码通道={}, rls={}",
                properties.tenantTableName(), properties.getDdlMode(),
                properties.getAuth().getPath(), properties.getAuth().getTokenType(),
                properties.getAuth().getExpireSeconds(),
                properties.getRegistrationKey().isEnabled() ? "开" : "关",
                properties.getRls().getMode());
    }
}
