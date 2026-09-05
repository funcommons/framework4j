package fun.commons.framework4j.web.exception;

import fun.commons.framework4j.web.config.WebAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #19:GlobalExceptionHandler 曾直接引用 spring-jdbc/spring-tx 异常类,
 * 未携带 spring-jdbc 的纯 Web 应用启动期 NoClassDefFoundError。
 * 修复后:DAO handler 拆到 DataAccessExceptionAdvice + @ConditionalOnClass 条件装配。
 */
class GlobalExceptionHandlerJdbcFreeTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebAutoConfiguration.class));

    @Test
    @DisplayName("无 spring-jdbc(FilteredClassLoader):主 advice 照常装配,DAO advice 缺席,上下文不炸")
    void withoutSpringJdbc_startsFine() {
        runner.withClassLoader(new FilteredClassLoader("org.springframework.jdbc", "org.springframework.dao"))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(GlobalExceptionHandler.class);
                    assertThat(ctx).doesNotHaveBean(DataAccessExceptionAdvice.class);
                });
    }

    @Test
    @DisplayName("有 spring-jdbc:DAO advice 装配,三个 handler 全在")
    void withSpringJdbc_advicePresent() throws Exception {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(DataAccessExceptionAdvice.class);
        });
        // handler 行为直验(与拆出前逐字一致)
        DataAccessExceptionAdvice advice = new DataAccessExceptionAdvice();
        assertThat(advice.handleDuplicateKeyException(
                new org.springframework.dao.DuplicateKeyException("uk_x"))).isNotNull();
        assertThat(advice.handleBadSqlGrammarException(
                new org.springframework.jdbc.BadSqlGrammarException("t", "select 1", null))).isNotNull();
        assertThat(advice.handleDataIntegrityViolationException(
                new org.springframework.dao.DataIntegrityViolationException("constraint"))).isNotNull();
    }

    @Test
    @DisplayName("GlobalExceptionHandler 常量池不再引用 spring dao/jdbc 类(结构性防回归)")
    void globalHandler_noJdbcReferences() {
        for (var method : GlobalExceptionHandler.class.getDeclaredMethods()) {
            for (Class<?> pt : method.getParameterTypes()) {
                assertThat(pt.getName()).doesNotStartWith("org.springframework.jdbc")
                        .doesNotStartWith("org.springframework.dao");
            }
        }
    }
}
