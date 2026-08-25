package fun.commons.framework4j.tracelog.config;

import fun.commons.framework4j.redis.manager.MultiRedisManager;
import fun.commons.framework4j.web.config.WebAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 动态追踪日志 SDK 自动装配入口。
 * <p>
 * 装配顺序：
 * <ol>
 *   <li>{@link TraceLogProperties} — 配置属性绑定</li>
 *   <li>{@link TraceLogFailureAnalyzer} — 启动失败分析</li>
 *   <li>{@link TraceLogBeansConfig} — 各层 Bean 定义</li>
 *   <li>{@link TraceLogWebMvcConfig} — Interceptor 注册</li>
 * </ol>
 */
@Slf4j
@AutoConfiguration(after = {
        WebAutoConfiguration.class,
        fun.commons.framework4j.redis.config.MultiRedisAutoConfiguration.class
})
@ConditionalOnClass(MultiRedisManager.class)
@ConditionalOnProperty(prefix = "framework4j.tracelog", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(TraceLogProperties.class)
@Import({TraceLogBeansConfig.class, TraceLogWebMvcConfig.class})
public class TraceLogAutoConfiguration {

    public TraceLogAutoConfiguration(TraceLogProperties props,
                                     ObjectProvider<MultiRedisManager> multiRedisManager,
                                     ApplicationContext applicationContext) {
        log.info("【TraceLog】初始化: enabled={}, redis-name={}, key-prefix={}",
                props.isEnabled(), props.getRedisName(), props.getStorage().getKeyPrefix());
    }

    /**
     * 从 {@link MultiRedisManager} 按 {@code redis-name} 解析得到 {@link StringRedisTemplate}。
     * <p>
     * 注册为同名 Bean，覆盖 {@code spring-boot-starter-data-redis} 默认 Bean（若有）。
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(MultiRedisManager multiRedisManager,
                                                  TraceLogProperties props) {
        return multiRedisManager.getStringRedisTemplate(props.getRedisName());
    }

    /**
     * 注册启动失败分析器：未配置 {@link TraceLogAuthValidator} 时给出明确错误信息。
     */
    @Bean
    public TraceLogFailureAnalyzer traceLogFailureAnalyzer() {
        return new TraceLogFailureAnalyzer();
    }
}