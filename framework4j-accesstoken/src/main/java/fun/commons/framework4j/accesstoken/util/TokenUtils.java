package fun.commons.framework4j.accesstoken.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.accesstoken.exception.AuthException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Token 工具类
 * <p>
 * JWT-like format: header.payload.signature，签名算法 HS256。
 * <p>
 * Payload 标准 claim（mc-java-security v1.0）：
 * <ul>
 *   <li>iss — 签发者（appName）</li>
 *   <li>sub — 主题（Token 类型）</li>
 *   <li>type — Token 类型（业务字段）</li>
 *   <li>nonce — 一次性随机数</li>
 *   <li>hash — Redis Key 哈希</li>
 *   <li>iat — 签发时间（ms）</li>
 *   <li>exp — 过期时间（ms）</li>
 *   <li>jti — Token 唯一 ID（用于撤销）</li>
 * </ul>
 *
 * @since 2.0.0（从 fastjson2 + commons-codec 迁移到 Jackson + JDK javax.crypto）
 */
public class TokenUtils {

    /**
     * v2.1 P1 修复：共享 ObjectMapper 单例。
     * <p>原 AccessTokenGenerator / RefreshTokenService / AccessTokenValidationStrategy 各 new ObjectMapper()，
     * 重复创建且未应用全局策略。改为共享此单例，避免重复构建 + 行为一致。
     */
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String HEADER = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * ThreadLocal Mac 缓存：Mac 不是线程安全的，且每次 getInstance 有开销。
     * 每个 ThreadLocal<Mac> 持有独立实例 + 上一次的 secret，secret 变化时重新 init。
     */
    private static final ThreadLocal<Mac> MAC_CACHE = ThreadLocal.withInitial(() -> {
        try {
            return Mac.getInstance(HMAC_SHA256);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 初始化失败", e);
        }
    });

    private static final ThreadLocal<String> LAST_SECRET = new ThreadLocal<>();

    /**
     * 生成 JWT 格式字符串
     */
    public static String createToken(String type, String nonce, String keyHash,
                                     String iss, long iat, long exp, String jti,
                                     String secret) {
        return createToken(type, nonce, keyHash, iss, iat, exp, jti, null, secret);
    }

    /**
     * 生成 JWT 格式字符串（含 family claim，用于 refresh token）
     */
    public static String createToken(String type, String nonce, String keyHash,
                                     String iss, long iat, long exp, String jti,
                                     String family, String secret) {
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("iss", iss);
        payloadMap.put("sub", type);
        payloadMap.put("type", type);
        payloadMap.put("nonce", nonce);
        payloadMap.put("hash", keyHash);
        payloadMap.put("iat", iat);
        payloadMap.put("exp", exp);
        payloadMap.put("jti", jti);
        if (family != null) {
            payloadMap.put("family", family);
        }

        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toJsonBytes(payloadMap));

        String content = HEADER + "." + payload;
        String signature = sign(content, secret);

        return content + "." + signature;
    }

    /**
     * 验证并解析 Token
     * <p>
     * 错误码映射：
     * <ul>
     *   <li>10207 — 格式错误（段数不对、Base64 解析失败、缺 exp/iat）</li>
     *   <li>10202 — 签名验证失败</li>
     *   <li>10201 — Token 过期（exp &lt; now）</li>
     * </ul>
     */
    public static Map<String, Object> parseToken(String token, String secret) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new AuthException(10207, "令牌格式错误");
        }

        String content = parts[0] + "." + parts[1];
        String signature = parts[2];

        // 常量时间比较签名（防止时序攻击，mc-java-security §1）
        byte[] expectedSig;
        byte[] receivedSig;
        try {
            expectedSig = hmacSha256(content, secret);
            receivedSig = Base64.getUrlDecoder().decode(signature);
        } catch (IllegalArgumentException e) {
            throw new AuthException(10207, "令牌格式错误");
        }
        if (!MessageDigest.isEqual(expectedSig, receivedSig)) {
            throw new AuthException(10202, "令牌签名验证失败");
        }

        Map<String, Object> payload;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            payload = MAPPER.readValue(decoded, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new AuthException(10207, "令牌解析失败");
        }

        // 校验 exp / iat 必填
        Object expObj = payload.get("exp");
        Object iatObj = payload.get("iat");
        if (!(expObj instanceof Number) || !(iatObj instanceof Number)) {
            throw new AuthException(10207, "令牌格式错误（缺少 exp/iat）");
        }

        // 校验是否过期
        long now = System.currentTimeMillis();
        if (now > ((Number) expObj).longValue()) {
            throw new AuthException(10201, "令牌已过期");
        }

        return payload;
    }

    /** Base64-URL 编码的签名（用于 createToken） */
    private static String sign(String content, String secret) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmacSha256(content, secret));
    }

    /** 原始 HMAC-SHA256 字节（ThreadLocal Mac 缓存） */
    private static byte[] hmacSha256(String content, String secret) {
        try {
            Mac mac = MAC_CACHE.get();
            if (!secret.equals(LAST_SECRET.get())) {
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
                LAST_SECRET.set(secret);
            }
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }

    private static byte[] toJsonBytes(Map<String, Object> payloadMap) {
        try {
            return MAPPER.writeValueAsBytes(payloadMap);
        } catch (Exception e) {
            throw new IllegalStateException("Token payload 序列化失败", e);
        }
    }

    /**
     * 计算 Redis Key 的 Hash 后缀（HMAC-SHA256 hex）
     * <p>
     * salt 必填，由 AccessTokenProperties.@NotBlank 校验。
     * @throws IllegalArgumentException 当 salt 为空
     */
    public static String calculateKeyHash(Object keyValue, String salt) {
        if (salt == null || salt.isEmpty()) {
            throw new IllegalArgumentException("hashSalt 不能为空");
        }
        return bytesToHex(hmacSha256(String.valueOf(keyValue), salt));
    }

    // v2.1 P1 修复：HEX 查表替代 String.format（吞吐提升 5-10x）
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xff;
            sb.append(HEX_CHARS[v >>> 4]).append(HEX_CHARS[v & 0x0f]);
        }
        return sb.toString();
    }
}
