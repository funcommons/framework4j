package fun.commons.framework4j.datetime.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import fun.commons.framework4j.datetime.DynamicTimeFilter;
import fun.commons.framework4j.datetime.StringArrayToOffsetDateTimeCollectionConverter;
import fun.commons.framework4j.datetime.StringToOffsetDateTimeConverter;
import fun.commons.framework4j.datetime.TimeFormatInterceptor;
import fun.commons.framework4j.datetime.TimeFormatStateHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.OffsetDateTime;

/**
 * 时间处理自动配置
 * <p>
 * 提供以下能力（受 {@code framework4j.datetime.enabled} 开关控制）：
 * <ul>
 *   <li>{@link TimeFormatInterceptor}：动态时间格式拦截器（基于 ThreadLocal 状态）</li>
 *   <li>{@link StringToOffsetDateTimeConverter}：String → OffsetDateTime 入参转换</li>
 *   <li>{@link StringArrayToOffsetDateTimeCollectionConverter}：String[] → 集合转换</li>
 *   <li>Jackson {@link DynamicTimeFilter}：OffsetDateTime → GMT+8 字符串序列化</li>
 * </ul>
 *
 * @since 2.0.0（从 framework4j-core 拆分为独立模块）
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(OffsetDateTime.class)
@ConditionalOnProperty(
        prefix = "framework4j.datetime",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DateTimeAutoConfiguration implements DisposableBean {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer framework4jDateTimeJacksonCustomizer() {
        return builder -> {
            SimpleModule datetimeModule = new SimpleModule();
            datetimeModule.addSerializer(OffsetDateTime.class, new DynamicTimeFilter());
            // v2.1 修复：用 modulesToInstall 累加而非 modules 覆盖（与 api 模块的 Long→String 模块共存）
            builder.modulesToInstall(datetimeModule);
            log.info("【DateTime】Jackson OffsetDateTime 序列化器已启用（GMT+8 动态格式）");
        };
    }

    @Bean
    public WebMvcConfigurer framework4jDateTimeWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new TimeFormatInterceptor());
                log.info("【DateTime】TimeFormatInterceptor 已注册");
            }

            @Override
            public void addFormatters(FormatterRegistry registry) {
                registry.addConverter(new StringToOffsetDateTimeConverter());
                registry.addConverter(new StringArrayToOffsetDateTimeCollectionConverter());
                log.info("【DateTime】String→OffsetDateTime 转换器已注册");
            }
        };
    }

    /**
     * v2.1: 上下文关闭时关闭 TimeFormatStateHolder 的静态线程池，避免资源泄漏。
     */
    @Override
    public void destroy() {
        log.info("【DateTime】destroy, 关闭 TimeFormatStateHolder 清理线程池");
        TimeFormatStateHolder.shutdown();
    }
}
