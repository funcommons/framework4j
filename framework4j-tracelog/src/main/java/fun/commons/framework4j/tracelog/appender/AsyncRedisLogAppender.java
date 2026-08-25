package fun.commons.framework4j.tracelog.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventTranslatorThreeArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import fun.commons.framework4j.tracelog.rate.PerTraceRateLimiter;
import fun.commons.framework4j.tracelog.store.LocalFallbackWriter;
import fun.commons.framework4j.tracelog.store.TraceIdNormalizer;
import fun.commons.framework4j.tracelog.store.TraceLogStore;
import fun.commons.framework4j.tracelog.util.TenantKeyResolver;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.encoder.LogstashEncoder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步 Redis 日志 Appender（分片 Disruptor）。
 * <p>
 * <b>架构</b>：
 * <pre>
 * [业务线程]                  [Disruptor × N shards]              [Worker × N]
 * ILoggingEvent                RingBuffer (size=65536)              ┌──────────────┐
 *   │                          │                                     │ 序列化 + 批写 │
 *   ▼                          ▼                                     │ Redis Pipeline│
 * extract traceId ──hash──▶ shard = traceId.hashCode() % N           └──────────────┘
 * normalize 32-hex             (按 traceId 顺序保留)                       │
 * rate-limit                                                                   ▼
 *   offer(RawEvent)                                                  TraceLogStore
 *   O(1) 返回
 * </pre>
 *
 * <b>关键设计</b>：
 * <ul>
 *   <li>业务线程 <b>只读 MDC + 入队</b>，序列化在 Worker 线程，p99 < 0.05ms</li>
 *   <li>按 traceId hash 分片，<b>同 traceId 必落同一 Worker</b>，保证时序</li>
 *   <li>LMAX Disruptor 无锁 MPSC，比 {@code ArrayBlockingQueue} GC 压力低</li>
 *   <li>每个 Worker 独立维护批，达到 {@code flush-batch-size} 或 {@code flush-interval-ms} 即刷盘</li>
 * </ul>
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §3.4</a>
 */
@Slf4j
public class AsyncRedisLogAppender extends AppenderBase<ILoggingEvent> {

    private final TraceLogProperties props;
    private final TraceLogStore store;
    private final LocalFallbackWriter fallbackWriter;
    private final PerTraceRateLimiter rateLimiter;
    private final TenantKeyResolver tenantKeyResolver;

    private Disruptor<RawEvent>[] disruptors;
    private RingBuffer<RawEvent>[] ringBuffers;
    private BatchWorker[] workers;
    /** 时间触发刷盘调度器（flushIfDue 的驱动者, 否则未满批的日志永不落 Redis） */
    private java.util.concurrent.ScheduledExecutorService flushScheduler;

    /** traceId → 该链路在本节点已"首次"标记（Lua 仅在真首次执行） */
    private final Set<String> localSeenTraceIds = ConcurrentHashMap.newKeySet();

    /** 指标计数器（v2.4 接入 Micrometer） */
    private final AtomicLong appendedCounter = new AtomicLong();
    private final AtomicLong droppedCounter = new AtomicLong();
    private final AtomicLong flushedCounter = new AtomicLong();

    public AsyncRedisLogAppender(TraceLogProperties props,
                                 TraceLogStore store,
                                 LocalFallbackWriter fallbackWriter,
                                 TenantKeyResolver tenantKeyResolver) {
        this.props = props;
        this.store = store;
        this.fallbackWriter = fallbackWriter;
        this.tenantKeyResolver = tenantKeyResolver;
        this.rateLimiter = new PerTraceRateLimiter(
                props.getCollection().getRateLimitPerTracePerSecond());
    }

