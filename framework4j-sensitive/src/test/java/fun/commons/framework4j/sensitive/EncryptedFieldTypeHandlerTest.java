package fun.commons.framework4j.sensitive;

import fun.commons.framework4j.sensitive.config.SensitiveProperties;
import fun.commons.framework4j.sensitive.typehandler.EncryptedFieldTypeHandler;
import fun.commons.framework4j.sensitive.util.AesGcmCryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * EncryptedFieldTypeHandler 测试
 */
@DisplayName("EncryptedFieldTypeHandler 测试")
class EncryptedFieldTypeHandlerTest {

    private EncryptedFieldTypeHandler handler;
    private static final byte[] KEY = AesGcmCryptoUtil.deriveKey("typehandler-test-key-padding-to-32!");

    @BeforeEach
    void setUp() {
        handler = new EncryptedFieldTypeHandler(KEY);
    }

    @Test
    @DisplayName("setNonNullParameter：加密后写入 PreparedStatement")
    void setNonNullParameterEncrypts() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, "secret-data", null);
        verify(ps).setString(eq(1), argThat(s -> !s.equals("secret-data") && s.length() > 0));
    }

    @Test
    @DisplayName("getNullableResult（columnName）：解密返回明文")
    void getNullableResultByColumnNameDecrypts() throws Exception {
        String cipher = AesGcmCryptoUtil.encrypt(KEY, "plain-value");
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("col")).thenReturn(cipher);

        String result = handler.getNullableResult(rs, "col");
        assertThat(result).isEqualTo("plain-value");
    }

    @Test
    @DisplayName("getNullableResult（columnIndex）：解密返回明文")
    void getNullableResultByColumnIndexDecrypts() throws Exception {
        String cipher = AesGcmCryptoUtil.encrypt(KEY, "plain-value");
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(1)).thenReturn(cipher);

        String result = handler.getNullableResult(rs, 1);
        assertThat(result).isEqualTo("plain-value");
    }

    @Test
    @DisplayName("DB 返回 null → 返回 null（不抛异常）")
    void nullFromDbReturnsNull() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("col")).thenReturn(null);
        assertThat(handler.getNullableResult(rs, "col")).isNull();
    }

    @Test
    @DisplayName("解密失败（篡改密文）→ 返回 null + 不抛异常")
    void decryptFailureReturnsNull() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("col")).thenReturn("!!!invalid-cipher!!!");

        // safeDecrypt 返回 null（不冒泡 RuntimeException）
        String result = handler.getNullableResult(rs, "col");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("CallableStatement 解密")
    void callableStatementDecrypts() throws Exception {
        String cipher = AesGcmCryptoUtil.encrypt(KEY, "cs-value");
        java.sql.CallableStatement cs = mock(java.sql.CallableStatement.class);
        when(cs.getString(1)).thenReturn(cipher);

        String result = handler.getNullableResult(cs, 1);
        assertThat(result).isEqualTo("cs-value");
    }

    @Test
    @DisplayName("构造校验：null keyBytes → 抛异常")
    void nullKeyBytesThrows() {
        assertThatThrownBy(() -> new EncryptedFieldTypeHandler((byte[]) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("构造校验：短 keyBytes（< 32）→ 抛异常")
    void shortKeyBytesThrows() {
        assertThatThrownBy(() -> new EncryptedFieldTypeHandler(new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("加密 → DB 存储往返一致性（同一 Handler 实例加解密）")
    void roundTripSameHandler() throws Exception {
        // 模拟写入
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, "round-trip", null);

        // 捕获写入的密文
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(ps).setString(eq(1), captor.capture());
        String storedCipher = captor.getValue();

        // 模拟读取
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("col")).thenReturn(storedCipher);
        String decrypted = handler.getNullableResult(rs, "col");

        assertThat(decrypted).isEqualTo("round-trip");
    }
}
