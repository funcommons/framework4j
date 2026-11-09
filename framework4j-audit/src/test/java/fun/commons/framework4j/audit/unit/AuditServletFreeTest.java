package fun.commons.framework4j.audit.unit;

import fun.commons.framework4j.audit.config.AuditAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #31 回归（v1.10.1）：WebFlux 消费方缺省态启动即炸修复。
 * <ul>
 *   <li>缺陷①：AuditService 签名引用 HttpServletRequest 而 pom 零申报——1.7.3 被
 *       idempotency→starter-web→tomcat 泄漏链掩盖（#27 修复后裸奔）。
 *       修复：pom 补 servlet-api provided + autoconfig SERVLET 收窄</li>
 *   <li>缺陷②：无 @ConditionalOnWebApplication(SERVLET) 收窄——对齐 rate-limit 同族形</li>
 * </ul>
 */
@DisplayName("issue #31：无 servlet 环境 audit 装配静默让位")
class AuditServletFreeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuditAutoConfiguration.class))
            // 模拟 WebFlux 消费方：servlet API 不在 classpath
            .withClassLoader(new FilteredClassLoader("jakarta.servlet"))
            .withPropertyValues("spring.application.name=audit-servlet-free-test");

    @Test
    @DisplayName("无 servlet 类：缺省闸（matchIfMissing=true）下装配静默跳过，上下文不炸")
    void shouldSkipQuietlyWithoutServletClasses() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("auditService");
            assertThat(context).doesNotHaveBean("auditAspect");
        });
    }

    @Test
    @DisplayName("结构契约：@ConditionalOnWebApplication(SERVLET) 显式声明")
    void servletNarrowingDeclared() {
        ConditionalOnWebApplication anno =
                AuditAutoConfiguration.class.getAnnotation(ConditionalOnWebApplication.class);
        assertThat(anno).isNotNull();
        assertThat(anno.type()).isEqualTo(ConditionalOnWebApplication.Type.SERVLET);
    }
}
