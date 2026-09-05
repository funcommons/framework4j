package fun.commons.framework4j.web.exception;

import fun.commons.framework4j.api.ApiCode;
import fun.commons.framework4j.web.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 数据访问层异常处理(spring-jdbc / spring-tx 的 DAO 异常)。
 * <p>
 * 独立于 {@link GlobalExceptionHandler} 的原因(GitHub Issue #19):
 * 上述异常类不在 spring-boot-starter-web 的传递依赖中,若合在同一 advice 类,
 * Spring MVC 内省 handler 方法签名时类解析失败 → 未携带 spring-jdbc 的纯 Web 应用启动即炸。
 * 本类由 WebAutoConfiguration 以 {@code @ConditionalOnClass(BadSqlGrammarException.class)}
 * 条件装配 —— 带 spring-jdbc 的应用照常生效,不带的应用不受影响。
 * <p>
 * v1.7.1(GitHub Issue #24): 显式声明 @Order。Spring MVC 跨 advice 解析规则是
 * 「按 Order 排序后,第一个含匹配方法的 advice 胜出」(不做全局最具体匹配);
 * 本类必须先于 {@link GlobalExceptionHandler} 的 {@code @ExceptionHandler(Exception.class)}
 * 兜底被匹配,否则 DuplicateKeyException 等被兜底吃掉 → 500,而契约是 200 + 业务信封。
 * 取 HIGHEST_PRECEDENCE + 100:宿主如需自定义翻译,可用更小 Order 值抢占。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@RestControllerAdvice
public class DataAccessExceptionAdvice {

    /**
     * 处理数据库唯一键冲突 (Duplicate Key)
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("[数据库唯一键冲突] {}", e.getMessage());
        // 通常意味着插入了重复数据
        return ApiResponse.fail(ApiCode.BUSINESS_RULE_ERROR, "数据已存在，请勿重复提交");
    }

    /**
     * 处理数据库 SQL 语法错误
     */
    @ExceptionHandler(BadSqlGrammarException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleBadSqlGrammarException(BadSqlGrammarException e) {
        log.error("[数据库SQL语法错误]", e);
        return ApiResponse.fail(ApiCode.SYSTEM_BUSY, "数据库操作异常");
    }

    /**
     * 处理数据库数据完整性异常 (如字段过长、非空约束等)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.error("[数据库数据完整性异常]", e);
        return ApiResponse.fail(ApiCode.BUSINESS_RULE_ERROR, "数据操作失败，请检查数据约束");
    }
}
