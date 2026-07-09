package fun.commons.framework4j.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.audit.config.AuditProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 审计日志服务（核心）
 * <p>
 * 负责组装记录 + Hash Chain 计算 + 同步写入 sink（sink 实现可自行异步）。
 *
 * <h3>安全责任边界</h3>
 * <ul>
 *   <li>{@code actor} 取自 {@code X-User-Id} Header、{@code ip} 取自 {@code X-Forwarded-For}</li>
 *   <li><b>这两个 Header 必须由网关在入口处剔除/覆写后才可信</b>，否则任意客户端可伪造审计 actor/IP</li>
 *   <li>消费者应用应在网关层强制：① 不允许客户端传 X-User-Id；② X-Forwarded-For 由网关重写为真实出口 IP</li>
 * </ul>
 *
 * @since 2.1.0
 */
@Slf4j
public class AuditService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AuditProperties properties;
    private final HashChainService hashChainService;
    private final AuditSink auditSink;

    public AuditService(AuditProperties properties,
                        HashChainService hashChainService,
                        AuditSink auditSink) {
        this.properties = properties;
        this.hashChainService = hashChainService;
        this.auditSink = auditSink;

        // 初始化 hash chain
        String lastHash = auditSink.loadLastHash();
        if (lastHash != null && !lastHash.isEmpty()) {
            hashChainService.setLastHash(lastHash);
        }
    }

    /**
     * 记录审计日志
     */
    public void audit(String action, String targetType, String targetId,
                      String result, String errorMessage,
                      Object[] args, Object returnValue) {
        try {
            doAudit(action, targetType, targetId, result, errorMessage, args, returnValue);
        } catch (Exception e) {
            log.warn("[Audit] write failed: {}", e.getMessage());
        }
    }

    private void doAudit(String action, String targetType, String targetId,
                         String result, String errorMessage,
                         Object[] args, Object returnValue) throws Exception {
        HttpServletRequest request = currentRequest();
        String actor = resolveActor(request);
        String ip = resolveIp(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : null;

        // trace_id 从 MDC 取
        String traceId = resolveTraceId();

        String argsJson = args != null ? MAPPER.writeValueAsString(args) : null;
        String resultJson = (returnValue != null) ? MAPPER.writeValueAsString(returnValue) : null;

        Instant now = Instant.now();

        // 构造 hash 内容（不含 hash 本身）
        // v2.1 P1: TreeMap 保证 key 序稳定（hash chain verify 可复现）
        Map<String, Object> content = new TreeMap<>();
        content.put("action", action);
        content.put("targetType", targetType);
        content.put("targetId", targetId);
        content.put("actor", actor);
        content.put("result", result);
        content.put("timestamp", now.toString());
        content.put("args", argsJson);
        String contentJson = MAPPER.writeValueAsString(content);

        // v2.1 P1: 原子快照 (prevHash, hash)，避免并发下 prevHash 与链上前驱不一致
        String[] snapshot = hashChainService.computeNextSnapshot(contentJson);
        String prevHash = snapshot[0];
        String hash = snapshot[1];

        AuditRecord record = new AuditRecord(
                action, targetType, targetId,
                actor, result, errorMessage,
                argsJson, resultJson,
                ip, userAgent, traceId,
                now, prevHash, hash);

        try {
            auditSink.write(record);
        } catch (Exception e) {
            // v2.1 P0 修复（第4轮）：sink 失败时尝试回滚 lastHash（CAS 比较）
            // <p>注意：若并发下 lastHash 已被后续线程推进，rollback CAS 失败，
            // 链上会出现"hash 已前进但 sink 无对应记录"的空洞。
            // 此时 verify 报告会标记该空洞（生产应通过 sink 重试机制弥补，
            // 如 Kafka 重试 / DB 事务回滚）。
            log.warn("[Audit] sink write failed, attempting rollback: {}", e.getMessage());
            boolean rolled = hashChainService.rollbackLastHash(hash, prevHash);
            if (!rolled) {
                log.error("[Audit] rollback CAS failed (concurrent chain advanced), "
                        + "hash chain hole at hash={}, prevHash={}", hash, prevHash);
            }
        }
    }

    private String resolveActor(HttpServletRequest request) {
        if (request != null) {
            String actor = request.getHeader(properties.getActorHeader());
            if (actor != null && !actor.isBlank()) return actor;
        }
        return properties.getActorFallback();
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader(properties.getIpHeader());
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveTraceId() {
        try {
            return org.slf4j.MDC.get("trace_id");
        } catch (Exception e) {
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                return sra.getRequest();
            }
        } catch (Exception ignored) {}
        return null;
    }

    public HashChainService getHashChainService() {
        return hashChainService;
    }
}
