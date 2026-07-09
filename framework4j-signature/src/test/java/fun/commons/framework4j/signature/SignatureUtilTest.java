package fun.commons.framework4j.signature;

import fun.commons.framework4j.signature.util.SignatureUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SignatureUtil 测试（签名串构造 + 常量时间比较）
 *
 * @since 2.1.0
 */
@DisplayName("SignatureUtil 单元测试")
class SignatureUtilTest {

    @Test
    @DisplayName("签名串构造：5 段 + 4 个换行")
    void shouldBuildStringToSign() {
        String s = SignatureUtil.buildStringToSign("POST", "/v1/orders",
                "1718660400000", "abc-123", "d41d8cd98f00b204e9800998ecf8427e");
        assertThat(s).isEqualTo("POST\n/v1/orders\n1718660400000\nabc-123\nd41d8cd98f00b204e9800998ecf8427e");
    }

    @Test
    @DisplayName("相同 secret+串 产生相同签名（确定性）")
    void shouldProduceDeterministicSignature() {
        String sig1 = SignatureUtil.sign("secret", "data");
        String sig2 = SignatureUtil.sign("secret", "data");
        assertThat(sig1).isEqualTo(sig2);
    }

    @Test
    @DisplayName("不同 secret 产生不同签名")
    void shouldProduceDifferentSignatureForDifferentSecret() {
        String s1 = SignatureUtil.sign("secret1", "data");
        String s2 = SignatureUtil.sign("secret2", "data");
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    @DisplayName("常量时间比较：相同返回 true")
    void constantTimeEqualsReturnsTrueForEqual() {
        assertThat(SignatureUtil.constantTimeEquals("abc", "abc")).isTrue();
    }

    @Test
    @DisplayName("常量时间比较：不同返回 false")
    void constantTimeEqualsReturnsFalseForDifferent() {
        assertThat(SignatureUtil.constantTimeEquals("abc", "abd")).isFalse();
    }

    @Test
    @DisplayName("常量时间比较：null 输入返回 false")
    void constantTimeEqualsReturnsFalseForNull() {
        assertThat(SignatureUtil.constantTimeEquals(null, "abc")).isFalse();
        assertThat(SignatureUtil.constantTimeEquals("abc", null)).isFalse();
        assertThat(SignatureUtil.constantTimeEquals(null, null)).isFalse();
    }

    @Test
    @DisplayName("常量时间比较：长度不同返回 false")
    void constantTimeEqualsReturnsFalseForDifferentLength() {
        assertThat(SignatureUtil.constantTimeEquals("abc", "abcd")).isFalse();
    }

    @Test
    @DisplayName("端到端：构造 → 签名 → 验证")
    void endToEndSignAndVerify() {
        String secret = "my-secret";
        String stringToSign = SignatureUtil.buildStringToSign(
                "POST", "/v1/orders", "1718660400000", "nonce-1", "body-md5");
        String clientSig = SignatureUtil.sign(secret, stringToSign);

        // 服务端用相同 secret + 相同串 → 验证通过
        String serverSig = SignatureUtil.sign(secret, stringToSign);
        assertThat(SignatureUtil.constantTimeEquals(clientSig, serverSig)).isTrue();

        // 攻击者用错误 secret → 验证失败
        String badSig = SignatureUtil.sign("wrong-secret", stringToSign);
        assertThat(SignatureUtil.constantTimeEquals(clientSig, badSig)).isFalse();
    }
}
