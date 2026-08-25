package fun.commons.framework4j.tracelog.util;

import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * * 多租户 Key 解析器。
 * * <p>
 * * 配置 {@code framework4j.tracelog.tenant.key-spel} 为 SpEL 表达式（如 {@code #request.getHeader('X-Tenant-Id')}），
 * * 启用多租户隔离时 Redis Key 加 {@code <tenantId>:} 前缀。
 * *
 * * <p><b>典型用法</b>：
 * * <ul>
 * *   <li>{@code #request.getHeader('X-Tenant-Id')} — 从 HTTP Header 取（最简单）</li>
 * *   <li>{@code #userInfo.tenantId} — 从登录上下文（{@code userInfo} 是某个 Bean）</li>
 * *   <li>{@code @tenantService.currentTenantId()} — 调用 Bean 方法</li>
 * * </ul>
 * *
 * * <p><b>失败策略</b>：tenant.enabled=true 但未配置 key-spel → 启动 fail-fast；
 * * 解析结果为 null → 当次请求视为无租户（不写入 Redis）。
 * *
 * * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §3.5.4</a>
 */
@Slf4j
public class TenantKeyResolver {

    private final TraceLogProperties props;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final Expression expression;
    private final org.springframework.beans.factory.BeanFactory beanFactory;

    public TenantKeyResolver(TraceLogProperties props, org.springframework.beans.factory.BeanFactory beanFactory) {
        this.props = props;
        this.beanFactory = beanFactory;

        if (!props.getTenant().isEnabled()) {
            this.expression = null;
        } else if (props.getTenant().getKeySpel() == null || props.getTenant().getKeySpel().isBlank()) {
            throw new IllegalStateException(
                    "framework4j.tracelog.tenant.enabled=true 但未配置 tenant.key-spel");
        } else {
            this.expression = parser.parseExpression(props.getTenant().getKeySpel());
            log.info("【TraceLog】TenantKeyResolver 启用: spel='{}'", props.getTenant().getKeySpel());
        }
    }

    /**
     * 解析当前请求的租户 Key。
     *
     * @return tenantId 或 null（启用多租户但无值时拒绝写入）
     */
    public String currentTenant() {
        if (!props.getTenant().isEnabled()) return null;
        if (expression == null) return null;

        HttpServletRequest request = currentRequest();
        if (request == null) return null;

        EvaluationContext ctx = new StandardEvaluationContext();
        ((StandardEvaluationContext) ctx).setBeanResolver(new BeanFactoryResolver(beanFactory));
        ctx.setVariable("request", request);
        ctx.setVariable("headerName", props.getTenant().getHeaderName());

        try {
            Object value = expression.getValue(ctx);
            if (value == null) return null;
            return value.toString();
        } catch (Exception e) {
            log.warn("【TraceLog】TenantKeyResolver 解析失败: spel='{}', err={}",
                    props.getTenant().getKeySpel(), e.getMessage());
            return null;
        }
    }

    private static HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception e) {
            return null;
        }
    }
}