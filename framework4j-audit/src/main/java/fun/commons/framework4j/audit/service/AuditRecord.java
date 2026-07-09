package fun.commons.framework4j.audit.service;

import java.time.Instant;

/**
 * 审计日志记录
 *
 * @since 2.1.0
 */
public class AuditRecord {

    private final String action;
    private final String targetType;
    private final String targetId;
    private final String actor;
    private final String result;          // SUCCESS / ERROR
    private final String errorMessage;    // 异常信息（result=ERROR 时）
    private final String argsJson;        // 入参 JSON
    private final String resultJson;      // 返回值 JSON
    private final String ip;
    private final String userAgent;
    private final String traceId;
    private final Instant timestamp;
    private final String prevHash;        // hash chain 前置 hash
    private final String hash;            // 本条 hash

    public AuditRecord(String action, String targetType, String targetId,
                       String actor, String result, String errorMessage,
                       String argsJson, String resultJson,
                       String ip, String userAgent, String traceId,
                       Instant timestamp, String prevHash, String hash) {
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.actor = actor;
        this.result = result;
        this.errorMessage = errorMessage;
        this.argsJson = argsJson;
        this.resultJson = resultJson;
        this.ip = ip;
        this.userAgent = userAgent;
        this.traceId = traceId;
        this.timestamp = timestamp;
        this.prevHash = prevHash;
        this.hash = hash;
    }

    // Getters
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getActor() { return actor; }
    public String getResult() { return result; }
    public String getErrorMessage() { return errorMessage; }
    public String getArgsJson() { return argsJson; }
    public String getResultJson() { return resultJson; }
    public String getIp() { return ip; }
    public String getUserAgent() { return userAgent; }
    public String getTraceId() { return traceId; }
    public Instant getTimestamp() { return timestamp; }
    public String getPrevHash() { return prevHash; }
    public String getHash() { return hash; }
}
