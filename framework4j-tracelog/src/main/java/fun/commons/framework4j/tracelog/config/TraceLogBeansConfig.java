package fun.commons.framework4j.tracelog.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.filter.Filter;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import fun.commons.framework4j.tracelog.appender.AsyncRedisLogAppender;
import fun.commons.framework4j.tracelog.config.TraceLogAuthValidator;
import fun.commons.framework4j.tracelog.appender.DynamicLevelTurboFilter;
import fun.commons.framework4j.tracelog.query.LogExporter;
import fun.commons.framework4j.tracelog.query.TraceLogQueryController;
import fun.commons.framework4j.tracelog.metrics.TraceLogMetrics;
import fun.commons.framework4j.tracelog.store.FallbackReplayer;
import fun.commons.framework4j.tracelog.store.LocalFallbackWriter;
import fun.commons.framework4j.tracelog.store.TraceLogStore;
import fun.commons.framework4j.tracelog.switcher.SwitchPubSubListener;
import fun.commons.framework4j.tracelog.switcher.SwitchStreamsListener;
import fun.commons.framework4j.tracelog.switcher.SwitchRateLimiter;
import fun.commons.framework4j.tracelog.switcher.SwitchResyncScheduler;
import fun.commons.framework4j.tracelog.switcher.SwitchRuleCache;
import fun.commons.framework4j.tracelog.switcher.TraceLogSwitchInterceptor;
import fun.commons.framework4j.tracelog.util.TenantKeyResolver;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 集中 Bean 定义：存储 / 采集 / 开关 / API / 提权各层。
 * <p>
 * 被 {@link TraceLogAutoConfiguration} {@code @Import} 引入。
 */
@Slf4j
@Configuration
@EnableScheduling
public class TraceLogBeansConfig {

    // ==================== 存储层 ====================

