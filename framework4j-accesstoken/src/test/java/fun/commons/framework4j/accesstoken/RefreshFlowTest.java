package fun.commons.framework4j.accesstoken;

import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator.TokenPair;
import fun.commons.framework4j.accesstoken.core.RefreshTokenService;
import fun.commons.framework4j.accesstoken.config.AccessTokenProperties;
import fun.commons.framework4j.accesstoken.exception.AuthException;
import fun.commons.framework4j.accesstoken.util.TokenUtils;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Refresh Token 流程集成测试
 */
@SpringBootTest
@ActiveProfiles("embedded-redis")
class RefreshFlowTest {

    @Resource
    private AccessTokenGenerator generator;

    @Resource
    private RefreshTokenService refreshService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AccessTokenProperties properties;

    @SpringBootApplication
    static class TestApplication {}

    @BeforeEach
    void setUp() {
        cleanRedis();
    }

    @AfterEach
    void tearDown() {
        cleanRedis();
    }

    private void cleanRedis() {
        // v2.1 P0 修复：补清 access:revoked + refresh:family + refresh:revoked 残留，避免跨测试污染
        String[] patterns = {
                "accesstoken-embedded-test:*",
                "access:revoked:accesstoken-embedded-test",
                "refresh:family:accesstoken-embedded-test:*",
                "refresh:revoked:accesstoken-embedded-test:*"
        };
        for (String pattern : patterns) {
            Set<String> keys = stringRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        }
    }

    @Test
    @DisplayName("generateTokenPair 产出 access + refresh + familyId")
    void testGenerateTokenPair() {
        TokenPair pair = refreshService.generateTokenPair(Map.of("uid", "u1", "type", "WEB"));
        assertNotNull(pair.accessToken());
        assertNotNull(pair.refreshToken());
        assertNotNull(pair.familyId());
        assertTrue(pair.accessExpiresInSeconds() > 0);
        assertEquals(2592000L, pair.refreshExpiresInSeconds(), "默认 refresh TTL = 30d");
    }

    @Test
    @DisplayName("refreshAccessToken 一次性使用：第二次用旧 refresh 抛 10211")
    void testRefreshOneTimeUse() {
        TokenPair pair = refreshService.generateTokenPair(Map.of("uid", "u2", "type", "WEB"));
        String oldRefresh = pair.refreshToken();
        String oldFamily = pair.familyId();

        // 第一次轮转成功
        TokenPair rotated = refreshService.refreshAccessToken(oldRefresh);
        assertNotEquals(oldRefresh, rotated.refreshToken());
        assertEquals(oldFamily, rotated.familyId(), "familyId 保持不变");

        // 第二次用旧的 refresh → 10211（重用检测）
        AuthException ex = assertThrows(AuthException.class,
                () -> refreshService.refreshAccessToken(oldRefresh));
        assertEquals(10211, ex.getCode());
    }

