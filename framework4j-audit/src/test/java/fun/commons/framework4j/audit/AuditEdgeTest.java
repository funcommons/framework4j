package fun.commons.framework4j.audit;

import fun.commons.framework4j.audit.config.AuditProperties;
import fun.commons.framework4j.audit.service.AuditRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Audit 边界测试")
class AuditEdgeTest {
    @Test @DisplayName("SHA-512 配置")
    void sha512() {
        AuditProperties p = new AuditProperties();
        p.setHashAlgorithm("SHA-512");
        assertThat(p.getHashAlgorithm()).isEqualTo("SHA-512");
    }
    @Test @DisplayName("禁用 hash chain")
    void disableHashChain() {
        AuditProperties p = new AuditProperties();
        p.setHashChainEnabled(false);
        assertThat(p.isHashChainEnabled()).isFalse();
    }
    @Test @DisplayName("AuditRecord 含 resultJson")
    void withResultJson() {
        AuditRecord r = new AuditRecord("GET", "user", "u-1", "admin", "SUCCESS", null,
                null, "{\"name\":\"Alice\"}", null, null, null, Instant.now(), "p", "h");
        assertThat(r.getResultJson()).contains("Alice");
    }
    @Test @DisplayName("AuditRecord ERROR + errorMessage")
    void errorRecord() {
        AuditRecord r = new AuditRecord("DELETE", "order", "o-1", "admin", "ERROR", "DB down",
                null, null, null, null, null, Instant.now(), "p", "h");
        assertThat(r.getResult()).isEqualTo("ERROR");
        assertThat(r.getErrorMessage()).contains("DB down");
    }
    @Test @DisplayName("AuditRecord 含 argsJson + userAgent")
    void fullRecord() {
        AuditRecord r = new AuditRecord("CREATE", "order", "o-1", "user-1", "SUCCESS", null,
                "[\"o-1\",\"Alice\"]", null, "10.0.0.1", "Mozilla/5.0", "trace-1",
                Instant.now(), "prev", "hash");
        assertThat(r.getArgsJson()).contains("Alice");
        assertThat(r.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(r.getIp()).isEqualTo("10.0.0.1");
    }
    @Test @DisplayName("actorHeader 自定义")
    void customActorHeader() {
        AuditProperties p = new AuditProperties();
        p.setActorHeader("X-Account-Id");
        assertThat(p.getActorHeader()).isEqualTo("X-Account-Id");
    }
}
