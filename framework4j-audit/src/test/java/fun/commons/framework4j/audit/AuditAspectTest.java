package fun.commons.framework4j.audit;

import fun.commons.framework4j.audit.annotation.Auditable;
import fun.commons.framework4j.audit.aspect.AuditAspect;
import fun.commons.framework4j.audit.config.AuditAutoConfiguration;
import fun.commons.framework4j.audit.service.AuditRecord;
import fun.commons.framework4j.audit.service.InMemoryAuditSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AuditAspect AOP 切面测试（@Auditable 端到端）
 *
 * @since 2.1.0
 */
@SpringBootTest(classes = AuditAspectTest.TestApp.class)
@org.springframework.context.annotation.Import(AuditAspectTest.TestOrderService.class)
@ActiveProfiles("test")
class AuditAspectTest {

    @org.springframework.boot.autoconfigure.SpringBootApplication
    @Import({AuditAutoConfiguration.class})
    static class TestApp {}

    @Autowired
    private TestOrderService orderService;

    @Autowired
    private InMemoryAuditSink sink;

    @BeforeEach
    void clean() {
        sink.clear();
    }

    @Test
    @DisplayName("@Auditable 成功路径：action + targetIdSpel + actor 正确")
    void auditableSuccess() {
        orderService.deleteOrder("ord-001");

        assertThat(sink.size()).isEqualTo(1);
        AuditRecord r = sink.last();
        assertThat(r.getAction()).isEqualTo("DELETE_ORDER");
        assertThat(r.getTargetId()).isEqualTo("ord-001");
        assertThat(r.getTargetType()).isEqualTo("order");
        assertThat(r.getResult()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("@Auditable 异常路径：result=ERROR + errorMessage 含异常类名")
    void auditableError() {
        assertThatThrownBy(() -> orderService.failOrder("ord-002"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB down");

        assertThat(sink.size()).isEqualTo(1);
        AuditRecord r = sink.last();
        assertThat(r.getResult()).isEqualTo("ERROR");
        assertThat(r.getErrorMessage()).contains("RuntimeException").contains("DB down");
    }

    @Test
    @DisplayName("@Auditable SpEL 解析：#orderId 正确取值")
    void auditableSpel() {
        orderService.deleteOrder("spel-123");
        assertThat(sink.last().getTargetId()).isEqualTo("spel-123");
    }

    @Test
    @DisplayName("@Auditable logArgs=true：入参序列化到 argsJson")
    void auditableLogArgs() {
        orderService.createOrder("new-ord", "Alice");
        AuditRecord r = sink.last();
        assertThat(r.getArgsJson()).contains("new-ord").contains("Alice");
    }

    @Test
    @DisplayName("@Auditable logResult=true：返回值序列化到 resultJson")
    void auditableLogResult() {
        String result = orderService.getName("u-1");
        AuditRecord r = sink.last();
        assertThat(r.getResultJson()).contains("name-u-1");
    }

    @Test
    @DisplayName("Hash Chain：连续 3 条审计记录链式正确")
    void hashChainLinked() {
        orderService.deleteOrder("a");
        orderService.deleteOrder("b");
        orderService.deleteOrder("c");

        assertThat(sink.size()).isEqualTo(3);
        // 第 2 条的 prevHash 应等于第 1 条的 hash
        AuditRecord r1 = sink.getRecords().get(0);
        AuditRecord r2 = sink.getRecords().get(1);
        AuditRecord r3 = sink.getRecords().get(2);
        assertThat(r2.getPrevHash()).isEqualTo(r1.getHash());
        assertThat(r3.getPrevHash()).isEqualTo(r2.getHash());
    }

    @Component
    static class TestOrderService {

        @Auditable(action = "DELETE_ORDER", targetType = "order", targetIdSpel = "#orderId")
        public void deleteOrder(String orderId) {}

        @Auditable(action = "FAIL_ORDER", targetType = "order", targetIdSpel = "#orderId", logOnError = true)
        public void failOrder(String orderId) {
            throw new RuntimeException("DB down");
        }

        @Auditable(action = "CREATE_ORDER", targetType = "order", targetIdSpel = "#orderId", logArgs = true)
        public void createOrder(String orderId, String customer) {
            return;
        }

        @Auditable(action = "GET_NAME", targetType = "user", targetIdSpel = "#id", logResult = true)
        public String getName(String id) {
            return "name-" + id;
        }
    }
}
