package fun.commons.framework4j.audit;

import fun.commons.framework4j.audit.config.AuditProperties;
import fun.commons.framework4j.audit.service.AuditRecord;
import fun.commons.framework4j.audit.service.AuditService;
import fun.commons.framework4j.audit.service.HashChainService;
import fun.commons.framework4j.audit.service.InMemoryAuditSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuditService 单元测试
 *
 * @since 2.1.0
 */
@DisplayName("AuditService 单元测试")
class AuditServiceTest {

    private AuditService auditService;
    private InMemoryAuditSink sink;
    private HashChainService hashChain;

    @BeforeEach
    void setUp() {
        AuditProperties props = new AuditProperties();
        props.setHashAlgorithm("SHA-256");
        props.setActorHeader("X-User-Id");
        props.setActorFallback("anonymous");
        hashChain = new HashChainService(props);
        sink = new InMemoryAuditSink();
        auditService = new AuditService(props, hashChain, sink);
    }

    @Test
    @DisplayName("成功记录：result=SUCCESS，hash 递增")
    void auditSuccess() {
        auditService.audit("DELETE_ORDER", "order", "ord-001",
                "SUCCESS", null, new Object[]{"ord-001"}, null);

        assertThat(sink.size()).isEqualTo(1);
        assertThat(sink.last().getAction()).isEqualTo("DELETE_ORDER");
        assertThat(sink.last().getResult()).isEqualTo("SUCCESS");
        assertThat(sink.last().getHash()).hasSize(64);
        assertThat(sink.last().getPrevHash()).isEqualTo("GENESIS");
    }

    @Test
    @DisplayName("错误记录：result=ERROR + errorMessage")
    void auditError() {
        auditService.audit("DELETE_ORDER", "order", "ord-002",
                "ERROR", "订单状态冲突", new Object[]{"ord-002"}, null);

        assertThat(sink.last().getResult()).isEqualTo("ERROR");
        assertThat(sink.last().getErrorMessage()).contains("状态冲突");
    }

    @Test
    @DisplayName("hash chain 链式：第 2 条 prevHash = 第 1 条 hash")
    void hashChainLinking() {
        auditService.audit("ACTION_1", "t", "1", "SUCCESS", null, null, null);
        String hash1 = sink.last().getHash();

        auditService.audit("ACTION_2", "t", "2", "SUCCESS", null, null, null);
        String hash2 = sink.last().getHash();
        String prevHash2 = sink.last().getPrevHash();

        assertThat(prevHash2).isEqualTo(hash1);
        assertThat(hash2).isNotEqualTo(hash1);
    }

    @Test
    @DisplayName("args 序列化为 JSON")
    void argsSerialized() {
        auditService.audit("UPDATE_USER", "user", "u-1",
                "SUCCESS", null, new Object[]{"u-1", "Alice"}, null);

        String argsJson = sink.last().getArgsJson();
        assertThat(argsJson).contains("u-1").contains("Alice");
    }

    @Test
    @DisplayName("returnValue 序列化")
    void resultSerialized() {
        auditService.audit("GET_USER", "user", "u-1",
                "SUCCESS", null, null, "result-value");

        assertThat(sink.last().getResultJson()).contains("result-value");
    }

    @Test
    @DisplayName("AuditSink 异常不应抛出到业务代码")
    void sinkFailureSwallowed() {
        // v2.1 P0: 用计数 sink 替代纯抛异常，加强断言（确认确实被调用）
        java.util.concurrent.atomic.AtomicInteger writeCount = new java.util.concurrent.atomic.AtomicInteger(0);
        fun.commons.framework4j.audit.service.AuditSink throwingSink = new fun.commons.framework4j.audit.service.AuditSink() {
            @Override
            public void write(AuditRecord record) {
                writeCount.incrementAndGet();
                throw new RuntimeException("DB down");
            }
        };
        AuditProperties props = new AuditProperties();
        AuditService svc = new AuditService(props, new HashChainService(props), throwingSink);

        // 不应抛异常
        svc.audit("ACTION", "t", "1", "SUCCESS", null, null, null);

        // v2.1 P0: 强断言 sink 被调用
        assertThat(writeCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Hash Chain 端到端：多记录写入后逐条 verify 全部 true")
    void hashChainEndToEndVerify() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        // 写入 5 条记录
        for (int i = 1; i <= 5; i++) {
            auditService.audit("ACTION_" + i, "type", "id-" + i,
                    "SUCCESS", null, new Object[]{"arg-" + i}, null);
        }

        // 取所有记录
        java.util.List<AuditRecord> records = sink.getRecords();
        assertThat(records).hasSize(5);

        // 逐条 verify：必须用 AuditService 同样的 content 构造方式
        for (int i = 0; i < records.size(); i++) {
            AuditRecord r = records.get(i);
            // 复制 AuditService.doAudit 的 content 构造逻辑（TreeMap 保证 key 序稳定）
            java.util.Map<String, Object> content = new java.util.TreeMap<>();
            content.put("action", r.getAction());
            content.put("targetType", r.getTargetType());
            content.put("targetId", r.getTargetId());
            content.put("actor", r.getActor());
            content.put("result", r.getResult());
            content.put("timestamp", r.getTimestamp().toString());
            content.put("args", r.getArgsJson());
            String contentJson = mapper.writeValueAsString(content);

            boolean ok = hashChain.verify(r.getPrevHash(), contentJson, r.getHash());
            assertThat(ok).as("记录 %d verify 应通过", i).isTrue();
        }
    }
}
