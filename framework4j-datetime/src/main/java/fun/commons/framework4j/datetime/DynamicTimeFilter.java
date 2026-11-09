package fun.commons.framework4j.datetime;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * OffsetDateTime 动态格式化 Jackson 序列化器
 *
 * <p>根据 {@link TimeFormatStateHolder} 的状态决定输出格式：
 * <ul>
 *   <li>状态为本地格式时 → {@code yyyy-MM-dd HH:mm:ss}，<strong>先换算到目标时区墙钟</strong>
 *       （v1.10，issue #30-②：此前直接按值自带 offset 排墙钟，UTC 值错 8 小时；
 *       目标时区经 {@code framework4j.datetime.local-zone} 配置，缺省 {@code Asia/Shanghai}）</li>
 *   <li>其他状态（注解 ISO / 默认）→ <strong>定长</strong> ISO-8601
 *       {@code yyyy-MM-dd'T'HH:mm:ssXXX}（v1.10，issue #30-①：此前 {@code toString()} 为变长形，
 *       秒与纳秒恰为 0 时省略秒段，同一字段 wire 形态随数据漂移）</li>
 * </ul>
 *
 * <p>集合类型（List/Set/Array）由 Jackson 自动遍历每个元素调用本序列化器，无需手写集合逻辑。
 *
 * @since 2.0.0（从 fastjson2 ValueFilter 迁移到 Jackson JsonSerializer）
 */
public class DynamicTimeFilter extends JsonSerializer<OffsetDateTime> {

    /** 本地格式分支的目标时区缺省值（issue #30-②） */
    public static final ZoneId DEFAULT_LOCAL_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter LOCAL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * v1.10 (issue #30-①)：定长 ISO——{@code OffsetDateTime.toString()} 是变长 ISO-8601
     * （秒与纳秒为 0 时省略秒段），改用固定 pattern 保证 wire 形态稳定。
     */
    private static final DateTimeFormatter ISO_FIXED_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /** 本地格式分支换算的目标时区 */
    private final ZoneId localZone;

    /** 缺省目标时区 {@code Asia/Shanghai} */
    public DynamicTimeFilter() {
        this(DEFAULT_LOCAL_ZONE);
    }

    public DynamicTimeFilter(ZoneId localZone) {
        this.localZone = localZone == null ? DEFAULT_LOCAL_ZONE : localZone;
    }

    @Override
    public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (TimeFormatStateHolder.shouldUseLocalFormat()) {
            // v1.10 (issue #30-②)：先换算到目标时区墙钟再格式化——
            // 此前直接 format(value)，UTC offset 值排 UTC 墙钟，北京墙钟错 8 小时
            gen.writeString(value.atZoneSameInstant(localZone).format(LOCAL_FORMATTER));
            return;
        }

        // 注解 ISO 态与默认态统一走定长 ISO（v1.10 issue #30-①，两分支此前均为变长 toString()）
        gen.writeString(value.format(ISO_FIXED_FORMATTER));
    }
}