package fun.commons.framework4j.accesstoken.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Issue #20:配置完整性启动期 fail-fast —— 各缺口逐项明确报错(含「key 是 claims 字段名」语义提示)。
 */
class AccessTokenConfigValidatorTest {

    private static AccessTokenProperties validProps() {
        AccessTokenProperties p = new AccessTokenProperties();
        p.setSecretKey("test-secret-key-for-jwt-must-be-at-least-32-chars!!");
        p.setHashSalt("salt");
        AccessTokenProperties.Policy policy = new AccessTokenProperties.Policy();
        policy.setKey(List.of("uid"));
        p.setPolicies(new HashMap<>(Map.of("APP", policy)));
        return p;
    }

    @Test
    @DisplayName("完整配置 → 通过")
    void valid_passes() {
        assertThatCode(() -> AccessTokenConfigValidator.validate(validProps()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("policies 未配置(运行期 NPE 的原凶)→ fail-fast 且提示 key 语义")
    void missingPolicies_failsFast() {
        AccessTokenProperties p = validProps();
        p.setPolicies(null);
        assertThatThrownBy(() -> AccessTokenConfigValidator.validate(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("policies 未配置")
                .hasMessageContaining("不是签名密钥");
    }

    @Test
    @DisplayName("policy.key 缺失 → fail-fast 且明确「claims 必需字段名列表」语义")
    void missingPolicyKey_failsFast() {
        AccessTokenProperties p = validProps();
        p.getPolicies().get("APP").setKey(null);
        assertThatThrownBy(() -> AccessTokenConfigValidator.validate(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("policies[APP].key")
                .hasMessageContaining("claims 必需字段名列表");
    }

    @Test
    @DisplayName("secretKey 短于 32 / hashSalt 空 → fail-fast,缺失项清单式报错")
    void missingSecrets_failsFast() {
        AccessTokenProperties p = validProps();
        p.setSecretKey("short");
        p.setHashSalt("");
        assertThatThrownBy(() -> AccessTokenConfigValidator.validate(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret-key")
                .hasMessageContaining("hash-salt");
    }

    @Test
    @DisplayName("多问题一次性列出(不逐个试错的接入体验)")
    void multipleProblems_listedTogether() {
        AccessTokenProperties p = new AccessTokenProperties();   // 全空
        assertThatThrownBy(() -> AccessTokenConfigValidator.validate(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret-key")
                .hasMessageContaining("hash-salt")
                .hasMessageContaining("policies");
    }
}
