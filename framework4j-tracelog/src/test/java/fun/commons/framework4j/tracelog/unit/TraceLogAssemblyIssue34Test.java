package fun.commons.framework4j.tracelog.unit;

import fun.commons.framework4j.tracelog.config.TraceLogBeansConfig;
import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import fun.commons.framework4j.tracelog.store.LocalFallbackWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Issue #34 回归（v1.10.1）：
 * <ul>
 *   <li>①Boot 从不装配 RedisMessageListenerContainer——框架自建兜底
 *       （{@code traceLogRedisMessageListenerContainer}，消费方自定义时让位），
 *       此前缺 bean 时 switchPubSubListener 启动即 NoSuchBeanDefinitionException</li>
 *   <li>②authValidatorBean 死键删除（全模块零消费点，宣称 fail-fast 实无此行为）</li>
 *   <li>③fallbackDir 不可写：WARN 停用降级能力，不再启动 fail-fast</li>
 * </ul>
 */
@DisplayName("issue #34：tracelog 装配缺口三处")
class TraceLogAssemblyIssue34Test {

    @Nested
    @DisplayName("①RMLC 框架兜底装配")
    class RmlcProvisionTest {

        @Test
        @DisplayName("自建容器挂框架连接工厂，可独立创建（不再依赖消费方提供 RMLC）")
        void provisionedContainerWiresFrameworkConnectionFactory() {
            org.springframework.data.redis.core.StringRedisTemplate redis =
                    mock(org.springframework.data.redis.core.StringRedisTemplate.class);
            org.springframework.data.redis.connection.RedisConnectionFactory factory =
                    mock(org.springframework.data.redis.connection.RedisConnectionFactory.class);
            when(redis.getConnectionFactory()).thenReturn(factory);

            RedisMessageListenerContainer container =
                    new TraceLogBeansConfig().traceLogRedisMessageListenerContainer(redis);

            assertThat(container).isNotNull();
            assertThat(container.getConnectionFactory()).isSameAs(factory);
        }

        @Test
        @DisplayName("兜底 bean 方法带 @ConditionalOnMissingBean(RedisMessageListenerContainer)——消费方自定义时让位")
        void provisionIsConditionalOnMissingBean() throws Exception {
            org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean anno =
                    TraceLogBeansConfig.class
                            .getDeclaredMethod("traceLogRedisMessageListenerContainer",
                                    org.springframework.data.redis.core.StringRedisTemplate.class)
                            .getAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean.class);
            assertThat(anno).isNotNull();
            assertThat(anno.value()).contains((Class) RedisMessageListenerContainer.class);
        }
    }

    @Nested
    @DisplayName("②authValidatorBean 死键删除")
    class DeadKeyRemovedTest {

        @Test
        @DisplayName("TraceLogProperties.Api 不再存在 authValidatorBean 字段（零消费点的死键）")
        void authValidatorBeanFieldRemoved() {
            Field[] fields = TraceLogProperties.Api.class.getDeclaredFields();
            assertThat(fields).extracting(Field::getName).doesNotContain("authValidatorBean");
        }
    }

    @Nested
    @DisplayName("③fallbackDir 不可写降级")
    class FallbackDirDegradeTest {

        private TraceLogProperties propsWithDir(String dir) {
            TraceLogProperties props = new TraceLogProperties();
            props.getCollection().setFallbackDir(dir);
            return props;
        }

        @Test
        @DisplayName("目录不可写（父路径是文件）→ 构造不再抛异常，available=false，writeBatch 静默跳过")
        void unwritableDirDegradesInsteadOfFailFast() throws IOException {
            // 构造一个"父路径是文件"的目录——mkdirs 必失败的平台无关写法
            Path parentFile = Files.createTempFile("tracelog-blocker", ".tmp");
            Path unwritable = parentFile.resolve("sub").resolve("fallback");

            LocalFallbackWriter writer =
                    new LocalFallbackWriter(propsWithDir(unwritable.toString()));

            assertThat(writer.isAvailable()).isFalse();
            assertThatCode(() -> writer.writeBatch(List.of(
                    new LocalFallbackWriter.RawPayload("tid", "{}"))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("可写目录 → available=true，正常写入")
        void writableDirStillWorks() throws IOException {
            Path dir = Files.createTempDirectory("tracelog-fallback-ok");
            LocalFallbackWriter writer = new LocalFallbackWriter(propsWithDir(dir.toString()));

            assertThat(writer.isAvailable()).isTrue();
            writer.writeBatch(List.of(new LocalFallbackWriter.RawPayload("tid", "{}")));
            assertThat(writer.listFallbackFiles()).isNotEmpty();
        }
    }
}
