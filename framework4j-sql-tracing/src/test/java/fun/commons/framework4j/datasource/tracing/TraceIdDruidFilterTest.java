package fun.commons.framework4j.datasource.tracing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TraceIdDruidFilter 单元测试
 * <p>
 * 不依赖 Druid FilterChain，直接调用 processSql 通过反射验证 SQL 注入逻辑。
 */
class TraceIdDruidFilterTest {

    private static final String TRACE_ID = "abc-123-trace";
    private static final String TOPIC = "test-app";

    private TraceIdDruidFilter filter(TraceIdProvider provider, SqlTracingProperties.TracingMode mode) {
        return new TraceIdDruidFilter(provider, TOPIC, mode);
    }

    @Test
    @DisplayName("DISABLED 模式：SQL 原样返回")
    void disabledModeReturnsOriginalSql() {
        TraceIdDruidFilter f = filter(() -> TRACE_ID, SqlTracingProperties.TracingMode.DISABLED);
        String sql = "SELECT * FROM users";
        assertEquals(sql, invokeProcessSql(f, sql));
    }

    @Test
    @DisplayName("ALL 模式：SELECT 也注入 traceid")
    void allModeInjectsSelect() {
        TraceIdDruidFilter f = filter(() -> TRACE_ID, SqlTracingProperties.TracingMode.ALL);
        String sql = "SELECT * FROM users";
        String result = invokeProcessSql(f, sql);
        assertTrue(result.startsWith("/*traceid=" + TRACE_ID + ",topic=" + TOPIC + "*/ "),
                "应注入 traceid 注释，实际：" + result);
        assertTrue(result.endsWith(sql));
    }

    @Test
    @DisplayName("WRITE_ONLY 模式：SELECT 跳过不注入")
    void writeOnlyModeSkipsSelect() {
        TraceIdDruidFilter f = filter(() -> TRACE_ID, SqlTracingProperties.TracingMode.WRITE_ONLY);
        String sql = "SELECT * FROM users";
        assertEquals(sql, invokeProcessSql(f, sql));
    }

    @Test
    @DisplayName("WRITE_ONLY 模式：INSERT 注入")
    void writeOnlyModeInjectsInsert() {
        TraceIdDruidFilter f = filter(() -> TRACE_ID, SqlTracingProperties.TracingMode.WRITE_ONLY);
        String sql = "INSERT INTO users (name) VALUES ('alice')";
        String result = invokeProcessSql(f, sql);
        assertTrue(result.startsWith("/*traceid="));
    }

    @Test
    @DisplayName("WRITE_ONLY 模式：UPDATE / DELETE 注入")
    void writeOnlyModeInjectsUpdateAndDelete() {
        TraceIdDruidFilter f = filter(() -> TRACE_ID, SqlTracingProperties.TracingMode.WRITE_ONLY);

        String update = "UPDATE users SET name='bob' WHERE id=1";
        assertTrue(invokeProcessSql(f, update).startsWith("/*traceid="));

        String delete = "DELETE FROM users WHERE id=1";
        assertTrue(invokeProcessSql(f, delete).startsWith("/*traceid="));
    }

    @Test
    @DisplayName("已有 traceid 注释的 SQL 不会重复注入")
    void noDuplicateInjection() {
        TraceIdDruidFilter f = filter(() -> TRACE_ID, SqlTracingProperties.TracingMode.ALL);
        String sql = "/*traceid=existing,topic=app*/ SELECT * FROM users";
        assertEquals(sql, invokeProcessSql(f, sql));
    }

    @Test
    @DisplayName("TraceIdProvider 返回 null 时用 'none' 占位")
    void nullTraceIdUsesPlaceholder() {
        TraceIdDruidFilter f = filter(() -> null, SqlTracingProperties.TracingMode.ALL);
        String result = invokeProcessSql(f, "SELECT 1");
        assertTrue(result.startsWith("/*traceid=none,topic=" + TOPIC + "*/"),
                "应使用 none 占位，实际：" + result);
    }

    @Test
    @DisplayName("TraceIdProvider 返回空白时用 'none' 占位")
    void blankTraceIdUsesPlaceholder() {
        TraceIdDruidFilter f = filter(() -> "   ", SqlTracingProperties.TracingMode.ALL);
        String result = invokeProcessSql(f, "SELECT 1");
        assertTrue(result.contains("traceid=none,"));
    }

    @Test
    @DisplayName("traceId 含特殊字符 * / \\n 时被 sanitize 替换为 _（白名单策略）")
    void sanitizeRemovesDangerousChars() {
        TraceIdDruidFilter f = filter(() -> "a*b/c\nd", SqlTracingProperties.TracingMode.ALL);
        String result = invokeProcessSql(f, "SELECT 1");
        // v2.1: 白名单策略，* / \n 替换为 _，其余字母数字保留
        assertTrue(result.contains("traceid=a_b_c_d,"), "应替换特殊字符为 _，实际：" + result);
    }

    // v2.1 P1: 参数化覆盖 sanitize 攻击向量

