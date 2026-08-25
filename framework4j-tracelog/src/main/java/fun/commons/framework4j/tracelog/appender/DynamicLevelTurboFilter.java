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
 *   <li>事件级别 ≥ root 级别（全局该输出的，如 INFO/WARN/ERROR）→
 *       {@link FilterReply#NEUTRAL}（走原有逻辑）</li>
 *   <li>事件级别 &lt; root 级别（DEBUG/TRACE）且 logger 不在
 *       {@code elevation.allowed-packages} 内 → DENY</li>
 *   <li>当前请求线程 MDC 存在 {@code DYNAMIC_LOG_LEVEL}（来自 Interceptor）→
 *       比较提权级别与当前日志级别：
 *       <ul>
 *         <li>提权到 DEBUG，当前 DEBUG/TRACE → ACCEPT（强制放行，绕过级别检查）</li>
 *         <li>提权到 TRACE，当前 TRACE → ACCEPT</li>
 *         <li>其他 → DENY</li>
 *       </ul>
 *   </li>
 *   <li>未提权 → DENY（拦截）</li>
 * </ol>
 *
 * <p>接入方 logback 配置要点：业务包 logger 设 DEBUG（mybatis 等 isDebugEnabled()
 * guard 型日志才会生成事件，本过滤器才有机会拦截/放行）；未提权的 DEBUG 事件由
 * 本过滤器 DENY 拦截，不会全量输出。本过滤器由
 * {@link fun.commons.framework4j.tracelog.config.TraceLogBeansConfig} 编程式注册，
 * 不要在 logback-spring.xml 中声明。
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
        // 1. 基准是 root 级别而非 logger 自身级别:
        //    业务包 logger 需设 DEBUG (否则 mybatis 等 isDebugEnabled() guard 型日志
        //    根本不生成事件, 提权无从谈起); 若以 logger 自身级别为基准, 未提权的
        //    DEBUG 事件也会 NEUTRAL 放行 → 全量输出, 动态提权失效。
        //    以 root 级别为基准: 全局该输出的 (INFO+) 走原有逻辑, 低于全局级别的
        //    (DEBUG/TRACE) 由本过滤器按提权状态决定 ACCEPT/DENY。
        Logger root = logger.getLoggerContext().getLogger(Logger.ROOT_LOGGER_NAME);
        Level globalLevel = root.getLevel() != null ? root.getLevel() : Level.INFO;
        if (level.isGreaterOrEqual(globalLevel)) {
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