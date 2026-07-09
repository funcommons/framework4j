package fun.commons.framework4j.redis.exception;

/**
 * Redis 数据源异常
 */
public class RedisDataSourceException extends RuntimeException {
    public RedisDataSourceException(String message) {
        super(message);
    }

    public RedisDataSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}