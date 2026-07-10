package fun.commons.framework4j.audit;

import fun.commons.framework4j.audit.config.AuditProperties;
import fun.commons.framework4j.audit.service.AuditRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AuditProperties + AuditRecord 测试")
class AuditPropertiesTest {

    @Test @DisplayName("AuditProperties 默认值")
    void defaults() {
        AuditProperties p = new AuditProperties();
        assertThat(p.isEnabled()).isTrue();
        assertThat(p.getTableName()).isEqualTo("audit_log");
        assertThat(p.isHashChainEnabled()).isTrue();
        assertThat(p.getHashAlgorithm()).isEqualTo("SHA-256");
        assertThat(p.getActorHeader()).isEqualTo("X-User-Id");
        assertThat(p.getActorFallback()).isEqualTo("anonymous");
        assertThat(p.getIpHeader()).isEqualTo("X-Forwarded-For");
    }

    @Test @DisplayName("AuditProperties 所有属性可修改")
    void allMutable() {
        AuditProperties p = new AuditProperties();
        p.setEnabled(false);
        p.setTableName("custom_audit");
        p.setHashChainEnabled(false);
        p.setHashAlgorithm("SHA-512");
        p.setActorHeader("X-Account");
        p.setActorFallback("unknown");
        p.setIpHeader("X-Real-IP");

        assertThat(p.isEnabled()).isFalse();
        assertThat(p.getTableName()).isEqualTo("custom_audit");
        assertThat(p.isHashChainEnabled()).isFalse();
        assertThat(p.getHashAlgorithm()).isEqualTo("SHA-512");
        assertThat(p.getActorHeader()).isEqualTo("X-Account");
        assertThat(p.getActorFallback()).isEqualTo("unknown");
        assertThat(p.getIpHeader()).isEqualTo("X-Real-IP");
    }

    @Test @DisplayName("AuditRecord record 构造 + getter")
    void auditRecordConstruction() {
        Instant now = Instant.now();
        AuditRecord r = new AuditRecord(
                "CREATE_ORDER", "order", "ord-123",
                "user-456", "SUCCESS", null,
                "[\"ord-123\"]", null,
                "192.168.1.1", "Mozilla/5.0", "trace-abc",
                now, "prev-hash", "curr-hash");

        assertThat(r.getAction()).isEqualTo("CREATE_ORDER");
        assertThat(r.getTargetType()).isEqualTo("order");
        assertThat(r.getTargetId()).isEqualTo("ord-123");
        assertThat(r.getActor()).isEqualTo("user-456");
        assertThat(r.getResult()).isEqualTo("SUCCESS");
        assertThat(r.getErrorMessage()).isNull();
        assertThat(r.getArgsJson()).isEqualTo("[\"ord-123\"]");
        assertThat(r.getResultJson()).isNull();
        assertThat(r.getIp()).isEqualTo("192.168.1.1");
        assertThat(r.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(r.getTraceId()).isEqualTo("trace-abc");
        assertThat(r.getTimestamp()).isEqualTo(now);
        assertThat(r.getPrevHash()).isEqualTo("prev-hash");
        assertThat(r.getHash()).isEqualTo("curr-hash");
    }

    @Test @DisplayName("AuditRecord ERROR 记录")
    void auditRecordError() {
        AuditRecord r = new AuditRecord(
                "DELETE_ORDER", "order", "ord-456",
                "admin", "ERROR", "RuntimeException: DB down",
                null, null,
                "10.0.0.1", null, "trace-err",
                Instant.now(), "prev", "curr");

        assertThat(r.getResult()).isEqualTo("ERROR");
        assertThat(r.getErrorMessage()).contains("DB down");
    }


}
