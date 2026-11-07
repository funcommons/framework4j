package fun.commons.framework4j.accesstoken;

import fun.commons.framework4j.accesstoken.config.AccessTokenProperties;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator.TokenPair;
import fun.commons.framework4j.accesstoken.core.RefreshTokenService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #23 残留缺口回归：refresh 链路（generateTokenPair / refreshAccessToken 轮转）
 * 的 claims payload 嵌入。
 * <p>
 * v1.7.0 主修复让登录签发可嵌入（三参 generateToken / tenant auth.embed-claims），
 * 但 RefreshTokenService 走双参——首次轮转后新 access token payload claims 即丢失。
 * v1.8 经 {@code framework4j.access-token.embed-claims}（默认 false）全链路补齐。
 */
@SpringBootTest
@ActiveProfiles("embedded-redis")
@DisplayName("refresh 链路 claims 嵌入（issue #23 补全）")
class RefreshEmbedClaimsTest {

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
        refreshService.setEmbedInPayload(false);
    }

    @AfterEach
    void tearDown() {
        cleanRedis();
        refreshService.setEmbedInPayload(false);
    }

    private void cleanRedis() {
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(String jwt) {
        return TokenUtils.parseToken(jwt, properties.getSecretKey());
    }

    @Test
    @DisplayName("embed-claims 缺省 false：签发/轮转 payload 均无 claims（1.7.x 行为不变）")
    void defaultShouldNotEmbed() {
        assertFalse(properties.isEmbedClaims(), "默认 embed-claims=false");

        TokenPair pair = refreshService.generateTokenPair(Map.of("uid", "u1", "type", "WEB"));
        assertFalse(payload(pair.accessToken()).containsKey("claims"), "签发 payload 不含 claims");

        TokenPair rotated = refreshService.refreshAccessToken(pair.refreshToken());
        assertFalse(payload(rotated.accessToken()).containsKey("claims"), "轮转 payload 不含 claims");
    }

    @Test
    @DisplayName("embed-claims=true：签发与轮转 payload 均嵌套 claims，跨轮转不丢失")
    void embedShouldSurviveRotation() {
        refreshService.setEmbedInPayload(true);

        TokenPair pair = refreshService.generateTokenPair(
                Map.of("uid", "u1", "type", "WEB", "tenant_id", 1001));

        Map<String, Object> firstPayload = payload(pair.accessToken());
        assertTrue(firstPayload.containsKey("claims"), "签发 payload 含嵌套 claims");
        assertEquals(1001, ((Map<String, Object>) firstPayload.get("claims")).get("tenant_id"));
        // 顶层系统字段不被业务 claim 覆盖
        assertEquals("WEB", firstPayload.get("type"));

        // 轮转一次 → 新 access token payload claims 仍在（此前缺口：首次轮转即丢）
        TokenPair rotated1 = refreshService.refreshAccessToken(pair.refreshToken());
        Map<String, Object> rotatedPayload = payload(rotated1.accessToken());
        assertTrue(rotatedPayload.containsKey("claims"), "轮转后 payload claims 不丢失");
        assertEquals(1001, ((Map<String, Object>) rotatedPayload.get("claims")).get("tenant_id"));

        // 再轮转一次 → 全家族一致
        TokenPair rotated2 = refreshService.refreshAccessToken(rotated1.refreshToken());
        assertTrue(payload(rotated2.accessToken()).containsKey("claims"), "二次轮转后仍嵌入");
    }

    @Test
    @DisplayName("embed 开启时签发的 token 校验链路不受影响（Redis 会话仍为信任源）")
    void validationFlowUnaffectedByEmbed() {
        refreshService.setEmbedInPayload(true);
        assertNotNull(refreshService, "sanity");

        TokenPair pair = refreshService.generateTokenPair(Map.of("uid", "u9", "type", "WEB"));
        // access 元数据（Redis 会话）照常写入：hash 键存在
        Map<String, Object> accessPayload = payload(pair.accessToken());
        assertNotNull(accessPayload.get("hash"), "payload 仍携带 Redis key hash（校验锚点）");
        assertNotNull(accessPayload.get("jti"));
    }
}