    @Override
    public void start() {
        int workers = resolveWorkerCount();
        int bufferSize = props.getCollection().getDisruptorBufferSize();

        @SuppressWarnings("unchecked")
        Disruptor<RawEvent>[] tmpDisruptors = new Disruptor[workers];
        @SuppressWarnings("unchecked")
        RingBuffer<RawEvent>[] tmpRings = (RingBuffer<RawEvent>[]) new RingBuffer[workers];
        BatchWorker[] tmpWorkers = new BatchWorker[workers];

        for (int i = 0; i < workers; i++) {
            Disruptor<RawEvent> d = new Disruptor<>(
                    RawEventFactory.INSTANCE,
                    bufferSize,
                    new NamedThreadFactory("TraceLog-Worker-" + i),
                    ProducerType.MULTI,
                    new com.lmax.disruptor.YieldingWaitStrategy());
            BatchWorker handler = new BatchWorker(i);
            d.handleEventsWithWorkerPool(handler);
            d.start();
            tmpDisruptors[i] = d;
            tmpRings[i] = d.getRingBuffer();
            tmpWorkers[i] = handler;
        }
        this.disruptors = tmpDisruptors;
        this.ringBuffers = tmpRings;
        this.workers = tmpWorkers;

        // 时间触发刷盘: 单 trace 限速 200/s 永远攒不满 500 批, 无定时器则日志永不落 Redis
        long flushIntervalMs = props.getCollection().getFlushIntervalMs();
        flushScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("TraceLog-Flush-Scheduler"));
        flushScheduler.scheduleWithFixedDelay(() -> {
            for (BatchWorker w : this.workers) {
                try {
                    w.flushIfDue();
                } catch (Exception e) {
                    log.warn("【TraceLog】定时刷盘异常: {}", e.getMessage());
                }
            }
        }, flushIntervalMs, flushIntervalMs, java.util.concurrent.TimeUnit.MILLISECONDS);

