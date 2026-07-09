package fun.commons.framework4j.sensitive.typehandler;

import fun.commons.framework4j.sensitive.util.AesGcmCryptoUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * AES-256-GCM 字段加密 TypeHandler
 * <p>
 * MyBatis 写入 DB 自动加密，读取自动解密。
 * <p>
 * v2.1 P1: 构造接受 {@code byte[]} keyBytes（由 Spring 派生一次后注入），避免 MyBatis
 * 每个映射字段新建 TypeHandler 实例时重复 SHA-256 派生。
 *
 * @since 2.1.0
 */
public class EncryptedFieldTypeHandler extends BaseTypeHandler<String> {

    private final byte[] keyBytes;

    /**
     * 推荐构造：接受 Spring 派生好的 keyBytes（单例）
     */
    public EncryptedFieldTypeHandler(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != 32) {
            throw new IllegalArgumentException("keyBytes 必须是 32 字节（AES-256）");
        }
        this.keyBytes = keyBytes;
    }

    /**
     * 兼容旧构造（每次 SHA-256 派生，仅作 fallback）
     *
     * @deprecated 用 {@link #EncryptedFieldTypeHandler(byte[])} 替代
     */
    @Deprecated
    public EncryptedFieldTypeHandler(String encryptionKey) {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException("encryption-key 必须配置");
        }
        this.keyBytes = AesGcmCryptoUtil.deriveKey(encryptionKey);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, AesGcmCryptoUtil.encrypt(keyBytes, parameter));
        } catch (Exception e) {
            throw new SQLException("字段加密失败", e);
        }
    }

    // v2.1 P1: decrypt 失败（密钥轮换/数据篡改）返回 null + warn，不冒泡 RuntimeException
    private String safeDecrypt(String value) {
        if (value == null) return null;
        try {
            return AesGcmCryptoUtil.decrypt(keyBytes, value);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EncryptedFieldTypeHandler.class)
                    .warn("[Sensitive] 字段解密失败（密钥轮换/数据篡改）: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return safeDecrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return safeDecrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return safeDecrypt(cs.getString(columnIndex));
    }
}
