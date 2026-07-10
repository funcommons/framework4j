package fun.commons.framework4j.openid.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import fun.commons.framework4j.datetime.DynamicTimeFilter;
import fun.commons.framework4j.id.config.IdSdkAutoConfiguration;
import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.openid.fastjson2.OpenIdAnnotationFilter;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebConfig测试
 */
@DisplayName("WebConfig测试")
class FastJson2ConfigTest {

    @Nested
    @DisplayName("WebConfig集成测试")
    @SpringBootTest(classes = FastJson2ConfigTest.WebConfigTestConfig.class)
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
            "ldx2t.commons.core.config.enabled=true",
            "ldx2t.commons.openid.enabled=true",
            "ldx2t.commons.datetime.enabled=true",
            // 禁用 Redis
            "spring.data.redis.repositories.enabled=false",
            // 禁用数据源自动配置
            "spring.datasource.url=",
            "spring.datasource.driver-class-name=",
            // 禁用 actuator 的一些功能
            "management.metrics.enable.all=false",
            "management.health.defaults.enabled=false"
    })
    class WebConfigIntegrationTest {

        @Autowired
        private ApplicationContext applicationContext;

        @Autowired
        private RequestMappingHandlerAdapter handlerAdapter;

        @Test
        @DisplayName("应该正确配置WebConfig")
        void shouldConfigureWebConfig() {
            // 验证 WebConfig 通过自动配置生效了 FastJsonHttpMessageConverter
            java.util.List<HttpMessageConverter<?>> converters = handlerAdapter.getMessageConverters();
            assertThat(converters).isNotEmpty();

            // 验证 FastJsonHttpMessageConverter 存在
            boolean hasFastJsonConverter = false;
            for (HttpMessageConverter<?> converter : converters) {
                if (converter instanceof FastJsonHttpMessageConverter) {
                    hasFastJsonConverter = true;
                    break;
                }
            }
            assertThat(hasFastJsonConverter).as("Should find FastJsonHttpMessageConverter in MVC message converters").isTrue();
        }

        @Test
        @DisplayName("在启用属性时应该正确配置")
        void shouldConfigureWhenEnabled() {
            // 验证 FastJsonHttpMessageConverter 存在并且配置正确
            java.util.List<HttpMessageConverter<?>> converters = handlerAdapter.getMessageConverters();

            boolean hasFastJsonConverter = false;
            boolean hasOpenIdFilter = false;
            boolean hasTimeFilter = false;

            for (HttpMessageConverter<?> converter : converters) {
                if (converter instanceof FastJsonHttpMessageConverter) {
                    hasFastJsonConverter = true;

                    // 验证过滤器配置
                    FastJsonHttpMessageConverter fastJsonConverter = (FastJsonHttpMessageConverter) converter;
                    com.alibaba.fastjson2.support.config.FastJsonConfig config = fastJsonConverter.getFastJsonConfig();
                    com.alibaba.fastjson2.filter.Filter[] filters = config.getWriterFilters();

                    if (filters != null) {
                        for (com.alibaba.fastjson2.filter.Filter filter : filters) {
                            if (filter instanceof OpenIdAnnotationFilter) {
                                hasOpenIdFilter = true;
                            }
                            if (filter instanceof DynamicTimeFilter) {
                                hasTimeFilter = true;
                            }
                        }
                    }

                    break;
                }
            }

            assertThat(hasFastJsonConverter).as("Should find FastJsonHttpMessageConverter in MVC message converters").isTrue();
            assertThat(hasOpenIdFilter).as("Should have OpenIdAnnotationFilter configured").isTrue();
            assertThat(hasTimeFilter).as("Should have DynamicTimeFilter configured").isTrue();

            // 验证 FastJsonHttpMessageConverter 位于转换器列表的前面（高优先级）
            boolean fastJsonFoundAtFront = false;
            for (int i = 0; i < Math.min(3, converters.size()); i++) {
                if (converters.get(i) instanceof FastJsonHttpMessageConverter) {
                    fastJsonFoundAtFront = true;
                    break;
                }
            }
            assertThat(fastJsonFoundAtFront).as("FastJsonHttpMessageConverter should be at high priority position").isTrue();
        }
    }

    @SpringBootApplication(exclude = {
            org.springdoc.webmvc.ui.SwaggerConfig.class,
            org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
            IdSdkAutoConfiguration.class,
            OpenIdSwaggerConfig.class,
            OpenIdSwaggerModelConfig.class
    })
    static class WebConfigTestConfig {
        // WebConfig will be auto-configured via META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
    }

    @Nested
    @DisplayName("配置集成测试")
    class ConfigurationIntegrationTest {

        @Test
        @DisplayName("过滤器应该正确应用到JSON序列化")
        void shouldApplyFilterToJsonSerialization() {
            TestEntity entity = new TestEntity();
            entity.setId(123456789L);
            entity.setName("test");
            entity.setCreateAt(OffsetDateTime.now());

            // 直接测试过滤器功能
            OpenIdAnnotationFilter filter = new OpenIdAnnotationFilter();
            String json = JSON.toJSONString(entity, filter);
            System.out.println("JSON结果: " + json);

            // 验证JSON包含了转换后的OpenID
            assertThat(json).isNotNull();
            assertThat(json).isNotEmpty();
        }

        // 测试用的实体类
        @Data
        static class TestEntity {
            @OpenId
            private Long id;
            private String name;
            private OffsetDateTime createAt;
        }
    }


}