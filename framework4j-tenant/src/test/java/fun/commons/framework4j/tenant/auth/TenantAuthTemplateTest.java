package fun.commons.framework4j.tenant.auth;

import fun.commons.framework4j.accesstoken.config.AccessTokenProperties;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.accesstoken.util.TokenUtils;
import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.tenant.config.Framework4jTenantProperties;
import fun.commons.framework4j.tenant.entity.TenantEntity;
import fun.commons.framework4j.tenant.store.TenantStore;
import fun.commons.framework4j.web.ApiResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证模板行为矩阵(泛化自 benefit4j TenantSecurityIT,§5/§5.5/§8 契约)。
 * 前置:本地 Redis localhost:6379(仓内测试约定,同 accesstoken 模块)。
 */
class TenantAuthTemplateTest {

    private static final String APP_NAME = "tenant-auth-test";
    private static final String HASH_SALT = "test-salt";

    private static LettuceConnectionFactory cf;
    private static StringRedisTemplate redis;
    private static AccessTokenGenerator generator;

    private Framework4jTenantProperties props;
    private TenantAuthTemplate template;

    /** stub store:两张租户 —— 1001(ACTIVE,主钥 s-main,旧钥 s-old)+ 1002(SUSPEND) */
    private final Map<Long, TenantEntity> tenants = new HashMap<>();

    @BeforeAll
    static void redisAndGenerator() {
        cf = new LettuceConnectionFactory("localhost", 6379);
        cf.afterPropertiesSet();
        redis = new StringRedisTemplate(cf);

        AccessTokenProperties atProps = new AccessTokenProperties();
        atProps.setSecretKey("test-secret-key-for-jwt-must-be-at-least-32-chars!!");
        atProps.setHashSalt(HASH_SALT);
        atProps.setExpireTime(7200);
        AccessTokenProperties.Policy policy = new AccessTokenProperties.Policy();
        policy.setKey(List.of("tenant_id"));
        policy.setExpireTime(28800L);
        policy.setMaxUsage(-1);
        atProps.setPolicies(new HashMap<>(Map.of("TENANT", policy)));
        generator = new AccessTokenGenerator(atProps, redis, APP_NAME);
    }

