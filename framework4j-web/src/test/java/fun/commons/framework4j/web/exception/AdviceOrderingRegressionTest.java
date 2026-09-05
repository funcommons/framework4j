package fun.commons.framework4j.web.exception;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #24 结构性防回归：v1.6.0(#19) 将 DAO 异常翻译拆出 GlobalExceptionHandler 后，
 * 两个 @RestControllerAdvice 均未声明 @Order。Spring MVC 跨 advice 解析规则是
 * 「按 Order 排序后，第一个含匹配方法的 advice 胜出」（不做全局最具体匹配）——
 * 兜底 {@code @ExceptionHandler(Exception.class)} 因此抢跑 DuplicateKeyException，
 * 200 + 业务信封契约退化为 500。
 */
class AdviceOrderingRegressionTest {

    @Test
    void dataAccessAdvice_Should_PrecedeGlobalCatchAll_When_ResolvedBySpringOrder() {
        Integer daoOrder = orderOf(DataAccessExceptionAdvice.class);
        Integer globalOrder = orderOf(GlobalExceptionHandler.class);

        assertThat(daoOrder).as("DataAccessExceptionAdvice 必须显式声明 @Order").isNotNull();
        assertThat(globalOrder).as("GlobalExceptionHandler 必须显式声明 @Order").isNotNull();
        assertThat(daoOrder).as("具体 DAO advice 必须先于兜底被匹配").isLessThan(globalOrder);
        assertThat(globalOrder).as("兜底殿后（宿主/具体 advice 均可插队）").isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }

    @Test
    void springOrderComparator_Should_SortDaoAdviceFirst_EvenWhenRegisteredBefore() {
        List<Object> advices = new ArrayList<>(List.of(new GlobalExceptionHandler(), new DataAccessExceptionAdvice()));
        AnnotationAwareOrderComparator.sort(advices);
        assertThat(advices.get(0)).isInstanceOf(DataAccessExceptionAdvice.class);
    }

    private Integer orderOf(Class<?> type) {
        Order order = type.getAnnotation(Order.class);
        return order != null ? order.value() : null;
    }
}
