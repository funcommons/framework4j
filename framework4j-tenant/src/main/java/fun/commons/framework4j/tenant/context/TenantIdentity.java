package fun.commons.framework4j.tenant.context;

import fun.commons.framework4j.accesstoken.context.TokenContext;
import fun.commons.framework4j.tenant.interceptor.DomainGuardInterceptor;

/**
 * 租户身份解析(业务取数层单点收口,§3.2 规约 2 的代码化)。
 * <p>
 * 解析序:token claim 优先 → 缺省回落默认租户(单租户模式)→ 都没有返回 null。
 * 业务 Service/Query 层一律经此取 tenant_id,不直接读 TokenContext ——
 * 否则单租户模式下无 claim 的请求在守卫放行后,业务层取到的仍是 null(写库 tenant_id=null)。
 */
public final class TenantIdentity {

    private TenantIdentity() {
    }

    /**
     * 当前租户 id:claim 优先;无 claim 时回落默认租户;均无 → null(多租户模式,调用方应拒绝)。
     */
    public static Long currentTenantId(Long defaultTenantId) {
        Object claim = TokenContext.getClaim(DomainGuardInterceptor.CLAIM_TENANT_ID);
        if (claim instanceof Number number) {
            return number.longValue();
        }
        if (claim != null) {
            try {
                return Long.parseLong(String.valueOf(claim));
            } catch (NumberFormatException e) {
                return null;   // claim 非法:不回落默认租户(不用默认值掩盖认证问题)
            }
        }
        return defaultTenantId;
    }

    /**
     * 必须解析成功,否则抛 SecurityException(403 语义)。
     */
    public static long requireTenantId(Long defaultTenantId) {
        Long id = currentTenantId(defaultTenantId);
        if (id == null) {
            throw new SecurityException("缺少租户身份(无 tenant_id claim 且未配置默认租户)");
        }
        return id;
    }
}
