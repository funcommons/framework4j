package fun.commons.framework4j.datasource.tracing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SqlTracingProperties 单元测试
 */
class SqlTracingPropertiesTest {

    @Test
    @DisplayName("默认 mode = ALL, provider = auto")
    void defaultsAreCorrect() {
        SqlTracingProperties p = new SqlTracingProperties();
        assertEquals(SqlTracingProperties.TracingMode.ALL, p.getMode());
        assertEquals("auto", p.getProvider());
        assertNull(p.getTopic());
        assertTrue(p.isEnabled(), "ALL 模式应启用");
        assertTrue(p.isAll());
        assertFalse(p.isWriteOnly());
    }

    @Test
    @DisplayName("mode=DISABLED 时 isEnabled=false")
    void disabledModeNotEnabled() {
        SqlTracingProperties p = new SqlTracingProperties();
        p.setMode(SqlTracingProperties.TracingMode.DISABLED);
        assertFalse(p.isEnabled());
        assertFalse(p.isAll());
        assertFalse(p.isWriteOnly());
    }

    @Test
    @DisplayName("mode=WRITE_ONLY 时 isWriteOnly=true, isEnabled=true")
    void writeOnlyMode() {
        SqlTracingProperties p = new SqlTracingProperties();
        p.setMode(SqlTracingProperties.TracingMode.WRITE_ONLY);
        assertTrue(p.isEnabled());
        assertTrue(p.isWriteOnly());
        assertFalse(p.isAll());
    }

    @Test
    @DisplayName("mode=null 时 isEnabled=false（防御）")
    void nullModeNotEnabled() {
        SqlTracingProperties p = new SqlTracingProperties();
        p.setMode(null);
        assertFalse(p.isEnabled());
    }

    @Test
    @DisplayName("TracingMode 枚举包含 DISABLED / WRITE_ONLY / ALL 三个值")
    void enumHasThreeValues() {
        SqlTracingProperties.TracingMode[] values = SqlTracingProperties.TracingMode.values();
        assertEquals(3, values.length);
        assertArrayEquals(
                new SqlTracingProperties.TracingMode[]{
                        SqlTracingProperties.TracingMode.DISABLED,
                        SqlTracingProperties.TracingMode.WRITE_ONLY,
                        SqlTracingProperties.TracingMode.ALL
                },
                values
        );
    }
}
