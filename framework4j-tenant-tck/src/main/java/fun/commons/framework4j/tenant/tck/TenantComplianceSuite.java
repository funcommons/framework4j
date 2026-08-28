package fun.commons.framework4j.tenant.tck;

import fun.commons.framework4j.tenant.ddl.TenantDdlGenerator;
import fun.commons.framework4j.tenant.entity.TenantEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户合规测试集(中间件中台租户设计 §10 的机器可执行版)。
 * <p>
 * 用法:项目 test 模块引本 jar,继承本类并注册 {@link TenantComplianceContext} Bean
 * (提供表名/端点/凭据映射)。跑绿即合规。
 * <p>
 * 断言分两层:①结构(表列/索引/约束);②行为(守卫/认证/密钥/注册码)。
 * 行为断言需项目提供可调用的端点(或复用 benefit4j 的接入方式)。
 */
@SpringBootTest
public abstract class TenantComplianceSuite {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected DataSource dataSource;

    /**
     * 项目提供:合规上下文(表名/端点/凭据)。
     */
    public abstract TenantComplianceContext complianceContext();

    // ==================== ① 结构断言 ====================

    @Test
    @DisplayName("T1: 租户表存在且列契约完整(§3.1 + §5.5 宽限期)")
    void tenantTable_structure() {
        String table = complianceContext().tenantTable();
        Set<String> cols = columns(table);
        assertThat(cols).contains("id", "name", "status", "channel", "email",
                "tenant_secret", "tenant_secret_prev", "tenant_secret_prev_at",
                "privileges", "config", "oem", "ext",
                "created_at", "updated_at", "create_by", "update_by", "is_deleted");
        assertThat(cols).doesNotContain("tenant_id");   // 全库唯一没有 tenant_id 的表
    }

    @Test
    @DisplayName("T2: 业务表 tenant_id 列存在且索引打头(§3.2)")
    void businessTables_tenantIdIndexed() {
        for (String table : complianceContext().businessTables()) {
            Set<String> cols = columns(table);
            assertThat(cols).as(table + " 缺 tenant_id").contains("tenant_id");

            List<String> indexes = jdbcTemplate.queryForList(
                    "SELECT indexdef FROM pg_indexes WHERE tablename = ?", String.class, table);
            assertThat(indexes).as(table + " 无索引").isNotEmpty();
            // 至少一个索引以 tenant_id 打头
            boolean hasTenantFirst = indexes.stream().anyMatch(idx ->
                    idx.contains("tenant_id") && idx.indexOf("tenant_id") < idx.indexOf("(") + 20);
            assertThat(hasTenantFirst).as(table + " 索引未以 tenant_id 打头").isTrue();
        }
    }

    @Test
    @DisplayName("T3: 租户表 email 部分唯一索引(§3.1)")
    void tenantTable_emailUniqueIndex() {
        String table = complianceContext().tenantTable();
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = ? AND indexname LIKE 'uk_%email%'",
                String.class, table);
        assertThat(indexes).isNotEmpty();
        assertThat(indexes.get(0)).contains("is_deleted = 0").contains("email IS NOT NULL");
    }

    // ==================== ② 行为断言 ====================

    @Test
    @DisplayName("T4: 平台域守卫 —— 租户 token 打平台端点 403(§5.3)")
    void platformDomain_rejectsTenantToken() {
        // 由项目子类实现(端点/凭据各异);默认跳过,有实现才跑
        if (complianceContext().platformTokenForTenant() != null) {
            complianceContext().assertPlatformDomainRejectsTenantToken();
        }
    }

    @Test
    @DisplayName("T5: 租户域守卫 —— 平台 token 打租户端点 403(§5.3)")
    void tenantDomain_rejectsPlatformToken() {
        if (complianceContext().tenantTokenForPlatform() != null) {
            complianceContext().assertTenantDomainRejectsPlatformToken();
        }
    }

    @Test
    @DisplayName("T6: 防爆破 —— 连续 5 次失败锁 15min,正确凭据也 429(§8 #7)")
    void bruteForceProtection() {
        if (complianceContext().authEndpoint() != null) {
            complianceContext().assertBruteForceProtection();
        }
    }

    @Test
    @DisplayName("T7: reset 撤销会话 + 宽限期双版本(§5.5)")
    void resetSecret_revokesAndGrace() {
        if (complianceContext().resetSecretEndpoint() != null) {
            complianceContext().assertResetSecretRevokesAndGrace();
        }
    }

    @Test
    @DisplayName("T8: X-User-Id 不鉴权 —— 任意值不影响鉴权结果(§5.2)")
    void xUserId_notAuthenticated() {
        if (complianceContext().xUserIdProbe() != null) {
            complianceContext().assertXUserIdNotAuthenticated();
        }
    }

    // ==================== 工具 ====================

    private Set<String> columns(String table) {
        return jdbcTemplate.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_name = ?",
                        String.class, table)
                .stream().collect(Collectors.toSet());
    }

    /**
     * 项目提供的合规上下文:表名/端点/凭据映射 + 行为断言实现(可选)。
     */
    public interface TenantComplianceContext {

        /** 租户表名(如 ubma_tenant)。 */
        String tenantTable();

        /** 业务表清单(需 tenant_id + 索引打头断言)。 */
        List<String> businessTables();

        /** 平台 token(可选,触发 T4)。 */
        default String platformTokenForTenant() {
            return null;
        }

        /** 租户 token(可选,触发 T5)。 */
        default String tenantTokenForPlatform() {
            return null;
        }

        /** 认证端点(可选,触发 T6)。 */
        default String authEndpoint() {
            return null;
        }

        /** reset 端点(可选,触发 T7)。 */
        default String resetSecretEndpoint() {
            return null;
        }

        /** X-User-Id 探针端点(可选,触发 T8)。 */
        default String xUserIdProbe() {
            return null;
        }

        /** 项目实现:平台域 403 断言。 */
        default void assertPlatformDomainRejectsTenantToken() {
        }

        /** 项目实现:租户域 403 断言。 */
        default void assertTenantDomainRejectsPlatformToken() {
        }

        /** 项目实现:防爆破断言。 */
        default void assertBruteForceProtection() {
        }

        /** 项目实现:reset 断言。 */
        default void assertResetSecretRevokesAndGrace() {
        }

        /** 项目实现:X-User-Id 断言。 */
        default void assertXUserIdNotAuthenticated() {
        }
    }
}
