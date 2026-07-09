package fun.commons.framework4j.signature.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 签名串构造 + 常量时间比较
 * <p>
 * 对齐 mc-java-security §6：
 * <ul>
 *   <li>签名串 = METHOD + "\n" + PATH + "\n" + TIMESTAMP + "\n" + NONCE + "\n" + BODY_MD5</li>
 *   <li>签名值 = BASE64(HMAC_SHA256(secret, 签名串))</li>
 *   <li>比较用 MessageDigest.isEqual（防 timing attack，禁 String.equals）</li>
 * </ul>
 *
 * @since 2.1.0
 */
public final class SignatureUtil {

    private SignatureUtil() {}

    /**
     * 构造签名串
     *
     * @param method HTTP 方法（GET/POST/...）
     * @param path 请求路径（不含 query string）
     * @param timestamp Unix 毫秒
     * @param nonce UUID v4
     * @param bodyMd5Hex 请求体 MD5 十六进制（空 body 为 MD5 of empty string）
     */
    public static String buildStringToSign(String method, String path, String timestamp,
                                           String nonce, String bodyMd5Hex) {
        return method + "\n"
                + path + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + bodyMd5Hex;
    }

    /**
     * 计算签名（BASE64）
     *
     * @param secret HMAC 密钥
     * @param stringToSign 签名串
     */
    public static String sign(String secret, String stringToSign) {
        return MacUtil.hmacSha256Base64(secret, stringToSign);
    }

    /**
     * 常量时间比较（防 timing attack）
     *
     * @param client 客户端提供的签名
     * @param server 服务端计算的签名
     */
    public static boolean constantTimeEquals(String client, String server) {
        if (client == null || server == null) return false;
        byte[] a = client.getBytes(StandardCharsets.UTF_8);
        byte[] b = server.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
