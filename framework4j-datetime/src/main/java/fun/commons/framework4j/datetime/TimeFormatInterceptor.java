package fun.commons.framework4j.datetime;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 时间格式化拦截器
 *
 * <p>优化后的时间格式化拦截器，负责：
 * <ul>
 *   <li>检测 @LocalTimeFormat 注解</li>
 *   <li>设置线程本地的时间格式化状态</li>
 *   <li>管理状态的生命周期</li>
 * </ul></p>
 *
 * <p>通过集成 TimeFormatStateHolder，提供高性能的注解检测和缓存机制。</p>
 *
 * @since 1.0.0
 */
@Slf4j
public class TimeFormatInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;

            // 使用 TimeFormatStateHolder 进行高效的注解检测
            TimeFormatState state = TimeFormatStateHolder.detectTimeFormatState(handlerMethod);

            // 设置线程本地状态
            TimeFormatStateHolder.setState(state);

            // 调试日志
            if (log.isTraceEnabled()) {
                log.trace("TimeFormatState set for {}: useLocalFormat={}, fromAnnotation={}",
                    handlerMethod.getBean().getClass().getSimpleName(),
                    state.isUseLocalFormat(),
                    state.isFromAnnotation());
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 务必清理 ThreadLocal，防止内存泄漏或线程污染
        try {
            TimeFormatStateHolder.clear();
        } catch (Exception e) {
            // 记录清理异常，但不影响请求处理
            log.warn("Failed to clear TimeFormatState", e);
        }
    }
}