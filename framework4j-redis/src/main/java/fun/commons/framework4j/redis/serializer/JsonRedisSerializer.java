package fun.commons.framework4j.redis.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * JSON Redis 序列化器（基于 Jackson）
 * <p>
 * 使用 Jackson 进行 Redis 值的序列化和反序列化。
 * 替代原 {@code JsonRedisSerializer}，消除 fastjson2 autotype RCE 风险。
 *
 * <p><b>兼容性说明</b>：与原 fastjson2 版本不兼容——fastjson2 的 {@code WriteClassName}
 * 会在 JSON 前写入 {@code @type} 字段，Jackson 无此机制。若 Redis 中有旧数据含 {@code @type}，
 * 反序列化需清理或保留旧序列化器过渡。
 *
 * @param <T> 序列化对象类型
 * @since 2.0.0（从 fastjson2 迁移到 Jackson）
 */
public class JsonRedisSerializer<T> implements RedisSerializer<T> {

    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private final ObjectMapper objectMapper;
    private final Type type;

    public JsonRedisSerializer(Type type) {
        this(type, null);
    }

    public JsonRedisSerializer(Type type, ObjectMapper objectMapper) {
        this.type = type;
        this.objectMapper = objectMapper != null ? objectMapper : DEFAULT_OBJECT_MAPPER;
    }

    /**
     * v2.1 P0 修复：原每次 new JsonRedisSerializer 都新建 ObjectMapper + activateDefaultTyping，
     * 构造重且白名单校验每次重算。改 static final 单例共享。
     */
    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = buildDefaultObjectMapper();

    private static ObjectMapper buildDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // v2.1 安全加固：限制 default typing 的子类型白名单，仅允许业务包前缀 + JDK 安全集合/基本类型，
        // 防止攻击者通过 Redis 写入恶意 @class 触发 RCE（如 java.lang.Runtime / ProcessBuilder）
        mapper.activateDefaultTyping(
                com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .allowIfSubType("fun.commons.framework4j.")
                        .allowIfSubType("java.time.")
                        // v2.1 P1 修复：精确白名单 java.util 中的常用集合类（不再用前缀匹配 java.util.*，
                        // 避免 java.util.jar.JarFile / java.util.logging.* 等 gadget 链风险）
                        .allowIfSubType(java.util.HashMap.class)
                        .allowIfSubType(java.util.LinkedHashMap.class)
                        .allowIfSubType(java.util.TreeMap.class)
                        .allowIfSubType(java.util.ArrayList.class)
                        .allowIfSubType(java.util.LinkedList.class)
                        .allowIfSubType(java.util.HashSet.class)
                        .allowIfSubType(java.util.LinkedHashSet.class)
                        .allowIfSubType(java.util.TreeSet.class)
                        .allowIfSubType(java.util.concurrent.ConcurrentHashMap.class)
                        // v2.1: 精确白名单 java.lang 中的安全类（不再用前缀匹配 java.lang.*）
                        .allowIfSubType(java.lang.String.class)
                        .allowIfSubType(java.lang.Long.class)
                        .allowIfSubType(java.lang.Integer.class)
                        .allowIfSubType(java.lang.Double.class)
                        .allowIfSubType(java.lang.Float.class)
                        .allowIfSubType(java.lang.Boolean.class)
                        .allowIfSubType(java.lang.Number.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );
        return mapper;
    }

    @Override
    public byte[] serialize(T value) throws SerializationException {
        if (value == null) {
            return new byte[0];
        }
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception ex) {
            throw new SerializationException("Could not serialize: " + ex.getMessage(), ex);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            Class<?> rawClass = TypeFactory.defaultInstance().constructType(type).getRawClass();
            return (T) objectMapper.readValue(bytes, rawClass);
        } catch (Exception ex) {
            throw new SerializationException("Could not deserialize: " + ex.getMessage(), ex);
        }
    }
}
