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
 *   <li>{@link TraceConfig}（Micrometer Tracing + MDC + X-Trace-Id 响应头）</li>
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
@Import({TraceConfig.class, WebConfig.class})
public class WebAutoConfiguration {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
