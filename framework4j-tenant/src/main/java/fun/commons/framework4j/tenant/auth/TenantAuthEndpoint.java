package fun.commons.framework4j.tenant.auth;

import fun.commons.framework4j.web.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内置认证端点(client_credentials)。路径取 {@code framework4j.tenant.auth.path}
 * (默认 {@code /api/v1/auth/token}),由自动配置代填进 accesstoken 的 exclude-path(免 token 拦截)。
 * <p>
 * {@code framework4j.tenant.auth.enabled=false} 时不注册 —— 项目自带端点,逻辑委托
 * {@link TenantAuthTemplate}(注入即用)。
 */
@RestController
public class TenantAuthEndpoint {

    private final TenantAuthTemplate authTemplate;

    public TenantAuthEndpoint(TenantAuthTemplate authTemplate) {
        this.authTemplate = authTemplate;
    }

    @PostMapping("${framework4j.tenant.auth.path:/api/v1/auth/token}")
    public ApiResponse<Map<String, Object>> token(
            @RequestParam("grant_type") String grantType,
            @RequestParam("client_id") String clientId,
            @RequestParam("client_secret") String clientSecret) {
        return authTemplate.postToken(grantType, clientId, clientSecret);
    }
}
