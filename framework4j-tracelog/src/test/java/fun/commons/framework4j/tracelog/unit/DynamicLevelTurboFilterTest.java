package fun.commons.framework4j.tracelog.unit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.spi.FilterReply;
import fun.commons.framework4j.tracelog.appender.DynamicLevelTurboFilter;
import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DynamicLevelTurboFilter 提权过滤器")
class DynamicLevelTurboFilterTest {

    private DynamicLevelTurboFilter filter;
    private Logger logger;

    private static Logger createLogger(String name, Level level) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger l = lc.getLogger(name);
        l.setLevel(level);
        return l;
    }

    @BeforeEach
    void setUp() {
        TraceLogProperties props = new TraceLogProperties();
        props.getElevation().setAllowedPackages(List.of("com.example"));
        filter = new DynamicLevelTurboFilter(props);
        logger = createLogger("com.example.TestService", Level.INFO);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("全局 INFO → DEBUG 级别日志正常放行（走原有逻辑）")
    void infoPasses() {
        assertThat(filter.decide(null, logger, Level.INFO, null, null, null))
                .isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    @DisplayName("未提权 → DEBUG 被拦截")
    void noElevationDenies() {
        assertThat(filter.decide(null, logger, Level.DEBUG, null, null, null))
                .isEqualTo(FilterReply.DENY);
    }

    @Test
    @DisplayName("MDC 提权到 DEBUG → DEBUG / TRACE 都放行")
    void debugElevation() {
        MDC.put(DynamicLevelTurboFilter.MDC_DYNAMIC_LEVEL, "DEBUG");
        assertThat(filter.decide(null, logger, Level.DEBUG, null, null, null))
                .isEqualTo(FilterReply.ACCEPT);
        assertThat(filter.decide(null, logger, Level.TRACE, null, null, null))
                .isEqualTo(FilterReply.ACCEPT);
    }

    @Test
    @DisplayName("MDC 提权到 TRACE → 仅 TRACE 放行，DEBUG 拦截")
    void traceElevation() {
        MDC.put(DynamicLevelTurboFilter.MDC_DYNAMIC_LEVEL, "TRACE");
        assertThat(filter.decide(null, logger, Level.TRACE, null, null, null))
                .isEqualTo(FilterReply.ACCEPT);
        assertThat(filter.decide(null, logger, Level.DEBUG, null, null, null))
                .isEqualTo(FilterReply.DENY);
    }

    @Test
    @DisplayName("allowed-packages 外的 logger 永远拦截")
    void packageRestriction() {
        Logger externalLogger = createLogger("org.apache.http.client", Level.INFO);
        MDC.put(DynamicLevelTurboFilter.MDC_DYNAMIC_LEVEL, "DEBUG");
        // 即便提权，第三方包还是拦截
        assertThat(filter.decide(null, externalLogger, Level.DEBUG, null, null, null))
                .isEqualTo(FilterReply.DENY);
    }

    @Test
    @DisplayName("sub-package 也在 allowed 范围内")
    void subPackageMatch() {
        Logger subLogger = createLogger("com.example.order.OrderService", Level.INFO);
        MDC.put(DynamicLevelTurboFilter.MDC_DYNAMIC_LEVEL, "DEBUG");
        assertThat(filter.decide(null, subLogger, Level.DEBUG, null, null, null))
                .isEqualTo(FilterReply.ACCEPT);
    }

    @Test
    @DisplayName("提权到 INFO（高于 logger 级别）→ 放行 DEBUG（INFO 涵盖 DEBUG 及以下）")
    void notDowngradeBelow() {
        // Logback levelInt: TRACE=5000, DEBUG=10000, INFO=20000
        // 提权到 INFO (20000) 意味着 levelInt <= 20000 都放行
        // DEBUG.levelInt (10000) <= 20000 → ACCEPT
        MDC.put(DynamicLevelTurboFilter.MDC_DYNAMIC_LEVEL, "INFO");
        assertThat(filter.decide(null, logger, Level.DEBUG, null, null, null))
                .isEqualTo(FilterReply.ACCEPT);
    }
}