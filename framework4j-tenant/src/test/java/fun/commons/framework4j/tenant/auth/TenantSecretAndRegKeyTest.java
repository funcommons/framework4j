package fun.commons.framework4j.tenant.auth;

import fun.commons.framework4j.accesstoken.config.AccessTokenProperties;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.accesstoken.core.TokenKeyBuilder;
import fun.commons.framework4j.accesstoken.util.TokenUtils;
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
 * 密钥生命周期 + 注册码通道(泛化自 benefit4j TenantSecurityIT §5.5/§6.2)。
 * 前置:本地 Redis localhost:6379。
 */
class TenantSecretAndRegKeyTest {

    private static final String APP_NAME = "tenant-secret-test";
    private static final String HASH_SALT = "test-salt";

    private static LettuceConnectionFactory cf;
    private static StringRedisTemplate redis;
    private static AccessTokenGenerator generator;
    private static AccessTokenProperties atProps;

    private Framework4jTenantProperties props;
    private TenantSessionRevoker revoker;
    private TenantSecretService secretService;
    private RegistrationKeyService regKeyService;

    private final Map<Long, TenantEntity> tenants = new HashMap<>();

    @BeforeAll
    static void redisAndGenerator() {
        cf = new LettuceConnectionFactory("localhost", 6379);
        cf.afterPropertiesSet();
        redis = new StringRedisTemplate(cf);

        atProps = new AccessTokenProperties();
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
        props.getRegistrationKey().setEnabled(true);

        tenants.clear();
        revoker = new TenantSessionRevoker(redis, APP_NAME, atProps,
                List.of("TENANT", "APP", "OPS"));

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
        secretService = new TenantSecretService(store, revoker);
        regKeyService = new RegistrationKeyService(props, store, redis, APP_NAME);
    }

    // ---------- TenantSecretService(§5.5) ----------

    @Test
    @DisplayName("reset:旧钥入 prev + prevAt,新钥返回明文,存量会话撤销")
    void reset_rotatesAndRevokes() {
        TenantEntity t = tenant(2001L, "acme", "s-old");
        tenants.put(2001L, t);
        generator.generateToken("TENANT", Map.of("tenant_id", 2001L));
        assertThat(redis.hasKey(sessionKey(2001L, "TENANT"))).isTrue();

        ApiResponse<Map<String, Object>> resp = secretService.reset(2001L);
        assertThat(resp.getCode()).isZero();
        assertThat(resp.getData().get("tenant_secret")).isNotNull();
        assertThat(resp.getData().get("id")).isEqualTo(
                fun.commons.framework4j.id.util.IdObfuscator.toOpenId(2001L));

        TenantEntity after = tenants.get(2001L);
        assertThat(after.getTenantSecret()).isEqualTo(resp.getData().get("tenant_secret"));
        assertThat(after.getTenantSecretPrev()).isEqualTo("s-old");
        assertThat(after.getTenantSecretPrevAt()).isNotNull();
        assertThat(redis.hasKey(sessionKey(2001L, "TENANT"))).isFalse();
    }

    @Test
    @DisplayName("reset 不存在的租户 → 404")
    void reset_notFound() {
        assertThat(secretService.reset(999L).getCode()).isEqualTo(404);
    }

    // ---------- RegistrationKeyService(§6.2 五铁律) ----------

    @Test
    @DisplayName("发码 → 凭码注册 → 租户 ACTIVE + 密钥明文 + 码失效")
    void issueAndRegister() {
        ApiResponse<Map<String, Object>> issue = regKeyService.issue("test", 1, 1,
                Map.of("billing", true), Map.of("quotaDefault", 1000));
        assertThat(issue.getCode()).isZero();
        String code = (String) issue.getData().get("registration_key");
        assertThat(code).startsWith("RK-");

        ApiResponse<Map<String, Object>> reg = regKeyService.register(code, "newco", "a@b.com");
        assertThat(reg.getCode()).isZero();
        assertThat(reg.getData()).containsKeys("tenant_id", "tenant_secret");

        TenantEntity created = tenants.values().stream()
                .filter(t -> "newco".equals(t.getName())).findFirst().orElseThrow();
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(created.getChannel()).isEqualTo("SELF");
        assertThat(created.getPrivileges()).containsEntry("billing", true);

        // 码已失效(原子扣减后吊销)
        assertThat(regKeyService.register(code, "another", null).getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("码超次(uses=1 注册两次) → 第二次 401")
    void register_overUse_rejected() {
        ApiResponse<Map<String, Object>> issue = regKeyService.issue("test", 1, 1, null, null);
        String code = (String) issue.getData().get("registration_key");
        assertThat(regKeyService.register(code, "a", null).getCode()).isZero();
        assertThat(regKeyService.register(code, "b", null).getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("吊销 → 注册 401")
    void revoke_rejects() {
        ApiResponse<Map<String, Object>> issue = regKeyService.issue("test", 1, 1, null, null);
        String code = (String) issue.getData().get("registration_key");
        assertThat(regKeyService.revoke(code).getCode()).isZero();
        assertThat(regKeyService.register(code, "x", null).getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("无效码 → 401")
    void invalidCode_rejected() {
        assertThat(regKeyService.register("RK-bad", "x", null).getCode()).isEqualTo(401);
    }

    // ---------- 工具 ----------

    private String sessionKey(long tenantId, String type) {
        String hash = TokenUtils.calculateKeyHash(String.valueOf(tenantId), HASH_SALT);
        return APP_NAME + ":accesstoken:" + type + ":" + hash;
    }

    private static TenantEntity tenant(long id, String name, String secret) {
        TenantEntity t = new TenantEntity() {
        };
        t.setId(id);
        t.setName(name);
        t.setStatus("ACTIVE");
        t.setChannel("OPS");
        t.setTenantSecret(secret);
        t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now());
        t.setIsDeleted((short) 0);
        return t;
    }
}
