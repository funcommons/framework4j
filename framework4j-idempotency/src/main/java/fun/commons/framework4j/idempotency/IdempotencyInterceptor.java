package fun.commons.framework4j.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.api.ApiCode;
import fun.commons.framework4j.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Idempotency-Key 拦截器
 * <p>
 * v2.1 修复：
 * <ul>
 *   <li>ObjectMapper 由容器注入（与 WebConfig 全局 snake_case + Long→String 一致）</li>
 *   <li>preHandle 阶段主动 readAllBytes 触发 ContentCachingRequestWrapper 缓存（修 body hash 全相同 bug）</li>
 *   <li>afterCompletion 从 ContentCachingResponseWrapper 取响应体（修回放空响应 bug）</li>
 * </ul>
 * <p>
 * v1.2.7 修复（下游 benefit4j 排查报告 bug2 "第一次必 409"）：
 * <ul>
 *   <li><b>重入守卫</b>：同一请求第二次进入 preHandle（典型场景：拦截器被重复注册，
 *       如 v1.2.5 框架注册 + 下游自建 workaround 注册未拆除）时，检测到本请求已通过
 *       SETNX（{@code ATTR_REDIS_KEY} 属性存在）直接放行 —— 不再读到自己刚写的
 *       PENDING 标记而 409 自己。</li>
 *   <li><b>PENDING 并发态区分</b>：同 key 前一请求仍在处理中（PENDING）时的 409
 *       响应消息与普通重复提交区分，并打 WARN 日志，便于排查。</li>
 * </ul>
 */
@Slf4j
public class IdempotencyInterceptor implements HandlerInterceptor {

