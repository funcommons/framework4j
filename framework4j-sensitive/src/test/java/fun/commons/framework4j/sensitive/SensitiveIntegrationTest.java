package fun.commons.framework4j.sensitive;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.sensitive.annotation.Sensitive;
import fun.commons.framework4j.sensitive.annotation.SensitiveRule;
import fun.commons.framework4j.sensitive.util.AesGcmCryptoUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AES-256-GCM 加解密测试 + Jackson 脱敏集成
 *
 * @since 2.1.0
 */
@DisplayName("AES-256-GCM 加解密 + Jackson 脱敏集成")
class SensitiveIntegrationTest {

    @Test
    @DisplayName("加解密往返：decrypt(encrypt(plain)) == plain")
    void encryptDecryptRoundTrip() {
        byte[] key = AesGcmCryptoUtil.deriveKey("my-secret-key");
        String plaintext = "身份证:110101199001011234 手机:13812345678";

        String cipher = AesGcmCryptoUtil.encrypt(key, plaintext);
        String decrypted = AesGcmCryptoUtil.decrypt(key, cipher);

        assertThat(decrypted).isEqualTo(plaintext);
        assertThat(cipher).isNotEqualTo(plaintext);
    }

    @Test
    @DisplayName("每次加密结果不同（随机 IV）")
    void randomIv() {
        byte[] key = AesGcmCryptoUtil.deriveKey("key");
        String plain = "same-plaintext";

        String c1 = AesGcmCryptoUtil.encrypt(key, plain);
        String c2 = AesGcmCryptoUtil.encrypt(key, plain);

        assertThat(c1).isNotEqualTo(c2);
        // 但都能正确解密
        assertThat(AesGcmCryptoUtil.decrypt(key, c1)).isEqualTo(plain);
        assertThat(AesGcmCryptoUtil.decrypt(key, c2)).isEqualTo(plain);
    }

    @Test
    @DisplayName("错误密钥解密失败（GCM Tag 校验）")
    void wrongKeyFails() {
        byte[] key1 = AesGcmCryptoUtil.deriveKey("key1");
        byte[] key2 = AesGcmCryptoUtil.deriveKey("key2");
        String cipher = AesGcmCryptoUtil.encrypt(key1, "secret");

        // v2.1 P0: 改用 assertThrows 替代 try/catch + assertThat(false) 反模式
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> AesGcmCryptoUtil.decrypt(key2, cipher))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decrypt failed");
    }

    @Test
    @DisplayName("Jackson 序列化时自动脱敏")
    void jacksonAutoDesensitize() throws Exception {
        TestUser user = new TestUser("u-1", "13812345678", "110101199001011234");

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(user);

        assertThat(json).contains("138****5678");
        assertThat(json).contains("110101********1234");
        assertThat(json).doesNotContain("13812345678");
        assertThat(json).doesNotContain("110101199001011234");
    }

    public static class TestUser {
        private String id;

        @Sensitive(SensitiveRule.PHONE)
        private String phone;

        @Sensitive(SensitiveRule.ID_CARD)
        private String idCard;

        public TestUser() {}
        public TestUser(String id, String phone, String idCard) {
            this.id = id; this.phone = phone; this.idCard = idCard;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getIdCard() { return idCard; }
        public void setIdCard(String idCard) { this.idCard = idCard; }
    }
}
