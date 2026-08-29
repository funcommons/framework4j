package fun.commons.framework4j.tenant.config;

import fun.commons.framework4j.tenant.enums.DdlMode;
import fun.commons.framework4j.tenant.enums.RlsMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * framework4j-tenant 配置属性 —— 中间件中台租户设计 v2.1 契约层的参数面
 * (framework4j-tenant模块设计 v1.1 §4)。
 * <p>
 * 对应 YAML 前缀: {@code framework4j.tenant}。默认值即契约,改动须过设计文档。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "framework4j.tenant")
public class Framework4jTenantProperties {

    /**
     * 是否启用。默认 false —— 本模块含 DDL 执行与端点注册,引入依赖不等于生效,必须显式开启。
     */
    private boolean enabled = false;

    /**
     * 租户表名前缀(项目简码_,如 ubma_/gwy_/demo_)。
     * 租户表 = {table-prefix}tenant —— 守「所有表以项目简码_开头」规范,框架不建自有命名空间(D-1 B 方案)。
     */
    @NotBlank(message = "table-prefix 不能为空 —— 租户表名 = {table-prefix}tenant,须以项目简码_开头(如 ubma_/gwy_)")
    @Pattern(regexp = "^[a-z][a-z0-9]{1,9}_$",
            message = "table-prefix 须为小写字母数字简码以下划线结尾,如 ubma_")
    private String tablePrefix = "";

    /**
     * DDL 管理模式(见 {@link DdlMode}),默认 AUTO 幂等建表。
     */
    private DdlMode ddlMode = DdlMode.AUTO;

    /**
     * 内置认证端点(client_credentials)。
     */
    @Valid
    private Auth auth = new Auth();

    /**
     * 平台合成租户(tenant_id=0)凭据。
     */
    @Valid
    private Platform platform = new Platform();

    /**
     * 密钥生命周期。
     */
    @Valid
    private Secret secret = new Secret();

    /**
     * 注册码通道(通道 B,默认关)。
     */
    @Valid
    private RegistrationKey registrationKey = new RegistrationKey();

    /**
     * RLS 行级安全。
     */
    @Valid
    private Rls rls = new Rls();

    /**
     * 租户表名 = {table-prefix}tenant。
     * 单一 SSOT:DDL 初始化器、结构断言(tck)、文档示例共用此拼装,不各自拼接。
     */
    public String tenantTableName() {
        return tablePrefix + "tenant";
    }

    /**
     * 认证端点(client_credentials:防爆破 / 平台合成租户 / 宽限期双版本)。
     */
    @Data
    public static class Auth {

        /**
         * 是否注册内置认证端点;false 时项目自带端点,逻辑委托 TenantAuthTemplate(Step 4)。
         */
        private boolean enabled = true;

        /**
         * 内置认证端点路径。
         */
        private String path = "/api/v1/auth/token";

        /**
         * 连续失败锁定阈值(次)。
         */
        @Positive
        private int maxFail = 5;

        /**
         * 锁定时长(分钟)。
         */
        @Positive
        private int lockMinutes = 15;

        /**
         * 签发的 token 型别名。默认 TENANT;
         * 存量项目(如 benefit4j)可配 APP 使签发型别与存量会话一致,避免迁移期全部失效。
         */
        private String tokenType = "TENANT";

        /**
         * token 有效期(秒)。默认 8h;文档 §5.2 硬上限 12h(M2M 无 refresh)。
         */
        @Positive
        @Max(value = 43200, message = "expire-seconds 上限 43200(12h,租户设计 §5.2:M2M token 不发 refresh)")
        private long expireSeconds = 28800;
    }

    /**
     * 平台合成租户凭据 —— 平台域管理面,不是记账主体(租户设计 §5.3)。
     */
    @Data
    public static class Platform {

        private String clientId = "PLATFORM";

        private String clientSecret = "";

        /**
         * 平台身份的 tenant_id 取值(合成租户,不依赖 DB 行)。默认 0 ——
         * 真实租户 id 为雪花正整数,0 天然不冲突;双面守卫与认证模板共用此值。
         */
        @jakarta.validation.constraints.Min(0)
        private long tenantId = 0;
    }

    /**
     * 密钥生命周期。
     */
    @Data
    public static class Secret {

        /**
         * 密钥轮换宽限期(小时):reset 后旧密钥入 prev,窗口内仍可换 token(双版本过渡,§5.5)。
         */
        @Positive
        private int graceHours = 24;
    }

    /**
     * 注册码通道(租户设计 §6.2:信任 L2 —— 凭注册码自助注册,凭码即 ACTIVE)。
     */
    @Data
    public static class RegistrationKey {

        /**
         * 是否开启(默认关,按需开)。
         */
        private boolean enabled = false;

        /**
         * 单码默认可用次数。
         */
        @Positive
        private int defaultUses = 1;

        /**
         * 单码默认有效期(小时)。
         */
        @Positive
        private int defaultTtlHours = 24;
    }

    /**
     * RLS 行级安全(渐进:OFF → POLICY 就位不 FORCE → FULL 连接层强制)。
     */
    @Data
    public static class Rls {

        private RlsMode mode = RlsMode.OFF;
    }
}