    /** UUID v4 正则：8-4-4-4-12，第 3 段以 4 开头，第 4 段以 8/9/a/b 开头 */
    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE);

    private static final String PENDING_MARKER = "PENDING";
    private static final String OK_PREFIX = "OK:";

    // v2.1: request attribute key 常量化（原魔法字符串）
    private static final String ATTR_REDIS_KEY = "__idempotency_redis_key__";
    private static final String ATTR_BODY_HASH = "__idempotency_body_hash__";

    /**
     * v2.1: Lua 脚本原子化"GET + SETNX if absent + 返回旧值"，消除 TOCTOU 竞态。
     * <p>原 setIfAbsent 失败后 get + 重试 setIfAbsent 存在并发窗口。
     * <p>KEYS[1] = redisKey; ARGV[1] = value; ARGV[2] = ttlSeconds
     * <p>返回: nil（首次设置成功）或 旧值（已存在）
     */
    private static final DefaultRedisScript<String> GET_OR_SETNX = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]); " +
            "if v then return v end; " +
            "redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]); " +
            "return nil",
            String.class);

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyProperties properties;
    private final ObjectMapper objectMapper;

    public IdempotencyInterceptor(StringRedisTemplate redisTemplate,
                                  IdempotencyProperties properties,
                                  ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * v2.2 P1 修复：仅对写方法生效（POST/PUT/PATCH/DELETE）。
     * <p>原实现对所有 method 都生效，导致 GET 请求带 Idempotency-Key 时也走 Redis 检查 — 浪费且语义错位。
     * <p>PUT/PATCH 与 POST 一样可重复提交，必须纳入保护。
     */
    private static final java.util.Set<String> WRITE_METHODS =
            java.util.Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // v1.2.7: 重入守卫 —— 同一请求已通过幂等检查（SETNX 成功时设置了 ATTR_REDIS_KEY）则直接放行。
        // 防御拦截器被重复注册（框架注册 + 下游自建注册并存）时，第二次进入读到自己刚写的
        // PENDING 标记而 409 自己（下游 benefit4j 排查报告 bug2 "第一次必 409" 的框架侧根因）。
        if (request.getAttribute(ATTR_REDIS_KEY) != null) {
            return true;
        }
        // v2.2 P1: 非写方法直接放行（GET/HEAD/OPTIONS 不需要幂等保护）
        if (!WRITE_METHODS.contains(request.getMethod().toUpperCase())) {
            return true;
        }
        String headerName = properties.getHeaderName();
        String key = request.getHeader(headerName);
        if (key == null || key.isEmpty()) {
            return true;
        }
        if (!UUID_V4.matcher(key).matches()) {
            writeJson(response, 400, ApiResponse.fail(
                    ApiCode.PARAM_FORMAT_ERROR.getCode(),
                    headerName + " 必须是 UUID v4 格式"));
            return false;
        }

        String bodyHash = properties.isBodyHashRequired() ? hashBody(request) : "*";
        String redisKey = properties.getKeyPrefix() + ":" + request.getRequestURI() + ":" + key;
        String value = bodyHash + "|" + PENDING_MARKER;

        // v2.1: Lua 原子化 GET + SETNX，消除 TOCTOU 竞态（原 setIfAbsent 失败后 get + 重试有窗口）
        String existing = redisTemplate.execute(
                GET_OR_SETNX,
                List.of(redisKey),
                value,
                String.valueOf(properties.getTtlSeconds()));

        if (existing == null) {
            // 首次设置成功
            request.setAttribute(ATTR_REDIS_KEY, redisKey);
            request.setAttribute(ATTR_BODY_HASH, bodyHash);
            return true;
        }

        // 已存在值
        int sep = existing.indexOf('|');
        if (sep < 0) {
            log.warn("[Idempotency] 异常缓存值 key={} value={}", redisKey, existing);
            writeJson(response, 409, ApiResponse.fail(ApiCode.DUPLICATE_SUBMIT));
            return false;
        }
        String existingHash = existing.substring(0, sep);
        String status = existing.substring(sep + 1);

        if (!existingHash.equals(bodyHash)) {
            writeJson(response, 409, ApiResponse.fail(ApiCode.DUPLICATE_SUBMIT));
            return false;
        }

        if (status.startsWith(OK_PREFIX)) {
            String cachedBody = status.substring(OK_PREFIX.length());
            response.setStatus(200);
            response.setContentType("application/json;charset=UTF-8");
            // v2.1 P0 修复：response 是 ContentCachingResponseWrapper（Filter 包装），写入只进 wrapper 缓冲区。
            // 不再设置 ATTR_REPLAY_WRITTEN，让 Filter 的 finally 调 copyBodyToResponse() 把缓冲区刷到真实 response。
            // （原实现设置标记跳过 copy，导致客户端收到空响应体）
            response.getWriter().write(cachedBody);
            response.getWriter().flush();
            return false;
        }

        // v1.2.7: PENDING 并发态区分 —— 同 key 前一请求仍在处理中（尚未回写 OK）。
        // 与"已缓存响应的重复提交"不同：此时重试可能成功，消息提示稍后重试，便于排查。
        if (PENDING_MARKER.equals(status)) {
            log.warn("【Idempotency】同 key 前一请求仍在处理中（PENDING），拒绝并发重复提交 key={}", redisKey);
            writeJson(response, 409, ApiResponse.fail(ApiCode.DUPLICATE_SUBMIT.getCode(),
                    "相同 Idempotency-Key 的前一请求仍在处理中，请稍后重试"));
            return false;
        }

        log.warn("【Idempotency】未知缓存状态 key={} status={}", redisKey, status);
        writeJson(response, 409, ApiResponse.fail(ApiCode.DUPLICATE_SUBMIT));
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String redisKey = (String) request.getAttribute(ATTR_REDIS_KEY);
        String bodyHash = (String) request.getAttribute(ATTR_BODY_HASH);
        if (redisKey == null || bodyHash == null) {
            return;
        }
        int status = response.getStatus();
        // v2.1 P1 修复：非 2xx 或异常不缓存，删除 redisKey 让客户端可用同 Idempotency-Key 重试。
        // （原实现写 ERR:status marker，TTL 48h，重试被 409 阻塞无法恢复）
        if (ex != null || status < 200 || status >= 300) {
            try {
                redisTemplate.delete(redisKey);
            } catch (Exception e) {
                log.warn("[Idempotency] 删除失败 redisKey={} 失败: {}", redisKey, e.getMessage());
            }
            return;
        }
        // 从 ContentCachingResponseWrapper 取响应体
        String body = extractResponseBody(response);
        String marker = OK_PREFIX + body;
        try {
            redisTemplate.opsForValue().set(redisKey, bodyHash + "|" + marker,
                    properties.getTtlSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[Idempotency] 回写 Redis 失败 key={}", redisKey, e);
        }
    }

    /**
     * 从 ContentCachingResponseWrapper 拿响应体。如果不是 wrapper（Filter 未生效），返回空串。
     */
    private static String extractResponseBody(HttpServletResponse response) {
        if (response instanceof ContentCachingResponseWrapper wrapper) {
            byte[] buf = wrapper.getContentAsByteArray();
            return buf.length > 0 ? new String(buf, StandardCharsets.UTF_8) : "";
        }
        return "";
    }

    private String hashBody(HttpServletRequest request) throws IOException {
        ContentCachingRequestWrapper wrapper = resolveContentCachingWrapper(request);
        if (wrapper != null) {
            // v2.1 P0 修复：IdempotencyBodyCacheFilter 已在 preHandle 之前 cacheBody()，
            // getContentAsByteArray() 直接返回缓存内容，不再消费 InputStream（原 readAllBytes 破坏 Controller @RequestBody）。
            byte[] cached = wrapper.getContentAsByteArray();
            return sha256Hex(cached);
        }
        // v2.1 P1 修复：兜底分支原直接读 inputStream 会消费原始流，破坏下游 Controller @RequestBody。
        // 无 wrapper 时（IdempotencyBodyCacheFilter 未生效）fail-secure：拒绝请求而非用 "*" 放行，
        // 避免同 key 不同 body 命中回放绕过 body 校验。
        if (properties.isBodyHashRequired()) {
            throw new java.io.IOException(
                "IdempotencyBodyCacheFilter 未生效，无法校验 body hash（请检查 Filter 注册与路径配置）");
        }
        log.warn("[Idempotency] ContentCachingRequestWrapper 未找到但 bodyHashRequired=false，跳过 body hash 校验");
        return "*";
    }

    /** 沿 wrapper 链找到 ContentCachingRequestWrapper（可能被多层 wrapper 包裹） */
    private static ContentCachingRequestWrapper resolveContentCachingWrapper(HttpServletRequest request) {
        HttpServletRequest cur = request;
        for (int i = 0; i < 5 && cur != null; i++) {
            if (cur instanceof ContentCachingRequestWrapper w) return w;
            if (cur instanceof HttpServletRequestWrapper w) {
                cur = (HttpServletRequest) w.getRequest();
            } else {
                break;
            }
        }
        return null;
    }

    private void writeJson(HttpServletResponse response, int status,
                           ApiResponse<?> body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    // v2.1: ThreadLocal 缓存 MessageDigest（原每次 getInstance 有 JCA 查找开销，MessageDigest 非线程安全）
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    });

    // v2.1 P1: HEX 查表替代 String.format（吞吐提升 5-10x）
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private static String sha256Hex(byte[] data) {
        MessageDigest md = SHA_256.get();
        md.reset();
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder(64);  // SHA-256 固定 32 字节 = 64 hex 字符
        for (byte b : digest) {
            int v = b & 0xff;
            sb.append(HEX_CHARS[v >>> 4]).append(HEX_CHARS[v & 0x0f]);
        }
        return sb.toString();
    }
}
