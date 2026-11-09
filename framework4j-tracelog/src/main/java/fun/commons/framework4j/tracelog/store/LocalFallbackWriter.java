package fun.commons.framework4j.tracelog.store;

import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * 本地降级写入器（Redis 故障时使用）。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>滚动策略：按 1 小时切换文件，文件格式 {@code tracelog-fallback-{host}-{yyyyMMdd-HH}.log}</li>
 *   <li>每行格式：{@code <traceId>|<iso8601>|<jsonPayload>}</li>
 *   <li>Replayer：{@link ReplayWorker} 每 {@code fallback-replay-interval-seconds} 探测，
 *       Redis 恢复后顺序回灌（按时间戳）</li>
 *   <li>磁盘告警：fallback 目录累计 > 1GB 时打印 ERROR 日志</li>
 * </ul>
 *
 * <p><b>重要</b>：容器化部署必须挂载 hostPath / PVC 到 {@code fallback.dir}，
 * 容器本地文件系统在 Pod 驱逐时丢失。
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §3.4.5</a>
 */
@Slf4j
public class LocalFallbackWriter {

    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HH");
    private static final long DISK_ALERT_BYTES = 1L * 1024 * 1024 * 1024; // 1GB

    private final TraceLogProperties props;
    private final Path baseDir;
    private final String hostName;
    private final ReentrantLock writeLock = new ReentrantLock();

    private volatile BufferedWriter currentWriter;
    private volatile String currentHourKey;

    /**
     * v1.10.1 (issue #34-③)：目录不可写时降级停用（WARN）而非启动 fail-fast——
     * fallback 是「Redis 故障时才有用」的应急面，为应急面目录权限炸掉主链路启动代价收益倒挂
     * （非容器化 root 环境/CI 开闸即炸）。Redis 正常时本能力无感；Redis 故障 + 目录不可写时
     * 日志无法落盘回灌（WARN 显形，容器化部署请挂载 hostPath / PVC）。
     */
    private volatile boolean available = true;
    private volatile boolean unavailableWarned = false;

    public LocalFallbackWriter(TraceLogProperties props) {
        this.props = props;
        this.hostName = resolveHostName();
        this.baseDir = Paths.get(props.getCollection().getFallbackDir());
        try {
            Files.createDirectories(baseDir);
            // 验证可写：尝试创建并删除一个临时文件
            Path probe = baseDir.resolve(".tracelog-probe");
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
        } catch (IOException e) {
            // v1.10.1 (issue #34-③)：WARN 降级，不再抛 IllegalStateException 阻断启动
            available = false;
            log.warn("【TraceLog】fallback目录不可写，本地降级能力停用（启动不受影响）: dir={}, err={}. "
                    + "Redis 故障期间该部分日志将无法落盘回灌；容器化部署请挂载 hostPath / PVC 到 fallback.dir",
                    baseDir, e.getMessage());
        }
    }

    /** v1.10.1 (issue #34-③)：fallback 能力是否可用（目录可写） */
    public boolean isAvailable() {
        return available;
    }

    /**
     * 写入一批日志到 fallback 文件。
     *
     * @param payloads 日志负载列表（traceId, json）
     */
    public void writeBatch(List<RawPayload> payloads) {
        if (payloads == null || payloads.isEmpty()) return;
        if (!available) {
            if (!unavailableWarned) {
                unavailableWarned = true;
                log.warn("【TraceLog】fallback 已停用（目录不可写），本次 {} 条降级日志丢弃", payloads.size());
            }
            return;
        }
        writeLock.lock();
        try {
            BufferedWriter w = ensureWriter();
            String now = LocalDateTime.now().toString();
            for (RawPayload p : payloads) {
                // 格式: traceId|iso8601|json  (|分隔便于回灌时拆分)
                w.write(p.traceId());
                w.write('|');
                w.write(now);
                w.write('|');
                w.write(p.json());
                w.write('\n');
            }
            w.flush();
            checkDiskAlert();
        } catch (IOException e) {
            log.error("【TraceLog】fallback写入失败: err={}", e.getMessage());
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 读取所有 fallback 文件并返回（按文件时间序）。
     * <p>
     * Replayer 调用，按行解析后批量回灌到 Redis。
     */
    public List<Path> listFallbackFiles() throws IOException {
        if (!available) {
            return java.util.List.of();
        }
        try (Stream<Path> stream = Files.list(baseDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith("tracelog-fallback-"))
                    .filter(p -> p.getFileName().toString().endsWith(".log"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * 读取单个 fallback 文件的所有行（Replayer 调用）。
     */
    public BufferedReader openReader(Path file) throws IOException {
        return Files.newBufferedReader(file, StandardCharsets.UTF_8);
    }

    /**
     * 删除已成功回灌的 fallback 文件。
     */
    public void deleteFile(Path file) {
        try {
            Files.deleteIfExists(file);
            log.info("【TraceLog】fallback回灌成功, 删除文件: {}", file.getFileName());
        } catch (IOException e) {
            log.warn("【TraceLog】fallback文件删除失败: file={}, err={}", file, e.getMessage());
        }
    }

    public Path getBaseDir() {
        return baseDir;
    }

    // ==================== 私有 ====================

    private BufferedWriter ensureWriter() throws IOException {
        String hourKey = LocalDateTime.now().format(HOUR_FMT);
        if (currentWriter != null && hourKey.equals(currentHourKey)) {
            return currentWriter;
        }
        // 切小时：关闭旧 writer，打开新文件
        if (currentWriter != null) {
            try { currentWriter.close(); } catch (IOException ignore) { /* nop */ }
        }
        Path file = baseDir.resolve(String.format("tracelog-fallback-%s-%s.log", hostName, hourKey));
        currentWriter = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        currentHourKey = hourKey;
        log.info("【TraceLog】fallback文件切换: {}", file.getFileName());
        return currentWriter;
    }

    private void checkDiskAlert() {
        long totalBytes = 0;
        try (Stream<Path> stream = Files.list(baseDir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                totalBytes += Files.size(p);
            }
        } catch (IOException e) {
            // ignore
            return;
        }
        if (totalBytes > DISK_ALERT_BYTES) {
            log.error("【TraceLog】fallback目录磁盘占用 {} MB > 1GB, 请检查Redis连通性",
                    totalBytes / 1024 / 1024);
        }
    }

    private String resolveHostName() {
        try {
            return InetAddress.getLocalHost().getHostName().replace('.', '_');
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    /**
     * 原始日志负载（traceId + JSON 字符串）。
     */
    public record RawPayload(String traceId, String json) {}
}