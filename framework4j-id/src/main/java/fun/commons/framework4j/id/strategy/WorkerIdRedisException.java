package fun.commons.framework4j.id.strategy;

/**
 * Redis 不可达/访问失败导致的 WorkerID 申请异常（issue #26）。
 * <p>
 * 继承 {@link IllegalStateException} 保持向后兼容——既有捕获 IllegalStateException 的代码
 * （含 1.7.x 及之前的消费方）行为不变。
 * <p>
 * 与「槽位耗尽」（普通 {@link IllegalStateException}）区分开：连接类失败可安全降级 IP Hash
 * （{@link FallbackWorkerIdStrategy} 只捕获本异常），槽位耗尽降级会产生 worker id 碰撞风险，必须显式失败。
 *
 * @see RedisWorkerIdStrategy
 * @see FallbackWorkerIdStrategy
 * @since 1.8.0
 */
public class WorkerIdRedisException extends IllegalStateException {

    public WorkerIdRedisException(String message, Throwable cause) {
        super(message, cause);
    }
}
