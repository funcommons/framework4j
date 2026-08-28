package fun.commons.framework4j.demo.tenant;

import fun.commons.framework4j.tenant.annotation.TenantDomain;
import fun.commons.framework4j.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 三域守卫演示:租户域端点。
 * <ul>
 *   <li>无 token → 401(accesstoken TokenInterceptor 先拦)</li>
 *   <li>token 但 tenant_id=0(平台身份)→ 403(DomainGuardInterceptor,「平台身份不是记账主体」)</li>
 *   <li>token 且 tenant_id&gt;0 → 200</li>
 * </ul>
 * 行为矩阵由模块测试 DomainGuardInterceptorTest 钉死;本端点验证拦截器真实注册进 MVC 链。
 */
@RestController
@TenantDomain
public class TenantDemoController {

    @GetMapping("/tenant-demo/runtime")
    public ApiResponse<String> runtime() {
        return ApiResponse.success("tenant-domain-ok");
    }
}
