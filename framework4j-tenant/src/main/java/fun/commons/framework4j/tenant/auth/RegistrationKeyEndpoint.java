package fun.commons.framework4j.tenant.auth;

import fun.commons.framework4j.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 注册码开放域端点(可选,{@code framework4j.tenant.registration-key.enabled=true} 才注册)。
 * 参数读取走 {@link TenantAuthEndpoint#formParams}(body 包装兼容),不用 @RequestParam。
 */
@RestController
public class RegistrationKeyEndpoint {

    private final RegistrationKeyService service;

    public RegistrationKeyEndpoint(RegistrationKeyService service) {
        this.service = service;
    }

    @PostMapping("/open/api/v1/tenants/register")
    public ApiResponse<Map<String, Object>> register(HttpServletRequest request) {
        Map<String, String> params = TenantAuthEndpoint.formParams(request);
        return service.register(
                params.get("registration_key"), params.get("tenant_name"), params.get("email"));
    }
}
