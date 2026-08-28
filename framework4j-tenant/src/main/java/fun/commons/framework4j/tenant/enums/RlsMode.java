package fun.commons.framework4j.tenant.enums;

/**
 * RLS(行级安全)干预模式(benefit4j V1.4.1 已验证的渐进路径)。
 */
public enum RlsMode {

    /** 不干预(默认)。 */
    OFF,

    /**
     * 策略就位:业务表 ENABLE RLS + tenant_isolation 策略,但<strong>不 FORCE</strong>
     * —— 表 owner/superuser 连接零行为变化,应用完全无感(零风险起步形态)。
     */
    POLICY,

    /**
     * 全量强制:POLICY 基础上连接层 set_config('app.tenant_id', ...) + FORCE RLS。
     * 要求应用连接使用非 owner 角色(否则 FORCE 对 owner 仍不生效)。
     */
    FULL
}
