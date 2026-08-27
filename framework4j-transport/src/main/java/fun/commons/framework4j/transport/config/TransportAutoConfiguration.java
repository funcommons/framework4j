package fun.commons.framework4j.transport.config;

import fun.commons.framework4j.transport.HttpTransport;
import fun.commons.framework4j.transport.RestTemplateHttpTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;

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
@EnableConfigurationProperties(TransportProperties.class)
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
     * <p>
     * v1.4.2（Issue #18）：形参改 {@link ObjectProvider}，业务方声明 ≥2 个
     * RestTemplate Bean 时不再 NoUniqueBeanDefinitionException 启动失败。
     * 复用解析顺序见 {@link TransportProperties#setRestTemplateBeanName}：
     * 显式 pin 的 Bean 名 &gt; 唯一候选（业务 0 个→框架兜底 / 业务 1 个→复用业务实例 /
     * 多个但标 @Primary→复用主 Bean）&gt; 降级内置默认实例 + WARN。
     */
    @Bean
    @ConditionalOnMissingBean(HttpTransport.class)
    public HttpTransport framework4jHttpTransport(ObjectProvider<RestTemplate> restTemplates,
                                                  TransportProperties properties,
                                                  ListableBeanFactory beanFactory) {
        RestTemplate restTemplate = null;

        String pinned = properties.getRestTemplateBeanName();
        if (StringUtils.hasText(pinned)) {
            // 指名道姓：Bean 不存在 / 类型不符时启动失败，错误信息即配置错误本身
            restTemplate = beanFactory.getBean(pinned, RestTemplate.class);
            log.info("【Transport】HttpTransport 使用指定 RestTemplate Bean [{}]", pinned);
        } else {
            // 唯一候选（含 @Primary 收敛）；0 个或多个未收敛时返回 null
            restTemplate = restTemplates.getIfUnique();
        }

        if (restTemplate == null) {
            String[] candidates = beanFactory.getBeanNamesForType(RestTemplate.class);
            log.warn("【Transport】检测到多个 RestTemplate Bean {}，未配置 framework4j.transport.rest-template-bean-name，"
                    + "HttpTransport 降级使用内置默认实例（无超时配置）。如需复用业务实例请配置该属性指定 bean name",
                    Arrays.toString(candidates));
            restTemplate = new RestTemplate();
        }

        log.info("【Transport】默认 HttpTransport = RestTemplateHttpTransport (重试 3 次/500ms)");
        return new RestTemplateHttpTransport(restTemplate);
    }
}