    @Bean
    @ConditionalOnMissingBean
    public LocalFallbackWriter localFallbackWriter(TraceLogProperties props) {
        log.info("【TraceLog】创建 LocalFallbackWriter: dir={}", props.getCollection().getFallbackDir());
        return new LocalFallbackWriter(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantKeyResolver tenantKeyResolver(TraceLogProperties props,
                                               org.springframework.beans.factory.BeanFactory beanFactory) {
        return new TenantKeyResolver(props, beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceLogStore traceLogStore(@org.springframework.beans.factory.annotation.Qualifier("traceLogStringRedisTemplate") StringRedisTemplate stringRedisTemplate,
                                       TraceLogProperties props,
                                       TenantKeyResolver tenantKeyResolver) {
        log.info("【TraceLog】创建 TraceLogStore");
        return new TraceLogStore(stringRedisTemplate, props, traceId -> tenantKeyResolver.currentTenant());
    }

    @Bean
    @ConditionalOnMissingBean
    public FallbackReplayer fallbackReplayer(TraceLogProperties props,
                                             @org.springframework.beans.factory.annotation.Qualifier("traceLogStringRedisTemplate") StringRedisTemplate redis,
                                             LocalFallbackWriter fallbackWriter,
                                             TraceLogStore store) {
        log.info("【TraceLog】创建 FallbackReplayer");
        return new FallbackReplayer(props, redis, fallbackWriter, store);
    }

    // ==================== 采集层 ====================

    @Bean
    @ConditionalOnProperty(prefix = "framework4j.tracelog", name = "append-to-logback", havingValue = "true", matchIfMissing = true)
    public AsyncRedisLogAppender asyncRedisLogAppender(TraceLogProperties props,
                                                       TraceLogStore store,
                                                       LocalFallbackWriter fallbackWriter,
                                                       TenantKeyResolver tenantKeyResolver) {
        log.info("【TraceLog】创建 AsyncRedisLogAppender");
        AsyncRedisLogAppender appender = new AsyncRedisLogAppender(props, store, fallbackWriter, tenantKeyResolver);
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        // 挂到 root logger（也可让消费方手动配置 logback-spring.xml 引用）
        Logger root = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(appender);
        return appender;
    }

    // ==================== 开关层 ====================

    @Bean
    @ConditionalOnMissingBean
    public SwitchRuleCache switchRuleCache(TraceLogProperties props) {
        return new SwitchRuleCache(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public SwitchPubSubListener switchPubSubListener(TraceLogProperties props,
                                                     @org.springframework.beans.factory.annotation.Qualifier("traceLogStringRedisTemplate") StringRedisTemplate redis,
                                                     SwitchRuleCache cache,
                                                     RedisMessageListenerContainer container) {
        return new SwitchPubSubListener(props, redis, cache, container);
    }

    @Bean
    @ConditionalOnMissingBean
    public SwitchStreamsListener switchStreamsListener(TraceLogProperties props,
                                                       @org.springframework.beans.factory.annotation.Qualifier("traceLogStringRedisTemplate") StringRedisTemplate redis,
                                                       SwitchRuleCache cache) {
        return new SwitchStreamsListener(props, redis, cache);
    }

    @Bean
    @ConditionalOnMissingBean
    public SwitchResyncScheduler switchResyncScheduler(TraceLogProperties props,
                                                       @org.springframework.beans.factory.annotation.Qualifier("traceLogStringRedisTemplate") StringRedisTemplate redis,
                                                       SwitchRuleCache cache) {
        return new SwitchResyncScheduler(props, redis, cache);
    }

    @Bean
    @ConditionalOnMissingBean
    public SwitchRateLimiter switchRateLimiter(TraceLogProperties props) {
        return new SwitchRateLimiter(props.getApi().getSwitchRateLimitPerMinute());
    }

    // ==================== 指标层 ====================

    /**
     * 绑定 Micrometer 指标（需 spring-boot-starter-actuator 或 micrometer-core）。
     */
    @Bean
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    @ConditionalOnMissingBean
    public TraceLogMetrics traceLogMetrics(AsyncRedisLogAppender appender,
                                           org.springframework.beans.factory.ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistryProvider,
                                           TraceLogProperties props) {
        io.micrometer.core.instrument.MeterRegistry registry =
                meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            log.info("【TraceLog】MeterRegistry 不可用, 指标绑定跳过");
            return null;
        }
        return new TraceLogMetrics(appender, registry, props);
    }

    // ==================== 提权层 ====================

    /**
     * 注册 Logback TurboFilter（启动后立即生效）。
     */
    @Bean
    public DynamicLevelTurboFilter dynamicLevelTurboFilter(TraceLogProperties props) {
        DynamicLevelTurboFilter filter = new DynamicLevelTurboFilter(props);
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        filter.setContext(lc);
        filter.setName("DynamicLevelTurboFilter");
        filter.start();
        lc.addTurboFilter(filter);
        log.info("【TraceLog】注册 TurboFilter: DynamicLevelTurboFilter");
        return filter;
    }

    // ==================== API 层 ====================

    @Bean
    @ConditionalOnMissingBean
    public TraceLogQueryController traceLogQueryController(TraceLogProperties props,
                                                          TraceLogStore store,
                                                          LogExporter exporter,
                                                          @org.springframework.beans.factory.annotation.Qualifier("traceLogStringRedisTemplate") StringRedisTemplate redis,
                                                          SwitchRateLimiter switchRateLimiter,
                                                          TenantKeyResolver tenantKeyResolver,
                                                          ObjectProvider<SwitchStreamsListener> streamsListenerProvider,
                                                          ObjectProvider<TraceLogAuthValidator> authValidatorProvider) {
        return new TraceLogQueryController(props, store, exporter, redis, switchRateLimiter,
                tenantKeyResolver, streamsListenerProvider, authValidatorProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public LogExporter logExporter(TraceLogStore store, TraceLogProperties props) {
        return new LogExporter(store, props);
    }
}