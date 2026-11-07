package fun.commons.framework4j.web.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #24 端到端回归（真实 MVC 解析链）：DAO 异常必须命中
 * {@link DataAccessExceptionAdvice}（@Order HIGHEST_PRECEDENCE+100），
 * 不得被 {@link GlobalExceptionHandler} 的兜底 @ExceptionHandler(Exception)
 * （@Order LOWEST_PRECEDENCE）抢跑成 10001 SYSTEM_BUSY。
 * <p>
 * v1.7.1 的 {@link AdviceOrderingRegressionTest} 是结构性断言（@Order 值比较 + 排序器验证）；
 * 本测试补真实 {@code ExceptionHandlerExceptionResolver} 链路——
 * standalone MockMvc 的 ControllerAdvice 解析与生产同一机制（含 @Order 排序）。
 */
@DisplayName("issue #24 advice 排序端到端契约（DAO 异常 → 200 信封，非 10001）")
class AdviceOrderingMvcContractTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/dao/duplicate-key")
        public String duplicateKey() {
            throw new DuplicateKeyException("Duplicate entry 'a@b.c' for key 'uk_email'");
        }

        @GetMapping("/dao/bad-grammar")
        public String badGrammar() {
            throw new BadSqlGrammarException("dao", "select * from gone_table", null);
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new DataAccessExceptionAdvice(), new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("DuplicateKeyException → HTTP 200 + BUSINESS_RULE_ERROR「数据已存在」，非兜底 10001")
    void duplicateKey_shouldHitDaoAdvice() throws Exception {
        mockMvc.perform(get("/dao/duplicate-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10106))
                .andExpect(jsonPath("$.message").value("数据已存在，请勿重复提交"))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(10001)));
    }

    @Test
    @DisplayName("BadSqlGrammarException → DAO advice 的「数据库操作异常」（非兜底文案）")
    void badGrammar_shouldHitDaoAdvice() throws Exception {
        mockMvc.perform(get("/dao/bad-grammar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.message").value("数据库操作异常"));
    }
}
