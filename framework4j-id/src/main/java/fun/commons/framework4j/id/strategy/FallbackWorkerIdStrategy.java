package fun.commons.framework4j.id.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

/**
 * fail-soft 降级装饰器（issue #26）：Redis 租约获取失败时降级 IP Hash，保证应用可启动。
 * <p>
 * 降级语义（默认 {@code framework4j.id.worker.fallback-to-ip=true} 时由 IdSdkAutoConfiguration 包装启用）：
 * <ul>
 *   <li>delegate 抛 {@link WorkerIdRedisException}（Redis 连接/访问失败）→ WARN + 降级
 *       {@link IpHashWorkerIdStrategy}，降级结果缓存，后续调用不再触碰 Redis</li>
 *   <li>槽位耗尽等其他 {@link IllegalStateException} → <b>原样抛出</b>：1024 槽全占时降级 IP Hash
 *       会在多实例间产生 worker id 碰撞（雪花 ID 重复），必须显式失败</li>
 * </ul>
 *
 * @since 1.8.0
 */
@Slf4j
public class FallbackWorkerIdStrategy implements WorkerIdStrategy, DisposableBean {

    private final WorkerIdStrategy delegate;
    private final WorkerIdStrategy fallback;

    /** 降级后缓存的 workerId（-1 = 尚未降级） */
    private volatile long fallbackWorkerId = -1;

    public FallbackWorkerIdStrategy(WorkerIdStrategy delegate, WorkerIdStrategy fallback) {
        this.delegate = delegate;
        this.fallback = fallback;
    }

    @Override
    public long getWorkerId() {
        if (fallbackWorkerId >= 0) {
            return fallbackWorkerId;
        }
        try {
            return delegate.getWorkerId();
        } catch (WorkerIdRedisException e) {
            synchronized (this) {
                if (fallbackWorkerId >= 0) {
                    return fallbackWorkerId;
                }
                log.warn("【ID-SDK】Redis WorkerID 申请失败（{}），fail-soft 降级为 IP-Hash 模式。"
                        + "多实例部署建议恢复 Redis 后重启，或设置 framework4j.id.worker.fallback-to-ip=false 改为启动失败",
                        e.getMessage());
                fallbackWorkerId = fallback.getWorkerId();
                return fallbackWorkerId;
            }
        }
    }

    @Override
    public void destroy() throws Exception {
        if (delegate instanceof DisposableBean disposable) {
            disposable.destroy();
        }
    }
}
