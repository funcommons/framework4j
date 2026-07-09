package fun.commons.framework4j.accesstoken.core;

/**
 * AccessToken 模块 Redis Key 构造工具
 * <p>
 * 统一管理 4 类 Redis Key 的命名规则，避免散落在 Generator / Interceptor / Service 中的字符串拼接：
 * <ul>
 *   <li>{@code {app}:accesstoken:{type}:{hash}} — access token 元数据</li>
 *   <li>{@code {app}:accesstoken:{type}:{hash}:stats:usage} — 限次计数</li>
 *   <li>{@code access:revoked:{app}} — access jti 撤销 Set</li>
 *   <li>{@code refresh:family:{app}:{familyId}} — refresh 家族 Hash</li>
 *   <li>{@code refresh:revoked:{app}:{familyId}} — refresh 家族毒丸</li>
 * </ul>
 *
 * @since 2.0.0
 */
public final class TokenKeyBuilder {

    private TokenKeyBuilder() {}

    /** Access token 元数据 Key：{app}:accesstoken:{type}:{hash} */
    public static String accessMetadata(String app, String tokenType, String hash) {
        return app + ":accesstoken:" + tokenType + ":" + hash;
    }

    /** Access token 限次计数 Key：{redisKey}:stats:usage */
    public static String accessUsageStats(String redisKey) {
        return redisKey + ":stats:usage";
    }

    /** Access token 激活窗口 Key（首次使用前需激活的场景）：{app}:accesstoken:activation:{type}:{hash} */
    public static String accessActivation(String app, String tokenType, String hash) {
        return app + ":accesstoken:activation:" + tokenType + ":" + hash;
    }

    /** Access jti 撤销 Set Key：access:revoked:{app} */
    public static String accessRevokedSet(String app) {
        return "access:revoked:" + app;
    }

    /** Refresh 家族 Hash Key：refresh:family:{app}:{familyId} */
    public static String refreshFamily(String app, String familyId) {
        return "refresh:family:" + app + ":" + familyId;
    }

    /** Refresh 家族毒丸 Key：refresh:revoked:{app}:{familyId} */
    public static String refreshRevokedPoison(String app, String familyId) {
        return "refresh:revoked:" + app + ":" + familyId;
    }
}
