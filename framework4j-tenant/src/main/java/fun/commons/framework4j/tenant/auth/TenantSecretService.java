package fun.commons.framework4j.tenant.auth;

import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.tenant.entity.TenantEntity;
import fun.commons.framework4j.tenant.store.TenantStore;
import fun.commons.framework4j.web.ApiResponse;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 租户密钥生命周期(§5.5):reset(双版本过渡)+ 会话撤销 + 明文只显一次。
 * <p>
 * reset 三步:①旧密钥挪入 prev + prevAt(宽限期起点);②新密钥落库(TypeHandler 自动加密);
 * ③撤销该租户全部存量会话(持旧 token 者立即失效,宽限期内旧密钥仍可换新 token)。
 * <p>
 * 响应只含新密钥明文 + OpenID —— 这是密钥唯一一次明文出现,之后 DB 只存密文、接口只回脱敏。
 */
@Slf4j
public class TenantSecretService {

    private final TenantStore tenantStore;
    private final TenantSessionRevoker sessionRevoker;

    public TenantSecretService(TenantStore tenantStore, TenantSessionRevoker sessionRevoker) {
        this.tenantStore = tenantStore;
        this.sessionRevoker = sessionRevoker;
    }

    /**
     * 重置密钥(平台域运营操作)。返回新密钥明文(只此一次)。
     */
    public ApiResponse<Map<String, Object>> reset(Long tenantId) {
        TenantEntity tenant = tenantStore.findActiveById(tenantId);
        if (tenant == null) {
            return ApiResponse.fail(404, "租户不存在或非 ACTIVE 状态");
        }

        String newSecret = UUID.randomUUID().toString().replace("-", "");
        // 旧密钥进宽限期列(§5.5 双版本过渡,默认 24h 内两把皆可换 token)
        tenant.setTenantSecretPrev(tenant.getTenantSecret());
        tenant.setTenantSecretPrevAt(OffsetDateTime.now());
        tenant.setTenantSecret(newSecret);
        tenant.setUpdatedAt(OffsetDateTime.now());
        tenantStore.update(tenant);

        sessionRevoker.revoke(tenantId);   // 重置即撤销存量会话

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", IdObfuscator.toOpenId(tenant.getId()));
        result.put("name", tenant.getName());
        result.put("tenant_secret", newSecret);   // 明文只显一次
        log.info("[TenantSecret] 密钥重置: tenantId={}, 宽限期起算", tenantId);
        return ApiResponse.success(result);
    }
}
