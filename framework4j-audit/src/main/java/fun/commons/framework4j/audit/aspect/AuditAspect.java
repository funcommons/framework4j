package fun.commons.framework4j.audit.aspect;

import fun.commons.framework4j.audit.annotation.Auditable;
import fun.commons.framework4j.audit.service.AuditService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;

/**
 * 审计 AOP 切面
 * <p>
 * 拦截 {@link Auditable} 注解方法，自动记录审计日志。
 *
 * @since 2.1.0
 */
@Slf4j
@Aspect
public class AuditAspect {

    private final AuditService auditService;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer pnd = new DefaultParameterNameDiscoverer();

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(auditable)")
    public Object around(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        Object result = null;
        String errorMessage = null;
        boolean success = false;
        try {
            result = pjp.proceed();
            success = true;
            return result;
        } catch (Throwable e) {
            // v2.1 P0: 保留异常类型 + 消息，便于审计追溯
            errorMessage = e.getClass().getName() + ": " + e.getMessage();
            throw e;
        } finally {
            if (success || auditable.logOnError()) {
                try {
                    record(pjp, auditable, success, errorMessage, result);
                } catch (Exception e) {
                    log.warn("[Audit] aspect record failed: {}", e.getMessage());
                }
            }
        }
    }

    private void record(ProceedingJoinPoint pjp, Auditable auditable,
                        boolean success, String errorMessage, Object result) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();

        String targetId = "";
        if (!auditable.targetIdSpel().isEmpty()) {
            try {
                MethodBasedEvaluationContext ctx = new MethodBasedEvaluationContext(
                        pjp.getTarget(), method, pjp.getArgs(), pnd);
                Object evaluated = parser.parseExpression(auditable.targetIdSpel()).getValue(ctx);
                targetId = evaluated != null ? String.valueOf(evaluated) : "";
            } catch (Exception e) {
                // v2.1 P0: SpEL 失败应 warn（targetId 缺失影响审计完整性）
                log.warn("[Audit] targetIdSpel evaluate failed expr='{}': {}",
                        auditable.targetIdSpel(), e.getMessage());
            }
        }

        Object[] args = auditable.logArgs() ? pjp.getArgs() : null;
        Object returnValue = auditable.logResult() ? result : null;

        auditService.audit(
                auditable.action(),
                auditable.targetType(),
                targetId,
                success ? "SUCCESS" : "ERROR",
                errorMessage,
                args,
                returnValue);
    }
}
