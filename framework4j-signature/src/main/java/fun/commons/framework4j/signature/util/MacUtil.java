package fun.commons.framework4j.signature.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HMAC-SHA256 工具（遵循 Java开发准则 §5.1：ThreadLocal Mac 缓存 + reset）
 *
 * @since 2.1.0
 */
public final class MacUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /** ThreadLocal<Mac>：Mac 非线程安全 + getInstance 有 JCA 查找开销 */
    private static final ThreadLocal<Mac> MAC_CACHE = ThreadLocal.withInitial(() -> {
        try {
            return Mac.getInstance(HMAC_SHA256);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    });

    private MacUtil() {}

    /**
     * 计算 HMAC-SHA256
     *
     * @param keyBytes 密钥字节
     * @param data 待签名数据
     * @return HMAC 结果字节数组
     */
    public static byte[] hmacSha256(byte[] keyBytes, byte[] data) {
        Mac mac = MAC_CACHE.get();
        try {
            mac.init(new SecretKeySpec(keyBytes, HMAC_SHA256));
            byte[] result = mac.doFinal(data);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("HMAC compute failed", e);
        } finally {
            mac.reset();  // 关键：reset 防残留状态
        }
    }

    /**
     * 计算 HMAC-SHA256 并 BASE64 编码
     */
    public static String hmacSha256Base64(String secret, String data) {
        byte[] hmac = hmacSha256(
                secret.getBytes(StandardCharsets.UTF_8),
                data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmac);
    }
}
