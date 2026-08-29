package fun.commons.framework4j.tenant.interceptor;

import fun.commons.framework4j.accesstoken.context.TokenContext;
import fun.commons.framework4j.tenant.annotation.PlatformDomain;
import fun.commons.framework4j.tenant.annotation.TenantDomain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 双面守卫行为矩阵(租户设计 §5.3 方案 B,泛化自 benefit4j TenantSecurityIT):
 * 同一 claim 两面 —— 平台认 0 / 租户拒 0;claim 缺失默认拒绝;未标注的 controller 不管辖。
 */
class DomainGuardInterceptorTest {

    @RestController
    static class RuntimeController {          // 租户域
        @GetMapping("/runtime/issue")
        public String issue() {
            return "ok";
        }
    }

    @RestController
    @TenantDomain
    static class TenantRuntimeController {
        @GetMapping("/t/runtime")
        public String runtime() {
            return "ok";
        }
    }

    @RestController
    @PlatformDomain
    static class PlatformOpsController {
        @GetMapping("/t/platform")
        public String platform() {
            return "ok";
        }
    }

    @RestController
    @PlatformDomain
    @TenantDomain
    static class ContradictoryController {
        @GetMapping("/t/both")
        public String both() {
            return "ok";
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new RuntimeController(), new TenantRuntimeController(),
                    new PlatformOpsController(), new ContradictoryController())
            .addInterceptors(new DomainGuardInterceptor())
            .build();

    @AfterEach
    void clear() {
        TokenContext.clear();
    }

    @Test
    @DisplayName("租户域:真实租户(tenant_id=5)放行;字符串 claim 同样放行")
    void tenantDomain_realTenantPasses() throws Exception {
        TokenContext.set("APP", Map.of("tenant_id", 5L));
        mockMvc.perform(get("/t/runtime")).andExpect(status().isOk());

        TokenContext.set("APP", Map.of("tenant_id", "5"));   // 字符串形态(benefit4j 兼容)
        mockMvc.perform(get("/t/runtime")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("租户域:平台身份(tenant_id=0)403 —— 平台身份不是记账主体(§5.3)")
    void tenantDomain_platformIdentityRejected() throws Exception {
        TokenContext.set("APP", Map.of("tenant_id", 0L));
        mockMvc.perform(get("/t/runtime"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("记账主体")));
    }

    @Test
    @DisplayName("租户域:claim 缺失/非法 → 403(默认拒绝)")
    void tenantDomain_missingOrInvalidClaimRejected() throws Exception {
        mockMvc.perform(get("/t/runtime")).andExpect(status().isForbidden());   // 缺失

        TokenContext.set("APP", Map.of("tenant_id", "abc"));                    // 非法
        mockMvc.perform(get("/t/runtime")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("平台域:平台身份(tenant_id=0)放行")
    void platformDomain_platformIdentityPasses() throws Exception {
        TokenContext.set("APP", Map.of("tenant_id", 0L));
        mockMvc.perform(get("/t/platform")).andExpect(status().isOk());

        TokenContext.set("APP", Map.of("tenant_id", "0"));      // 字符串形态
        mockMvc.perform(get("/t/platform")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("平台域:真实租户(tenant_id=5)403 —— 越权防线(benefit4j P0 缺口的泛化)")
    void platformDomain_tenantIdentityRejected() throws Exception {
        TokenContext.set("APP", Map.of("tenant_id", 5L));
        mockMvc.perform(get("/t/platform"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("未标注的 controller 一律放行(模块不扩大管辖面)")
    void unannotated_alwaysPasses() throws Exception {
        TokenContext.set("APP", Map.of("tenant_id", 0L));
        mockMvc.perform(get("/runtime/issue")).andExpect(status().isOk());

        TokenContext.set("APP", Map.of("tenant_id", 5L));
        mockMvc.perform(get("/runtime/issue")).andExpect(status().isOk());

        TokenContext.clear();
        mockMvc.perform(get("/runtime/issue")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("同一 controller 双标注 → 403(三域互斥)")
    void bothAnnotations_rejected() throws Exception {
        TokenContext.set("APP", Map.of("tenant_id", 0L));
        mockMvc.perform(get("/t/both")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("平台身份取值可配(framework4j.tenant.platform.tenant-id):非默认 0 时守卫跟随")
    void configurablePlatformTenantId() throws Exception {
        MockMvc customMvc = MockMvcBuilders
                .standaloneSetup(new TenantRuntimeController(), new PlatformOpsController())
                .addInterceptors(new DomainGuardInterceptor(999L))   // 平台身份 = 999
                .build();

        TokenContext.set("APP", Map.of("tenant_id", 999L));
        customMvc.perform(get("/t/platform")).andExpect(status().isOk());       // 999 → 平台域放行
        customMvc.perform(get("/t/runtime")).andExpect(status().isForbidden()); // 999 → 租户域拒

        TokenContext.set("APP", Map.of("tenant_id", 0L));
        customMvc.perform(get("/t/platform")).andExpect(status().isForbidden()); // 0 不再是平台身份
    }
}
