package fun.commons.framework4j.datetime;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * OffsetDateTime 动态格式化 Jackson 序列化器
 *
 * <p>根据 {@link TimeFormatStateHolder} 的状态决定输出格式：
 * <ul>
 *   <li>状态为本地格式时 → {@code yyyy-MM-dd HH:mm:ss}（GMT+8，对齐 mc-java-spec §5 时间处理）</li>
 *   <li>其他状态 → ISO-8601（默认，由 Jackson {@code JavaTimeModule} 处理）</li>
 * </ul>
 *
 * <p>集合类型（List/Set/Array）由 Jackson 自动遍历每个元素调用本序列化器，无需手写集合逻辑。
 *
 * @since 2.0.0（从 fastjson2 ValueFilter 迁移到 Jackson JsonSerializer）
 */
public class DynamicTimeFilter extends JsonSerializer<OffsetDateTime> {

    private static final DateTimeFormatter LOCAL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (TimeFormatStateHolder.shouldUseLocalFormat()) {
            gen.writeString(value.format(LOCAL_FORMATTER));
            return;
        }

        TimeFormatState currentState = TimeFormatStateHolder.getState();
        if (currentState != null && !currentState.isDefault()) {
            // 状态明确设置但非本地格式，按 ISO-8601 输出
            gen.writeString(value.toString());
            return;
        }

        // 默认 ISO-8601
        gen.writeString(value.toString());
    }
}
