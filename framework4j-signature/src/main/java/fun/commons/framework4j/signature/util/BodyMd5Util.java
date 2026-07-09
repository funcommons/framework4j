package fun.commons.framework4j.signature.util;

import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Body MD5 计算（用于签名串）
 * <p>
 * 兼容两种 wrapper：
 * <ul>
 *   <li>{@link ContentCachingRequestWrapper}（Spring）</li>
 *   <li>{@code fun.commons.framework4j.web.cache.CachedBodyRequestWrapper}（继承 ContentCachingRequestWrapper）</li>
 * </ul>
 * 调用方保证 Filter 已在拦截器之前 cacheBody()。
 *
 * @since 2.1.0
 */
public final class BodyMd5Util {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private BodyMd5Util() {}

    /**
     * 从 ContentCachingRequestWrapper 拿缓存的 body 算 MD5 hex（32 字符）
     *
     * @param wrapper ContentCachingRequestWrapper（已 cacheBody）
     * @return 32 字符 hex；wrapper 为空 body 返回 MD5 of empty string
     */
    public static String md5Hex(ContentCachingRequestWrapper wrapper) {
        byte[] body = wrapper != null ? wrapper.getContentAsByteArray() : new byte[0];
        return md5Hex(body);
    }

    /** 计算字节数组的 MD5 hex（32 字符） */
    public static String md5Hex(byte[] data) {
        if (data == null) data = new byte[0];
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            return toHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xff;
            sb.append(HEX[v >>> 4]).append(HEX[v & 0x0f]);
        }
        return sb.toString();
    }
}
