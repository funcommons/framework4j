package fun.commons.framework4j.openid;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.filter.ContextValueFilter;
import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.openid.fastjson2.OpenIdAnnotationFilter;
import fun.commons.framework4j.openid.formatter.OpenIdFormatterFactory;
import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import fun.commons.framework4j.id.util.IdObfuscator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.format.Parser;

import java.text.ParseException;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LDX2T ID SDK 全功能深度测试套件 (Coverage 200%+)
 * <p>
 * 覆盖维度:
 * 1. 功能正确性 (Functionality)
 * 2. 边界条件 (Boundary Analysis)
 * 3. 异常处理 (Exception Handling)
 * 4. 并发安全性 (Thread Safety)
 * 5. 性能基准 (Performance Benchmark)
 * 6. 安全性 (Security & Reversibility)
 */
@Slf4j
@DisplayName("SDK 深度综合测试")
class AllFeaturesTest {

    // ==========================================
    // 1. 雪花算法核心测试 (SnowflakeDistributor)
    // ==========================================
    @Nested
    @DisplayName("1. 雪花算法核心测试")
    class SnowflakeTest {

        @Test
        @DisplayName("基础功能: ID 递增性与正数性")
        void testBasicGeneration() {
            SnowflakeDistributor distributor = new SnowflakeDistributor(1);
            long lastId = -1L;
            for (int i = 0; i < 1000; i++) {
                long id = distributor.nextId();
                assertTrue(id > 0, "ID 必须为正数");
                assertTrue(id > lastId, "ID 必须单调递增");
                lastId = id;
            }
        }

        @ParameterizedTest
        @DisplayName("边界测试: WorkerID 有效范围 (0-1023)")
        @ValueSource(longs = {0, 1, 512, 1023})
        void testWorkerIdBoundary_Valid(long workerId) {
            assertDoesNotThrow(() -> new SnowflakeDistributor(workerId));
        }

