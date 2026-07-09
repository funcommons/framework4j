package fun.commons.framework4j.cache.lua;

import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 缓存 Lua 脚本（单飞加锁/解锁）
 *
 * @since 2.1.0
 */
public final class CacheLuaScripts {

    private CacheLuaScripts() {}

    /**
     * 单飞加锁
     * <p>KEYS[1] = lock key; ARGV[1] = token; ARGV[2] = ttl seconds
     * <p>返回 1 = 加锁成功，0 = 已被占用
     */
    public static final DefaultRedisScript<Long> LOCK = new DefaultRedisScript<>(
            "if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then return 1 else return 0 end",
            Long.class);

    /**
     * 单飞解锁（防误删他人锁：GET == token 才 DEL）
     * <p>KEYS[1] = lock key; ARGV[1] = token
     * <p>返回 1 = 解锁成功，0 = 锁已被他人持有
     */
    public static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);
}
