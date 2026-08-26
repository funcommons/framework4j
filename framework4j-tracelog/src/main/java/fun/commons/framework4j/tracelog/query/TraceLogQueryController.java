package fun.commons.framework4j.tracelog.query;

import fun.commons.framework4j.tracelog.config.TraceLogAuthValidator;
import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import fun.commons.framework4j.tracelog.store.TraceLogStore;
import fun.commons.framework4j.tracelog.switcher.SwitchRule;
import fun.commons.framework4j.tracelog.switcher.SwitchRateLimiter;
import fun.commons.framework4j.tracelog.switcher.SwitchStreamsListener;
import fun.commons.framework4j.tracelog.util.TenantKeyResolver;
import fun.commons.framework4j.web.ApiResponse;
import fun.commons.framework4j.web.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 动态追踪日志查询 / 控制 / 导出 API。
 * <p>
 * 端点：
 * <ul>
 *   <li>{@code GET /api/logs/trace/{traceId}} — 按 traceId 查询</li>
 *   <li>{@code POST /api/logs/switch} — 开启动态提权开关</li>
 *   <li>{@code GET /api/logs/trace/{traceId}/export} — 导出</li>
 * </ul>
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §3.5</a>
 */
@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class TraceLogQueryController {

    private final TraceLogProperties props;
    private final TraceLogStore store;
    private final LogExporter exporter;
    private final StringRedisTemplate redis;
    private final SwitchRateLimiter switchRateLimiter;
    private final TenantKeyResolver tenantKeyResolver;
    /** 可选的 Streams 监听器（仅 transport=streams 时实际使用） */
    private final ObjectProvider<SwitchStreamsListener> streamsListenerProvider;
    /**
     * 业务方注入的鉴权实现（ObjectProvider 避免硬依赖导致启动失败——若强制开启鉴权则由
     * {@link fun.commons.framework4j.tracelog.config.TraceLogFailureAnalyzer} 提示）。
     */
    private final ObjectProvider<TraceLogAuthValidator> authValidatorProvider;

    // ==================== 查询 ====================

    @GetMapping("/trace/{traceId}")
    public ApiResponse<List<LogDto>> queryLogs(
            @PathVariable String traceId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {

        requirePermission(request, validator -> validator.canQuery(currentOperator(request), currentTenant(request)));

        String tenantPrefix = tenantKeyResolver.currentTenant();
        List<String> raw = store.rangeTraceLogs(traceId, 0, props.getApi().getMaxReturnLogs() - 1, tenantPrefix);
        if (raw == null || raw.isEmpty()) {
            return ApiResponse.success(List.of(), TraceContext.getTraceId());
        }

        List<LogDto> result = new ArrayList<>(raw.size());
        for (String json : raw) {
            LogDto dto = parseLine(json);
            if (!matchLevel(dto, level)) continue;
            if (!matchKeyword(dto, keyword)) continue;
            result.add(dto);
        }
        return ApiResponse.success(result, TraceContext.getTraceId());
    }

    // ==================== 开关 ====================

    @PostMapping("/switch")
    public ApiResponse<Void> openSwitch(@Valid @RequestBody SwitchRequest req,
                                        HttpServletRequest request) {

        requirePermission(request, validator -> validator.canOpenSwitch(
                currentOperator(request), currentTenant(request), req));

        // 频控
        if (!switchRateLimiter.tryAcquire(req.getType() + ":" + req.getValue())) {
            throw new IllegalStateException("同维度每分钟最多开启 " +
                    props.getApi().getSwitchRateLimitPerMinute() + " 次");
        }

        // TTL 上限强制
        long ttl = Math.min(req.getTtlSeconds(), props.getSync().getMaxTtlSeconds());
        // 用 SwitchRule 归一化后的 type（小写）拼 key, 保证与匹配侧/resync 一致
        SwitchRule rule = new SwitchRule(req.getType(), req.getValue(), req.getLevel());
        redis.opsForValue().set(rule.redisKey(), req.getLevel(), Duration.ofSeconds(ttl));

        // Pub/Sub 或 Streams 广播
        SwitchStreamsListener streams = streamsListenerProvider.getIfAvailable();
        if ("streams".equalsIgnoreCase(props.getSync().getTransport()) && streams != null) {
            streams.publish(rule);
        } else {
            redis.convertAndSend(props.getSync().getChannel(), rule.pubSubPayload());
        }

        log.info("【TraceLog】开关开启: type={}, value={}, level={}, ttl={}s",
                req.getType(), req.getValue(), req.getLevel(), ttl);
        return ApiResponse.success();
    }

    // ==================== 导出 ====================

    @GetMapping("/trace/{traceId}/export")
    public void exportLogs(@PathVariable String traceId,
                           @RequestParam(defaultValue = "txt") String format,
                           HttpServletRequest request,
                           HttpServletResponse response) throws IOException {

        if (!props.getExport().isEnabled()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        requirePermission(request, validator -> validator.canExport(
                currentOperator(request), currentTenant(request)));

        String tenantPrefix = tenantKeyResolver.currentTenant();
        exporter.export(traceId, format, response, tenantPrefix);
    }

    // ==================== 辅助 ====================

    private void requirePermission(HttpServletRequest request, PermissionCheck check) {
        if (!props.getApi().isRequireAuth()) return;
        TraceLogAuthValidator validator = authValidatorProvider.getIfAvailable();
        if (validator == null) {
            // 启动时 fail-fast 已拦截，这里再检查一次
            throw new IllegalStateException(
                    "framework4j.tracelog.api.require-auth=true 但未找到 TraceLogAuthValidator Bean");
        }
        if (!check.test(validator)) {
            throw new SecurityException("无权限");
        }
        // IP 白/黑名单
        String ip = resolveClientIp(request);
        if (!props.getApi().getIpWhitelist().isEmpty()
                && !props.getApi().getIpWhitelist().contains(ip)) {
            throw new SecurityException("IP 不在白名单: " + ip);
        }
        if (props.getApi().getIpBlacklist().contains(ip)) {
            throw new SecurityException("IP 在黑名单: " + ip);
        }
    }

    private String currentOperator(HttpServletRequest request) {
        String uid = request.getHeader("X-User-Id");
        return uid == null ? "anonymous" : uid;
    }

    private String currentTenant(HttpServletRequest request) {
        if (!props.getTenant().isEnabled()) return null;
        return request.getHeader(props.getTenant().getHeaderName());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private boolean matchLevel(LogDto dto, String level) {
        if (level == null || level.isBlank() || "ALL".equalsIgnoreCase(level)) return true;
        return level.equalsIgnoreCase(dto.getLevel());
    }

    private boolean matchKeyword(LogDto dto, String keyword) {
        if (keyword == null || keyword.isBlank()) return true;
        if (dto.getMessage() != null && dto.getMessage().contains(keyword)) return true;
        if (dto.getLogger() != null && dto.getLogger().contains(keyword)) return true;
        return false;
    }

    private LogDto parseLine(String json) {
        // 直接解析关键字段，控制台前端也按此 JSON schema。
        // 双字段名兼容：文档 schema（logger/thread/tsIso）+ LogstashEncoder 默认（logger_name/thread_name/@timestamp）
        try {
            LogDto dto = new LogDto();
            dto.setTs(extractLong(json, "ts"));
            String tsIso = firstNonNull(
                    extractString(json, "tsIso"),
                    extractString(json, "@timestamp"));
            dto.setTsIso(tsIso);
            // ts 缺失时从 ISO 时间戳补齐（毫秒）
            if (dto.getTs() == 0L && tsIso != null) {
                dto.setTs(parseIsoToMillis(tsIso));
            }
            dto.setLevel(extractString(json, "level"));
            dto.setLogger(firstNonNull(extractString(json, "logger"),
                    extractString(json, "logger_name")));
            dto.setThread(firstNonNull(extractString(json, "thread"),
                    extractString(json, "thread_name")));
            dto.setTraceId(extractString(json, "traceId"));
            dto.setSpanId(extractString(json, "spanId"));
            dto.setMessage(extractString(json, "message"));
            dto.setApp(firstNonNull(extractString(json, "app"),
                    extractString(json, "app_name")));
            dto.setHost(extractString(json, "host"));
            dto.setHost(firstNonNull(dto.getHost(), extractString(json, "host_name")));
            return dto;
        } catch (Exception e) {
            LogDto dto = new LogDto();
            dto.setMessage(json);
            dto.setLevel("INFO");
            return dto;
        }
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private static long parseIsoToMillis(String iso) {
        try {
            return java.time.Instant.parse(iso).toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }

    private long extractLong(String json, String key) {
        String s = extractStringRaw(json, key);
        if (s == null) return 0L;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0L; }
    }

    private String extractString(String json, String key) {
        String s = extractStringRaw(json, key);
        return s == null ? null : s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String extractStringRaw(String json, String key) {
        String pattern = "\"" + key + "\":";
        int i = json.indexOf(pattern);
        if (i < 0) return null;
        int start = i + pattern.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return null;
        char c = json.charAt(start);
        if (c == '"') {
            int end = start + 1;
            while (end < json.length() && json.charAt(end) != '"') {
                if (json.charAt(end) == '\\') end++;
                end++;
            }
            return json.substring(start + 1, end);
        }
        int end = start;
        while (end < json.length() && ",-]}".indexOf(json.charAt(end)) < 0) end++;
        return json.substring(start, end).trim();
    }

    @FunctionalInterface
    private interface PermissionCheck {
        boolean test(TraceLogAuthValidator validator);
    }
}