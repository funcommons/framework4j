package fun.commons.framework4j.tenant.auth;

import fun.commons.framework4j.web.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 注册码开放域端点(可选,{@code framework4j.tenant.registration-key.enabled=true} 才注册)。
 * 平台域发码走 SecretService/RegistrationKeyService 直接调用(管理面,无公开端点)。
 */
@RestController
public class RegistrationKeyEndpoint {

    private final RegistrationKeyService service;

    public RegistrationKeyEndpoint(RegistrationKeyService service) {
        this.service = service;
    }

    @PostMapping("/open/api/v1/tenants/register")
    public ApiResponse<Map<String, Object>> register(
            @RequestParam("registration_key") String registrationKey,
            @RequestParam("tenant_name") String tenantName,
            @RequestParam(value = "email", required = false) String email) {
        return service.register(registrationKey, tenantName, email);
    }
}
