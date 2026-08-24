package fun.commons.framework4j.transport.config;

import fun.commons.framework4j.transport.HttpTransport;
import fun.commons.framework4j.transport.RestTemplateHttpTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * framework4j-transport 自动装配。
 * <p>
 * 默认提供 {@link RestTemplateHttpTransport}（classpath 有 RestTemplate 时）。
 * 业务方自定义 HttpTransport Bean（如 WebClient 实现 / S2S 签名装饰器）时,
 * 通过 {@code @ConditionalOnMissingBean} 自动让位。
 * <p>
 * 默认 RestTemplate 无超时配置, 生产环境建议业务方自行声明带超时的
 * RestTemplate Bean 覆盖。
 *
 * @since 1.2.9
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(RestTemplate.class)
@ConditionalOnProperty(prefix = "framework4j.transport", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TransportAutoConfiguration {

    /**
     * 兜底 RestTemplate（业务方未声明时）。无超时配置, 生产应覆盖。
     */
    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate framework4jRestTemplate() {
        log.info("【Transport】业务方未声明 RestTemplate, 使用默认实例 (无超时配置, 生产建议覆盖)");
        return new RestTemplate();
    }

    /**
     * 默认 HttpTransport（RestTemplate + 重试 3 次/500ms）。
     * 业务方声明任意 HttpTransport Bean 后自动让位。
     */
    @Bean
    @ConditionalOnMissingBean(HttpTransport.class)
    public HttpTransport framework4jHttpTransport(RestTemplate restTemplate) {
        log.info("【Transport】默认 HttpTransport = RestTemplateHttpTransport (重试 3 次/500ms)");
        return new RestTemplateHttpTransport(restTemplate);
    }
}
