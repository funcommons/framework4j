package fun.commons.framework4j.tracelog.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Disruptor 传输对象（业务线程 → Worker）。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>仅持有 {@link ILoggingEvent} 引用，**不在业务线程做 JSON 序列化**</li>
 *   <li>Worker 线程拿到后做 LogstashEncoder 序列化，避免污染业务线程耗时</li>
 * </ul>
 *
 * <p>复用策略：Disruptor RingBuffer 槽位复用，事件字段在 {@link com.lmax.disruptor.EventFactory#newInstance()}
 * 创建时初始化，{@link com.lmax.disruptor.WorkHandler#onEvent(Object)} 末尾必须清理避免脏数据。
 */
public class RawEvent {

    /** 标准化后的 traceId（32-hex） */
    private String traceId;

    /** 当前事件的 normalized 时间戳（worker 序列化时可能用到） */
    private long timestamp;

    /** 原始 Logback 事件（持有引用，序列化在 worker 线程） */
    private ILoggingEvent raw;

    public RawEvent() {}

    public RawEvent(String traceId, long timestamp, ILoggingEvent raw) {
        this.traceId = traceId;
        this.timestamp = timestamp;
        this.raw = raw;
    }

    public void set(String traceId, long timestamp, ILoggingEvent raw) {
        this.traceId = traceId;
        this.timestamp = timestamp;
        this.raw = raw;
    }

    public void clear() {
        this.traceId = null;
        this.timestamp = 0;
        this.raw = null;
    }

    public String getTraceId() { return traceId; }
    public long getTimestamp() { return timestamp; }
    public ILoggingEvent getRaw() { return raw; }
}