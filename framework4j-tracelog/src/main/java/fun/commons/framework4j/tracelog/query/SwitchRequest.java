package fun.commons.framework4j.tracelog.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 开启动态日志开关请求 DTO。
 */
@Data
public class SwitchRequest {

    /** 维度类型 */
    @NotBlank
    private String type; // user | trace | url | order

    /** 维度值（userId / traceId / url pattern / orderId） */
    @NotBlank
    private String value;

    /** 提权目标级别 */
    @NotNull
    private String level; // DEBUG | TRACE

    /** 有效秒数（API 层强制 ≤ 3600） */
    @Min(1)
    @Max(3600)
    private int ttlSeconds = 3600;
}