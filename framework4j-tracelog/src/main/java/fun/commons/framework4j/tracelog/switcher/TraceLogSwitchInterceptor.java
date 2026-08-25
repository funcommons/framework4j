package fun.commons.framework4j.tracelog.switcher;

import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.List;

/**
 * 请求拦截器：检测本地规则缓存命中 → 注入 MDC 提权标记 → 响应完成后清理。
 * <p>
 * <b>MDC 清理</b>：用 {@link MDC.MDCCloseable} 包装，避免线程复用污染。
 *
 * <p>匹配维度：
 * <ul>
 *   <li>user — 从 {@code X-User-Id} / {@code userId} 取值</li>
 *   <li>trace — 当前请求 traceId</li>
 *   <li>url — Ant 风格 URL 路径（{@link AntPathMatcher}）</li>
 *   <li>order — 从 {@code X-Order-Id} 取值</li>
 * </ul>
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §3.3.2</a>
 */
@Slf4j
@RequiredArgsConstructor
public class TraceLogSwitchInterceptor implements HandlerInterceptor {

    private static final String MDC_DYNAMIC_LEVEL = "DYNAMIC_LOG_LEVEL";
    private static final String MDC_DYNAMIC_DIMERS = "DYNAMIC_LOG_DIMERS";

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_ORDER_ID = "X-Order-Id";

    private final TraceLogProperties props;
    private final SwitchRuleCache cache;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** 用于 preHandle/afterCompletion 传递 MDCCloseable */
    private static final ThreadLocal<List<MDC.MDCCloseable>> CLOSEABLES_HOLDER = ThreadLocal.withInitial(ArrayList::new);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        List<SwitchRule> hits = matchRules(request);
        if (hits.isEmpty()) return true;

        // 取最高级别（DEBUG < TRACE）
        String targetLevel = highestLevel(hits);
        List<String> dimers = hits.stream().map(SwitchRule::toString).toList();

        // 写入 MDC（用 closeable 注册到 ThreadLocal，afterCompletion 关闭）
        List<MDC.MDCCloseable> list = CLOSEABLES_HOLDER.get();
        list.add(MDC.putCloseable(MDC_DYNAMIC_LEVEL, targetLevel));
        list.add(MDC.putCloseable(MDC_DYNAMIC_DIMERS, String.join(",", dimers)));

        log.debug("【TraceLog】请求提权: level={}, dimers={}", targetLevel, dimers);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        List<MDC.MDCCloseable> list = CLOSEABLES_HOLDER.get();
        // 倒序关闭
        for (int i = list.size() - 1; i >= 0; i--) {
            try {
                list.get(i).close();
            } catch (Exception ignore) { /* nop */ }
        }
        list.clear();
    }

    private List<SwitchRule> matchRules(HttpServletRequest request) {
        List<SwitchRule> hits = new ArrayList<>();

        // trace 维度
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            SwitchRule r = cache.matchDimension("trace", traceId);
            if (r != null) hits.add(r);
        }

        // user 维度
        String userId = request.getHeader(HEADER_USER_ID);
        if (userId != null && !userId.isBlank()) {
            SwitchRule r = cache.matchDimension("user", userId);
            if (r != null) hits.add(r);
        }

        // order 维度
        String orderId = request.getHeader(HEADER_ORDER_ID);
        if (orderId != null && !orderId.isBlank()) {
            SwitchRule r = cache.matchDimension("order", orderId);
            if (r != null) hits.add(r);
        }

        // url 维度（Ant 风格匹配）
        String uri = request.getRequestURI();
        for (String pattern : cache.valuesOf("url")) {
            if (pathMatcher.match(pattern, uri)) {
                SwitchRule r = cache.matchDimension("url", pattern);
                if (r != null) hits.add(r);
            }
        }

        return hits;
    }

    private String highestLevel(List<SwitchRule> hits) {
        String best = "DEBUG";
        for (SwitchRule r : hits) {
            if ("TRACE".equalsIgnoreCase(r.getLevel())) {
                return "TRACE";
            }
        }
        return best;
    }
}