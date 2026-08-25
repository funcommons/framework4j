package fun.commons.framework4j.tracelog.query;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 控制台 / API 响应的日志 DTO（JSON Schema 标准字段）。
 * <p>
 * 对应 {@link <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §8 日志 JSON Schema</a>}。
 */
@Data
public class LogDto {

    /** 毫秒时间戳 */
    private long ts;

    /** ISO-8601 UTC 时间 */
    @JsonProperty("tsIso")
    private String tsIso;

    /** 日志级别: DEBUG / INFO / WARN / ERROR / TRACE */
    private String level;

    /** Logger 名 */
    private String logger;

    /** 线程名 */
    private String thread;

    /** 32-hex OTel traceId */
    private String traceId;

    /** SpanId（v2.5 关联树） */
    private String spanId;

    /** 格式化后的日志消息 */
    private String message;

    /** 异常结构（class / message / stacktrace[]） */
    private Object exception;

    /** MDC 键值对（除内部字段） */
    private java.util.Map<String, String> mdc;

    /** 应用名（spring.application.name） */
    private String app;

    /** 主机名 */
    private String host;
}