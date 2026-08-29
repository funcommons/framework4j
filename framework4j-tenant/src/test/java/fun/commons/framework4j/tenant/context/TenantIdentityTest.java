package fun.commons.framework4j.tenant.context;

import fun.commons.framework4j.accesstoken.context.TokenContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 业务取数层租户身份解析:claim 优先 / 默认租户回落 / 非法 claim 不回落。
 */
class TenantIdentityTest {

    @AfterEach
    void clear() {
        TokenContext.clear();
    }

    @Test
    @DisplayName("claim 优先:有 claim 时默认租户不生效")
    void claimWins() {
        TokenContext.set("APP", Map.of("tenant_id", 5L));
        assertThat(TenantIdentity.currentTenantId(1L)).isEqualTo(5L);

        TokenContext.set("APP", Map.of("tenant_id", "5"));   // 字符串形态
        assertThat(TenantIdentity.currentTenantId(1L)).isEqualTo(5L);
    }

    @Test
    @DisplayName("单租户模式:无 claim → 默认租户;require 通过")
    void defaultTenantFallback() {
        assertThat(TenantIdentity.currentTenantId(1L)).isEqualTo(1L);
        assertThat(TenantIdentity.requireTenantId(1L)).isEqualTo(1L);
    }

    @Test
    @DisplayName("多租户模式:无 claim 且无默认 → null;require 抛 SecurityException")
    void multiTenant_noClaim() {
        assertThat(TenantIdentity.currentTenantId(null)).isNull();
        assertThatThrownBy(() -> TenantIdentity.requireTenantId(null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("claim 非法(不可解析)→ null,不回落默认租户(不掩盖认证问题)")
    void invalidClaim_noFallback() {
        TokenContext.set("APP", Map.of("tenant_id", "abc"));
        assertThat(TenantIdentity.currentTenantId(1L)).isNull();
        assertThatThrownBy(() -> TenantIdentity.requireTenantId(1L))
                .isInstanceOf(SecurityException.class);
    }
}
