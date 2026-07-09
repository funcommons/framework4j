package fun.commons.framework4j.web.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;

/**
 * API Web 配置（v2.0：Jackson + 模块拆分后精简）
 * <p>
 * 仅保留全局 Jackson 定制：
 * <ul>
 *   <li>全局 snake_case 命名策略（对齐 mc-api-spec §5.1）</li>
 *   <li>{@code Long} / {@code long} → {@code String} 序列化（防 JS 精度丢失，对齐 mc-api-spec §5.2）</li>
 * </ul>
 *
 * <p>已迁移到独立模块的能力：
 * <ul>
 *   <li>OffsetDateTime 序列化 → {@code framework4j-datetime}</li>
 *   <li>@OpenId 字段混淆 + 入参还原 → {@code framework4j-id}</li>
 * </ul>
 *
 * @since 2.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(Jackson2ObjectMapperBuilder.class)
@ConditionalOnProperty(name = "framework4j.api.config.enabled", havingValue = "true", matchIfMissing = true)
public class WebConfig {

    /** Long → String 序列化器（防 JS 精度丢失，对齐 mc-api-spec §5.2） */
    private static final ToStringSerializer LONG_TO_STRING = new ToStringSerializer();

    /** Long → String 模块（复用实例，避免每次启动重建） */
    private static final SimpleModule LONG_TO_STRING_MODULE;
    static {
        LONG_TO_STRING_MODULE = new SimpleModule();
        LONG_TO_STRING_MODULE.addSerializer(Long.class, LONG_TO_STRING);
        LONG_TO_STRING_MODULE.addSerializer(Long.TYPE, LONG_TO_STRING);
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer framework4jJacksonCustomizer() {
        return builder -> {
            // 1. 全局 snake_case
            builder.propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);

            // 2. Long / long → String（防 JS 精度丢失）
            // v2.1 修复：用 modulesToInstall 累加而非 modules 覆盖（其他 customizer 注册的模块不会被冲掉）
            builder.modulesToInstall(LONG_TO_STRING_MODULE);

            builder.failOnUnknownProperties(false);

            log.info("【API】Jackson 定制器已启用（snake_case + Long→String）");
        };
    }

    /**
     * Long → String 序列化器（防 JS 精度丢失，对齐 mc-api-spec §5.2 大整数与精度）
     */
    private static class ToStringSerializer extends JsonSerializer<Long> {
        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.toString());
        }
    }
}