        super.start();
        log.info("【TraceLog】AsyncRedisLogAppender 启动: workers={}, buffer-size={}, "
                        + "flush-batch={}, flush-interval={}ms, rate-limit={}/s",
                workers, bufferSize,
                props.getCollection().getFlushBatchSize(),
                props.getCollection().getFlushIntervalMs(),
                props.getCollection().getRateLimitPerTracePerSecond());
    }

    @Override
    public void stop() {
        super.stop();
        if (flushScheduler != null) {
            flushScheduler.shutdown();
        }
        if (disruptors == null) return;

        long timeout = props.getCollection().getShutdownDrainTimeoutSeconds();
        log.info("【TraceLog】AsyncRedisLogAppender 优雅停机: workers={}, timeout={}s",
                disruptors.length, timeout);
        for (Disruptor<RawEvent> d : disruptors) {
            try {
                d.shutdown(timeout, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("【TraceLog】Disruptor shutdown 异常: {}", e.getMessage());
            }
        }
        // drain 后未满批的残留日志最后刷一次
        if (workers != null) {
            for (BatchWorker w : workers) {
                try {
                    w.flush();
                } catch (Exception e) {
                    log.warn("【TraceLog】停机残留刷盘异常: {}", e.getMessage());
                }
            }
        }
        log.info("【TraceLog】AsyncRedisLogAppender 已停止: appended={}, dropped={}, flushed={}",
                appendedCounter.get(), droppedCounter.get(), flushedCounter.get());
    }

    @Override
    protected void append(ILoggingEvent event) {
        // 1. 解析 traceId
        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc == null) return;
        String raw = mdc.get("traceId");
        if (raw == null) return;

        String traceId = TraceIdNormalizer.normalize(raw);
        if (traceId == null) return;

        // 2. 单 trace 限速
        if (!rateLimiter.tryAcquire(traceId)) {
            droppedCounter.incrementAndGet();
            return;
        }

        // 3. 分片选 RingBuffer
        int shard = Math.abs(traceId.hashCode()) % ringBuffers.length;
        RingBuffer<RawEvent> rb = ringBuffers[shard];

        // 4. 无锁入队
        long seq;
        try {
            seq = rb.tryNext();
        } catch (com.lmax.disruptor.InsufficientCapacityException e) {
            // RingBuffer 满（Producer 速度 > Worker 消费速度）：丢弃并计数
            droppedCounter.incrementAndGet();
            return;
        }
        try {
            rb.get(seq).set(traceId, event.getTimeStamp(), event);
        } finally {
            rb.publish(seq);
        }
        appendedCounter.incrementAndGet();
    }

    private int resolveWorkerCount() {
        int configured = props.getCollection().getWorkerCount();
        return configured > 0 ? configured : Runtime.getRuntime().availableProcessors();
    }

    public long getAppendedCount() { return appendedCounter.get(); }
    public long getDroppedCount() { return droppedCounter.get(); }
    public long getFlushedCount() { return flushedCounter.get(); }

    // ==================== Worker ====================

    /**
     * 每个 shard 一个 Worker，独立维护批，达到阈值或时间触发刷盘。
     */
    private final class BatchWorker implements WorkHandler<RawEvent> {
        private final int shardId;
        private final LogstashEncoder encoder;
        private final List<TraceLogStore.LogItem> pending = new ArrayList<>(props.getCollection().getFlushBatchSize());
        private final Set<String> firstSeenInBatch = new HashSet<>();
        private long lastFlushMs = System.currentTimeMillis();

        BatchWorker(int shardId) {
            this.shardId = shardId;
            this.encoder = new LogstashEncoder();
            this.encoder.setContext(getContext());
            this.encoder.start();
        }

        @Override
        public void onEvent(RawEvent raw) {
            try {
                String traceId = raw.getTraceId();
                String json = serialize(raw);
                if (json == null) return;

                boolean firstSeen = firstSeenInBatch.add(traceId)
                        && localSeenTraceIds.add(traceId);

                // 多租户隔离：Worker 线程取一次（同一请求的日志同租户）
                String tenantPrefix = tenantKeyResolver != null ? tenantKeyResolver.currentTenant() : null;

                pending.add(new TraceLogStore.LogItem(traceId, json, firstSeen, tenantPrefix));

                if (pending.size() >= props.getCollection().getFlushBatchSize()) {
                    flush();
                }
            } finally {
                raw.clear();
            }
        }

        /**
         * 由外部定时器调用，检查时间触发刷盘。
         */
        public void flushIfDue() {
            if (pending.isEmpty()) return;
            long elapsed = System.currentTimeMillis() - lastFlushMs;
            if (elapsed >= props.getCollection().getFlushIntervalMs()) {
                flush();
            }
        }

        private void flush() {
            if (pending.isEmpty()) return;
            try {
                store.flushBatch(new ArrayList<>(pending));
                flushedCounter.addAndGet(pending.size());
            } catch (Exception e) {
                // Redis 故障：降级到本地文件
                log.warn("【TraceLog】Redis写入失败, 降级到本地: shard={}, count={}, err={}",
                        shardId, pending.size(), e.getMessage());
                List<LocalFallbackWriter.RawPayload> payloads = new ArrayList<>(pending.size());
                for (TraceLogStore.LogItem item : pending) {
                    payloads.add(new LocalFallbackWriter.RawPayload(item.traceId(), item.json()));
                }
                fallbackWriter.writeBatch(payloads);
            } finally {
                pending.clear();
                firstSeenInBatch.clear();
                lastFlushMs = System.currentTimeMillis();
            }
        }

        private String serialize(RawEvent raw) {
            try {
                // 直接 encode 为 byte[] 再解码为字符串（避免 Jackson StringWriter 分配）
                byte[] bytes = encoder.encode(raw.getRaw());
                return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.debug("【TraceLog】序列化失败, 跳过: {}", e.getMessage());
                return null;
            }
        }
    }

    // ==================== Disruptor EventFactory ====================

    private static final class RawEventFactory implements EventFactory<RawEvent> {
        static final RawEventFactory INSTANCE = new RawEventFactory();
        @Override public RawEvent newInstance() { return new RawEvent(); }
    }

    /**
     * 命名线程工厂（Disruptor 要求）。
     */
    private static final class NamedThreadFactory implements java.util.concurrent.ThreadFactory {
        private final String name;
        NamedThreadFactory(String name) { this.name = name; }
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        }
    }
}