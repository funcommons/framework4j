package fun.commons.framework4j.web.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
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
 * API Web 配置（v2.2：Jackson 三开关独立可关）
 * <p>
 * 三个独立 Jackson 定制器，各自受 {@code framework4j.web.jackson.*} 控制，默认全开（向后兼容）：
 * <ul>
 *   <li>{@code snake-case}（默认 true）：全局 snake_case 命名（对齐 mc-api-spec §5.1）</li>
 *   <li>{@code long-to-string}（默认 true）：{@code Long}/{@code long} → {@code String}（防 JS 精度丢失）</li>
 *   <li>{@code fail-on-unknown-properties}（默认 false）：未知字段是否报错</li>
 * </ul>
 * <p>
 * Master 开关是 {@code framework4j.web.enabled}（默认 true）。关闭它将同时关闭整个 WebAutoConfiguration。
 * <p>
 * 已迁移到独立模块的能力：
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
@ConditionalOnProperty(prefix = "framework4j.web", name = "enabled", havingValue = "true", matchIfMissing = true)
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
    @ConditionalOnProperty(prefix = "framework4j.web.jackson", name = "snake-case",
            havingValue = "true", matchIfMissing = true)
    public Jackson2ObjectMapperBuilderCustomizer framework4jSnakeCaseCustomizer() {
        return builder -> {
            builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            log.info("【API】Jackson snake_case 已启用（framework4j.web.jackson.snake-case=true）");
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework4j.web.jackson", name = "long-to-string",
            havingValue = "true", matchIfMissing = true)
    public Jackson2ObjectMapperBuilderCustomizer framework4jLongToStringCustomizer() {
        return builder -> {
            // v2.1 修复：用 modulesToInstall 累加而非 modules 覆盖（其他 customizer 注册的模块不会被冲掉）
            builder.modulesToInstall(LONG_TO_STRING_MODULE);
            log.info("【API】Jackson Long→String 已启用（framework4j.web.jackson.long-to-string=true）");
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework4j.web.jackson", name = "fail-on-unknown-properties",
            havingValue = "false", matchIfMissing = true)
    public Jackson2ObjectMapperBuilderCustomizer framework4jLenientUnknownPropertiesCustomizer() {
        return builder -> {
            builder.failOnUnknownProperties(false);
            log.info("【API】Jackson failOnUnknownProperties=false 已启用（默认 lenient）");
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
