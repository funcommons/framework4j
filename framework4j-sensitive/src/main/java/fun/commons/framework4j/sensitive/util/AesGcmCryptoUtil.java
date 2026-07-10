package fun.commons.framework4j.sensitive.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM 加解密工具（mc-java-security 铁律 9）
 * <p>
 * 输出格式：Base64(IV || ciphertext || tag)
 * <p>
 * v2.1 P0 修复：{@code ThreadLocal<Cipher>} 复用（遵循 Java开发准则 §5.1），
 * 避免 TypeHandler 高频调用每次 {@code Cipher.getInstance()} 的 JCA 查找开销。
 *
 * @since 2.1.0
 */
public final class AesGcmCryptoUtil {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String AES = "AES";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    /** ThreadLocal<Cipher>：Cipher 非线程安全 + getInstance 有 JCA 查找开销 */
    private static final ThreadLocal<Cipher> CIPHER_CACHE = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(TRANSFORMATION);
        } catch (Exception e) {
            throw new IllegalStateException("AES/GCM/NoPadding not available", e);
        }
    });

    /** SecureRandom 单例（线程安全） */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesGcmCryptoUtil() {}

    public static String encrypt(byte[] keyBytes, String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = CIPHER_CACHE.get();
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(keyBytes, AES),
                    new GCMParameterSpec(TAG_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] output = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(ciphertext, 0, output, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(output);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encrypt failed: " + e.getMessage(), e);
        }
    }

    public static String decrypt(byte[] keyBytes, String ciphertextBase64) {
        try {
            byte[] input = Base64.getDecoder().decode(ciphertextBase64);
            if (input.length < IV_LENGTH + 1) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            byte[] iv = Arrays.copyOfRange(input, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(input, IV_LENGTH, input.length);

            Cipher cipher = CIPHER_CACHE.get();
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(keyBytes, AES),
                    new GCMParameterSpec(TAG_BITS, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decrypt failed: " + e.getMessage(), e);
        }
    }

    /**
     * 从任意字符串派生 32 字节 AES 密钥（SHA-256）。
     * <p>P0-2: 派生密钥仅在开发/测试环境使用。生产环境必须从 KMS 取 32 字节随机密钥，
     * 通过 {@link #deriveKeyFromBytes(byte[])} 直接传入。
     */
    public static byte[] deriveKey(String passphrase) {
        if (passphrase == null || passphrase.length() < 32) {
            throw new IllegalArgumentException(
                "AES 密钥派生失败：passphrase 必须 ≥ 32 字符（实际 "
                + (passphrase == null ? 0 : passphrase.length()) + "）");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(passphrase.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("derive key failed", e);
        }
    }

    /**
     * 直接使用 32 字节密钥（生产环境推荐，从 KMS 取）。
     */
    public static byte[] deriveKeyFromBytes(byte[] rawKey) {
        if (rawKey == null || rawKey.length != 32) {
            throw new IllegalArgumentException(
                "AES-256 密钥必须为 32 字节（实际 "
                + (rawKey == null ? 0 : rawKey.length) + "）");
        }
        return rawKey;
    }
}
