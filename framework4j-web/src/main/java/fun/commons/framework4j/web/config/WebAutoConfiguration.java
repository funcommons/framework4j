package fun.commons.framework4j.web.config;

import fun.commons.framework4j.web.ApiResponse;
import fun.commons.framework4j.web.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * framework4j-web 自动装配
 * <p>
 * 注册 Web 层契约：
 * <ul>
 *   <li>{@link GlobalExceptionHandler}（@RestControllerAdvice 自动扫描）</li>
 *   <li>{@link TraceConfig}（Micrometer Tracing + MDC + X-Trace-Id 响应头——自 v1.6.0 起独立 AutoConfiguration 注册,见 imports）</li>
 *   <li>{@link WebConfig}（Jackson snake_case + Long→String + JavaTimeModule）</li>
 * </ul>
 * <p>
 * 通过 {@code framework4j.web.enabled=false} 关闭（默认开启）。
 *
 * @since 2.1.0
 */
@AutoConfiguration
@ConditionalOnClass({ApiResponse.class, WebMvcConfigurer.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "framework4j.web", name = "enabled", matchIfMissing = true)
@Import({WebConfig.class})
public class WebAutoConfiguration {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * DAO/DB 异常 advice(Issue #19):仅当 classpath 有 spring-jdbc 才装配 ——
     * 拆出独立类是因为 @ExceptionHandler 方法签名在 MVC 内省 advice 时必须解析,
     * 合在主 advice 里会让纯 Web(不带 spring-jdbc)的应用启动期 NoClassDefFoundError。
     */
    @Bean
    @ConditionalOnClass(org.springframework.jdbc.BadSqlGrammarException.class)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    public fun.commons.framework4j.web.exception.DataAccessExceptionAdvice dataAccessExceptionAdvice() {
        return new fun.commons.framework4j.web.exception.DataAccessExceptionAdvice();
    }
}
