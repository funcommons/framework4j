package fun.commons.framework4j.tracelog.unit;

import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import fun.commons.framework4j.tracelog.util.TenantKeyResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TenantKeyResolver SpEL 解析")
class TenantKeyResolverTest {

    @Test
    @DisplayName("未启用多租户 → 始终返回 null")
    void disabled() {
        TraceLogProperties props = new TraceLogProperties();
        TenantKeyResolver resolver = new TenantKeyResolver(props, new DefaultListableBeanFactory());
        assertThat(resolver.currentTenant()).isNull();
    }

    @Test
    @DisplayName("启用但未配置 spel → 启动 fail-fast")
    void missingSpel() {
        TraceLogProperties props = new TraceLogProperties();
        props.getTenant().setEnabled(true);

        assertThatThrownBy(() -> new TenantKeyResolver(props, new DefaultListableBeanFactory()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant.key-spel");
    }

    @Test
    @DisplayName("启用并配置 spel → 无请求上下文时返回 null（不抛异常）")
    void configuredSpel() {
        TraceLogProperties props = new TraceLogProperties();
        props.getTenant().setEnabled(true);
        props.getTenant().setKeySpel("'tenant_' + 'A'");

        TenantKeyResolver resolver = new TenantKeyResolver(props, new DefaultListableBeanFactory());
        // 无请求上下文 → currentTenant() 内部取不到 request → 返回 null（按设计：视为无租户）
        assertThat(resolver.currentTenant()).isNull();
    }
}