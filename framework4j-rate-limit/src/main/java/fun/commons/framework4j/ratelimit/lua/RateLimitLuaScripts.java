package fun.commons.framework4j.ratelimit.lua;

import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/**
 * 限流 Lua 脚本常量
 * <p>
 * 对齐 mc-api-spec §8.5 + Java开发准则 §3.1（Lua 原子化）。
 *
 * @since 2.1.0
 */
public final class RateLimitLuaScripts {

    private RateLimitLuaScripts() {}

    /**
     * 滑动窗口限流（ZSET 实现）
     * <p>
     * 算法：移除 window 外旧记录 → 统计当前 count → 若 count < limit 则添加当前时间戳 → 返回结果
     * <p>
     * v2.1 P0 修复：member 用 Redis INCR 序列号拼接（替代 math.random），防同毫秒并发 score 撞车导致 ZADD 丢弃
     * <p>
     * KEYS[1] = rate limit key
     * <br>KEYS[2] = 序列号 key（{KEYS[1]}:seq）
     * <br>ARGV[1] = window ms（窗口大小，毫秒）
     * <br>ARGV[2] = limit（窗口内最大请求数）
     * <br>ARGV[3] = now ms（当前时间戳）
     * <p>
     * 返回数组：{allowed (1/0), current_count, reset_at_ms, max_limit}
     */
    public static final DefaultRedisScript<List> SLIDING_WINDOW = new DefaultRedisScript<>(
            "local now = tonumber(ARGV[3]); " +
            "local window = tonumber(ARGV[1]); " +
            "local limit = tonumber(ARGV[2]); " +
            "local cutoff = now - window; " +
            // 移除窗口外旧记录
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, cutoff); " +
            "local count = redis.call('ZCARD', KEYS[1]); " +
            "local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES'); " +
            "if count < limit then " +
            // v2.1 P0: member 用 INCR 序列号（防同毫秒 score 撞车丢请求）
            "  local seq = redis.call('INCR', KEYS[2]); " +
            "  redis.call('PEXPIRE', KEYS[2], window); " +
            "  redis.call('ZADD', KEYS[1], now, now .. '-' .. seq); " +
            "  redis.call('PEXPIRE', KEYS[1], window); " +
            "  return {1, count + 1, now + window, limit}; " +
            "else " +
            "  local resetAt; " +
            "  if oldest[2] then resetAt = tonumber(oldest[2]) + window; else resetAt = now + window; end; " +
            "  return {0, count, resetAt, limit}; " +
            "end",
            List.class);

    /**
     * v2.1 功能增强：固定窗口限流（Lua INCR + EXPIRE）
     * <p>最简单算法，允许窗口边界突刺。
     * <p>KEYS[1] = rate limit key; ARGV[1] = limit; ARGV[2] = ttl seconds
     * <p>返回 {allowed, current_count, ttl_seconds}
     */
    public static final DefaultRedisScript<List> FIXED_WINDOW = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); " +
            "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]); end; " +
            "local limit = tonumber(ARGV[1]); " +
            "if count <= limit then return {1, count, tonumber(ARGV[2])}; " +
            "else return {0, count, tonumber(ARGV[2])}; end",
            List.class);
}
