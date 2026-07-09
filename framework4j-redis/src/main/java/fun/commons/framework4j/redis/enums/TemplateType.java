package fun.commons.framework4j.redis.enums;

/**
 * Redis 模板类型枚举
 *
 * @since 1.0.0
 */
public enum TemplateType {

    /**
     * 字符串模板类型
     * 生成 StringRedisTemplate，用于简单的字符串 K-V 操作
     */
    STRING,

    /**
     * 对象模板类型
     * 生成 RedisTemplate&lt;String, Object&gt;，用于复杂对象序列化
     * 使用 Jackson 作为序列化器
     */
    OBJECT
}
