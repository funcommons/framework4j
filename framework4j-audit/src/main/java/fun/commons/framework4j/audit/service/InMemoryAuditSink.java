package fun.commons.framework4j.audit.service;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存 AuditSink（开发/测试用）
 * <p>
 * v2.1 P1: AtomicInteger 维护计数（ConcurrentLinkedDeque.size() 是 O(n)）
 * <p>
 * 生产环境应替换为 DB / Kafka 实现。
 *
 * @since 2.1.0
 */
@Slf4j
public class InMemoryAuditSink implements AuditSink {

    private static final int MAX_CAPACITY = 10_000;

    private final ConcurrentLinkedDeque<AuditRecord> records = new ConcurrentLinkedDeque<>();
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public synchronized void write(AuditRecord record) {
        // v2.1 P0 修复（第4轮）：synchronized 串行化"计数+驱逐+入队"，防并发 counter 与 deque 错位
        if (counter.get() >= MAX_CAPACITY) {
            AuditRecord evicted = records.pollFirst();
            if (evicted != null) {
                counter.decrementAndGet();
            }
        }
        records.addLast(record);
        counter.incrementAndGet();
        log.info("[Audit] recorded: action={} target={} result={} actor={}",
                record.getAction(), record.getTargetType() + ":" + record.getTargetId(),
                record.getResult(), record.getActor());
    }

    public List<AuditRecord> getRecords() {
        return new ArrayList<>(records);
    }

    public int size() {
        return counter.get();
    }

    public AuditRecord last() {
        return records.peekLast();
    }

    public void clear() {
        records.clear();
        counter.set(0);
    }
}
