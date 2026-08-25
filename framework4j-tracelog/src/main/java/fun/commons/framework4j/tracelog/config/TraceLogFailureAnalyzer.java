package fun.commons.framework4j.tracelog.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;

/**
 * 启动失败分析器：在配置了强制鉴权但未提供 {@link TraceLogAuthValidator} 时给出明确错误信息。
 * <p>
 * 避免启动失败时只看到 Spring 内部的 {@code NoSuchBeanDefinitionException}，业务方不知道该配什么。
 *
 * @see TraceLogProperties.Api#requireAuth
 */
@Slf4j
public class TraceLogFailureAnalyzer implements FailureAnalyzer {

    @Override
    public FailureAnalysis analyze(Throwable failure) {
        // 仅在 NoSuchBeanDefinitionException 时介入
        if (!(failure instanceof NoSuchBeanDefinitionException ex)) {
            return null;
        }

        // 仅关心 TraceLogAuthValidator 类型的 Bean 缺失
        if (!TraceLogAuthValidator.class.getName().equals(ex.getBeanType())) {
            return null;
        }

        log.error("【TraceLog】TraceLogAuthValidator Bean 未配置，启动失败");

        String description = String.format(
                "framework4j.tracelog.api.require-auth=true 但未找到 %s Bean。"
                        + "这是一个 SPI 接口，业务方必须实现并注册为 Spring Bean。",
                TraceLogAuthValidator.class.getSimpleName());

        String action = """
                修复方法:
                  1. 创建 TraceLogAuthValidator 实现类:
                     @Component("traceLogAuthValidator")
                     public class MyAuthValidator implements TraceLogAuthValidator {
                         public boolean canQuery(String operatorId, String tenantId) { ... }
                         public boolean canOpenSwitch(...) { ... }
                         public boolean canExport(...) { ... }
                     }

                  2. 或关闭强制鉴权（仅用于本地调试，**生产禁止**）:
                     framework4j:
                       tracelog:
                         api:
                           require-auth: false

                  3. 推荐接入 framework4j-accesstoken，按 RBAC 校验 trace:log:* 权限。
                """;

        return new FailureAnalysis(description, action, failure);
    }
}