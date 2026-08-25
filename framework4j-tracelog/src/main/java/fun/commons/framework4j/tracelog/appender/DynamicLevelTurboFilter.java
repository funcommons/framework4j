package fun.commons.framework4j.tracelog.appender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import org.slf4j.MDC;
import org.slf4j.Marker;

import java.util.List;

/**
 * 动态级别提权 TurboFilter。
 * <p>
 * 工作流程：
 * <ol>
 *   <li>全局配置 INFO/WARN/ERROR → {@link FilterReply#NEUTRAL}（走原有逻辑）</li>
 *   <li>全局配置 DEBUG 但当前是 INFO → NEUTRAL</li>
 *   <li>当前请求线程 MDC 存在 {@code DYNAMIC_LOG_LEVEL}（来自 Interceptor）→
 *       比较提权级别与当前日志级别：
 *       <ul>
 *         <li>提权到 DEBUG，当前 DEBUG/TRACE → ACCEPT（强制放）</li>
 *         <li>提权到 TRACE，当前 TRACE → ACCEPT</li>
 *         <li>其他 → DENY</li>
 *       </ul>
 *   </li>
 *   <li>未提权 → DENY（拦截）</li>
 * </ol>
 *
 * <p>作用域限制：通过 {@code elevation.allowed-packages}（默认 {@code com.yourcompany}）
 * 配置仅提权业务包路径，第三方库保持原级别。
 *
 * <p>Logback 配置：
 * <pre>{@code
 * <configuration>
 *   <turboFilter class="fun.commons.framework4j.tracelog.appender.DynamicLevelTurboFilter"/>
 *   ...
 * </configuration>
 * }</pre>
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §3.3.3</a>
 */
public class DynamicLevelTurboFilter extends TurboFilter {

    public static final String MDC_DYNAMIC_LEVEL = "DYNAMIC_LOG_LEVEL";

    private List<String> allowedPackages = List.of("com.yourcompany");
    private Level defaultElevation = Level.DEBUG;

    public DynamicLevelTurboFilter() {}

    public DynamicLevelTurboFilter(TraceLogProperties props) {
        if (props != null) {
            this.allowedPackages = props.getElevation().getAllowedPackages();
            this.defaultElevation = props.getElevation().getDefaultLevel();
        }
    }

    public void setAllowedPackages(List<String> allowedPackages) {
        this.allowedPackages = allowedPackages == null ? List.of() : allowedPackages;
    }

    public void setDefaultElevation(String level) {
        this.defaultElevation = Level.toLevel(level, Level.DEBUG);
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level,
                              String format, Object[] params, Throwable t) {
        // 1. 全局配置能放行 → 走原有逻辑
        if (level.isGreaterOrEqual(logger.getEffectiveLevel())) {
            return FilterReply.NEUTRAL;
        }

        // 2. 包路径作用域限制
        if (!isAllowed(logger.getName())) {
            return FilterReply.DENY;
        }

        // 3. 检查 MDC 提权标记
        String dynamicLevel = MDC.get(MDC_DYNAMIC_LEVEL);
        if (dynamicLevel == null) {
            return FilterReply.DENY;
        }

        // 4. 级别比较（Logback 语义：levelInt 越小越详细）
        // 提权到 DEBUG (10000) → TRACE (5000) 和 DEBUG 都放行
        // 提权到 TRACE (5000)  → 仅 TRACE 放行（DEBUG 不放行）
        Level targetLevel = Level.toLevel(dynamicLevel, defaultElevation);
        if (level.levelInt <= targetLevel.levelInt) {
            return FilterReply.ACCEPT;
        }
        return FilterReply.DENY;
    }

    private boolean isAllowed(String loggerName) {
        if (loggerName == null) return false;
        for (String pkg : allowedPackages) {
            if (loggerName.equals(pkg) || loggerName.startsWith(pkg + ".")) {
                return true;
            }
        }
        return false;
    }
}