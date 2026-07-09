package fun.commons.framework4j.audit.service;

import fun.commons.framework4j.audit.config.AuditProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Hash Chain 服务（防篡改）
 * <p>
 * v2.1 P0+P1 修复：
 * <ul>
 *   <li>{@link #computeNext(String)} 不接受外部 prevHash（避免与全局 lastHash 不一致）</li>
 *   <li>{@link #verify(String, String, String)} 独立实现，不修改 lastHash</li>
 *   <li>{@link #computeNextSnapshot(String)} 原子返回 (prevHash, hash)，保证链上前驱一致</li>
 * </ul>
 *
 * @since 2.1.0
 */
public class HashChainService {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final String algorithm;
    private volatile String lastHash = "GENESIS";

    public HashChainService(AuditProperties properties) {
        this.algorithm = properties.getHashAlgorithm();
    }

    /**
     * 计算下一条 hash（基于内部 lastHash + 当前 content），同步更新 lastHash
     */
    public synchronized String computeNext(String content) {
        return computeNextSnapshot(content)[1];
    }

    /**
     * v2.1 P1: 原子返回 (prevHash, hash) 快照
     * <p>解决 AuditService 中先读 lastHash 再 computeNext 的非原子问题
     *
     * @param content 待哈希的内容
     * @return 长度 2 的数组：[0]=prevHash, [1]=hash
     */
    public synchronized String[] computeNextSnapshot(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            String prevHash = this.lastHash;
            String combined = prevHash + "|" + content;
            byte[] digest = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            String hash = toHex(digest);
            this.lastHash = hash;
            return new String[]{prevHash, hash};
        } catch (Exception e) {
            throw new IllegalStateException("Hash chain compute failed: " + e.getMessage(), e);
        }
    }

    /**
     * 验证 hash chain 完整性（独立计算，不影响 lastHash）
     */
    public boolean verify(String prevHash, String content, String currentHash) {
        if (prevHash == null || content == null || currentHash == null) return false;
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            String combined = prevHash + "|" + content;
            byte[] digest = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            String computed = toHex(digest);
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    currentHash.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    public String getLastHash() {
        return lastHash;
    }

    public void setLastHash(String hash) {
        this.lastHash = hash;
    }

    /**
     * v2.1 P1: 回滚 lastHash（CAS 比较）
     * <p>sink 失败时调用，保证链不前进。仅当当前 lastHash == expectedCurrentHash 才回滚到 prevHash。
     *
     * @param expectedCurrentHash 期望的当前 hash（应等于本次计算的 hash）
     * @param prevHash 要回滚到的前驱 hash
     */
    public synchronized boolean rollbackLastHash(String expectedCurrentHash, String prevHash) {
        if (expectedCurrentHash != null && expectedCurrentHash.equals(this.lastHash)) {
            this.lastHash = prevHash != null ? prevHash : "GENESIS";
            return true;
        }
        return false;
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
