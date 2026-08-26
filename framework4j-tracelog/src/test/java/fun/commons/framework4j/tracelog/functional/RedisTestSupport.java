package fun.commons.framework4j.tracelog.functional;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

/**
 * 集成测试 Redis 引导（嵌入式优先，回退本机 Redis db 15）。
 * <p>
 * 与 {@code TraceLogStoreIntegrationTest} 相同的来源策略：嵌入式（16380）失败时
 * 回退 localhost:6379，固定连接 <b>db 15</b> 并只 {@code flushDb}（绝不触碰业务库）。
 */
final class RedisTestSupport {

    static final int EMBEDDED_PORT = 16380;
    static final int LOCAL_PORT = 6379;
    static final int TEST_DATABASE = 15;

    private RedisTestSupport() {}

    static final class Holder {
        RedisServer embedded;
        LettuceConnectionFactory factory;
        StringRedisTemplate redis;
        boolean available;
        String source;
    }

    static Holder bootstrap() {
        Holder h = new Holder();
        try {
            h.embedded = new RedisServer(EMBEDDED_PORT);
            h.embedded.start();
            connect(h, EMBEDDED_PORT, "embedded:" + EMBEDDED_PORT);
        } catch (Exception e) {
            System.err.println("【集成测试】嵌入式 Redis 启动失败: " + e.getMessage());
        }
        if (!h.available) {
            connect(h, LOCAL_PORT, "local:" + LOCAL_PORT + "/db" + TEST_DATABASE);
        }
        if (!h.available) {
            System.err.println("【集成测试】无可用 Redis（嵌入式 + 本机均失败）, 相关测试将跳过");
        } else {
            System.err.println("【集成测试】使用 Redis 来源: " + h.source);
        }
        return h;
    }

    private static void connect(Holder h, int port, String source) {
        try {
            RedisStandaloneConfiguration conf = new RedisStandaloneConfiguration("localhost", port);
            conf.setDatabase(TEST_DATABASE);
            h.factory = new LettuceConnectionFactory(conf);
            h.factory.afterPropertiesSet();
            h.redis = new StringRedisTemplate(h.factory);
            h.redis.afterPropertiesSet();
            h.available = "PONG".equalsIgnoreCase(h.redis.getConnectionFactory().getConnection().ping());
            h.source = source;
        } catch (Exception e) {
            if (h.factory != null) {
                try { h.factory.destroy(); } catch (Exception ignore) { /* nop */ }
                h.factory = null;
            }
            h.redis = null;
            h.available = false;
        }
    }

    static void shutdown(Holder h) {
        if (h.factory != null) h.factory.destroy();
        if (h.embedded != null) {
            try { h.embedded.stop(); } catch (Exception ignore) { /* nop */ }
        }
    }

    static void flush(Holder h) {
        h.redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }
}