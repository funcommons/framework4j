package fun.commons.framework4j.demo.controller;

import fun.commons.framework4j.web.ApiResponse;
import fun.commons.framework4j.web.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TraceLog 演示 Controller。
 * <p>
 * 演示：
 * <ul>
 *   <li>DEBUG/TRACE 日志输出（默认全局 INFO 拦截，提权后放行）</li>
 *   <li>不同方法产生不同 traceId，便于按 traceId 查询</li>
 *   <li>异常日志（含堆栈）</li>
 * </ul>
 *
 * <p>使用方式：
 * <ol>
 *   <li>访问 {@code GET /api/demo/order?orderId=xxx} 触发日志</li>
 *   <li>从响应头 {@code X-Trace-Id} 取 traceId</li>
 *   <li>访问 {@code http://localhost:8080/tracelog.html} 查询日志</li>
 *   <li>在控制台"开关"面板开启 user 维度 DEBUG → 该用户后续请求会输出 DEBUG 日志</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/api/demo")
public class TraceLogDemoController {

    private static final Logger DEMO_LOG = LoggerFactory.getLogger(TraceLogDemoController.class);

    @GetMapping("/order")
    public ApiResponse<String> createOrder(@RequestParam(defaultValue = "OD001") String orderId) {
        String traceId = TraceContext.getTraceId();
        DEMO_LOG.info("订单创建入口: orderId={}, traceId={}", orderId, traceId);
        DEMO_LOG.debug("订单参数校验: orderId={}, amount=199.00", orderId);
        DEMO_LOG.trace("订单详细参数: orderId={}, items=[skuA, skuB], address=xxx", orderId);

        try {
            // 模拟业务逻辑
            DEMO_LOG.debug("准备调用支付服务...");
            DEMO_LOG.info("订单已创建: orderId={}, amount=199.00, status=PENDING", orderId);
            return ApiResponse.success("订单创建成功: " + orderId, traceId);
        } catch (Exception e) {
            DEMO_LOG.error("订单创建失败: orderId={}", orderId, e);
            throw e;
        }
    }

    @GetMapping("/slow")
    public ApiResponse<String> slowMethod() {
        String traceId = TraceContext.getTraceId();
        DEMO_LOG.info("慢方法开始: traceId={}", traceId);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        DEMO_LOG.debug("慢方法结束: traceId={}", traceId);
        return ApiResponse.success("slow done", traceId);
    }

    @GetMapping("/error")
    public ApiResponse<String> errorMethod() {
        String traceId = TraceContext.getTraceId();
        DEMO_LOG.info("错误方法入口: traceId={}", traceId);
        DEMO_LOG.error("业务异常: traceId={}", traceId, new IllegalStateException("demo exception for testing"));
        return ApiResponse.success("error done", traceId);
    }
}