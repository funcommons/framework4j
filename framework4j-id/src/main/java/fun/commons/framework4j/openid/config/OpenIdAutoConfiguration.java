package fun.commons.framework4j.openid.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.openid.formatter.OpenIdFormatterFactory;
import fun.commons.framework4j.openid.util.OpenIdTypeSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

/**
 * OpenID 自动配置
 * <p>
 * 提供以下能力（受 {@code framework4j.openid.enabled} 开关控制）：
 * <ul>
 *   <li>Jackson {@link OpenIdJsonSerializer}：@OpenId 字段 Long → 12 字符混淆字符串</li>
 *   <li>Jackson {@link OpenIdBeanDeserializerModifier}（v1.3）：@RequestBody 中 @OpenId 字段
 *       Long / List&lt;Long&gt; / 嵌套字段 ← 混淆串还原（R1/R2/R3），
 *       子开关 {@code framework4j.openid.request-body-deserializer}（默认 true）</li>
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

    /**
     * v1.3: 请求体反序列化器子开关（默认 true）。
     * <p>
     * {@code framework4j.openid.enabled=false} 关闭整个 OpenId（含序列化）；本开关仅在 enabled=true 时
     * 进一步控制反序列化侧，供"特殊 DTO 走自定义反序列化"的场景单独关闭。
     */
    private static final String REQUEST_BODY_DESERIALIZER_PROPERTY = "framework4j.openid.request-body-deserializer";

    /**
     * v1.3 关键修复：把 OpenId Jackson 模块通过 {@link BeanPostProcessor} 直接 {@code registerModule}
     * 到容器内的 {@link ObjectMapper}，而<strong>不再</strong>走
     * {@code Jackson2ObjectMapperBuilderCustomizer#modulesToInstall}。
     * <p>
     * 原因：{@code modulesToInstall} 在存在多个 customizer 时（典型：framework4j-web 的 Long→String 也用
     * {@code modulesToInstall}）会让先注册的 {@link SimpleModule} 成为"孤儿"——其 {@code setupModule}
     * 不被调用，序列化与反序列化 modifier 全部失效。这是 framework4j 既有的一个隐藏缺陷（序列化侧也受波及，
     * 对应 {@code OpenIdWebIntegrationTest} 长期 {@code @Disabled}）。容器级集成测试
     * {@code OpenIdRequestBodyIntegrationTest} 证明：换用 modulesToInstall 后 @OpenId 字段既不混淆也不还原。
     * <p>
     * 改用 {@link BeanPostProcessor} 在 {@link ObjectMapper} 完全初始化后直接注册模块，
     * 绕开 builder 的 module 装配语义，彻底消除互冲，序列化/反序列化同时可靠生效。
     * <p>
     * static：与同模块 {@code OpenIdArgumentResolverRegistrar} 一致，避免 BeanPostProcessor 触发过早初始化告警。
     */
    @Bean
    public static BeanPostProcessor framework4jOpenIdJacksonModuleRegistrar(Environment environment) {
        boolean requestBodyDeserializer = environment.getProperty(
                REQUEST_BODY_DESERIALIZER_PROPERTY, Boolean.class, Boolean.TRUE);
        OpenIdTypeSupport typeSupport = OpenIdTypeSupport.from(environment);
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof ObjectMapper mapper) {
                    SimpleModule openIdModule = new SimpleModule("framework4j-openid");
                    // 序列化侧：@OpenId Long / Integer / String / 其集合数组 → 混淆串（受三开关控制）
                    openIdModule.setSerializerModifier(new OpenIdBeanSerializerModifier(typeSupport));
                    // 反序列化侧（R1/R2/R3 请求体还原），受子开关控制
                    if (requestBodyDeserializer) {
                        openIdModule.setDeserializerModifier(new OpenIdBeanDeserializerModifier(typeSupport));
                    }
                    mapper.registerModule(openIdModule);
                    log.info("【OpenID】Jackson @OpenId 模块已注册到 ObjectMapper[{}]（序列化✓，反序列化{}，受理标量: {}）",
                            beanName, requestBodyDeserializer ? "✓"
                                    : "✗（" + REQUEST_BODY_DESERIALIZER_PROPERTY + "=false）",
                            typeSupport.scalarTypes());
                }
                return bean;
            }
        };
    }

    @Bean
    public WebMvcConfigurer framework4jOpenIdWebMvcConfigurer(Environment environment) {
        OpenIdTypeSupport typeSupport = OpenIdTypeSupport.from(environment);
        return new WebMvcConfigurer() {
            @Override
            public void addFormatters(FormatterRegistry registry) {
                registry.addFormatterForFieldAnnotation(new OpenIdFormatterFactory(typeSupport));
                log.info("【OpenID】OpenIdFormatterFactory 已注册（受理标量类型: {}）", typeSupport.scalarTypes());
            }
        };
    }

    /**
     * v2.2：注册前置 ArgumentResolver，处理 {@code @OpenId @PathVariable} 入参还原，
     * 绕过 Spring {@code MethodParameter.getParameterName()} 反射（要求 javac -parameters）。
     * <p>
     * BeanPostProcessor 必须是 static，确保在 {@code RequestMappingHandlerAdapter} 之前实例化。
     */
    @Bean
    public static fun.commons.framework4j.openid.web.OpenIdArgumentResolverRegistrar framework4jOpenIdArgumentResolverRegistrar(
            Environment environment) {
        return new fun.commons.framework4j.openid.web.OpenIdArgumentResolverRegistrar(OpenIdTypeSupport.from(environment));
    }

    /**
     * v2.2：启动期 fail-fast 校验，把 silent failure 变成 loud startup failure。
     * v1.3：追加 @RequestBody 字段误用扫描(@OpenId 标在未受理类型上)，需 {@link OpenIdTypeSupport}。
     */
    @Bean
    public fun.commons.framework4j.openid.web.OpenIdFailFastValidator framework4jOpenIdFailFastValidator(
            org.springframework.context.ApplicationContext applicationContext, Environment environment) {
        return new fun.commons.framework4j.openid.web.OpenIdFailFastValidator(
                applicationContext, OpenIdTypeSupport.from(environment));
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
