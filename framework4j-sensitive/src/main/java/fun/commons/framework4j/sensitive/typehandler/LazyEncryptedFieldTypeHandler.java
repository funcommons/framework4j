package fun.commons.framework4j.sensitive.typehandler;

import fun.commons.framework4j.sensitive.context.SpringContextHolder;
import fun.commons.framework4j.sensitive.util.AesGcmCryptoUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * AES-256-GCM 字段加密 TypeHandler（lazy key 变体）。
 * <p>
 * 父类 {@link EncryptedFieldTypeHandler} 构造时固定 {@code keyBytes}，但 MyBatis 在
 * Mapper 解析阶段（启动早期）反射实例化 TypeHandler，此时若 key 来自 Spring Bean
 * （{@code sensitiveAesKeyBytes}），容器可能未就绪 → 取不到 key → fallback 测试 key
 * → 加密/解密 key 错位。
 * <p>
 * 本类改为 lazy：无参构造不取 key，每次 {@code set/get} 时从
 * {@link SpringContextHolder} 取（运行时 context 必就绪），保证 insert/select 用同一真 key。
 * <p>
 * 用法：实体字段标注
 * {@code @TableField(typeHandler = LazyEncryptedFieldTypeHandler.class)}。
 * 仅适用于 key 由 {@code sensitiveAesKeyBytes} Bean 提供的场景；若 key 为编译期常量，
 * 用 {@link EncryptedFieldTypeHandler#EncryptedFieldTypeHandler(byte[])} 更直接。
 *
 * @since 1.2.9
 */
@MappedTypes(String.class)
public class LazyEncryptedFieldTypeHandler extends BaseTypeHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(LazyEncryptedFieldTypeHandler.class);

    /** 无参构造（MyBatis 反射用），不在此取 key */
    public LazyEncryptedFieldTypeHandler() {
    }

    private byte[] key() {
        return SpringContextHolder.getBean("sensitiveAesKeyBytes", byte[].class);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, AesGcmCryptoUtil.encrypt(key(), parameter));
        } catch (Exception e) {
            throw new SQLException("字段加密失败", e);
        }
    }

    private String safeDecrypt(String value) {
        if (value == null) return null;
        try {
            return AesGcmCryptoUtil.decrypt(key(), value);
        } catch (Exception e) {
            // 旧明文数据 / 密钥轮换不匹配 → null（需走迁移接口加密）
            log.warn("[Sensitive] 字段解密失败（明文/密钥不匹配）: {}", e.getMessage());
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
