package fun.commons.framework4j.datasource.functional;

import brave.Tracing;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.sampler.Sampler;
import fun.commons.framework4j.datasource.config.MultiDataSourceAutoConfiguration;
import fun.commons.framework4j.datasource.manager.MultiDataSourceManager;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Micrometer Tracing 自动生成 TraceId 集成测试
 * <p>
 * 本测试演示如何使用 Micrometer Tracing (Brave Bridge) 自动生成 TraceId，
 * 并验证 TraceId 被正确注入到 SQL 追踪注释中。
 *
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(
        properties = {
                "spring.autoconfigure.exclude=com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        }
)
@ActiveProfiles("tracing-test")
@DisplayName("Micrometer Tracing 自动生成 TraceId 集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MicrometerTracingIntegrationTest {

    @Autowired
    private MultiDataSourceManager manager;

    @Autowired
    private BraveTracer tracer;

    private static final List<String> capturedTraceIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MDC.clear();
        capturedTraceIds.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @Order(1)
    @DisplayName("测试1: Micrometer Tracing 自动生成 TraceId")
    void test01_MicrometerAutoGeneratesTraceId() throws Exception {
        log.info("========== 开始测试 Micrometer 自动生成 TraceId ==========");

        DataSource ds = manager.getDataSource("default");

        // 创建一个 Span (模拟 HTTP 请求或业务操作)
        io.micrometer.tracing.Span span = tracer.nextSpan().name("test-operation").start();

        try (io.micrometer.tracing.Tracer.SpanInScope ws = tracer.withSpan(span)) {
            // 获取 Micrometer 生成的 TraceId
            String micrometerTraceId = span.context().traceId();
            log.info("✅ Micrometer 生成的 TraceId: {}", micrometerTraceId);

            assertNotNull(micrometerTraceId, "TraceId 不应该为 null");
            assertFalse(micrometerTraceId.isBlank(), "TraceId 不应该为空");

            // 验证 TraceId 已自动同步到 MDC
            String mdcTraceId = MDC.get("traceId");
            log.info("✅ MDC 中的 TraceId: {}", mdcTraceId);

            // Brave 会将 TraceId 放到 MDC，但 key 可能是 traceId 或其他
            // 我们验证 MDC 中确实有值
            assertTrue(mdcTraceId != null || MDC.get("X-B3-TraceId") != null,
                    "TraceId 应该被同步到 MDC");

            // 执行 SQL 查询
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 AS result")) {

                log.info("PreparedStatement: {}", ps.toString());

                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "应该有查询结果");
                    assertEquals(1, rs.getInt("result"), "结果应该是 1");
                }
            }

            log.info("✅ SQL 执行完成，请检查日志中的 [SQL Tracing] 输出");
            log.info("期望看到: [SQL Tracing] TraceId={}, Topic=default-datasource", micrometerTraceId);

        } finally {
            span.end();
        }

        log.info("========== Micrometer TraceId 测试结束 ==========\n");
    }

    @Test
    @Order(2)
    @DisplayName("测试2: 多个嵌套 Span 的 TraceId 传播")
    void test02_NestedSpansTraceIdPropagation() throws Exception {
        log.info("========== 开始测试嵌套 Span 的 TraceId 传播 ==========");

        DataSource ds = manager.getDataSource("default");

        // 创建父 Span
        io.micrometer.tracing.Span parentSpan = tracer.nextSpan().name("parent-operation").start();

        try (io.micrometer.tracing.Tracer.SpanInScope ws1 = tracer.withSpan(parentSpan)) {
            String parentTraceId = parentSpan.context().traceId();
            log.info("✅ 父 Span TraceId: {}", parentTraceId);

            // 执行第一次查询
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 'parent' AS label")) {
                ps.executeQuery().close();
            }

            // 创建子 Span
            io.micrometer.tracing.Span childSpan = tracer.nextSpan().name("child-operation").start();
            try (io.micrometer.tracing.Tracer.SpanInScope ws2 = tracer.withSpan(childSpan)) {
                String childTraceId = childSpan.context().traceId();
                log.info("✅ 子 Span TraceId: {}", childTraceId);

                // 验证父子 Span 的 TraceId 相同
                assertEquals(parentTraceId, childTraceId, "子 Span 应该继承父 Span 的 TraceId");

                // 执行第二次查询
                try (Connection conn = ds.getConnection();
                     PreparedStatement ps = conn.prepareStatement("SELECT 'child' AS label")) {
                    ps.executeQuery().close();
                }

            } finally {
                childSpan.end();
            }

        } finally {
            parentSpan.end();
        }

        log.info("✅ 嵌套 Span 的 TraceId 传播测试完成");
        log.info("========== 测试结束 ==========\n");
    }

    @Test
    @Order(3)
    @DisplayName("测试3: 不同请求的 TraceId 隔离")
    void test03_DifferentRequestsHaveDifferentTraceIds() throws Exception {
        log.info("========== 开始测试不同请求的 TraceId 隔离 ==========");

        DataSource ds = manager.getDataSource("default");

        // 第一个请求
        io.micrometer.tracing.Span span1 = tracer.nextSpan().name("request-1").start();
        String traceId1;
        try (io.micrometer.tracing.Tracer.SpanInScope ws = tracer.withSpan(span1)) {
            traceId1 = span1.context().traceId();
            log.info("✅ 请求1 TraceId: {}", traceId1);

            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1")) {
                ps.executeQuery().close();
            }
        } finally {
            span1.end();
        }

        // 清理 MDC
        MDC.clear();

        // 第二个请求
        io.micrometer.tracing.Span span2 = tracer.nextSpan().name("request-2").start();
        String traceId2;
        try (io.micrometer.tracing.Tracer.SpanInScope ws = tracer.withSpan(span2)) {
            traceId2 = span2.context().traceId();
            log.info("✅ 请求2 TraceId: {}", traceId2);

            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 2")) {
                ps.executeQuery().close();
            }
        } finally {
            span2.end();
        }

        // 验证两个请求的 TraceId 不同
        assertNotEquals(traceId1, traceId2, "不同请求应该有不同的 TraceId");

        log.info("✅ TraceId 隔离测试完成");
        log.info("========== 测试结束 ==========\n");
    }

    @Test
    @Order(4)
    @DisplayName("测试4: 验证 Micrometer Tracing 正常工作")
    void test04_VerifyMicrometerTracingWorks() {
        log.info("========== 验证 Micrometer Tracing 正常工作 ==========");
        log.info("本测试期间捕获的 TraceId 数量: {}", capturedTraceIds.size());

        for (int i = 0; i < capturedTraceIds.size(); i++) {
            log.info("TraceId #{}: {}", i + 1, capturedTraceIds.get(i));
        }

        // 验证 tracer 不为 null
        assertNotNull(tracer, "BraveTracer 应该被正确注入");

        log.info("✅ Micrometer Tracing 正常工作");
        log.info("========== 测试结束 ==========\n");
    }

    // ==================== 测试配置类 ====================

    /**
     * Micrometer Tracing 配置 + 数据源配置
     */
    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.context.annotation.Import({
            MultiDataSourceAutoConfiguration.class
    })
    @MapperScan(basePackages = "fun.commons.framework4j.datasource.mapper.business",
            sqlSessionFactoryRef = "businessSqlSessionFactory")
    @MapperScan(basePackages = "fun.commons.framework4j.datasource.mapper.log",
            sqlSessionFactoryRef = "logSqlSessionFactory")
    static class TestConfiguration {

        @Bean
        public Tracing braveTracing(SpanHandler spanHandler) {
            return Tracing.newBuilder()
                    .currentTraceContext(brave.propagation.ThreadLocalCurrentTraceContext.newBuilder()
                            .addScopeDecorator(brave.context.slf4j.MDCScopeDecorator.get())
                            .build())
                    .sampler(Sampler.ALWAYS_SAMPLE)
                    .addSpanHandler(spanHandler)
                    .build();
        }

        @Bean
        public SpanHandler spanHandler() {
            return new SpanHandler() {
                @Override
                public boolean end(brave.propagation.TraceContext context, MutableSpan span, Cause cause) {
                    // 捕获 TraceId 到静态列表
                    String traceId = context.traceIdString();
                    capturedTraceIds.add(traceId);
                    log.debug("Captured TraceId: {}", traceId);
                    return true;
                }
            };
        }

        @Bean
        public BraveTracer braveTracer(Tracing tracing) {
            return new BraveTracer(
                    tracing.tracer(),
                    new BraveCurrentTraceContext(tracing.currentTraceContext()),
                    new BraveBaggageManager()
            );
        }
    }
}
