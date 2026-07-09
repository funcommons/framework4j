package fun.commons.framework4j.demo.controller;

import fun.commons.framework4j.audit.annotation.Auditable;
import fun.commons.framework4j.cache.annotation.CacheableGet;
import fun.commons.framework4j.demo.model.UserVO;
import fun.commons.framework4j.ratelimit.annotation.RateLimit;
import fun.commons.framework4j.signature.annotation.RequiresSignature;
import fun.commons.framework4j.web.ApiResponse;
import org.springframework.web.bind.annotation.*;

/**
 * Demo Controller — 全链路展示 signature + rate-limit + cache + audit + sensitive
 */
@RestController
@RequestMapping("/v1/api")
public class DemoController {

    /**
     * 需要签名 + 限流 + 审计 + 缓存
     * <p>curl 测试见 README.md
     */
    @RequiresSignature
    @RateLimit(limit = 10, window = "1m", scope = "ip")
    @Auditable(action = "GET_USER", targetType = "user", targetIdSpel = "#id")
    @CacheableGet(prefix = "demo-user", key = "#id")
    @GetMapping("/users/{id}")
    public ApiResponse<UserVO> getUser(@PathVariable String id) {
        // 模拟 DB 查询
        UserVO user = new UserVO(id, "13812345678", "alice@example.com", "张三丰");
        return ApiResponse.success(user);
    }

    /**
     * 无需签名（用于对比测试）
     */
    @GetMapping("/public/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("OK");
    }
}
