package fun.commons.framework4j.audit;

import fun.commons.framework4j.audit.config.AuditProperties;
import fun.commons.framework4j.audit.service.HashChainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HashChainService 单元测试（v2.1 P0：API 重构后）
 *
 * @since 2.1.0
 */
@DisplayName("HashChainService 防篡改测试")
class HashChainServiceTest {

    private HashChainService hashChain;

    @BeforeEach
    void setUp() {
        AuditProperties props = new AuditProperties();
        props.setHashAlgorithm("SHA-256");
        hashChain = new HashChainService(props);
    }

    @Test
    @DisplayName("相同 content 产生相同 hash（确定性，需相同 lastHash）")
    void deterministicHash() {
        String h1 = hashChain.computeNext("content-1");
        hashChain.setLastHash("GENESIS");
        String h2 = hashChain.computeNext("content-1");
        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(64);
    }

    @Test
    @DisplayName("不同 content 产生不同 hash")
    void differentContent() {
        String h1 = hashChain.computeNext("content-1");
        hashChain.setLastHash("GENESIS");
        String h2 = hashChain.computeNext("content-2");
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    @DisplayName("hash chain 链式：每条 prev = 上一条 hash")
    void chainLinking() {
        String h1 = hashChain.computeNext("c1");
        String h2 = hashChain.computeNext("c2");
        String h3 = hashChain.computeNext("c3");

        // v2.1 P0: verify 独立计算，不影响 lastHash
        assertThat(hashChain.verify(h1, "c2", h2)).isTrue();
        assertThat(hashChain.verify(h2, "c3", h3)).isTrue();
        assertThat(hashChain.getLastHash()).isEqualTo(h3);
    }

    @Test
    @DisplayName("verify 独立：不影响 lastHash（v2.1 P0 修复）")
    void verifyDoesNotMutate() {
        String h1 = hashChain.computeNext("c1");
        String beforeVerify = hashChain.getLastHash();

        boolean result = hashChain.verify("GENESIS", "c1", h1);

        assertThat(result).isTrue();
        assertThat(hashChain.getLastHash()).isEqualTo(beforeVerify);
    }

    @Test
    @DisplayName("篡改某条记录的 content → verify 失败")
    void tamperDetection() {
        String h1 = hashChain.computeNext("real-content");

        boolean verified = hashChain.verify("GENESIS", "real-content", h1);
        assertThat(verified).isTrue();

        boolean tamperedVerify = hashChain.verify("GENESIS", "tampered-content", h1);
        assertThat(tamperedVerify).isFalse();
    }

    @Test
    @DisplayName("verify null 安全")
    void verifyNullSafe() {
        assertThat(hashChain.verify(null, "content", "hash")).isFalse();
        assertThat(hashChain.verify("GENESIS", null, "hash")).isFalse();
        assertThat(hashChain.verify("GENESIS", "content", null)).isFalse();
    }
}
