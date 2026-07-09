package fun.commons.framework4j.datasource.unit;

import fun.commons.framework4j.datasource.tracing.DefaultTraceIdProvider;
import fun.commons.framework4j.datasource.tracing.SqlTracingProperties;
import fun.commons.framework4j.datasource.tracing.TraceIdDruidFilter;
import fun.commons.framework4j.datasource.tracing.TraceIdProvider;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TraceIdDruidFilter 单元测试
 */
@DisplayName("TraceIdDruidFilter 单元测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TraceIdDruidFilterTest {

    private TraceIdDruidFilter allModeFilter;
    private TraceIdDruidFilter writeOnlyFilter;
    private TraceIdDruidFilter disabledFilter;

    @BeforeEach
    void setUp() {
        TraceIdProvider provider = () -> "test-trace-123";

        allModeFilter = new TraceIdDruidFilter(provider, "TestApp", SqlTracingProperties.TracingMode.ALL);
        writeOnlyFilter = new TraceIdDruidFilter(provider, "TestApp", SqlTracingProperties.TracingMode.WRITE_ONLY);
        disabledFilter = new TraceIdDruidFilter(provider, "TestApp", SqlTracingProperties.TracingMode.DISABLED);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    // ==================== processSql 测试 ====================

    @Test
    @Order(1)
    @DisplayName("ALL 模式: SELECT 语句应添加追踪注释")
    void testAllMode_SelectShouldBeTraced() throws Exception {
        String sql = "SELECT * FROM users WHERE id = ?";
        String result = invokeProcessSql(allModeFilter, sql);

        assertTrue(result.startsWith("/*traceid="), "应该以追踪注释开头");
        assertTrue(result.contains("traceid=test-trace-123"), "应该包含 traceId");
        assertTrue(result.contains("topic=TestApp"), "应该包含 topic");
        assertTrue(result.endsWith(sql), "应该以原始 SQL 结尾");
    }

    @Test
    @Order(2)
    @DisplayName("ALL 模式: INSERT 语句应添加追踪注释")
    void testAllMode_InsertShouldBeTraced() throws Exception {
        String sql = "INSERT INTO users (name) VALUES (?)";
        String result = invokeProcessSql(allModeFilter, sql);

        assertTrue(result.startsWith("/*traceid="));
        assertTrue(result.contains("topic=TestApp"));
    }

    @Test
    @Order(3)
    @DisplayName("ALL 模式: UPDATE 语句应添加追踪注释")
    void testAllMode_UpdateShouldBeTraced() throws Exception {
        String sql = "UPDATE users SET name = ? WHERE id = ?";
        String result = invokeProcessSql(allModeFilter, sql);

        assertTrue(result.startsWith("/*traceid="));
    }

    @Test
    @Order(4)
    @DisplayName("ALL 模式: DELETE 语句应添加追踪注释")
    void testAllMode_DeleteShouldBeTraced() throws Exception {
        String sql = "DELETE FROM users WHERE id = ?";
        String result = invokeProcessSql(allModeFilter, sql);

        assertTrue(result.startsWith("/*traceid="));
    }

    @Test
    @Order(5)
    @DisplayName("WRITE_ONLY 模式: SELECT 不应添加追踪注释")
    void testWriteOnlyMode_SelectShouldNotBeTraced() throws Exception {
        String sql = "SELECT * FROM users";
        String result = invokeProcessSql(writeOnlyFilter, sql);

        assertEquals(sql, result, "SELECT 不应该被修改");
    }

    @Test
    @Order(6)
    @DisplayName("WRITE_ONLY 模式: 带空格的 SELECT 不应添加追踪注释")
    void testWriteOnlyMode_SelectWithSpacesShouldNotBeTraced() throws Exception {
        String sql = "  SELECT * FROM users";
        String result = invokeProcessSql(writeOnlyFilter, sql);

        assertEquals(sql, result, "带空格的 SELECT 不应该被修改");
    }

    @Test
    @Order(7)
    @DisplayName("WRITE_ONLY 模式: 子查询 SELECT 不应添加追踪注释")
    void testWriteOnlyMode_SubquerySelectShouldNotBeTraced() throws Exception {
        String sql = "(SELECT id FROM users WHERE status = 1)";
        String result = invokeProcessSql(writeOnlyFilter, sql);

        assertEquals(sql, result, "子查询 SELECT 不应该被修改");
    }

    @Test
    @Order(8)
    @DisplayName("WRITE_ONLY 模式: INSERT 应添加追踪注释")
    void testWriteOnlyMode_InsertShouldBeTraced() throws Exception {
        String sql = "INSERT INTO users (name) VALUES ('test')";
        String result = invokeProcessSql(writeOnlyFilter, sql);

        assertTrue(result.startsWith("/*traceid="), "INSERT 应该被追踪");
    }

    @Test
    @Order(9)
    @DisplayName("WRITE_ONLY 模式: UPDATE 应添加追踪注释")
    void testWriteOnlyMode_UpdateShouldBeTraced() throws Exception {
        String sql = "UPDATE users SET name = 'test'";
        String result = invokeProcessSql(writeOnlyFilter, sql);

        assertTrue(result.startsWith("/*traceid="), "UPDATE 应该被追踪");
    }

    @Test
    @Order(10)
    @DisplayName("WRITE_ONLY 模式: DELETE 应添加追踪注释")
    void testWriteOnlyMode_DeleteShouldBeTraced() throws Exception {
        String sql = "DELETE FROM users WHERE id = 1";
        String result = invokeProcessSql(writeOnlyFilter, sql);

        assertTrue(result.startsWith("/*traceid="), "DELETE 应该被追踪");
    }

    @Test
    @Order(11)
    @DisplayName("DISABLED 模式: 所有 SQL 不应添加追踪注释")
    void testDisabledMode_NothingShouldBeTraced() throws Exception {
        String selectSql = "SELECT * FROM users";
        String insertSql = "INSERT INTO users (name) VALUES ('test')";
        String updateSql = "UPDATE users SET name = 'test'";
        String deleteSql = "DELETE FROM users WHERE id = 1";

        assertEquals(selectSql, invokeProcessSql(disabledFilter, selectSql));
        assertEquals(insertSql, invokeProcessSql(disabledFilter, insertSql));
        assertEquals(updateSql, invokeProcessSql(disabledFilter, updateSql));
        assertEquals(deleteSql, invokeProcessSql(disabledFilter, deleteSql));
    }

    @Test
    @Order(12)
    @DisplayName("空 SQL 应返回原值")
    void testNullAndEmptySql() throws Exception {
        assertNull(invokeProcessSql(allModeFilter, null));
        assertEquals("", invokeProcessSql(allModeFilter, ""));
        assertEquals("   ", invokeProcessSql(allModeFilter, "   "));
    }

    @Test
    @Order(13)
    @DisplayName("防止重复添加: 已有追踪注释的 SQL 不应再次添加")
    void testPreventDuplicateTracing() throws Exception {
        String sql = "/*traceid=existing,topic=OldApp*/ SELECT * FROM users";
        String result = invokeProcessSql(allModeFilter, sql);

        assertEquals(sql, result, "已有追踪注释的 SQL 不应该被修改");
    }

    @Test
    @Order(14)
    @DisplayName("特殊字符应被过滤")
    void testSpecialCharactersSanitized() throws Exception {
        // 创建包含特殊字符的 Filter
        TraceIdProvider specialProvider = () -> "trace*/id\r\n";
        TraceIdDruidFilter specialFilter = new TraceIdDruidFilter(
                specialProvider, "My*/App\n", SqlTracingProperties.TracingMode.ALL);

        String sql = "SELECT 1";
        String result = invokeProcessSql(specialFilter, sql);

        // v2.1: 白名单策略，* / \r \n 都替换为 _。trace*/id\r\n → trace__id__
        assertTrue(result.contains("traceid=trace__id__"), "特殊字符应替换为 _，实际：" + result);
        // v2.1: topic My*/App\n → My__App_
        assertTrue(result.contains("topic=My__App_"), "特殊字符应替换为 _，实际：" + result);
    }

    @Test
    @Order(15)
    @DisplayName("TraceId 为空时使用 'none'")
    void testNoTraceIdUsesNone() throws Exception {
        TraceIdProvider nullProvider = () -> null;
        TraceIdDruidFilter nullFilter = new TraceIdDruidFilter(
                nullProvider, "TestApp", SqlTracingProperties.TracingMode.ALL);

        String sql = "SELECT 1";
        String result = invokeProcessSql(nullFilter, sql);

        assertTrue(result.contains("traceid=none"), "空 traceId 应该使用 'none'");
    }

    @Test
    @Order(16)
    @DisplayName("TraceIdProvider 为 null 时使用 'none'")
    void testNullProviderUsesNone() throws Exception {
        TraceIdDruidFilter nullProviderFilter = new TraceIdDruidFilter(
                null, "TestApp", SqlTracingProperties.TracingMode.ALL);

        String sql = "SELECT 1";
        String result = invokeProcessSql(nullProviderFilter, sql);

        assertTrue(result.contains("traceid=none"), "null provider 应该使用 'none'");
    }

    @Test
    @Order(17)
    @DisplayName("默认 TraceIdProvider 从 MDC 获取")
    void testDefaultTraceIdProviderFromMDC() {
        MDC.put("traceId", "mdc-trace-123");

        DefaultTraceIdProvider provider = new DefaultTraceIdProvider();
        String traceId = provider.getTraceId();

        assertEquals("mdc-trace-123", traceId);
    }

    @Test
    @Order(18)
    @DisplayName("默认 TraceIdProvider 尝试多个 MDC key")
    void testDefaultTraceIdProviderMultipleKeys() {
        // 测试 X-Request-Id
        MDC.put("X-Request-Id", "request-id-456");
        DefaultTraceIdProvider provider = new DefaultTraceIdProvider();
        assertEquals("request-id-456", provider.getTraceId());
        MDC.clear();

        // 测试 trace_id
        MDC.put("trace_id", "trace_id-789");
        assertEquals("trace_id-789", provider.getTraceId());
    }

    @Test
    @Order(19)
    @DisplayName("SHOW 语句识别为读操作")
    void testShowIsReadOperation() throws Exception {
        String sql = "SHOW TABLES";
        String result = invokeProcessSql(writeOnlyFilter, sql);
        assertEquals(sql, result, "SHOW 应该被识别为读操作");
    }

    @Test
    @Order(20)
    @DisplayName("DESCRIBE 语句识别为读操作")
    void testDescribeIsReadOperation() throws Exception {
        String sql = "DESCRIBE users";
        String result = invokeProcessSql(writeOnlyFilter, sql);
        assertEquals(sql, result, "DESCRIBE 应该被识别为读操作");

        String descSql = "DESC users";
        String descResult = invokeProcessSql(writeOnlyFilter, descSql);
        assertEquals(descSql, descResult, "DESC 应该被识别为读操作");
    }

    @Test
    @Order(21)
    @DisplayName("EXPLAIN 语句识别为读操作")
    void testExplainIsReadOperation() throws Exception {
        String sql = "EXPLAIN SELECT * FROM users";
        String result = invokeProcessSql(writeOnlyFilter, sql);
        assertEquals(sql, result, "EXPLAIN 应该被识别为读操作");
    }

    // ==================== 辅助方法 ====================

    /**
     * 通过反射调用私有方法 processSql
     */
    private String invokeProcessSql(TraceIdDruidFilter filter, String sql) throws Exception {
        Method method = TraceIdDruidFilter.class.getDeclaredMethod("processSql", String.class);
        method.setAccessible(true);
        return (String) method.invoke(filter, sql);
    }
}
