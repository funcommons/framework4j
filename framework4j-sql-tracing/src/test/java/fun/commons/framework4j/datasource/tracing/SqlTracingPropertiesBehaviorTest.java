package fun.commons.framework4j.datasource.tracing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SqlTracingProperties 边界 / 行为方法 / 配置切换补充测试。
 *
 * <p>已存在的 SqlTracingPropertiesTest / SqlTracingPropertiesAdditionalTest 覆盖了
 * 默认值、setter 往返、TracingMode 枚举值。本测试补充：
 * <ol>
 *   <li>3 种 TracingMode 下 isEnabled/isWriteOnly/isAll 的真值表</li>
 *   <li>mode=null 时 isEnabled=false / isWriteOnly=false / isAll=false</li>
 *   <li>topic 边界：null / "" / 自定义值往返一致</li>
 *   <li>provider 字段往返（@Deprecated 但仍可配置）</li>
 *   <li>实例独立性：mutate 一个不影响另一个</li>
 * </ol>
 *
 * @since 2.1.0
 */
@DisplayName("SqlTracingProperties 边界与行为方法测试")
class SqlTracingPropertiesBehaviorTest {

    @Test
    @DisplayName("默认 mode=ALL → isEnabled=true, isWriteOnly=false, isAll=true")
    void defaultModeAll() {
        SqlTracingProperties p = new SqlTracingProperties();
        assertThat(p.getMode()).isEqualTo(SqlTracingProperties.TracingMode.ALL);
        assertThat(p.isEnabled()).isTrue();
        assertThat(p.isWriteOnly()).isFalse();
        assertThat(p.isAll()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(SqlTracingProperties.TracingMode.class)
    @DisplayName("真值表：每个 TracingMode 下行为方法返回值符合契约")
    void truthTable(SqlTracingProperties.TracingMode mode) {
        SqlTracingProperties p = new SqlTracingProperties();
        p.setMode(mode);

        switch (mode) {
            case DISABLED -> {
                assertThat(p.isEnabled()).isFalse();
                assertThat(p.isWriteOnly()).isFalse();
                assertThat(p.isAll()).isFalse();
            }
            case WRITE_ONLY -> {
                assertThat(p.isEnabled()).isTrue();
                assertThat(p.isWriteOnly()).isTrue();
                assertThat(p.isAll()).isFalse();
            }
            case ALL -> {
                assertThat(p.isEnabled()).isTrue();
                assertThat(p.isWriteOnly()).isFalse();
                assertThat(p.isAll()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("mode=null：所有行为方法都返回 false（保守）")
    void nullModeConservative() {
        SqlTracingProperties p = new SqlTracingProperties();
        p.setMode(null);
        assertThat(p.isEnabled()).isFalse();
        assertThat(p.isWriteOnly()).isFalse();
        assertThat(p.isAll()).isFalse();
    }

    @Test
    @DisplayName("mode 切换：DISABLED → WRITE_ONLY → ALL 状态变化正确")
    void modeSwitching() {
        SqlTracingProperties p = new SqlTracingProperties();

        p.setMode(SqlTracingProperties.TracingMode.DISABLED);
        assertThat(p.isEnabled()).isFalse();

        p.setMode(SqlTracingProperties.TracingMode.WRITE_ONLY);
        assertThat(p.isEnabled()).isTrue();
        assertThat(p.isWriteOnly()).isTrue();
        assertThat(p.isAll()).isFalse();

        p.setMode(SqlTracingProperties.TracingMode.ALL);
        assertThat(p.isEnabled()).isTrue();
        assertThat(p.isWriteOnly()).isFalse();
        assertThat(p.isAll()).isTrue();
    }

    @Test
    @DisplayName("topic 边界：null / 空字符串 / 自定义值往返一致")
    void topicRoundTrip() {
        SqlTracingProperties p = new SqlTracingProperties();
        assertThat(p.getTopic()).isNull();

        p.setTopic("");
        assertThat(p.getTopic()).isEmpty();

        p.setTopic("order-service");
        assertThat(p.getTopic()).isEqualTo("order-service");

        p.setTopic(null);
        assertThat(p.getTopic()).isNull();
    }

    @Test
    @DisplayName("provider 字段往返（@Deprecated 但保留 API）")
    void providerRoundTrip() {
        SqlTracingProperties p = new SqlTracingProperties();
        assertThat(p.getProvider()).isEqualTo("auto");

        p.setProvider("custom");
        assertThat(p.getProvider()).isEqualTo("custom");

        p.setProvider(null);
        assertThat(p.getProvider()).isNull();
    }

    @Test
    @DisplayName("实例独立：mutate 一个 properties 不影响另一个")
    void instancesAreIndependent() {
        SqlTracingProperties a = new SqlTracingProperties();
        SqlTracingProperties b = new SqlTracingProperties();

        a.setMode(SqlTracingProperties.TracingMode.DISABLED);
        a.setTopic("app-a");
        a.setProvider("p1");

        assertThat(b.getMode()).isEqualTo(SqlTracingProperties.TracingMode.ALL);
        assertThat(b.getTopic()).isNull();
        assertThat(b.getProvider()).isEqualTo("auto");
    }

    @Test
    @DisplayName("Lombok @Data 生成 hashCode/equals：相同字段值 equals=true")
    void lombokEqualsAndHashCode() {
        SqlTracingProperties a = new SqlTracingProperties();
        SqlTracingProperties b = new SqlTracingProperties();
        // 默认值相同
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());

        a.setTopic("x");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Lombok @Data 生成 toString：包含字段名")
    void lombokToString() {
        SqlTracingProperties p = new SqlTracingProperties();
        String s = p.toString();
        assertThat(s).contains("mode").contains("ALL");
        assertThat(s).contains("provider").contains("auto");
    }

    @Test
    @DisplayName("TracingMode.valueOf(String)：合法名称解析")
    void tracingModeValueOf() {
        assertThat(SqlTracingProperties.TracingMode.valueOf("DISABLED"))
                .isEqualTo(SqlTracingProperties.TracingMode.DISABLED);
        assertThat(SqlTracingProperties.TracingMode.valueOf("WRITE_ONLY"))
                .isEqualTo(SqlTracingProperties.TracingMode.WRITE_ONLY);
        assertThat(SqlTracingProperties.TracingMode.valueOf("ALL"))
                .isEqualTo(SqlTracingProperties.TracingMode.ALL);
    }
}
