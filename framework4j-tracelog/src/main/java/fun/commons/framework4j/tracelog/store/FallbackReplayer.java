package fun.commons.framework4j.tracelog.store;

import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.BufferedReader;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Fallback 文件回灌器（Redis 恢复后异步回灌）。
 * <p>
 * <ul>
 *   <li>每 {@code fallback-replay-interval-seconds}（默认 30s）探测</li>
 *   <li>Redis ping 成功 → 遍历 fallback 文件，按时间序回灌</li>
 *   <li>回灌成功 → 删除文件</li>
 *   <li>回灌失败 → 保留文件，下次重试</li>
 *   <li>文件首行时间戳超过 {@code trace-ttl-seconds} → 跳过（视为过期）</li>
 * </ul>
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §3.4.5</a>
 */
@Slf4j
@RequiredArgsConstructor
public class FallbackReplayer {

    private final TraceLogProperties props;
    private final StringRedisTemplate redis;
    private final LocalFallbackWriter fallbackWriter;
    private final TraceLogStore store;

    /**
     * 定时探测：Redis 恢复则批量回灌。
     */
    @Scheduled(
            fixedDelayString = "#{${framework4j.tracelog.collection.fallback-replay-interval-seconds:30} * 1000}",
            initialDelayString = "#{${framework4j.tracelog.collection.fallback-replay-interval-seconds:30} * 1000}")
    public void replay() {
        if (!isRedisHealthy()) {
            log.debug("【TraceLog】FallbackReplayer: Redis 仍不可用, 跳过本轮");
            return;
        }

        List<Path> files;
        try {
            files = fallbackWriter.listFallbackFiles();
        } catch (Exception e) {
            log.debug("【TraceLog】FallbackReplayer 列出文件失败: {}", e.getMessage());
            return;
        }

        if (files.isEmpty()) return;
        log.info("【TraceLog】FallbackReplayer 发现 {} 个待回灌文件", files.size());

        for (Path file : files) {
            try {
                replayFile(file);
            } catch (Exception e) {
                log.warn("【TraceLog】回灌失败, 保留文件下次重试: file={}, err={}",
                        file.getFileName(), e.getMessage());
            }
        }
    }

    private void replayFile(Path file) {
        long ttlSeconds = props.getStorage().getTraceTtlSeconds();
        Instant expiredCutoff = Instant.now().minus(Duration.ofSeconds(ttlSeconds));

        List<TraceLogStore.LogItem> batch = new ArrayList<>(props.getCollection().getFlushBatchSize());
        boolean anyAdded = false;
        long lineCount = 0;

        try (BufferedReader reader = fallbackWriter.openReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                // 格式: traceId|iso8601|json
                int firstSep = line.indexOf('|');
                if (firstSep < 0) continue;
                int secondSep = line.indexOf('|', firstSep + 1);
                if (secondSep < 0) continue;

                String traceId = line.substring(0, firstSep);
                String tsStr = line.substring(firstSep + 1, secondSep);
                String json = line.substring(secondSep + 1);

                // 跳过过期
                try {
                    Instant ts = Instant.parse(tsStr);
                    if (ts.isBefore(expiredCutoff)) continue;
                } catch (Exception ignore) {
                    // 时间戳格式错误，跳过此条
                    continue;
                }

                batch.add(new TraceLogStore.LogItem(traceId, json, false, null));
                anyAdded = true;

                if (batch.size() >= props.getCollection().getFlushBatchSize()) {
                    store.flushBatch(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                store.flushBatch(batch);
                batch.clear();
            }
        } catch (Exception e) {
            log.warn("【TraceLog】读取fallback文件失败: file={}, err={}", file, e.getMessage());
            return;
        }

        if (anyAdded) {
            fallbackWriter.deleteFile(file);
            log.info("【TraceLog】回灌成功: file={}, lines={}", file.getFileName(), lineCount);
        } else {
            // 全是过期，删除
            fallbackWriter.deleteFile(file);
            log.info("【TraceLog】fallback文件全部过期, 删除: file={}", file.getFileName());
        }
    }

    /**
     * Redis 健康检查（ping 命令）。
     */
    private boolean isRedisHealthy() {
        try {
            String pong = redis.getConnectionFactory().getConnection().ping();
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            return false;
        }
    }
}