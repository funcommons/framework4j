package fun.commons.framework4j.tracelog.metrics;

import fun.commons.framework4j.tracelog.appender.AsyncRedisLogAppender;
import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TraceLog Micrometer 指标绑定。
 * <p>
 * 暴露 5 类指标：
 * <ul>
 *   <li>{@code tracelog.appended} — Counter：append() 成功入队</li>
 *   <li>{@code tracelog.dropped} — Counter：队列满 / 速率限制 / 序列化失败</li>
 *   <li>{@code tracelog.flushed} — Counter：成功 flush 到 Redis</li>
 *   <li>{@code tracelog.flush.duration} — Timer：批 flush 耗时</li>
 *   <li>{@code tracelog.queue.depth} — Gauge：当前 RingBuffer 队列深度</li>
 *   <li>{@code tracelog.redis.fail} — Counter：Redis 调用失败</li>
 * </ul>
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §6 风险控制</a>
 */
@Slf4j
public class TraceLogMetrics {

    private final AsyncRedisLogAppender appender;
    private final MeterRegistry registry;

    private Counter appendedCounter;
    private Counter droppedCounter;
    private Counter flushedCounter;
    private Counter redisFailCounter;
    private Counter rateLimitCounter;
    private Counter queueFullCounter;
    private Timer flushTimer;
    private final AtomicLong queueDepth = new AtomicLong(0);

    public TraceLogMetrics(AsyncRedisLogAppender appender, MeterRegistry registry, TraceLogProperties props) {
        this.appender = appender;
        this.registry = registry;
        bind();
        log.info("【TraceLog】指标绑定完成: meterRegistry={}", registry.getClass().getSimpleName());
    }

    private void bind() {
        appendedCounter = Counter.builder("tracelog.appended")
                .description("成功入队日志条数")
                .register(registry);
        droppedCounter = Counter.builder("tracelog.dropped")
                .description("丢弃日志条数（含原因 tag）")
                .register(registry);
        flushedCounter = Counter.builder("tracelog.flushed")
                .description("成功 flush 到 Redis 的日志条数")
                .register(registry);
        redisFailCounter = Counter.builder("tracelog.redis.fail")
                .description("Redis 调用失败次数")
                .register(registry);

        // Gauge：队列深度（每 5s 更新）
        registry.gauge("tracelog.queue.depth", queueDepth);

        // Timer：flush 耗时
        flushTimer = Timer.builder("tracelog.flush.duration")
                .description("批 flush 耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // 周期任务：从 Appender 拉取指标 + 更新 Gauge
        Thread refresher = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    updateFromAppender();
                    Thread.sleep(5_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.debug("【TraceLog】指标刷新异常: {}", e.getMessage());
                }
            }
        }, "TraceLog-Metrics-Refresher");
        refresher.setDaemon(true);
        refresher.start();
    }

    private long lastAppended = 0;
    private long lastDropped = 0;
    private long lastFlushed = 0;

    private void updateFromAppender() {
        long curAppended = appender.getAppendedCount();
        long curDropped = appender.getDroppedCount();
        long curFlushed = appender.getFlushedCount();

        long deltaAppended = curAppended - lastAppended;
        long deltaDropped = curDropped - lastDropped;
        long deltaFlushed = curFlushed - lastFlushed;

        if (deltaAppended > 0) appendedCounter.increment(deltaAppended);
        if (deltaDropped > 0) droppedCounter.increment(deltaDropped);
        if (deltaFlushed > 0) flushedCounter.increment(deltaFlushed);

        lastAppended = curAppended;
        lastDropped = curDropped;
        lastFlushed = curFlushed;
    }

    /** 记录一次 flush 耗时（由 TraceLogStore 调用） */
    public void recordFlushDuration(long nanos) {
        flushTimer.record(nanos, TimeUnit.NANOSECONDS);
    }

    /** 记录 Redis 失败（由 TraceLogStore / Appender 调用） */
    public void recordRedisFail() {
        redisFailCounter.increment();
    }

    /** 记录速率限制丢弃 */
    public void recordRateLimitDrop() {
        rateLimitCounter.increment();
    }

    /** 记录队列满丢弃 */
    public void recordQueueFullDrop() {
        queueFullCounter.increment();
    }

    /** 更新队列深度 Gauge */
    public void updateQueueDepth(long depth) {
        queueDepth.set(depth);
    }
}