    @Test
    @DisplayName("refreshAccessToken 旧 jti 已 consumed 后整个 family 撤销")
    void testReuseRevokesEntireFamily() {
        TokenPair pair = refreshService.generateTokenPair(Map.of("uid", "u3", "type", "WEB"));
        String familyId = pair.familyId();

        refreshService.refreshAccessToken(pair.refreshToken());  // 第一次 OK

        // 模拟攻击者复用旧 refresh
        assertThrows(AuthException.class,
                () -> refreshService.refreshAccessToken(pair.refreshToken()));

        // family 应被毒丸
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.hasKey("refresh:revoked:accesstoken-embedded-test:" + familyId)),
                "family 应被 poison");
        // 家族 hash 已被删除
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey("refresh:family:accesstoken-embedded-test:" + familyId)),
                "family hash 应已被 DEL");
    }

    @Test
    @DisplayName("refreshAccessToken 错把 access token 当 refresh 抛 10211")
    void testAccessTokenNotAcceptedAsRefresh() {
        // 直接用 access 类型的 generator.generateToken 拿一个 access token
        String accessToken = generator.generateToken("WEB", Map.of("uid", "u4", "type", "WEB"));

        AuthException ex = assertThrows(AuthException.class,
                () -> refreshService.refreshAccessToken(accessToken));
        assertEquals(10211, ex.getCode());
    }

    @Test
    @DisplayName("refreshAccessToken 空字符串抛 10211")
    void testEmptyStringRejected() {
        AuthException ex = assertThrows(AuthException.class,
                () -> refreshService.refreshAccessToken(""));
        assertEquals(10211, ex.getCode());
    }

    @Test
    @DisplayName("refreshAccessToken 篡改签名抛 10211")
    void testTamperedSignatureRejected() {
        TokenPair pair = refreshService.generateTokenPair(Map.of("uid", "u5", "type", "WEB"));
        String tampered = pair.refreshToken().substring(0, pair.refreshToken().length() - 4) + "AAAA";

        AuthException ex = assertThrows(AuthException.class,
                () -> refreshService.refreshAccessToken(tampered));
        assertEquals(10211, ex.getCode());
    }

    @Test
    @DisplayName("parseToken 解析 refresh 后 family 字段可被消费端读取")
    void testRefreshPayloadHasFamily() throws Exception {
        TokenPair pair = refreshService.generateTokenPair(Map.of("uid", "u6", "type", "WEB"));
        Map<String, Object> payload = TokenUtils.parseToken(pair.refreshToken(),
                "test-secret-key-for-embedded-redis-jwt-signing-must-be-at-least-32-chars-long-xx");
        assertEquals("refresh", payload.get("type"));
        assertEquals(pair.familyId(), payload.get("family"));
    }

    @Test
    @DisplayName("maxRotations 边界：达到上限后抛 10212（R9 回归）")
    void testMaxRotationsBoundary() {
        // 程序化设置 WEB 策略 max-rotations=2
        AccessTokenProperties.Policy webPolicy = properties.getPolicies().get("WEB");
        Integer originalMaxRotations = webPolicy.getMaxRotations();
        webPolicy.setMaxRotations(2);
        try {
            TokenPair pair = refreshService.generateTokenPair(Map.of("uid", "rot-test", "type", "WEB"));

            // 第 1 次轮转：generation 0→1，OK
            TokenPair pair2 = refreshService.refreshAccessToken(pair.refreshToken());
            assertNotNull(pair2.refreshToken());

            // 第 2 次轮转：generation 1→2，OK（达到 maxRotations=2）
            TokenPair pair3 = refreshService.refreshAccessToken(pair2.refreshToken());
            assertNotNull(pair3.refreshToken());

            // 第 3 次轮转：generation 2 >= maxRotations=2，应抛 10212
            AuthException ex = assertThrows(AuthException.class,
                    () -> refreshService.refreshAccessToken(pair3.refreshToken()));
            assertEquals(10212, ex.getCode(), "达到 maxRotations 应抛 10212");
        } finally {
            webPolicy.setMaxRotations(originalMaxRotations);
        }
    }

    @Test
    @DisplayName("refresh 后新 access TTL ≤ 2h（R8 P0 修复回归）")
    void testNewAccessTokenTtlAfterRefresh() {
        TokenPair pair = refreshService.generateTokenPair(Map.of("uid", "ttl-test", "type", "WEB"));
        TokenPair rotated = refreshService.refreshAccessToken(pair.refreshToken());

        // R8 修复：原 newAccessTtl = refreshTtl（30 天），客户端误判 access 30 天后过期
        // 修复后应 ≤ 2h（7200s）
        assertTrue(rotated.accessExpiresInSeconds() <= 7200,
                "新 access token TTL 应 ≤ 7200s（2h），实际: " + rotated.accessExpiresInSeconds());
        assertTrue(rotated.accessExpiresInSeconds() > 0,
                "新 access token TTL 应 > 0，实际: " + rotated.accessExpiresInSeconds());
    }

    @Test
    @DisplayName("refresh 后旧 access jti 进入撤销 Set（R8 P1 修复回归）")
    void testOldAccessTokenRevokedAfterRefresh() {
        TokenPair pair = refreshService.generateTokenPair(Map.of("uid", "revoke-test", "type", "WEB"));

        // 解析旧 access token 拿 jti
        String oldAccessToken = pair.accessToken();
        Map<String, Object> oldPayload = TokenUtils.parseToken(oldAccessToken,
                "test-secret-key-for-embedded-redis-jwt-signing-must-be-at-least-32-chars-long-xx");
        String oldAccessJti = (String) oldPayload.get("jti");

        // refresh 前旧 jti 未撤销
        assertFalse(generator.isRevoked(oldAccessJti), "refresh 前旧 access jti 不应在撤销 Set");

        // refresh 后旧 jti 应进入撤销 Set
        refreshService.refreshAccessToken(pair.refreshToken());
        assertTrue(generator.isRevoked(oldAccessJti),
                "refresh 后旧 access jti 应进入撤销 Set（R8 P1 修复点）");
    }

    @Test
    @DisplayName("撤销 Set TTL 单调延长（R7 P0 修复回归）")
    void testRevokedSetTtlMonotonicIncrease() throws Exception {
        // 生成两个 token：短 TTL（INVITE 60s）+ 长 TTL（ADMIN 7200s）
        TokenPair shortPair = refreshService.generateTokenPair(Map.of("inviteCode", "short-ttl", "type", "INVITE"));
        TokenPair longPair = refreshService.generateTokenPair(Map.of("uid", "long-ttl", "type", "ADMIN"));

        // 解析 jti
        String shortJti = (String) TokenUtils.parseToken(shortPair.accessToken(),
                "test-secret-key-for-embedded-redis-jwt-signing-must-be-at-least-32-chars-long-xx").get("jti");
        String longJti = (String) TokenUtils.parseToken(longPair.accessToken(),
                "test-secret-key-for-embedded-redis-jwt-signing-must-be-at-least-32-chars-long-xx").get("jti");

        String revokedKey = "access:revoked:accesstoken-embedded-test";

        // 先撤销短 TTL token（短 TTL 后写）
        generator.revokeToken(shortPair.accessToken());
        Long ttlAfterShort = stringRedisTemplate.getExpire(revokedKey, java.util.concurrent.TimeUnit.SECONDS);

        // 再撤销长 TTL token（应单调延长，不应被短 TTL 覆盖缩短）
        generator.revokeToken(longPair.accessToken());
        Long ttlAfterLong = stringRedisTemplate.getExpire(revokedKey, java.util.concurrent.TimeUnit.SECONDS);

        // R7 修复：长 TTL 后写应延长 Set TTL，不应缩短
        // 允许 2s 误差（两次 revoke 调用间的时间流逝导致 TTL 自然递减）
        assertTrue(ttlAfterLong >= ttlAfterShort - 2,
                "撤销 Set TTL 应单调延长（R7 修复），短 TTL=" + ttlAfterShort + " 后=" + ttlAfterLong);

        // 两个 jti 都应在撤销 Set
        assertTrue(generator.isRevoked(shortJti));
        assertTrue(generator.isRevoked(longJti));
    }

    @Test
    @DisplayName("Lua 原子轮转并发：50 线程同 refresh 应只 1 成功，其余 10211（R7 P0 修复回归）")
    void testConcurrentRefreshOnlyOneSucceeds() throws Exception {
        TokenPair pair = refreshService.generateTokenPair(Map.of("uid", "concurrent-refresh", "type", "WEB"));

        int threadCount = 50;
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger reusedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger otherErrorCount = new java.util.concurrent.atomic.AtomicInteger(0);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        refreshService.refreshAccessToken(pair.refreshToken());
                        successCount.incrementAndGet();
                    } catch (AuthException e) {
                        if (e.getCode() == 10211) {
                            reusedCount.incrementAndGet();
                        } else {
                            otherErrorCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        otherErrorCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();  // 同时开始
            assertTrue(doneLatch.await(30, java.util.concurrent.TimeUnit.SECONDS),
                    "所有线程应在 30s 内完成");

            // R7 修复核心：Lua 原子轮转保证只 1 个成功
            assertEquals(1, successCount.get(), "应只 1 个线程成功获取新 token");
            assertTrue(reusedCount.get() + otherErrorCount.get() == threadCount - 1,
                    "其余应抛 10211 或其他错误，success=" + successCount.get()
                            + " reused=" + reusedCount.get() + " other=" + otherErrorCount.get());
            // 至少应有 reused（被 Lua 标记 consumed 后抛 REUSED → 10211）
            assertTrue(reusedCount.get() > 0, "应至少有 1 个 REUSED，实际: " + reusedCount.get());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
        }
    }
}