    @ParameterizedTest(name = "[{index}] traceId=\"{0}\"")
    @DisplayName("sanitize 各种攻击向量：SQL 注释终止符 / 超长 / Unicode / 空白")
    @ValueSource(strings = {
            "trace*/id",       // SQL 注释终止符
            "trace;DROP",      // SQL 语句分隔符
            "trace--id",       // SQL 行注释
            "trace'OR'1",      // SQL 引号注入
            "trace\"OR\"1",    // SQL 双引号注入
            "trace\\bid",      // 退格
            "trace\tid",       // tab
            "trace id",        // 空格
            "trace\r\nid",     // CRLF
            "trace中id",       // Unicode 中文
            "trace🎉id",       // emoji
    })
    void sanitizeAttacksVectors(String traceId) {
        TraceIdDruidFilter f = filter(() -> traceId, SqlTracingProperties.TracingMode.ALL);
        String result = invokeProcessSql(f, "SELECT 1");
        // 提取 traceid= 后的值（到下一个逗号或 */ 之前）
        int start = result.indexOf("traceid=") + "traceid=".length();
        int endComma = result.indexOf(",", start);
        int endComment = result.indexOf("*/", start);
        int end = Math.min(endComma, endComment);
        String traceIdValue = result.substring(start, end);

        // 验证：sanitize 后的 traceId 值不含危险字符
        assertFalse(traceIdValue.contains("*/"), "traceId 值不应含 SQL 注释终止符，traceId=" + traceId + " value=" + traceIdValue);
        assertFalse(traceIdValue.contains(";"), "traceId 值不应含分号，traceId=" + traceId + " value=" + traceIdValue);
        assertFalse(traceIdValue.contains("'"), "traceId 值不应含引号，traceId=" + traceId + " value=" + traceIdValue);
        assertFalse(traceIdValue.contains("\""), "traceId 值不应含双引号，traceId=" + traceId + " value=" + traceIdValue);
        // 应被替换为 _ 或保留为合法字符
        assertTrue(result.contains("traceid="), "应包含 traceid= 前缀，traceId=" + traceId);
    }

    @ParameterizedTest(name = "[{index}] traceId 长度={0}")
    @DisplayName("sanitize 超长 traceId 处理（>1000 字符）")
    @CsvSource({
            "1,    'a'",
            "100,  '合法长度'",
            "1000, '边界长度'",
            "5000, '超长'",
    })
    void sanitizeVariousLengths(int length, String label) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append('a');
        String traceId = sb.toString();

        TraceIdDruidFilter f = filter(() -> traceId, SqlTracingProperties.TracingMode.ALL);
        String result = invokeProcessSql(f, "SELECT 1");
        // 验证：不抛异常，SQL 注释正常闭合
        assertNotNull(result);
        assertTrue(result.contains("/*"));
        assertTrue(result.contains("*/"));
    }

    @Test
    @DisplayName("topic 为 null 时用 'unknown' 兜底")
    void nullTopicFallsBackToUnknown() {
        TraceIdDruidFilter f = new TraceIdDruidFilter(() -> TRACE_ID, null, SqlTracingProperties.TracingMode.ALL);
        String result = invokeProcessSql(f, "SELECT 1");
        assertTrue(result.contains("topic=unknown"));
    }

    @Test
    @DisplayName("tracingMode 为 null 时按 DISABLED 处理（防御）")
    void nullModeTreatedAsDisabled() {
        TraceIdDruidFilter f = new TraceIdDruidFilter(() -> TRACE_ID, TOPIC, null);
        String sql = "SELECT 1";
        assertEquals(sql, invokeProcessSql(f, sql), "null mode 应安全降级为不注入");
    }

    @Test
    @DisplayName("SQL 为 null / 空白时原样返回")
    void nullOrBlankSqlReturnedAsIs() {
        TraceIdDruidFilter f = filter(() -> TRACE_ID, SqlTracingProperties.TracingMode.ALL);
        assertNull(invokeProcessSql(f, null));
        assertEquals("", invokeProcessSql(f, ""));
        assertEquals("   ", invokeProcessSql(f, "   "));
    }

    @Test
    @DisplayName("WRITE_ONLY 模式：(SELECT 子查询跳过")
    void writeOnlyModeSkipsSubquery() {
        TraceIdDruidFilter f = filter(() -> TRACE_ID, SqlTracingProperties.TracingMode.WRITE_ONLY);
        String sql = "(SELECT id FROM a) UNION (SELECT id FROM b)";
        assertEquals(sql, invokeProcessSql(f, sql));
    }

    @Test
    @DisplayName("WRITE_ONLY 模式：SHOW / DESCRIBE / EXPLAIN / DESC 跳过")
    void writeOnlyModeSkipsMetadataQueries() {
        TraceIdDruidFilter f = filter(() -> TRACE_ID, SqlTracingProperties.TracingMode.WRITE_ONLY);
        assertEquals("SHOW TABLES", invokeProcessSql(f, "SHOW TABLES"));
        assertEquals("DESCRIBE users", invokeProcessSql(f, "DESCRIBE users"));
        assertEquals("DESC users", invokeProcessSql(f, "DESC users"));
        assertEquals("EXPLAIN SELECT * FROM users", invokeProcessSql(f, "EXPLAIN SELECT * FROM users"));
    }

    @Test
    @DisplayName("v2.2 P1: WRITE_ONLY 模式：CTE 开头（WITH ... AS）也按读操作跳过")
    void writeOnlyModeSkipsCte() {
        // v2.2 修复前：CTE 开头（WITH）原 isReadOperation 未匹配，被当写操作注入了 traceid
        // v2.2 修复后：识别为读操作，WRITE_ONLY 模式跳过
        TraceIdDruidFilter f = filter(() -> TRACE_ID, SqlTracingProperties.TracingMode.WRITE_ONLY);
        String cte = "WITH active_users AS (SELECT * FROM users WHERE status='ACTIVE') SELECT * FROM active_users";
        assertEquals(cte, invokeProcessSql(f, cte));
    }

    /**
     * 通过反射调用 private processSql，避免构造完整 FilterChain。
     */
    private String invokeProcessSql(TraceIdDruidFilter filter, String sql) {
        try {
            var m = TraceIdDruidFilter.class.getDeclaredMethod("processSql", String.class);
            m.setAccessible(true);
            return (String) m.invoke(filter, sql);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
