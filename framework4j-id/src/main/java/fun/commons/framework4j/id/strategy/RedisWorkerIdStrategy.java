package fun.commons.framework4j.id.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Redis 租约模式 WorkerID 策略
 * <p>
 * 特性:
 * <ul>
 *   <li>自动注册: 启动时向 Redis 申请 WorkerID (0-1023)</li>
 *   <li>自动续期: 守护线程每 6 小时维持租约</li>
 *   <li>自动回收: 节点崩溃后 24 小时自动释放</li>
 *   <li>v2.1 修复：scheduler 改实例字段 + DisposableBean，避免资源泄漏</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
public class RedisWorkerIdStrategy implements WorkerIdStrategy, DisposableBean {

    private final StringRedisTemplate redisTemplate;
    private final String appName;

    private static final String KEY_PREFIX = "idsdk:worker:lease:";
    private static final long LEASE_HOURS = 24;
    private static final long HEARTBEAT_HOURS = 6;
    private static final int MAX_WORKER_ID = 1024;

    /**
     * v2.1 P1 修复：Lua 原子扫描首个空 slot 并占位。
     * <p>原实现 Java 循环 setIfAbsent，1024 实例同时启动时 O(1024) round-trip；
     * Lua 单次往返完成"扫描 + 占位"，竞争窗口从 O(N) 降到 O(1)。
     * <p>KEYS = 空（动态拼 key），ARGV[1]=keyPrefix, ARGV[2]=value, ARGV[3]=ttlSeconds, ARGV[4]=maxId
     * <p>返回: 首个成功占位的 workerId (number)，或 -1（全满）
     */
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            "for id = 0, tonumber(ARGV[4]) - 1 do " +
            "  local k = ARGV[1] .. id " +
            "  if redis.call('SET', k, ARGV[2], 'NX', 'EX', ARGV[3]) then " +
            "    return id " +
            "  end " +
            "end " +
            "return -1",
            Long.class);

    /**
     * v2.1 P0 修复：心跳 CAS 续期 Lua。
     * <p>原 Round 8 P1 #206 修复用 setIfAbsent 续期，但 NX 语义导致 key 已存在时必返回 false，
     * 每 6h 误判"租约丢失"清空 workerId。改用 Lua：先 GET 比对 value，相同才 EXPIRE 续期。
     * <p>KEYS[1] = leaseKey; ARGV[1] = expectedValue; ARGV[2] = ttlSeconds
     * <p>返回: 1 = 续期成功；0 = value 不匹配（租约已丢失，需重新申请）
     */
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('EXPIRE', KEYS[1], ARGV[2]) " +
            "end " +
            "return 0",
            Long.class);

    /** v2.1: 改实例字段，避免多上下文共享 + 实现 DisposableBean 优雅关闭 */
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "id-sdk-heartbeat");
        t.setDaemon(true);
        return t;
    });

    /** v2.1: 持有 schedule 句柄，release/destroy 时取消 */
    private volatile ScheduledFuture<?> heartbeatTask;

    private volatile long acquiredWorkerId = -1;
    private volatile String acquiredKey;

    public RedisWorkerIdStrategy(StringRedisTemplate redisTemplate, String appName) {
        this.redisTemplate = redisTemplate;
        this.appName = appName;
    }

    @Override
    public long getWorkerId() {
        if (acquiredWorkerId >= 0) {
            return acquiredWorkerId;
        }

        synchronized (this) {
            if (acquiredWorkerId >= 0) {
                return acquiredWorkerId;
            }

            String ip = getLocalIp();
            String value = ip + ";" + System.currentTimeMillis();
            String keyPrefix = KEY_PREFIX + appName + ":";

            // v2.1 P1 修复：Lua 原子扫描首个空 slot，单次往返，消除 1024 次循环 round-trip
            Long acquiredId;
            try {
                acquiredId = redisTemplate.execute(
                        ACQUIRE_SCRIPT,
                        Collections.emptyList(),
                        keyPrefix, value,
                        String.valueOf(LEASE_HOURS * 3600),
                        String.valueOf(MAX_WORKER_ID));
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[ID-SDK] Redis WorkerID 申请失败: " + e.getMessage(), e);
            }

            if (acquiredId == null || acquiredId < 0) {
                throw new IllegalStateException(
                        "[ID-SDK] CRITICAL: No available WorkerID (0-1023) in Redis for app: " + appName);
            }

            long id = acquiredId;
            String key = keyPrefix + id;
            log.info("[ID-SDK] WorkerID acquired via Redis Lease: {} (Key: {})", id, key);
            this.acquiredWorkerId = id;
            this.acquiredKey = key;
            startHeartbeat(key, value);
            return id;
        }
    }

    private void startHeartbeat(String key, String value) {
        heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                // v2.1 P0 修复：CAS 续期（Lua：value 匹配才 EXPIRE）。
                // 原 Round 8 P1 #206 用 setIfAbsent 但 NX 语义导致续期必失败，每 6h 误丢 workerId。
                Long renewed = redisTemplate.execute(
                        RENEW_SCRIPT,
                        Collections.singletonList(key),
                        value, String.valueOf(LEASE_HOURS * 3600));
                if (renewed == null || renewed == 0) {
                    log.error("[ID-SDK] WorkerID lease lost (key={}), releasing", key);
                    acquiredWorkerId = -1;
                    acquiredKey = null;
                    return;
                }
                log.debug("[ID-SDK] WorkerID lease renewed: {}", key);
            } catch (Exception e) {
                log.error("[ID-SDK] Failed to renew WorkerID lease for key: {}", key, e);
            }
        }, HEARTBEAT_HOURS, HEARTBEAT_HOURS, TimeUnit.HOURS);
    }

    private String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }

    public long getAcquiredWorkerId() {
        return acquiredWorkerId;
    }

    /**
     * 释放 WorkerID（测试清理用）。
     * <p>v2.1: 同时取消心跳任务。
     */
    public void release() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
        if (acquiredKey != null) {
            try {
                redisTemplate.delete(acquiredKey);
                log.info("[ID-SDK] WorkerID released: {}", acquiredKey);
            } catch (Exception e) {
                log.warn("[ID-SDK] Failed to release WorkerID: {}", acquiredKey, e);
            }
        }
        // v2.1 P1 修复：重置状态，避免 release 后再调 getWorkerId() 返回已释放的 id
        // （Redis key 已删，其他节点可能拿到相同 id）
        synchronized (this) {
            acquiredWorkerId = -1;
            acquiredKey = null;
        }
    }

    /**
     * Spring 上下文关闭时调用：取消心跳 + 关闭线程池 + 删除 key。
     */
    @Override
    public void destroy() {
        log.info("[ID-SDK] RedisWorkerIdStrategy destroy, releasing resources");
        release();
        heartbeatScheduler.shutdownNow();
        try {
            if (!heartbeatScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                log.warn("[ID-SDK] heartbeatScheduler did not terminate in 2s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