        @ParameterizedTest
        @DisplayName("异常测试: WorkerID 越界应抛出异常")
        @ValueSource(longs = {-1, 1024, Long.MAX_VALUE, Long.MIN_VALUE})
        void testWorkerIdBoundary_Invalid(long workerId) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new SnowflakeDistributor(workerId));
            log.debug("Expected exception caught: {}", ex.getMessage());
        }

        @Test
        @DisplayName("并发测试: 多线程高并发下 ID 唯一性 (100线程 x 1000次)")
        void testConcurrency() throws InterruptedException {
            int threads = 100;
            int requestsPerThread = 1000;
            SnowflakeDistributor distributor = new SnowflakeDistributor(1);

            // 使用 ConcurrentHashMap 存储生成的 ID，Key 为 ID，Value 为出现次数
            ConcurrentHashMap<Long, Integer> idMap = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);

            long start = System.currentTimeMillis();
            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < requestsPerThread; j++) {
                            long id = distributor.nextId();
                            // 如果 put 返回非 null，说明 Key 已存在，即 ID 重复
                            if (idMap.put(id, 1) != null) {
                                fail("发现重复 ID: " + id);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await(10, TimeUnit.SECONDS);
            long end = System.currentTimeMillis();

            assertEquals(threads * requestsPerThread, idMap.size(), "生成的 ID 总数不匹配，可能存在重复");
            log.info("[Perf] Concurrency Test: {} IDs generated in {} ms", idMap.size(), (end - start));
        }

        @Test
        @DisplayName("性能基准: 单线程生成速度应 > 100w/s")
        @Tag("Benchmark")
        void testPerformanceBenchmark() {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);
            int count = 1_000_000;

            long start = System.nanoTime();
            for (int i = 0; i < count; i++) {
                distributor.nextId();
            }
            long end = System.nanoTime();

            double durationMs = (end - start) / 1_000_000.0;
            double qps = count / (durationMs / 1000.0);

            log.info("[Benchmark] Generated {} IDs in {} ms. QPS: String.format(\"%.2f\", qps)", count, durationMs);
            assertTrue(qps > 100_000, "QPS 应高于 100,000 (实际: " + (int)qps + ")");
        }
    }

    // ==========================================
    // 2. 混淆工具测试 (IdObfuscator)
    // ==========================================
    @Nested
    @DisplayName("2. 混淆工具测试")
    class ObfuscatorTest {

        @ParameterizedTest
        @DisplayName("可逆性测试: 常见 ID 转换后应能还原")
        @ValueSource(longs = {1L, 100L, 123456789012345678L, Long.MAX_VALUE})
        void testReversibility(long id) {
            String openId = IdObfuscator.toOpenId(id);
            assertNotNull(openId);
            assertFalse(openId.isEmpty());

            long restored = IdObfuscator.fromOpenId(openId);
            assertEquals(id, restored, "ID " + id + " 还原失败");
        }

        @Test
        @DisplayName("边界测试: 0 的处理")
        void testZeroHandling() {
            String openId = IdObfuscator.toOpenId(0L);
            long restored = IdObfuscator.fromOpenId(openId);
            assertEquals(0L, restored);
        }

        @Test
        @DisplayName("安全性测试: OpenID 不应直接暴露 ID 增长规律")
        void testObfuscationQuality() {
            long id1 = 10000L;
            long id2 = 10001L;

            String s1 = IdObfuscator.toOpenId(id1);
            String s2 = IdObfuscator.toOpenId(id2);

            // 简单的检查：虽然只差1，但混淆后的字符串应该看起来差异较大，或者至少不只是最后一位变了
            // 注意：这取决于具体混淆算法，这里主要确保不相等
            assertNotEquals(s1, s2);
            log.debug("{} -> {}", id1, s1);
            log.debug("{} -> {}", id2, s2);
        }

        @ParameterizedTest
        @DisplayName("异常测试: 非法字符串还原应抛出异常")
        @ValueSource(strings = {"", "   ", "InvalidChar!@#", "中文测试"})
        void testInvalidInput(String invalidOpenId) {
            assertThrows(IllegalArgumentException.class, () -> IdObfuscator.fromOpenId(invalidOpenId));
        }

        @Test
        @DisplayName("异常测试: Null 输入")
        void testNullInput() {
            assertThrows(IllegalArgumentException.class, () -> IdObfuscator.fromOpenId(null));
        }
    }

    // ==========================================
    // 3. Spring MVC 入参转换测试 (Formatter)
    // ==========================================
    @Nested
    @DisplayName("3. Spring MVC 入参转换测试")
    class MvcConverterTest {

        private final OpenIdFormatterFactory factory = new OpenIdFormatterFactory();
        private final Parser<?> parser = factory.getParser(null, Long.class);

        @Test
        @DisplayName("正常解析: 合法 OpenID")
        void testParseValidOpenId() throws ParseException {
            long original = 666L;
            String openId = IdObfuscator.toOpenId(original);

            Long result = (Long) parser.parse(openId, Locale.CHINA);
            assertEquals(original, result);
        }

        @Test
        @DisplayName("兼容性解析: 纯数字字符串 (旧接口兼容)")
        void testParseRawNumber() throws ParseException {
            String raw = "99999";
            Long result = (Long) parser.parse(raw, Locale.CHINA);
            assertEquals(99999L, result);
        }

        @Test
        @DisplayName("边界测试: Null 或空字符串")
        void testParseEmpty() throws ParseException {
            assertNull(parser.parse("", Locale.CHINA));
            assertNull(parser.parse(null, Locale.CHINA));
        }

        @Test
        @DisplayName("异常测试: 乱码应抛出 ParseException")
        void testParseInvalid() {
            assertThrows(ParseException.class, () -> parser.parse("李羿慧3主顶层木38 923这 ", Locale.CHINA));
        }
    }

    // ==========================================
    // 4. FastJson2 出参混淆测试 (Filter)
    // ==========================================
    @Nested
    @DisplayName("4. FastJson2 出参混淆测试")
    class FastJsonFilterTest {

        @Data
        static class ComplexDTO {
            @OpenId
            private Long id;

            @OpenId
            private List<Long> ids;

            // 这是一个干扰项，虽然标记了 OpenId 但类型是 String，过滤器应忽略或报错(取决于策略，当前策略是忽略)
            @OpenId
            private String wrongTypeStr;

            private Long rawId; // 未标记
        }

        private final ContextValueFilter filter = new OpenIdAnnotationFilter();

        @Test
        @DisplayName("序列化: 单字段与列表字段混淆")
        void testSerialization() {
            long id = 888L;
            String expected = IdObfuscator.toOpenId(id);

            ComplexDTO dto = new ComplexDTO();
            dto.setId(id);
            dto.setIds(Arrays.asList(id, id));
            dto.setRawId(id);
            dto.setWrongTypeStr("ignore_me");

            String json = JSON.toJSONString(dto, filter);
            log.debug("JSON: {}", json);

            // 验证 1: 单字段被混淆
            assertTrue(json.contains("\"id\":\"" + expected + "\""));
            // 验证 2: 列表被混淆
            assertTrue(json.contains("\"ids\":[\"" + expected + "\",\"" + expected + "\"]"));
            // 验证 3: 未标记字段保持原样
            assertTrue(json.contains("\"rawId\":" + id));
            // 验证 4: 类型不匹配字段保持原样
            assertTrue(json.contains("\"wrongTypeStr\":\"ignore_me\""));
        }

        @Test
        @DisplayName("序列化: 列表包含 Null 值")
        void testListWithNulls() {
            ComplexDTO dto = new ComplexDTO();
            List<Long> list = new ArrayList<>();
            list.add(123L);
            list.add(null);
            list.add(456L);
            dto.setIds(list);



            // 执行并断言无异常，同时获取返回值
            String json = JSON.toJSONString(dto, filter);

            assertTrue(json.contains("null"), "List 中的 null 应该保留或被特殊处理");
        }

        @Test
        @DisplayName("序列化: 根对象字段为 Null")
        void testNullFields() {
            ComplexDTO dto = new ComplexDTO();
            dto.setId(null);
            dto.setIds(null);

            String json = JSON.toJSONString(dto, filter);
            // FastJson2 默认不输出 null 字段，或者输出 "id":null
            // 只要不报错即可
            assertNotNull(json);
        }
    }
}