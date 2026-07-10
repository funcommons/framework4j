package fun.commons.framework4j.id.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Redis 租约模式 WorkerID 策略
 * <p>
 * 特性:
 * <ul>
 *   <li>自动注册: 启动时向 Redis 申请 WorkerID (0-1023)</li>
 *   <li>自动续期: 守护线程每 6 小时维持租约</li>
 *   <li>自动回收: 节点崩溃后 24 小时自动释放</li>
 * </ul>
 *
 * @author LDX2T
 * @since 1.0.0
 */
@Slf4j
public class RedisWorkerIdStrategy implements WorkerIdStrategy {

    private final StringRedisTemplate redisTemplate;
    private final String appName;

    /**
     * Redis Key 前缀
     */
    private static final String KEY_PREFIX = "idsdk:worker:lease:";

    /**
     * 租约时间 (小时)
     */
    private static final long LEASE_HOURS = 24;

    /**
     * 心跳间隔 (小时)
     */
    private static final long HEARTBEAT_HOURS = 6;

    /**
     * 最大 WorkerID
     */
    private static final int MAX_WORKER_ID = 1024;

    /**
     * 心跳调度器
     */
    private static final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "id-sdk-heartbeat");
        t.setDaemon(true);
        return t;
    });

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

            // 尝试 0 - 1023 个坑位
            for (long id = 0; id < MAX_WORKER_ID; id++) {
                String key = KEY_PREFIX + appName + ":" + id;
                String value = ip + ";" + System.currentTimeMillis();

                Boolean success = redisTemplate.opsForValue()
                        .setIfAbsent(key, value, Duration.ofHours(LEASE_HOURS));

                if (Boolean.TRUE.equals(success)) {
                    log.info("[ID-SDK] WorkerID acquired via Redis Lease: {} (Key: {})", id, key);
                    this.acquiredWorkerId = id;
                    this.acquiredKey = key;
                    startHeartbeat(key, value);
                    return id;
                }
            }

            throw new RuntimeException("[ID-SDK] CRITICAL: No available WorkerID (0-1023) in Redis for app: " + appName);
        }
    }

    /**
     * 启动心跳续期
     */
    private void startHeartbeat(String key, String value) {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                redisTemplate.opsForValue().set(key, value, Duration.ofHours(LEASE_HOURS));
                log.debug("[ID-SDK] WorkerID lease renewed: {}", key);
            } catch (Exception e) {
                log.error("[ID-SDK] Failed to renew WorkerID lease for key: {}", key, e);
            }
        }, HEARTBEAT_HOURS, HEARTBEAT_HOURS, TimeUnit.HOURS);
    }

    /**
     * 获取本机 IP
     */
    private String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }

    /**
     * 获取已分配的 WorkerID (用于测试)
     */
    public long getAcquiredWorkerId() {
        return acquiredWorkerId;
    }

    /**
     * 释放 WorkerID (用于测试清理)
     */
    public void release() {
        if (acquiredKey != null) {
            try {
                redisTemplate.delete(acquiredKey);
                log.info("[ID-SDK] WorkerID released: {}", acquiredKey);
            } catch (Exception e) {
                log.warn("[ID-SDK] Failed to release WorkerID: {}", acquiredKey, e);
            }
        }
    }
}
