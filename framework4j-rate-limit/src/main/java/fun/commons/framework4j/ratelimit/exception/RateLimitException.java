package fun.commons.framework4j.ratelimit.exception;

import fun.commons.framework4j.api.ApiCode;
import fun.commons.framework4j.web.ApiException;

/**
 * 限流异常
 *
 * @since 2.1.0
 */
public class RateLimitException extends ApiException {

    /** 限流被触发时，距下次可请求的秒数（用于 Retry-After 响应头） */
    private final long retryAfterSeconds;

    /** 当前窗口内已用配额 */
    private final int currentCount;

    /** 窗口配额上限 */
    private final int limit;

    /** 距重置时间的毫秒时间戳 */
    private final long resetAtMs;

    public RateLimitException(long retryAfterSeconds, int currentCount, int limit, long resetAtMs) {
        super(ApiCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后重试");
        this.retryAfterSeconds = retryAfterSeconds;
        this.currentCount = currentCount;
        this.limit = limit;
        this.resetAtMs = resetAtMs;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public int getLimit() {
        return limit;
    }

    public long getResetAtMs() {
        return resetAtMs;
    }
}
