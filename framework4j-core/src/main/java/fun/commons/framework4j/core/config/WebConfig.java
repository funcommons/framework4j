package fun.commons.framework4j.core.config;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.filter.Filter;
import com.alibaba.fastjson2.support.config.FastJsonConfig;
import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import fun.commons.framework4j.datetime.DynamicTimeFilter;
import fun.commons.framework4j.datetime.StringArrayToOffsetDateTimeCollectionConverter;
import fun.commons.framework4j.datetime.StringToOffsetDateTimeConverter;
import fun.commons.framework4j.datetime.TimeFormatInterceptor;
import fun.commons.framework4j.openid.fastjson2.OpenIdAnnotationFilter;
import fun.commons.framework4j.openid.formatter.OpenIdFormatterFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@AutoConfiguration
@ConditionalOnClass(FastJsonHttpMessageConverter.class)
@ConditionalOnProperty(name = "ldx2t.commons.core.config.enabled", havingValue = "true", matchIfMissing = true)
public class WebConfig implements WebMvcConfigurer {

    private final boolean openIdEnabled;
    private final boolean datetimeEnabled;

    // 1. 构造器注入配置 (推荐)
    public WebConfig(
            @Value("${ldx2t.commons.openid.enabled:true}") boolean openIdEnabled,
            @Value("${ldx2t.commons.datetime.enabled:true}") boolean datetimeEnabled
    ) {
        this.openIdEnabled = openIdEnabled;
        this.datetimeEnabled = datetimeEnabled;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (datetimeEnabled) {
            registry.addInterceptor(new TimeFormatInterceptor());
            log.info("【DateTime】组件已开启：已注册 TimeFormatInterceptor");
        }
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        if (openIdEnabled) {
            registry.addFormatterForFieldAnnotation(new OpenIdFormatterFactory());
            log.info("【OpenID】OpenIdFormatterFactory 已注册");
        }
        if (datetimeEnabled) {
            registry.addConverter(new StringToOffsetDateTimeConverter());
            log.info("【DateTime】StringToOffsetDateTimeConverter 已注册");

            registry.addConverter(new StringArrayToOffsetDateTimeCollectionConverter());
            log.info("【DateTime】StringArrayToOffsetDateTimeCollectionConverter 已注册");
        }
    }

    // 2.【核心修改】在此处配置 FastJson2，替代 @Bean
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();
        FastJsonConfig config = new FastJsonConfig();

        // 防止 Filter 互相覆盖的修复逻辑
        List<Filter> filters = new ArrayList<>();

        if (openIdEnabled) {
            filters.add(new OpenIdAnnotationFilter());
            log.info("【OpenID】FastJson2 写过滤器已启用");
        }

        if (datetimeEnabled) {
            config.setDateFormat("iso8601");
            filters.add(new DynamicTimeFilter());
            log.info("【DateTime】FastJson2 动态时间过滤器已启用");
        }

        // 统一设置过滤器
        if (!filters.isEmpty()) {
            config.setWriterFilters(filters.toArray(new Filter[0]));
        }

        config.setCharset(StandardCharsets.UTF_8);
        config.setWriterFeatures(JSONWriter.Feature.WriteLongAsString); // 解决JS精度丢失

        converter.setFastJsonConfig(config);
        converter.setDefaultCharset(StandardCharsets.UTF_8);
        converter.setSupportedMediaTypes(Collections.singletonList(MediaType.APPLICATION_JSON));

        // 【关键】添加到列表首位，确保优先级高于 Spring Boot 可能添加的其他转换器
        converters.add(0, converter);
    }

    // 3. 仍然建议保留此方法，彻底清理 Jackson
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 移除 Jackson，确保系统中只有 FastJson2 在工作，避免混用带来的奇怪序列化问题
        converters.removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);
    }
}