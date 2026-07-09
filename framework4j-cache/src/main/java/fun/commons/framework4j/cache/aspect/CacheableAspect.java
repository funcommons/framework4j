package fun.commons.framework4j.cache.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.cache.annotation.CacheableEvict;
import fun.commons.framework4j.cache.annotation.CacheableGet;
import fun.commons.framework4j.cache.annotation.CacheablePut;
import fun.commons.framework4j.cache.service.CacheService;
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
 * 缓存注解 AOP 切面
 * <p>
 * v2.1 P0: 实现 {@link CacheableGet} / {@link CacheablePut} / {@link CacheableEvict} 三个注解的 AOP 拦截。
 * <p>
 * 注解仅做元数据声明，切面自动调用 {@link CacheService} 完成读写删。
 *
 * @since 2.1.0
 */
@Slf4j
@Aspect
public class CacheableAspect {

    private final CacheService cacheService;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer pnd = new DefaultParameterNameDiscoverer();

    public CacheableAspect(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Around("@annotation(cacheableGet)")
    public Object aroundGet(ProceedingJoinPoint pjp, CacheableGet cacheableGet) throws Throwable {
        String key = evalKey(pjp, cacheableGet.key());
        long ttl = cacheableGet.ttl() > 0 ? cacheableGet.ttl() : -1;
        long nullTtl = cacheableGet.nullTtl() > 0 ? cacheableGet.nullTtl() : -1;

        Class<?> returnType = ((MethodSignature) pjp.getSignature()).getReturnType();

        // v2.1 功能增强：透传 nullTtl 到 CacheService
        return cacheService.get(cacheableGet.prefix(), key, ttl, nullTtl,
                () -> {
                    try {
                        return uncheckedProceed(pjp);
                    } catch (Throwable t) {
                        if (t instanceof RuntimeException re) throw re;
                        throw new RuntimeException(t);
                    }
                }, returnType);
    }

    @Around("@annotation(cacheablePut)")
    public Object aroundPut(ProceedingJoinPoint pjp, CacheablePut cacheablePut) throws Throwable {
        Object result = pjp.proceed();
        // v2.1 P1: result==null 不写缓存（避免与 @CacheableGet 的空值短 TTL 语义冲突）
        if (result != null) {
            String key = evalKey(pjp, cacheablePut.key());
            long ttl = cacheablePut.ttl() > 0 ? cacheablePut.ttl() : -1;
            cacheService.put(cacheablePut.prefix(), key, ttl, result);
        }
        return result;
    }

    @Around("@annotation(cacheableEvict)")
    public Object aroundEvict(ProceedingJoinPoint pjp, CacheableEvict cacheableEvict) throws Throwable {
        Object result = pjp.proceed();
        String key = evalKey(pjp, cacheableEvict.key());
        cacheService.evict(cacheableEvict.prefix(), key);
        return result;
    }

    private String evalKey(ProceedingJoinPoint pjp, String spel) {
        if (spel == null || spel.isEmpty()) return "";
        try {
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            Method method = signature.getMethod();
            MethodBasedEvaluationContext ctx = new MethodBasedEvaluationContext(
                    pjp.getTarget(), method, pjp.getArgs(), pnd);
            Object evaluated = parser.parseExpression(spel).getValue(ctx);
            return evaluated != null ? String.valueOf(evaluated) : "";
        } catch (Exception e) {
            log.warn("[Cache] key SpEL evaluate failed expr='{}': {}", spel, e.getMessage());
            return "";
        }
    }

    /** 包装 proceed 抛出的 Throwable 供 Supplier 使用 */
    @SuppressWarnings("unchecked")
    private static <T> T uncheckedProceed(ProceedingJoinPoint pjp) throws Throwable {
        return (T) pjp.proceed();
    }
}
