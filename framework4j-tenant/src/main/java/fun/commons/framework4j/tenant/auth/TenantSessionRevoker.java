package fun.commons.framework4j.tenant.auth;

import fun.commons.framework4j.accesstoken.config.AccessTokenProperties;
import fun.commons.framework4j.accesstoken.core.TokenKeyBuilder;
import fun.commons.framework4j.accesstoken.util.TokenUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * 按租户撤销全部存量会话(§5.5 密钥生命周期)。
 * <p>
 * 会话 key = {@code {appName}:accesstoken:{type}:{calculateKeyHash(tenant_id, hashSalt)}};
 * 删除 tenant token 型别(可配多型,默认 TENANT + APP/OPS 兼容存量)的元数据 key 与限次计数 key ——
 * 持有旧 token 的会话立即失效。
 * <p>
 * 撤销失败不阻断主流程(新密钥已生效),但必须留痕(异常吞而不报 = 事故盲区)。
 */
@Slf4j
public class TenantSessionRevoker {

    private final StringRedisTemplate redis;
    private final String appName;
    private final AccessTokenProperties accessTokenProperties;
    private final List<String> tokenTypes;

    public TenantSessionRevoker(StringRedisTemplate redis, String appName,
                                AccessTokenProperties accessTokenProperties, List<String> tokenTypes) {
        this.redis = redis;
        this.appName = appName;
        this.accessTokenProperties = accessTokenProperties;
        this.tokenTypes = tokenTypes;
    }

    /**
     * 撤销指定租户的全部会话(APP/OPS/TENANT 各型)。
     */
    public void revoke(Long tenantId) {
        try {
            String hash = TokenUtils.calculateKeyHash(String.valueOf(tenantId),
                    accessTokenProperties.getHashSalt());
            for (String type : tokenTypes) {
                String metadataKey = TokenKeyBuilder.accessMetadata(appName, type, hash);
                redis.delete(metadataKey);
                redis.delete(TokenKeyBuilder.accessUsageStats(metadataKey));
            }
            log.info("[TenantAuth] 撤销租户全部会话: tenantId={}, 型别={}", tenantId, tokenTypes);
        } catch (Exception e) {
            log.error("[TenantAuth] 撤销租户会话失败(新密钥已生效,不阻断): tenantId={}", tenantId, e);
        }
    }
}
