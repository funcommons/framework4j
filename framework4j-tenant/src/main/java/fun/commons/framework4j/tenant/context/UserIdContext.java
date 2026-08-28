package fun.commons.framework4j.tenant.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * X-User-Id 请求头解析 → ThreadLocal(租户设计 §5.2:必填但不鉴权、不进 JWT)。
 * <p>
 * <strong>红线</strong>:此值<strong>永不鉴权</strong> —— 它只是调用方声明的「业务用户标识」,
 * 供审计/日志/业务追踪用;任何鉴权决策(能不能做)都不得依赖它,只能依赖 token claim(tenant_id)。
 * 运行时断言:{@link #currentUserId()} 返回值不得作为权限判断输入(契约文档化,防误用)。
 * <p>
 * 由 {@link UserIdContextInterceptor} 按请求解析并清理(Tomcat 线程复用,不清理 = 串用户)。
 */
public final class UserIdContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    public static final String HEADER = "X-User-Id";

    private UserIdContext() {
    }

    /**
     * 当前请求声明的用户 ID(可能为 null —— 必填由网关/前置校验保证,本类不强制)。
     * <strong>返回值为不可信输入,不得用于鉴权。</strong>
     */
    public static String currentUserId() {
        return HOLDER.get();
    }

    static void set(String userId) {
        HOLDER.set(userId);
    }

    static void clear() {
        HOLDER.remove();
    }

    /**
     * MVC 拦截器:解析 X-User-Id 入 ThreadLocal,请求结束清理。
     * 注册与守卫同链(LOWEST_PRECEDENCE 后,业务 handler 前)。
     */
    public static class UserIdContextInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            String userId = request.getHeader(HEADER);
            if (userId != null && !userId.isEmpty()) {
                UserIdContext.set(userId);
            }
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                    Object handler, Exception ex) {
            UserIdContext.clear();
        }
    }
}
