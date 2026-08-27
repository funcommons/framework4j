package fun.commons.framework4j.transport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * framework4j-transport 配置。
 *
 * @since 1.4.2
 */
@Data
@ConfigurationProperties(prefix = "framework4j.transport")
public class TransportProperties {

    /**
     * 是否启用自动装配（与 @ConditionalOnProperty 同键，仅作文档化载体）
     */
    private boolean enabled = true;

    /**
     * 显式指定 HttpTransport 复用的 RestTemplate Bean 名。
     * <p>
     * 业务方声明多个 RestTemplate Bean 时按类型注入存在歧义（v1.4.2 之前直接
     * NoUniqueBeanDefinitionException 启动失败，Issue #18）。解析顺序：
     * <ol>
     *   <li>配置了本属性 → 按名取用（Bean 不存在 / 类型不符时启动失败，指名道姓）</li>
     *   <li>未配置且有唯一候选（业务 0 个 → 框架兜底；业务 1 个 → 复用业务实例；
     *       多个但标了 @Primary → 复用主 Bean）→ 自动复用</li>
     *   <li>未配置且歧义 → 降级内置默认实例（无超时）+ WARN 列出候选 Bean 名</li>
     * </ol>
     */
    private String restTemplateBeanName;
}
