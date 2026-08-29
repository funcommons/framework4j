package fun.commons.framework4j.tenant.auth;

import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.tenant.config.Framework4jTenantProperties;
import fun.commons.framework4j.tenant.entity.TenantEntity;
import fun.commons.framework4j.tenant.store.TenantStore;
import fun.commons.framework4j.web.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 租户认证模板(client_credentials)—— benefit4j DefaultBenefitAuthService 的泛化,
 * 行为契约 = 租户设计 §5/§5.5/§8:
 * <ul>
 *   <li><b>平台合成租户</b>:client_id=平台凭据 → 签发 tenant_id=0(管理面,不依赖 DB 行)</li>
 *   <li><b>防爆破</b>:连续失败 {@code max-fail} 次锁 {@code lock-minutes}(429;固定窗口,成功清零)</li>
 *   <li><b>宽限期双版本</b>(§5.5):reset 后旧密钥在 {@code grace-hours} 内仍可换 token(懒校验)</li>
 *   <li><b>client_id 三形态</b>:OpenID(推荐,UI 暴露)/ 原始 Long id / name(向后兼容),仅 ACTIVE 可认证</li>
 *   <li>token 型别 = {@code framework4j.tenant.auth.token-type}(默认 TENANT,存量项目可配 APP)</li>
 * </ul>
 * 内置端点 {@link TenantAuthEndpoint} 委托本模板;项目自带端点时直接注入本模板即可。
 */
@Slf4j
public class TenantAuthTemplate {

    /** 防爆破计数 key 前缀:{appName}:tenant:auth:fail:{clientId} */
    public static final String FAIL_KEY_PREFIX = ":tenant:auth:fail:";

    private final Framework4jTenantProperties properties;
    private final TenantStore tenantStore;
    private final StringRedisTemplate redis;
    private final AccessTokenGenerator tokenGenerator;
    private final String appName;

    public TenantAuthTemplate(Framework4jTenantProperties properties, TenantStore tenantStore,
                              StringRedisTemplate redis, AccessTokenGenerator tokenGenerator, String appName) {
        this.properties = properties;
        this.tenantStore = tenantStore;
        this.redis = redis;
        this.tokenGenerator = tokenGenerator;
        this.appName = appName;
    }

    /**
     * 换 token(client_credentials)。响应码:0 成功 / 400 参数 / 401 凭据无效 / 429 防爆破锁定。
     */
    public ApiResponse<Map<String, Object>> postToken(String grantType, String clientId, String clientSecret) {
        if (!"client_credentials".equals(grantType)) {
            return ApiResponse.fail(400, "不支持的grant_type，仅支持client_credentials");
        }
        if (!hasText(clientId) || !hasText(clientSecret)) {
            return ApiResponse.fail(400, "client_id和client_secret不能为空");
        }

        String lockKey = failKey(clientId);
        if (isLocked(lockKey)) {
            log.warn("[TenantAuth] 换token已锁定(防爆破): clientId={}", clientId);
            return ApiResponse.fail(429, "认证失败次数过多，已锁定 " + properties.getAuth().getLockMinutes() + " 分钟");
        }

        TenantEntity tenant = resolveTenant(clientId, clientSecret);
        if (tenant == null) {
            recordFailure(lockKey, clientId);
            return ApiResponse.fail(401, "client_id或client_secret无效");
        }
        redis.delete(lockKey);   // 成功即清零

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("tenant_id", tenant.getId());

        String token = tokenGenerator.generateToken(properties.getAuth().getTokenType(), claims);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_token", token);
        result.put("token_type", "Bearer");
        result.put("expires_in", properties.getAuth().getExpireSeconds());
        return ApiResponse.success(result);
    }

    // ---------- 凭据解析 ----------

    /**
     * 平台凭据不依赖 DB 行(合成 id=0);租户凭据三形态查找 + 主钥/宽限旧钥双版本比对。
     */
    private TenantEntity resolveTenant(String clientId, String clientSecret) {
        Framework4jTenantProperties.Platform platform = properties.getPlatform();
        if (platform.getClientId() != null && platform.getClientId().equals(clientId)
                && hasText(platform.getClientSecret()) && platform.getClientSecret().equals(clientSecret)) {
            return syntheticPlatformTenant();
        }
        TenantEntity tenant = findTenant(clientId);
        if (tenant == null) {
            return null;
        }
        if (clientSecret.equals(tenant.getTenantSecret())) {
            return tenant;                       // 主密钥
        }
        if (matchesGraceSecret(tenant, clientSecret)) {
            return tenant;                       // 宽限期内旧密钥(§5.5)
        }
        return null;
    }

    /** client_id 三形态:OpenID / 原始 Long id / name(向后兼容) */
    private TenantEntity findTenant(String clientId) {
        if (IdObfuscator.isValid(clientId)) {
            try {
                TenantEntity tenant = tenantStore.findActiveById(IdObfuscator.fromOpenId(clientId));
                if (tenant != null) {
                    return tenant;
                }
            } catch (Exception ignored) {
                // 解码失败退回其它形态
            }
        }
        if (clientId.matches("\\d+")) {
            try {
                TenantEntity tenant = tenantStore.findActiveById(Long.parseLong(clientId));
                if (tenant != null) {
                    return tenant;
                }
            } catch (NumberFormatException ignored) {
                // 退回 name 形态
            }
        }
        return tenantStore.findActiveByName(clientId);
    }

    /** 宽限期双版本比对:旧钥命中且未过宽限期 → 视同认证成功(懒校验,无需清理任务) */
    private boolean matchesGraceSecret(TenantEntity tenant, String clientSecret) {
        String prev = tenant.getTenantSecretPrev();
        if (!hasText(prev) || !clientSecret.equals(prev)) {
            return false;
        }
        OffsetDateTime prevAt = tenant.getTenantSecretPrevAt();
        if (prevAt == null) {
            return false;
        }
        return prevAt.isAfter(OffsetDateTime.now().minusHours(properties.getSecret().getGraceHours()));
    }

    private TenantEntity syntheticPlatformTenant() {
        // 基类抽象,合成对象用轻量匿名子类(仅认证/签发用,不落库)
        TenantEntity platform = new TenantEntity() {
        };
        platform.setId(properties.getPlatform().getTenantId());   // 平台身份(默认 0,可配)
        platform.setName(properties.getPlatform().getClientId());
        platform.setStatus("ACTIVE");
        return platform;
    }

    // ---------- 防爆破(§8 #7:固定窗口,首次失败起算) ----------

    private String failKey(String clientId) {
        return appName + FAIL_KEY_PREFIX + clientId;
    }

    private boolean isLocked(String lockKey) {
        String fails = redis.opsForValue().get(lockKey);
        if (fails == null) {
            return false;
        }
        try {
            return Long.parseLong(fails) >= properties.getAuth().getMaxFail();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void recordFailure(String lockKey, String clientId) {
        Long n = redis.opsForValue().increment(lockKey);
        if (n != null && n == 1L) {
            redis.expire(lockKey, Duration.ofMinutes(properties.getAuth().getLockMinutes()));
        }
        log.warn("[TenantAuth] 换token失败: clientId={}, 窗口内第 {} 次(达 {} 次锁定)",
                clientId, n, properties.getAuth().getMaxFail());
    }

    private static boolean hasText(String s) {
        return s != null && !s.isEmpty();
    }
}