    @AfterAll
    static void shutdown() {
        if (cf != null) {
            cf.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        props = new Framework4jTenantProperties();
        props.setTablePrefix("t_");
        props.getPlatform().setClientId("PLATFORM");
        props.getPlatform().setClientSecret("plat-secret");

        tenants.clear();
        tenants.put(1001L, tenant(1001L, "acme", "ACTIVE", "s-main", "s-old", OffsetDateTime.now().minusHours(1)));
        tenants.put(1002L, tenant(1002L, "suspended-co", "SUSPEND", "s-2", null, null));

        TenantStore store = new TenantStore() {
            @Override
            public TenantEntity findActiveById(long id) {
                TenantEntity t = tenants.get(id);
                return t != null && "ACTIVE".equals(t.getStatus()) ? t : null;
            }

            @Override
            public TenantEntity findActiveByName(String name) {
                return tenants.values().stream()
                        .filter(t -> "ACTIVE".equals(t.getStatus()) && t.getName().equals(name))
                        .findFirst().orElse(null);
            }

            @Override
            public void update(TenantEntity tenant) {
                tenants.put(tenant.getId(), tenant);
            }
        };
        template = new TenantAuthTemplate(props, store, redis, generator, APP_NAME);
        tenants.keySet().forEach(id -> redis.delete(failKey(id)));
        redis.delete(failKey("unknown"));
        redis.delete(failKey("PLATFORM"));
    }

    // ---------- 400 ----------

    @Test
    @DisplayName("grant_type 非 client_credentials → 400")
    void wrongGrantType() {
        assertThat(template.postToken("password", "1001", "s-main").getCode()).isEqualTo(400);
        assertThat(template.postToken(null, "1001", "s-main").getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("client_id / client_secret 缺失 → 400")
    void missingParams() {
        assertThat(template.postToken("client_credentials", "", "s-main").getCode()).isEqualTo(400);
        assertThat(template.postToken("client_credentials", "1001", "").getCode()).isEqualTo(400);
    }

    // ---------- 认证矩阵 ----------

    @Test
    @DisplayName("平台凭据 → tenant_id=0 claim + 会话 key 就位(合成租户,不依赖 DB)")
    void platformCredentials_issuePlatformToken() {
        ApiResponse<Map<String, Object>> resp = template.postToken("client_credentials", "PLATFORM", "plat-secret");
        assertThat(resp.getCode()).isZero();
        assertThat(resp.getData()).containsKeys("access_token", "token_type", "expires_in");
        assertThat(resp.getData().get("expires_in")).isEqualTo(28800L);
        assertThat(redis.hasKey(sessionKey(0L))).isTrue();
    }

    @Test
    @DisplayName("embed-claims 默认 true:JWT payload 嵌套 claims 键携带 tenant_id(Issue #23)")
    @SuppressWarnings("unchecked")
    void embedClaims_payloadCarriesTenantId() {
        String token = (String) template.postToken("client_credentials", "1001", "s-main").getData().get("access_token");
        Map<String, Object> payload = TokenUtils.parseToken(token, "test-secret-key-for-jwt-must-be-at-least-32-chars!!");
        assertThat(payload).containsKey("claims");
        Map<String, Object> claims = (Map<String, Object>) payload.get("claims");
        assertThat(claims.get("tenant_id")).isEqualTo(1001);

        // 平台身份同样可判:payload.claims.tenant_id = 0
        String platformToken = (String) template.postToken("client_credentials", "PLATFORM", "plat-secret").getData().get("access_token");
        Map<String, Object> platformPayload = TokenUtils.parseToken(platformToken, "test-secret-key-for-jwt-must-be-at-least-32-chars!!");
        assertThat((Map<String, Object>) platformPayload.get("claims")).containsEntry("tenant_id", 0);
    }

    @Test
    @DisplayName("embed-claims=false → payload 无 claims 键(回落 1.6.x 非自包含行为)")
    @SuppressWarnings("unchecked")
    void embedClaimsOff_payloadHasNoClaimsKey() {
        props.getAuth().setEmbedClaims(false);
        String token = (String) template.postToken("client_credentials", "1001", "s-main").getData().get("access_token");
        Map<String, Object> payload = TokenUtils.parseToken(token, "test-secret-key-for-jwt-must-be-at-least-32-chars!!");
        assertThat(payload).doesNotContainKey("claims");
        // 会话侧 claims 不受影响(校验信任源仍是 Redis 会话)
        assertThat(redis.hasKey(sessionKey(1001L))).isTrue();
    }

    @Test
    @DisplayName("租户主密钥:client_id 三形态(原始 id / OpenID / name)皆可认证")
    void tenantMasterSecret_threeClientIdForms() {
        for (String clientId : new String[]{"1001", IdObfuscator.toOpenId(1001L), "acme"}) {
            ApiResponse<Map<String, Object>> resp = template.postToken("client_credentials", clientId, "s-main");
            assertThat(resp.getCode()).as("client_id 形态: " + clientId).isZero();
        }
        assertThat(redis.hasKey(sessionKey(1001L))).isTrue();
    }

    @Test
    @DisplayName("宽限期内旧密钥可用(§5.5 双版本);过宽限期 → 401")
    void graceSecret_window() {
        assertThat(template.postToken("client_credentials", "1001", "s-old").getCode())
                .as("1h 前的旧钥,宽限期 24h 内 → 成功").isZero();

        tenants.put(1001L, tenant(1001L, "acme", "ACTIVE", "s-main", "s-old",
                OffsetDateTime.now().minusHours(25)));   // 过宽限期
        assertThat(template.postToken("client_credentials", "1001", "s-old").getCode())
                .as("25h 前的旧钥 → 401").isEqualTo(401);
    }

    @Test
    @DisplayName("非 ACTIVE 租户 / 错误密钥 / 不存在租户 → 401")
    void invalidCredentials() {
        assertThat(template.postToken("client_credentials", "1002", "s-2").getCode())   // SUSPEND
                .isEqualTo(401);
        assertThat(template.postToken("client_credentials", "1001", "wrong").getCode())
                .isEqualTo(401);
        assertThat(template.postToken("client_credentials", "unknown", "x").getCode())
                .isEqualTo(401);
    }

    // ---------- 防爆破(§8 #7) ----------

    @Test
    @DisplayName("连续失败 5 次锁定:第 6 次即使凭据正确也 429;成功路径清零")
    void bruteForceProtection() {
        for (int i = 0; i < 5; i++) {
            assertThat(template.postToken("client_credentials", "1001", "wrong-" + i).getCode())
                    .isEqualTo(401);
        }
        ApiResponse<Map<String, Object>> locked =
                template.postToken("client_credentials", "1001", "s-main");
        assertThat(locked.getCode()).isEqualTo(429);
        assertThat(redis.hasKey(failKey(1001L))).isTrue();

        redis.delete(failKey(1001L));   // 模拟窗口过期
        assertThat(template.postToken("client_credentials", "1001", "s-main").getCode())
                .as("窗口清除后恢复").isZero();
    }

    @Test
    @DisplayName("失败后成功 → 计数清零(不累积误锁)")
    void successResetsFailCounter() {
        assertThat(template.postToken("client_credentials", "1001", "wrong").getCode()).isEqualTo(401);
        assertThat(template.postToken("client_credentials", "1001", "s-main").getCode()).isZero();
        assertThat(redis.hasKey(failKey(1001L))).isFalse();
    }

    // ---------- 工具 ----------

    private String failKey(Object clientId) {
        return APP_NAME + TenantAuthTemplate.FAIL_KEY_PREFIX + clientId;
    }

    private String sessionKey(long tenantId) {
        String hash = TokenUtils.calculateKeyHash(String.valueOf(tenantId), HASH_SALT);
        return APP_NAME + ":accesstoken:TENANT:" + hash;
    }

    private static TenantEntity tenant(long id, String name, String status, String secret,
                                       String prev, OffsetDateTime prevAt) {
        TenantEntity t = new TenantEntity() {
        };
        t.setId(id);
        t.setName(name);
        t.setStatus(status);
        t.setTenantSecret(secret);
        t.setTenantSecretPrev(prev);
        t.setTenantSecretPrevAt(prevAt);
        return t;
    }
}
