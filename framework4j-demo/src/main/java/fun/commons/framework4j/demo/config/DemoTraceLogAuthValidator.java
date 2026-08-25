package fun.commons.framework4j.demo.config;

import fun.commons.framework4j.tracelog.config.TraceLogAuthValidator;
import fun.commons.framework4j.tracelog.query.SwitchRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Demo 模块的 TraceLog 鉴权实现（无 accesstoken 时宽松放行）。
 * <p>
 * 生产环境务必接入 {@code framework4j-accesstoken}，按 RBAC 严格校验。
 *
 * @author framework4j-demo
 */
@Slf4j
@Component("traceLogAuthValidator")
public class DemoTraceLogAuthValidator implements TraceLogAuthValidator {

    @Override
    public boolean canQuery(String operatorId, String tenantId) {
        log.debug("[DemoAuth] query: operator={}, tenant={}", operatorId, tenantId);
        return true;
    }

    @Override
    public boolean canOpenSwitch(String operatorId, String tenantId, SwitchRequest req) {
        log.info("[DemoAuth] switch: operator={}, tenant={}, type={}, value={}",
                operatorId, tenantId, req.getType(), req.getValue());
        return true;
    }

    @Override
    public boolean canExport(String operatorId, String tenantId) {
        log.debug("[DemoAuth] export: operator={}, tenant={}", operatorId, tenantId);
        return true;
    }
}