package fun.commons.framework4j.id.strategy;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * IP Hash WorkerID 策略
 * <p>
 * 根据本机 IP 地址计算 WorkerID，适用于无 Redis 环境
 * <p>
 * 注意: 此策略在 K8s 环境下可能产生冲突，建议使用 Redis 策略
 *
 * @since 1.0.0
 */
@Slf4j
public class IpHashWorkerIdStrategy implements WorkerIdStrategy {

    private volatile long workerId = -1;

    @Override
    public long getWorkerId() {
        if (workerId >= 0) {
            return workerId;
        }

        synchronized (this) {
            if (workerId >= 0) {
                return workerId;
            }

            try {
                InetAddress ip = InetAddress.getLocalHost();
                byte[] address = ip.getAddress();
                int hash = 0;
                for (byte b : address) {
                    hash = (hash << 8) | (b & 0xFF);
                }
                // v2.1 修复：Math.abs(Integer.MIN_VALUE) 仍为负，用位运算清符号位防负 workerId
                workerId = (hash & Integer.MAX_VALUE) % 1024;
                log.info("[ID-SDK] WorkerID generated via IP Hash: {} (IP: {})", workerId, ip.getHostAddress());
            } catch (UnknownHostException e) {
                log.warn("[ID-SDK] Unknown host, using random WorkerID");
                workerId = ThreadLocalRandom.current().nextLong(0, 1024);
            }

            return workerId;
        }
    }
}
