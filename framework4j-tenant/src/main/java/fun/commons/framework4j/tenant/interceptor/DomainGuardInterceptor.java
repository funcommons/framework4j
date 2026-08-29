package fun.commons.framework4j.tenant.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.tenant.annotation.PlatformDomain;
import fun.commons.framework4j.tenant.annotation.TenantDomain;
import fun.commons.framework4j.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 三域身份守卫拦截器(租户设计 §5.3 方案 B「双面守卫」的框架化):
 * 同一 claim(tenant_id)的两面 —— {@code @PlatformDomain} 认 0,{@code @TenantDomain} 拒 0。
 * <p>
 * 未标注的 Controller 一律放行(模块只管注解了的域,不扩大管辖面);
 * claim 缺失/非法一律拒绝(默认拒绝)。
 * <p>
 * 响应契约:身份不符 403 + ApiResponse JSON(不依赖项目异常 handler,前端拿到统一信封);
 * 401(令牌缺失/型别不符)由 accesstoken 模块的 TokenInterceptor 负责,先于本拦截器执行。
 */
public class DomainGuardInterceptor implements HandlerInterceptor {

    /** token claim 键:租户身份(§5.3 双面守卫的唯一事实源) */
    public static final String CLAIM_TENANT_ID = "tenant_id";

    /** 平台身份的 tenant_id 取值(默认 0,与 framework4j.tenant.platform.tenant-id 对齐) */
    private final long platformTenantId;

    /**
     * 默认租户(单租户模式,framework4j.tenant.default-tenant-id):
     * 请求无 tenant_id claim 时按此租户放行租户域;null = 多租户模式(无 claim 即 403)。
     * claim 存在时永远优先;claim 非法(不可解析)仍拒绝 —— 不用默认值掩盖认证问题。
     */
    private final Long defaultTenantId;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public DomainGuardInterceptor() {
        this(0L, null);
    }

    public DomainGuardInterceptor(long platformTenantId) {
        this(platformTenantId, null);
    }

    public DomainGuardInterceptor(long platformTenantId, Long defaultTenantId) {
        this.platformTenantId = platformTenantId;
        this.defaultTenantId = defaultTenantId;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Class<?> beanType = handlerMethod.getBeanType();
        boolean platform = AnnotatedElementUtils.hasAnnotation(beanType, PlatformDomain.class);
        boolean tenant = AnnotatedElementUtils.hasAnnotation(beanType, TenantDomain.class);
        if (!platform && !tenant) {
            return true;
        }
        if (platform && tenant) {
            return reject(response, "同一 Controller 不可同时标注 @PlatformDomain 与 @TenantDomain(三域互斥,租户设计 §4)");
        }
        Long tenantId = readTenantIdClaim();
        if (tenantId == null) {
            if (tenant && defaultTenantId != null && !hasClaim()) {
                return true;   // 单租户模式:租户域无 claim → 默认租户放行(claim 非法不在此列)
            }
            return reject(response, "token 缺少有效的 tenant_id claim(tenant_id=0 平台身份 / >0 租户身份)");
        }
        if (platform) {
            if (tenantId != platformTenantId) {
                return reject(response, "平台域需要平台身份(tenant_id=" + platformTenantId
                        + "),租户身份(tenant_id=" + tenantId + ")不可访问");
            }
            return true;
        }
        if (tenantId <= 0L || tenantId == platformTenantId) {
            return reject(response, "租户域需要真实租户身份(tenant_id>0),平台身份(tenant_id="
                    + platformTenantId + ")不可作为记账主体(§5.3/§6.2)");
        }
        return true;
    }

    /** claim 是否存在(区分「无 claim」与「claim 非法」—— 单租户模式只对前者放行) */
    private static boolean hasClaim() {
        return fun.commons.framework4j.accesstoken.context.TokenContext.getClaim(CLAIM_TENANT_ID) != null;
    }

    /** claim 解析容忍 Number/String(benefit4j 兼容形态);缺失/非法 → null(默认拒绝) */
    private static Long readTenantIdClaim() {
        Object claim = fun.commons.framework4j.accesstoken.context.TokenContext.getClaim(CLAIM_TENANT_ID);
        if (claim instanceof Number number) {
            return number.longValue();
        }
        if (claim != null) {
            try {
                return Long.parseLong(String.valueOf(claim));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private boolean reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(403, message)));
        return false;
    }
}
