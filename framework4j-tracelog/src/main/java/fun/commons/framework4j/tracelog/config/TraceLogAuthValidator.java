package fun.commons.framework4j.tracelog.config;

import fun.commons.framework4j.tracelog.query.SwitchRequest;

/**
 * 控制台 API 鉴权 SPI（业务方必须实现）。
 * <p>
 * 框架未绑定任何鉴权框架，业务方按需实现：
 * <ul>
 *   <li>生产建议接入 {@code framework4j-accesstoken} + RBAC</li>
 *   <li>未配置则启动 fail-fast（{@link TraceLogFailureAnalyzer}）</li>
 * </ul>
 *
 * <p>实现示例（接入 accesstoken）：
 * <pre>{@code
 * @Component("traceLogAuthValidator")
 * public class AccessTokenTraceLogAuthValidator implements TraceLogAuthValidator {
 *     @Override
 *     public boolean canQuery(String operatorId, String tenantId) {
 *         return accessTokenService.hasPermission(operatorId, "trace:log:query", tenantId);
 *     }
 * }
 * }</pre>
 *
 * @see TraceLogProperties.Api#requireAuth
 * @see TraceLogFailureAnalyzer
 */
public interface TraceLogAuthValidator {

    /**
     * 查询接口鉴权
     *
     * @param operatorId ��作者 ID（来自 TokenContext 或 Header）
     * @param tenantId   当前租户 ID（多租户模式下非空）
     * @return true=允许查询
     */
    boolean canQuery(String operatorId, String tenantId);

    /**
     * 控制接口鉴权（开启/关闭开关）
     */
    boolean canOpenSwitch(String operatorId, String tenantId, SwitchRequest req);

    /**
     * 导出接口鉴权
     */
    boolean canExport(String operatorId, String tenantId);
}