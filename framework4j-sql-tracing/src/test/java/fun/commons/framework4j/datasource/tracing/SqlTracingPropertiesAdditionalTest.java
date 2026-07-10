package fun.commons.framework4j.datasource.tracing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SqlTracingProperties 补充测试")
class SqlTracingPropertiesAdditionalTest {

    @Test
    @DisplayName("默认值")
    void defaults() {
        SqlTracingProperties p = new SqlTracingProperties();
        assertThat(p.getMode()).isEqualTo(SqlTracingProperties.TracingMode.ALL);
        assertThat(p.getTopic()).isNull();
    }

    @Test
    @DisplayName("mode 可修改")
    void modeMutable() {
        SqlTracingProperties p = new SqlTracingProperties();
        p.setMode(SqlTracingProperties.TracingMode.WRITE_ONLY);
        assertThat(p.getMode()).isEqualTo(SqlTracingProperties.TracingMode.WRITE_ONLY);
    }

    @Test
    @DisplayName("topic 可修改")
    void topicMutable() {
        SqlTracingProperties p = new SqlTracingProperties();
        p.setTopic("my-app");
        assertThat(p.getTopic()).isEqualTo("my-app");
    }

    @Test
    @DisplayName("TracingMode 枚举值")
    void tracingModeValues() {
        SqlTracingProperties.TracingMode[] modes = SqlTracingProperties.TracingMode.values();
        assertThat(modes).contains(
                SqlTracingProperties.TracingMode.DISABLED,
                SqlTracingProperties.TracingMode.WRITE_ONLY,
                SqlTracingProperties.TracingMode.ALL);
    }

    @Test
    @DisplayName("TracingMode DISABLED 是第一个枚举值")
    void disabledIsFirst() {
        assertThat(SqlTracingProperties.TracingMode.values()[0])
                .isEqualTo(SqlTracingProperties.TracingMode.DISABLED);
    }
}
