package fun.commons.framework4j.tracelog.query;

import fun.commons.framework4j.tracelog.config.TraceLogProperties;
import fun.commons.framework4j.tracelog.store.TraceLogStore;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * 日志导出器（txt / json + 可选 gzip）。
 * <p>
 * 单 trace 限速 + 大小限制，避免恶意批量导出拖垮 Redis。
 *
 * @see <a href="file:../动态追踪日志 SDK 技术方案.md">设计文档 §3.5.2</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogExporter {

    private final TraceLogStore store;
    private final TraceLogProperties props;

    public void export(String traceId, String format, HttpServletResponse response) throws IOException {
        export(traceId, format, response, null);
    }

    public void export(String traceId, String format, HttpServletResponse response, String tenantPrefix) throws IOException {
        // 单文件大小限制（按条数估算：1KB/条）
        long maxBytes = (long) props.getExport().getMaxSizeMb() * 1024 * 1024;
        List<String> raw = store.rangeTraceLogs(traceId, 0, props.getApi().getMaxReturnLogs() - 1, tenantPrefix);
        if (raw == null || raw.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String ext = "json".equals(format) ? "json" : "log";
        String fileName = String.format("trace-%s-%d.%s%s",
                traceId, System.currentTimeMillis(),
                ext, props.getExport().isCompress() ? ".gz" : "");

        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"");

        long totalBytes = 0;
        try (OutputStream out = response.getOutputStream();
             OutputStream gzipOut = props.getExport().isCompress()
                     ? new GZIPOutputStream(out) : out) {

            for (String line : raw) {
                byte[] data = formatLine(format, line);
                // 限制单文件大小
                if (totalBytes + data.length > maxBytes) {
                    log.warn("【TraceLog】导出超出大小限制, 截断: trace={}, size={}", traceId, totalBytes);
                    break;
                }
                gzipOut.write(data);
                totalBytes += data.length;
            }
        }
    }

    private byte[] formatLine(String format, String rawJson) {
        if ("json".equals(format)) {
            // JSON Lines：原行 + \n
            return (rawJson + "\n").getBytes(StandardCharsets.UTF_8);
        }
        // 文本格式：提取关键字段做人类可读展示
        String ts = Instant.now().toString();
        String level = extractField(rawJson, "level");
        String thread = extractField(rawJson, "thread");
        String logger = extractField(rawJson, "logger");
        String message = extractField(rawJson, "message");
        String line = String.format("[%s] [%-5s] [%-20s] [%s] %s%n",
                ts, level == null ? "" : level,
                thread == null ? "" : thread,
                logger == null ? "" : logger,
                message == null ? "" : message);
        return line.getBytes(StandardCharsets.UTF_8);
    }

    private String extractField(String json, String key) {
        // 极简字段提取（避免引入 Jackson 解析开销）
        String pattern = "\"" + key + "\":";
        int i = json.indexOf(pattern);
        if (i < 0) return null;
        int start = i + pattern.length();
        // 跳过空白
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return null;
        char c = json.charAt(start);
        if (c == '"') {
            // 字符串
            int end = start + 1;
            while (end < json.length() && json.charAt(end) != '"') {
                if (json.charAt(end) == '\\') end++;
                end++;
            }
            return json.substring(start + 1, end).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        // 数字 / 布尔
        int end = start;
        while (end < json.length() && ",-]}".indexOf(json.charAt(end)) < 0) end++;
        return json.substring(start, end).trim();
    }
}