package fun.commons.framework4j.tenant.auth;

import fun.commons.framework4j.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 内置认证端点(client_credentials)。路径取 {@code framework4j.tenant.auth.path}
 * (默认 {@code /api/v1/auth/token}),由自动配置代填进 accesstoken 的 exclude-path(免 token 拦截)。
 * <p>
 * {@code framework4j.tenant.auth.enabled=false} 时不注册 —— 项目自带端点,逻辑委托
 * {@link TenantAuthTemplate}(注入即用)。
 * <p>
 * 参数读取不用 {@code @RequestParam}:上游链路(framework4j-web CachedBodyRequestWrapper 等)
 * 可能在 Tomcat form 解析前消费请求体 → 参数 map 为空。改为 query 优先 + 手动解析 form body,
 * 两种提交形态兼容(与 benefit4j 原实现同源)。
 */
@RestController
public class TenantAuthEndpoint {

    private final TenantAuthTemplate authTemplate;

    public TenantAuthEndpoint(TenantAuthTemplate authTemplate) {
        this.authTemplate = authTemplate;
    }

    @PostMapping("${framework4j.tenant.auth.path:/api/v1/auth/token}")
    public ApiResponse<Map<String, Object>> token(HttpServletRequest request) {
        Map<String, String> params = FormParams.of(request);
        return authTemplate.postToken(
                params.get("grant_type"), params.get("client_id"), params.get("client_secret"));
    }

    /**
     * form/query 参数容错读取:query 优先,body(form-urlencoded)兜底。
     * 供本模块端点共用(认证/注册码)。
     */
    static Map<String, String> formParams(HttpServletRequest request) {
        return FormParams.of(request);
    }

    private static final class FormParams {

        private FormParams() {
        }

        static Map<String, String> of(HttpServletRequest request) {
            Map<String, String> params = new LinkedHashMap<>();
            // ① form body(content-type 为 x-www-form-urlencoded 时手动解析,兼容 body 已被包装缓存的场景)
            String contentType = request.getContentType();
            if (contentType != null && contentType.startsWith("application/x-www-form-urlencoded")) {
                parsePairs(readBody(request), params);
            }
            // ② query/form 参数(getParameter 对 query string 恒有效)
            request.getParameterMap().forEach((k, v) -> {
                if (v != null && v.length > 0 && v[0] != null) {
                    params.putIfAbsent(k, v[0]);
                }
            });
            return params;
        }

        private static void parsePairs(String body, Map<String, String> out) {
            if (body == null || body.isEmpty()) {
                return;
            }
            for (String pair : body.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    out.put(urlDecode(kv[0]), urlDecode(kv[1]));
                }
            }
        }

        private static String readBody(HttpServletRequest request) {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8));
                return reader.lines().collect(Collectors.joining());
            } catch (Exception e) {
                return null;   // body 已被消费/不可读 → 回退 query 参数
            }
        }

        private static String urlDecode(String s) {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        }
    }
}
