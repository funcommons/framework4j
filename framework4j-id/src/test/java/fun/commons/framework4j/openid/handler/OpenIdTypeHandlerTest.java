package fun.commons.framework4j.openid.handler;

import fun.commons.framework4j.id.util.IdObfuscator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * OpenIdTypeHandler 全功能测试套件
 *
 * 测试覆盖维度:
 * 1. 基本功能测试 - 正常流程的参数设置和结果获取
 * 2. 边界条件测试 - 零值、极大值、空值等边界情况
 * 3. 异常处理测试 - 无效OpenID、SQL异常等
 * 4. NULL值处理测试 - 数据库NULL值的正确处理
 * 5. 类型转换测试 - BIGINT/INTEGER不同类型的处理
 * 6. 性能测试 - 批量操作的性能验证
 * 7. 并发安全测试 - 多线程环境下的安全性
 * 8. 集成测试 - 与MyBatis框架的集成模拟
 */
@DisplayName("OpenIdTypeHandler 测试")
class OpenIdTypeHandlerTest {

    private OpenIdTypeHandler typeHandler;

    @Mock
    private PreparedStatement mockPreparedStatement;

    @Mock
    private ResultSet mockResultSet;

    @Mock
    private CallableStatement mockCallableStatement;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        typeHandler = new OpenIdTypeHandler();
    }

    // ==========================================
    // 1. 基本功能测试
    // ==========================================

    @Nested
    @DisplayName("1. 基本功能测试")
    class BasicFunctionalityTest {

        @Test
        @DisplayName("setNonNullParameter: 有效OpenID应成功转换为数字")
        void testSetNonNullParameter_ValidOpenId_ShouldConvertToNumeric() throws SQLException {
            // 准备
            long originalId = 123456789L;
            String openId = IdObfuscator.toOpenId(originalId);

            // 执行
            typeHandler.setNonNullParameter(mockPreparedStatement, 1, openId, null);

            // 验证
            verify(mockPreparedStatement).setLong(1, originalId);
        }

        @Test
        @DisplayName("setNonNullParameter: BIGINT类型应正确设置")
        void testSetNonNullParameter_BigIntType_ShouldSetCorrectly() throws SQLException {
            long originalId = 9876543210L;
            String openId = IdObfuscator.toOpenId(originalId);

            typeHandler.setNonNullParameter(mockPreparedStatement, 2, openId,
                org.apache.ibatis.type.JdbcType.BIGINT);

            verify(mockPreparedStatement).setLong(2, originalId);
        }

        @Test
        @DisplayName("setNonNullParameter: INTEGER类型应正确设置")
        void testSetNonNullParameter_IntegerType_ShouldSetCorrectly() throws SQLException {
            int originalId = 12345;
            String openId = IdObfuscator.toOpenId(originalId);

            typeHandler.setNonNullParameter(mockPreparedStatement, 3, openId,
                org.apache.ibatis.type.JdbcType.INTEGER);

            verify(mockPreparedStatement).setLong(3, originalId);
        }

        @Test
        @DisplayName("getNullableResult(ResultSet, String): 正常读取应返回OpenID")
        void testGetNullableResult_ResultSetString_ShouldReturnOpenId() throws SQLException {
            // 准备
            long originalId = 555555L;
            String expectedOpenId = IdObfuscator.toOpenId(originalId);
            when(mockResultSet.getLong("id")).thenReturn(originalId);
            when(mockResultSet.wasNull()).thenReturn(false);

            // 执行
            String result = typeHandler.getNullableResult(mockResultSet, "id");

            // 验证
            assertEquals(expectedOpenId, result);
            verify(mockResultSet).getLong("id");
            verify(mockResultSet).wasNull();
        }

        @Test
        @DisplayName("getNullableResult(ResultSet, int): 正常读取应返回OpenID")
        void testGetNullableResult_ResultSetInt_ShouldReturnOpenId() throws SQLException {
            long originalId = 777777L;
            String expectedOpenId = IdObfuscator.toOpenId(originalId);
            when(mockResultSet.getLong(1)).thenReturn(originalId);
            when(mockResultSet.wasNull()).thenReturn(false);

            String result = typeHandler.getNullableResult(mockResultSet, 1);

            assertEquals(expectedOpenId, result);
            verify(mockResultSet).getLong(1);
            verify(mockResultSet).wasNull();
        }

        @Test
        @DisplayName("getNullableResult(CallableStatement): 正常读取应返回OpenID")
        void testGetNullableResult_CallableStatement_ShouldReturnOpenId() throws SQLException {
            long originalId = 999999L;
            String expectedOpenId = IdObfuscator.toOpenId(originalId);
            when(mockCallableStatement.getLong(1)).thenReturn(originalId);
            when(mockCallableStatement.wasNull()).thenReturn(false);

            String result = typeHandler.getNullableResult(mockCallableStatement, 1);

            assertEquals(expectedOpenId, result);
            verify(mockCallableStatement).getLong(1);
            verify(mockCallableStatement).wasNull();
        }
    }

    // ==========================================
    // 2. NULL值处理测试
    // ==========================================

    @Nested
    @DisplayName("2. NULL值处理测试")
    class NullHandlingTest {

        @ParameterizedTest
        @DisplayName("setNonNullParameter: NULL输入应设置数据库NULL")
        @ValueSource(strings = {"", "   "})
        void testSetNonNullParameter_NullInput_ShouldSetDatabaseNull(String nullInput) throws SQLException {
            typeHandler.setNonNullParameter(mockPreparedStatement, 1, nullInput, null);

            verify(mockPreparedStatement).setNull(1, org.apache.ibatis.type.JdbcType.BIGINT.TYPE_CODE);
        }

        @Test
        @DisplayName("setNonNullParameter: 明确NULL应设置数据库NULL")
        void testSetNonNullParameter_ExplicitNull_ShouldSetDatabaseNull() throws SQLException {
            typeHandler.setNonNullParameter(mockPreparedStatement, 1, null, null);

            verify(mockPreparedStatement).setNull(1, org.apache.ibatis.type.JdbcType.BIGINT.TYPE_CODE);
        }

        @Test
        @DisplayName("setNonNullParameter: 空字符串且INTEGER类型应正确设置NULL")
        void testSetNonNullParameter_EmptyStringWithIntegerType_ShouldSetIntegerNull() throws SQLException {
            typeHandler.setNonNullParameter(mockPreparedStatement, 1, "",
                org.apache.ibatis.type.JdbcType.INTEGER);

            verify(mockPreparedStatement).setNull(1, org.apache.ibatis.type.JdbcType.INTEGER.TYPE_CODE);
        }

        @ParameterizedTest
        @DisplayName("getNullableResult: 数据库NULL应返回Java NULL")
        @ValueSource(ints = {1, 2, 5, 10})
        void testGetNullableResult_DatabaseNull_ShouldReturnJavaNull(int columnIndex) throws SQLException {
            when(mockResultSet.getLong(columnIndex)).thenReturn(0L);
            when(mockResultSet.wasNull()).thenReturn(true);

            String result = typeHandler.getNullableResult(mockResultSet, columnIndex);

            assertNull(result);
        }

        @Test
        @DisplayName("getNullableResult(CallableStatement): 存储过程NULL应返回Java NULL")
        void testGetNullableResult_CallableStatementNull_ShouldReturnJavaNull() throws SQLException {
            when(mockCallableStatement.getLong(anyInt())).thenReturn(0L);
            when(mockCallableStatement.wasNull()).thenReturn(true);

            String result = typeHandler.getNullableResult(mockCallableStatement, 1);

            assertNull(result);
        }

        @Test
        @DisplayName("getNullableResult: 列名为NULL应正确处理")
        void testGetNullableResult_NullColumnName_ShouldReturnJavaNull() throws SQLException {
            when(mockResultSet.getLong(anyString())).thenReturn(0L);
            when(mockResultSet.wasNull()).thenReturn(true);

            String result = typeHandler.getNullableResult(mockResultSet, "nonexistent");

            assertNull(result);
        }
    }

    // ==========================================
    // 3. 边界条件测试
    // ==========================================

    @Nested
    @DisplayName("3. 边界条件测试")
    class BoundaryTest {

        @Test
        @DisplayName("setNonNullParameter: 最小ID值应正确处理")
        void testSetNonNullParameter_MinIdValue_ShouldHandleCorrectly() throws SQLException {
            long minId = 1L;
            String openId = IdObfuscator.toOpenId(minId);

            typeHandler.setNonNullParameter(mockPreparedStatement, 1, openId, null);

            verify(mockPreparedStatement).setLong(1, minId);
        }

        @Test
        @DisplayName("setNonNullParameter: 最大ID值应正确处理")
        void testSetNonNullParameter_MaxIdValue_ShouldHandleCorrectly() throws SQLException {
            long maxId = Long.MAX_VALUE;
            String openId = IdObfuscator.toOpenId(maxId);

            typeHandler.setNonNullParameter(mockPreparedStatement, 1, openId, null);

            verify(mockPreparedStatement).setLong(1, maxId);
        }

        @Test
        @DisplayName("setNonNullParameter: 零值ID应正确处理")
        void testSetNonNullParameter_ZeroId_ShouldHandleCorrectly() throws SQLException {
            long zeroId = 0L;
            String openId = IdObfuscator.toOpenId(zeroId);

            typeHandler.setNonNullParameter(mockPreparedStatement, 1, openId, null);

            verify(mockPreparedStatement).setLong(1, zeroId);
        }

        @ParameterizedTest
        @DisplayName("getNullableResult: 零值但非NULL应返回转换后的OpenID")
        @ValueSource(strings = {"id", "user_id", "order_id", "test_column"})
        void testGetNullableResult_ZeroValueNotNull_ShouldReturnConvertedOpenId(String columnName) throws SQLException {
            when(mockResultSet.getLong(columnName)).thenReturn(0L);
            when(mockResultSet.wasNull()).thenReturn(false);

            String result = typeHandler.getNullableResult(mockResultSet, columnName);

            assertNotNull(result);
            assertEquals(IdObfuscator.toOpenId(0L), result);
        }

        @Test
        @DisplayName("convertToOpenId: 大数值应正确转换")
        void testConvertToOpenId_LargeNumber_ShouldConvertCorrectly() {
            long largeId = 9223372036854775806L; // Long.MAX_VALUE - 1

            // 使用反射调用protected方法
            try {
                java.lang.reflect.Method method = OpenIdTypeHandler.class
                    .getDeclaredMethod("convertToOpenId", long.class);
                method.setAccessible(true);
                String result = (String) method.invoke(typeHandler, largeId);

                assertEquals(IdObfuscator.toOpenId(largeId), result);
            } catch (Exception e) {
                fail("反射调用失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("setNonNullParameter: INTEGER范围内的值应正确处理")
        void testSetNonNullParameter_IntegerRangeValue_ShouldHandleCorrectly() throws SQLException {
            int intValue = Integer.MAX_VALUE;
            String openId = IdObfuscator.toOpenId(intValue);

            typeHandler.setNonNullParameter(mockPreparedStatement, 1, openId,
                org.apache.ibatis.type.JdbcType.INTEGER);

            verify(mockPreparedStatement).setLong(1, intValue);
        }
    }

    // ==========================================
    // 4. 异常处理测试
    // ==========================================

    @Nested
    @DisplayName("4. 异常处理测试")
    class ExceptionHandlingTest {

        @ParameterizedTest
        @DisplayName("setNonNullParameter: 无效OpenID应抛出SQLException")
        @ValueSource(strings = {
            "!@#$%^&*()",
            "中文乱码",
            "invalid@id",
            "test#123"
        })
        void testSetNonNullParameter_InvalidOpenId_ShouldThrowSQLException(String invalidOpenId) {
            SQLException exception = assertThrows(SQLException.class, () -> {
                typeHandler.setNonNullParameter(mockPreparedStatement, 1, invalidOpenId, null);
            });

            assertTrue(exception.getMessage().contains("Failed to convert OpenID"));
            assertTrue(exception.getCause() != null);
        }

        @Test
        @DisplayName("setNonNullParameter: SQLException应被包装并重新抛出")
        void testSetNonNullParameter_SqlException_ShouldWrapAndRethrow() throws SQLException {
            String validOpenId = IdObfuscator.toOpenId(123L);
            doThrow(new SQLException("Database error"))
                .when(mockPreparedStatement).setLong(anyInt(), anyLong());

            SQLException exception = assertThrows(SQLException.class, () -> {
                typeHandler.setNonNullParameter(mockPreparedStatement, 1, validOpenId, null);
            });

            assertEquals("Database error", exception.getMessage());
        }

        @Test
        @DisplayName("setNonNullParameter: setNull异常应被正确处理")
        void testSetNonNullParameter_SetNullException_ShouldPropagate() throws SQLException {
            doThrow(new SQLException("Cannot set NULL"))
                .when(mockPreparedStatement).setNull(anyInt(), anyInt());

            SQLException exception = assertThrows(SQLException.class, () -> {
                typeHandler.setNonNullParameter(mockPreparedStatement, 1, "", null);
            });

            assertEquals("Cannot set NULL", exception.getMessage());
        }

        @ParameterizedTest
        @DisplayName("getNullableResult: ResultSet异常应被传播")
        @ValueSource(strings = {"invalid_column", "", "null"})
        void testGetNullableResult_ResultSetException_ShouldPropagate(String columnName) throws SQLException {
            when(mockResultSet.getLong(columnName))
                .thenThrow(new SQLException("Column not found: " + columnName));

            SQLException exception = assertThrows(SQLException.class, () -> {
                typeHandler.getNullableResult(mockResultSet, columnName);
            });

            assertTrue(exception.getMessage().contains("Column not found"));
        }

        @Test
        @DisplayName("getNullableResult: CallableStatement异常应被传播")
        void testGetNullableResult_CallableStatementException_ShouldPropagate() throws SQLException {
            when(mockCallableStatement.getLong(1))
                .thenThrow(new SQLException("Parameter index out of range"));

            SQLException exception = assertThrows(SQLException.class, () -> {
                typeHandler.getNullableResult(mockCallableStatement, 1);
            });

            assertTrue(exception.getMessage().contains("Parameter index out of range"));
        }
    }

    // ==========================================
    // 5. 类型转换测试
    // ==========================================

    @Nested
    @DisplayName("5. 类型转换测试")
    class TypeConversionTest {

        @ParameterizedTest
        @DisplayName("setNonNullParameter: 不同JDBC类型应正确处理")
        @EnumSource(org.apache.ibatis.type.JdbcType.class)
        void testSetNonNullParameter_DifferentJdbcTypes_ShouldHandleCorrectly(
                org.apache.ibatis.type.JdbcType jdbcType) throws SQLException {
            if (jdbcType == org.apache.ibatis.type.JdbcType.BIGINT ||
                jdbcType == org.apache.ibatis.type.JdbcType.INTEGER) {

                String openId = IdObfuscator.toOpenId(12345L);
                typeHandler.setNonNullParameter(mockPreparedStatement, 1, openId, jdbcType);

                verify(mockPreparedStatement).setLong(1, 12345L);
            }
        }

        @Test
        @DisplayName("setNonNullParameter: NULL JDBC类型应默认为BIGINT")
        void testSetNonNullParameter_NullJdbcType_ShouldDefaultToBigint() throws SQLException {
            String openId = IdObfuscator.toOpenId(54321L);
            typeHandler.setNonNullParameter(mockPreparedStatement, 1, openId, null);

            verify(mockPreparedStatement).setLong(1, 54321L);
        }

        @Test
        @DisplayName("getNullableResult: BIGINT值应正确转换")
        void testGetNullableResult_BigintValue_ShouldConvertCorrectly() throws SQLException {
            long bigintValue = 922337203685477580L;
            String expectedOpenId = IdObfuscator.toOpenId(bigintValue);

            when(mockResultSet.getLong("bigint_column")).thenReturn(bigintValue);
            when(mockResultSet.wasNull()).thenReturn(false);

            String result = typeHandler.getNullableResult(mockResultSet, "bigint_column");

            assertEquals(expectedOpenId, result);
        }

        @Test
        @DisplayName("getNullableResult: INTEGER值应正确转换")
        void testGetNullableResult_IntegerValue_ShouldConvertCorrectly() throws SQLException {
            int integerValue = 2147483647; // Integer.MAX_VALUE
            String expectedOpenId = IdObfuscator.toOpenId(integerValue);

            when(mockResultSet.getLong("int_column")).thenReturn((long) integerValue);
            when(mockResultSet.wasNull()).thenReturn(false);

            String result = typeHandler.getNullableResult(mockResultSet, "int_column");

            assertEquals(expectedOpenId, result);
        }
    }

    // ==========================================
    // 6. 性能测试
    // ==========================================

    @Nested
    @DisplayName("6. 性能测试")
    @Tag("Performance")
    class PerformanceTest {

        @Test
        @DisplayName("setNonNullParameter: 批量操作性能测试")
        void testSetNonNullParameter_BatchOperation_ShouldMaintainPerformance() throws SQLException {
            int batchSize = 10000;
            long startTime = System.nanoTime();

            for (int i = 0; i < batchSize; i++) {
                String openId = IdObfuscator.toOpenId(i);
                typeHandler.setNonNullParameter(mockPreparedStatement, 1, openId, null);
            }

            long duration = System.nanoTime() - startTime;
            double avgDurationNanos = (double) duration / batchSize;
            double avgDurationMicros = avgDurationNanos / 1000.0;

            // 平均每次操作应小于100微秒
            assertTrue(avgDurationMicros < 100.0,
                String.format("平均操作时间 %.2f 微秒超过阈值 100 微秒", avgDurationMicros));

            System.out.printf("批量操作性能: %d 次操作总耗时 %.2f 毫秒, 平均每次 %.2f 微秒%n",
                batchSize, duration / 1_000_000.0, avgDurationMicros);
        }

        @Test
        @DisplayName("getNullableResult: 批量读取性能测试")
        void testGetNullableResult_BatchRead_ShouldMaintainPerformance() throws SQLException {
            int batchSize = 10000;
            when(mockResultSet.getLong(anyString())).thenReturn(12345L);
            when(mockResultSet.wasNull()).thenReturn(false);

            long startTime = System.nanoTime();

            for (int i = 0; i < batchSize; i++) {
                typeHandler.getNullableResult(mockResultSet, "test_column");
            }

            long duration = System.nanoTime() - startTime;
            double avgDurationNanos = (double) duration / batchSize;
            double avgDurationMicros = avgDurationNanos / 1000.0;

            // 平均每次操作应小于50微秒
            assertTrue(avgDurationMicros < 50.0,
                String.format("平均操作时间 %.2f 微秒超过阈值 50 微秒", avgDurationMicros));

            System.out.printf("批量读取性能: %d 次操作总耗时 %.2f 毫秒, 平均每次 %.2f 微秒%n",
                batchSize, duration / 1_000_000.0, avgDurationMicros);
        }

        @Test
        @DisplayName("性能基准: QPS应超过10万/秒")
        void testPerformanceBenchmark_QpsShouldExceed100k() throws SQLException {
            int operations = 100000;
            when(mockResultSet.getLong(anyString())).thenReturn(123L);
            when(mockResultSet.wasNull()).thenReturn(false);

            long startTime = System.currentTimeMillis();

            // 混合读写操作
            for (int i = 0; i < operations; i++) {
                String openId = IdObfuscator.toOpenId(i);
                typeHandler.setNonNullParameter(mockPreparedStatement, 1, openId, null);
                typeHandler.getNullableResult(mockResultSet, "test_column");
            }

            long endTime = System.currentTimeMillis();
            double durationSeconds = (endTime - startTime) / 1000.0;
            double qps = (operations * 2) / durationSeconds; // 每次循环2个操作

            assertTrue(qps > 100000,
                String.format("QPS %.0f 低于预期阈值 100000", qps));

            System.out.printf("性能基准: %d 个操作耗时 %.2f 秒, QPS: %.0f%n",
                operations * 2, durationSeconds, qps);
        }
    }

    // ==========================================
    // 7. 并发安全测试
    // ==========================================

    @Nested
    @DisplayName("7. 并发安全测试")
    @Tag("Concurrency")
    class ConcurrencyTest {

        @Test
        @DisplayName("setNonNullParameter: 多线程并发写入应线程安全")
        void testSetNonNullParameter_ConcurrentWrites_ShouldBeThreadSafe() throws InterruptedException {
            int threadCount = 50;
            int operationsPerThread = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < operationsPerThread; i++) {
                            String openId = IdObfuscator.toOpenId(threadId * operationsPerThread + i);
                            typeHandler.setNonNullParameter(mockPreparedStatement, 1, openId, null);
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        System.err.printf("线程 %d 发生错误: %s%n", threadId, e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS), "并发测试超时");
            executor.shutdown();
        if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
            int expectedOperations = threadCount * operationsPerThread;
            assertEquals(expectedOperations, successCount.get() + errorCount.get());
            assertEquals(0, errorCount.get(),
                String.format("发生 %d 个错误", errorCount.get()));

            System.out.printf("并发写入测试: %d 个线程, 每线程 %d 次操作, 成功 %d 次, 失败 %d 次%n",
                threadCount, operationsPerThread, successCount.get(), errorCount.get());
        }

        @Test
        @DisplayName("getNullableResult: 多线程并发读取应线程安全")
        void testGetNullableResult_ConcurrentReads_ShouldBeThreadSafe() throws InterruptedException, SQLException {
            int threadCount = 50;
            int operationsPerThread = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            when(mockResultSet.getLong(anyString())).thenReturn(12345L);
            when(mockResultSet.wasNull()).thenReturn(false);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < operationsPerThread; i++) {
                            String result = typeHandler.getNullableResult(mockResultSet, "test_column");
                            assertNotNull(result);
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        System.err.printf("读取线程发生错误: %s%n", e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS), "并发读取测试超时");
            executor.shutdown();
        if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
            int expectedOperations = threadCount * operationsPerThread;
            assertEquals(expectedOperations, successCount.get() + errorCount.get());
            assertEquals(0, errorCount.get(),
                String.format("并发读取发生 %d 个错误", errorCount.get()));

            System.out.printf("并发读取测试: %d 个线程, 每线程 %d 次操作, 成功 %d 次, 失败 %d 次%n",
                threadCount, operationsPerThread, successCount.get(), errorCount.get());
        }

        @Test
        @DisplayName("混合操作: 并发读写应线程安全")
        void testMixedOperations_ConcurrentReadWrite_ShouldBeThreadSafe() throws InterruptedException, SQLException {
            int readerThreads = 25;
            int writerThreads = 25;
            int operationsPerThread = 500;
            ExecutorService executor = Executors.newFixedThreadPool(readerThreads + writerThreads);
            CountDownLatch latch = new CountDownLatch(readerThreads + writerThreads);
            AtomicLong totalOperations = new AtomicLong(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            when(mockResultSet.getLong(anyString())).thenReturn(123L);
            when(mockResultSet.wasNull()).thenReturn(false);

            // 启动写线程
            for (int t = 0; t < writerThreads; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < operationsPerThread; i++) {
                            String openId = IdObfuscator.toOpenId(threadId * operationsPerThread + i);
                            typeHandler.setNonNullParameter(mockPreparedStatement, 1, openId, null);
                            totalOperations.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // 启动读线程
            for (int t = 0; t < readerThreads; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < operationsPerThread; i++) {
                            String result = typeHandler.getNullableResult(mockResultSet, "test_column");
                            assertNotNull(result);
                            totalOperations.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS), "混合并发测试超时");
            executor.shutdown();
        if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
            int expectedOperations = (readerThreads + writerThreads) * operationsPerThread;
            assertEquals(expectedOperations, totalOperations.get() + errorCount.get());
            assertEquals(0, errorCount.get(),
                String.format("混合并发测试发生 %d 个错误", errorCount.get()));

            System.out.printf("混合并发测试: 完成 %d 次操作, 失败 %d 次%n",
                totalOperations.get(), errorCount.get());
        }
    }

    // ==========================================
    // 8. 集成测试
    // ==========================================

    @Nested
    @DisplayName("8. 集成测试")
    class IntegrationTest {

        @Test
        @DisplayName("完整流程: OpenID到数据库再回到OpenID")
        void testCompleteWorkflow_OpenIdToDatabaseAndBack_ShouldMaintainIntegrity() throws SQLException {
            // 准备
            long originalId = 888888888L;
            String originalOpenId = IdObfuscator.toOpenId(originalId);

            // 模拟写入数据库
            typeHandler.setNonNullParameter(mockPreparedStatement, 1, originalOpenId, null);
            verify(mockPreparedStatement).setLong(1, originalId);

            // 模拟从数据库读取
            when(mockResultSet.getLong("id")).thenReturn(originalId);
            when(mockResultSet.wasNull()).thenReturn(false);
            String retrievedOpenId = typeHandler.getNullableResult(mockResultSet, "id");

            // 验证完整性
            assertEquals(originalOpenId, retrievedOpenId,
                "完整流程后OpenID应该保持一致");
        }

        @ParameterizedTest
        @DisplayName("批量集成: 多个ID的完整流程")
        @CsvSource({
            "1, true",
            "12345, true",
            "999999999, true",
            "0, true",
            "2147483647, true"
        })
        void testBatchIntegration_MultipleIds_ShouldMaintainIntegrity(long id, boolean expected) throws SQLException {
            String openId = IdObfuscator.toOpenId(id);

            // 写入
            typeHandler.setNonNullParameter(mockPreparedStatement, 1, openId, null);

            // 读取
            when(mockResultSet.getLong("test_id")).thenReturn(id);
            when(mockResultSet.wasNull()).thenReturn(false);
            String resultOpenId = typeHandler.getNullableResult(mockResultSet, "test_id");

            assertEquals(expected, openId.equals(resultOpenId),
                String.format("ID %d 的OpenID '%s' 与读取结果 '%s' 应该一致",
                    id, openId, resultOpenId));
        }

        @Test
        @DisplayName("MyBatis模拟: 实体类字段映射集成")
        void testMyBatisSimulation_EntityFieldMapping_ShouldWorkCorrectly() throws SQLException {
            // 模拟实体类场景
            class TestEntity {
                @SuppressWarnings("unused")
                private String id;  // 使用OpenIdTypeHandler
            }

            long entityId = 555666777L;
            String entityOpenId = IdObfuscator.toOpenId(entityId);

            // 模拟MyBatis设置实体属性
            typeHandler.setNonNullParameter(mockPreparedStatement, 1, entityOpenId, null);

            // 模拟MyBatis从数据库加载实体
            when(mockResultSet.getLong("id")).thenReturn(entityId);
            when(mockResultSet.wasNull()).thenReturn(false);
            String loadedOpenId = typeHandler.getNullableResult(mockResultSet, "id");

            assertEquals(entityOpenId, loadedOpenId,
                "实体类字段的OpenID映射应该保持一致");
        }

        @Test
        @DisplayName("存储过程集成: CallableStatement场景")
        void testStoredProcedureIntegration_CallableStatementScenario_ShouldWorkCorrectly() throws SQLException {
            long spResult = 777999888L;
            String expectedOpenId = IdObfuscator.toOpenId(spResult);

            // 模拟存储过程OUT参数
            when(mockCallableStatement.getLong(1)).thenReturn(spResult);
            when(mockCallableStatement.wasNull()).thenReturn(false);
            String resultOpenId = typeHandler.getNullableResult(mockCallableStatement, 1);

            assertEquals(expectedOpenId, resultOpenId,
                "存储过程的OpenID转换应该正确");
        }
    }

    // ==========================================
    // 9. 特殊场景测试
    // ==========================================

    @Nested
    @DisplayName("9. 特殊场景测试")
    class SpecialScenarioTest {

        @Test
        @DisplayName("极长OpenID字符串处理")
        void testVeryLongOpenIdString_ShouldHandleGracefully() throws SQLException {
            // 创建一个相对较大的ID值
            long largeId = Long.MAX_VALUE - 1000000;
            String longOpenId = IdObfuscator.toOpenId(largeId);

            assertDoesNotThrow(() -> {
                typeHandler.setNonNullParameter(mockPreparedStatement, 1, longOpenId, null);
            });

            verify(mockPreparedStatement).setLong(1, largeId);
        }

        @Test
        @DisplayName("连续操作: 大量连续ID处理")
        void testContinuousOperations_LargeSequentialIds_ShouldMaintainCorrectness() throws SQLException {
            int count = 1000;
            long startId = 1000000L;

            for (int i = 0; i < count; i++) {
                long currentId = startId + i;
                String openId = IdObfuscator.toOpenId(currentId);

                // 写入
                typeHandler.setNonNullParameter(mockPreparedStatement, 1, openId, null);

                // 读取
                when(mockResultSet.getLong("sequential_id")).thenReturn(currentId);
                when(mockResultSet.wasNull()).thenReturn(false);
                String resultOpenId = typeHandler.getNullableResult(mockResultSet, "sequential_id");

                assertEquals(openId, resultOpenId,
                    String.format("连续ID %d 的转换应该正确", currentId));
            }

            System.out.printf("连续操作测试: 完成 %d 个连续ID的处理", count);
        }

        @Test
        @DisplayName("内存使用: 大量操作不应导致内存泄漏")
        void testMemoryUsage_LargeOperations_ShouldNotLeakMemory() throws SQLException {
            Runtime runtime = Runtime.getRuntime();
            runtime.gc(); // 强制垃圾回收

            long initialMemory = runtime.totalMemory() - runtime.freeMemory();

            int operations = 100000;
            for (int i = 0; i < operations; i++) {
                String openId = IdObfuscator.toOpenId(i % 10000); // 重复使用ID
                typeHandler.setNonNullParameter(mockPreparedStatement, 1, openId, null);

                if (i % 1000 == 0) {
                    runtime.gc(); // 定期垃圾回收
                }
            }

            runtime.gc();
            long finalMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryIncrease = finalMemory - initialMemory;

            // 内存增长不应超过50MB (考虑到Mock对象和GC的不确定性)
            assertTrue(memoryIncrease < 50 * 1024 * 1024,
                String.format("内存增长 %d 字节超过阈值 50MB", memoryIncrease));

            System.out.printf("内存使用测试: %d 次操作后内存增长 %.2f MB%n",
                operations, memoryIncrease / (1024.0 * 1024.0));
        }
    }
}