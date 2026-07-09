package fun.commons.framework4j.openid.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.openid.formatter.OpenIdFormatterFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

/**
 * OpenID 自动配置
 * <p>
 * 提供以下能力（受 {@code framework4j.openid.enabled} 开关控制）：
 * <ul>
 *   <li>Jackson {@link OpenIdJsonSerializer}：@OpenId 字段 Long → 12 字符混淆字符串</li>
 *   <li>{@link OpenIdFormatterFactory}：@OpenId 入参 String → Long 还原</li>
 * </ul>
 *
 * @since 2.0.0（从 framework4j-core 拆分为独立模块）
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(JsonSerializer.class)
@ConditionalOnProperty(
        prefix = "framework4j.openid",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OpenIdAutoConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer framework4jOpenIdJacksonCustomizer() {
        return builder -> {
            SimpleModule openIdModule = new SimpleModule();
            // v2.1 修复：不全局注册 Long.class（会覆盖 api 模块的 Long→String 策略）。
            // OpenIdJsonSerializer 通过 @JsonSerialize(using=...) 字段级注解生效，
            // 由 OpenIdAnnotationIntrospector 自动应用到 @OpenId 字段。
            openIdModule.addSerializer(new OpenIdJsonSerializer());
            builder.modulesToInstall(openIdModule);
            log.info("【OpenID】Jackson @OpenId 序列化器已启用（字段级，不覆盖 Long.class）");
        };
    }

    @Bean
    public WebMvcConfigurer framework4jOpenIdWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addFormatters(FormatterRegistry registry) {
                registry.addFormatterForFieldAnnotation(new OpenIdFormatterFactory());
                log.info("【OpenID】OpenIdFormatterFactory 已注册");
            }
        };
    }

    /**
     * @OpenId 字段 Long → 12 字符混淆字符串序列化器
     * <p>v2.1: 改为 public + 继承 StdSerializer&lt;Long&gt;（无类型注册时 Jackson 需要 handledType()），
     * 通过 @JsonSerialize(using=...) 字段级注解生效，不再全局覆盖 Long.class。
     */
    public static class OpenIdJsonSerializer extends StdSerializer<Long> {
        public OpenIdJsonSerializer() {
            super(Long.class);
        }

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            gen.writeString(IdObfuscator.toOpenId(value));
        }
    }
}
