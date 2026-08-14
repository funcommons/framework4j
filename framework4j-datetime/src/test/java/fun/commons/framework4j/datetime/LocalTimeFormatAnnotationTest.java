package fun.commons.framework4j.datetime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LocalTimeFormat} 注解契约测试（v1.2.4，GitHub Issue #8）。
 * <p>
 * 锁定 {@code @Target} 放行 PARAMETER 位置（此前仅 METHOD/TYPE，文档示例
 * {@code @LocalTimeFormat @RequestParam OffsetDateTime} 编译失败）。
 * 参数级注解仅为语义标记，无运行时效果 —— 入参解析由全局
 * {@link StringToOffsetDateTimeConverter} 负责，与注解无关。
 */
@DisplayName("LocalTimeFormat 注解契约（Issue #8）")
class LocalTimeFormatAnnotationTest {

    @Test
    @DisplayName("@Target 必须包含 METHOD / TYPE / PARAMETER 三个位置")
    void targetIncludesMethodTypeAndParameter() {
        Target target = LocalTimeFormat.class.getAnnotation(Target.class);
        assertThat(target).as("@Target 元注解必须存在").isNotNull();
        assertThat(target.value())
                .contains(ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER);
    }

    @Test
    @DisplayName("@Retention 必须为 RUNTIME（拦截器运行时检测依赖）")
    void retentionIsRuntime() {
        Retention retention = LocalTimeFormat.class.getAnnotation(Retention.class);
        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }

    /** 参数级使用示例 —— 本方法能编译即证明 @Target 放行 PARAMETER（Issue #8 修复点） */
    @SuppressWarnings("unused")
    void parameterUsageSample(@LocalTimeFormat OffsetDateTime start,
                              @LocalTimeFormat OffsetDateTime end) {
        // 仅声明，无实现：编译通过 = @Target 含 PARAMETER
    }

    @Test
    @DisplayName("参数上的 @LocalTimeFormat 可编译且运行时可读（语义标记）")
    void parameterLevelAnnotationCompilesAndReadable() throws Exception {
        Method method = LocalTimeFormatAnnotationTest.class
                .getDeclaredMethod("parameterUsageSample", OffsetDateTime.class, OffsetDateTime.class);
        for (Parameter parameter : method.getParameters()) {
            assertThat(parameter.isAnnotationPresent(LocalTimeFormat.class))
                    .as("参数 %s 上的 @LocalTimeFormat 应运行时可读", parameter.getName())
                    .isTrue();
        }
    }

    @LocalTimeFormat
    void methodLevelUsageSample() {
        // 方法级使用（既有能力，出参格式切换），保持不变
    }

    @Test
    @DisplayName("方法级 @LocalTimeFormat 仍生效（既有能力不回归）")
    void methodLevelAnnotationStillPresent() throws Exception {
        Method method = LocalTimeFormatAnnotationTest.class.getDeclaredMethod("methodLevelUsageSample");
        assertThat(method.isAnnotationPresent(LocalTimeFormat.class)).isTrue();
    }
}
