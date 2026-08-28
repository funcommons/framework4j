package fun.commons.framework4j.tenant.auth;

import fun.commons.framework4j.tenant.config.Framework4jTenantProperties;
import fun.commons.framework4j.tenant.entity.TenantEntity;
import fun.commons.framework4j.tenant.store.TenantStore;
import fun.commons.framework4j.web.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 注册码通道(租户设计 §6.2 信任分级 L2)—— 平台运营发码,消费方凭码自助注册,凭码即 ACTIVE。
 * <p>
 * 五铁律:①注册码仅 Redis 存储(无 DB 表,极简起步);②原子扣减(Lua/DECR,防并发超用);
 * ③预绑配置档(发码时可指定 privileges/config 模板,注册即应用);④可吊销;⑤注册即发密钥(明文只显一次)。
 * <p>
 * key 结构:{appName}:tenant:regkey:{code} → 剩余次数;{appName}:tenant:regkey:meta:{code} → JSON 元数据。
 */
@Slf4j
public class RegistrationKeyService {

    public static final String KEY_PREFIX = ":tenant:regkey:";
    public static final String META_PREFIX = ":tenant:regkey:meta:";

    private final Framework4jTenantProperties properties;
    private final TenantStore tenantStore;
    private final StringRedisTemplate redis;
    private final String appName;

    public RegistrationKeyService(Framework4jTenantProperties properties, TenantStore tenantStore,
                                  StringRedisTemplate redis, String appName) {
        this.properties = properties;
        this.tenantStore = tenantStore;
        this.redis = redis;
        this.appName = appName;
    }

    /**
     * 发码(平台域)。返回注册码明文 + 预绑配置档。
     */
    public ApiResponse<Map<String, Object>> issue(String label, Integer uses, Integer ttlHours,
                                                   Map<String, Object> privileges, Map<String, Object> config) {
        Framework4jTenantProperties.RegistrationKey cfg = properties.getRegistrationKey();
        int effectiveUses = uses != null ? uses : cfg.getDefaultUses();
        int effectiveTtl = ttlHours != null ? ttlHours : cfg.getDefaultTtlHours();
        if (effectiveUses <= 0 || effectiveTtl <= 0) {
            return ApiResponse.fail(400, "uses/ttl 必须为正");
        }

        String code = "RK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String key = appName + KEY_PREFIX + code;
        redis.opsForValue().set(key, String.valueOf(effectiveUses), Duration.ofHours(effectiveTtl));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("label", label == null ? "" : label);
        meta.put("uses", effectiveUses);
        meta.put("issued_at", OffsetDateTime.now().toString());
        meta.put("privileges", privileges == null ? Map.of() : privileges);
        meta.put("config", config == null ? Map.of() : config);
        redis.opsForValue().set(appName + META_PREFIX + code,
                fun.commons.framework4j.tenant.util.Jsons.write(meta), Duration.ofHours(effectiveTtl));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registration_key", code);
        result.put("uses", effectiveUses);
        result.put("ttl_hours", effectiveTtl);
        log.info("[RegKey] 发码: code={}, label={}, uses={}, ttl={}h", code, label, effectiveUses, effectiveTtl);
        return ApiResponse.success(result);
    }

    /**
     * 凭码注册(开放域,凭码即 ACTIVE)。原子扣减 + 建租户 + 发密钥(明文只显一次)。
     */
    public ApiResponse<Map<String, Object>> register(String code, String tenantName, String email) {
        if (!hasText(code) || !hasText(tenantName)) {
            return ApiResponse.fail(400, "registration_key 和 tenant_name 不能为空");
        }
        String key = appName + KEY_PREFIX + code;
        Long remaining = redis.opsForValue().decrement(key);   // 原子扣减
        if (remaining == null || remaining < 0) {
            if (remaining != null) {
                redis.opsForValue().increment(key);   // 扣过头回滚(并发保护)
            }
            return ApiResponse.fail(401, "注册码无效、已用完或已过期");
        }

        Map<String, Object> meta = readMeta(code);
        TenantEntity tenant = newTenant(tenantName, email, meta);
        tenantStore.update(tenant);   // insert(实体子类 SPI 的 BaseMapper.insert 语义)
        revokeKey(key, code);          // 一码一用(uses>1 时此处已是最后一次,仍吊销防复用)

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenant_id", fun.commons.framework4j.id.util.IdObfuscator.toOpenId(tenant.getId()));
        result.put("tenant_secret", tenant.getTenantSecret());   // 明文只显一次
        log.info("[RegKey] 凭码注册: code={}, tenantId={}, name={}", code, tenant.getId(), tenantName);
        return ApiResponse.success(result);
    }

    /**
     * 吊销(平台域)。
     */
    public ApiResponse<Void> revoke(String code) {
        revokeKey(appName + KEY_PREFIX + code, code);
        return ApiResponse.success();
    }

    // ---------- 内部 ----------

    private TenantEntity newTenant(String name, String email, Map<String, Object> meta) {
        TenantEntity t = new fun.commons.framework4j.tenant.entity.TenantEntity() {
        };
        // id 由雪花生成(ASSIGN_ID);此处显式置 null 让 MyBatis Plus 触发
        t.setName(name);
        t.setEmail(email);
        t.setChannel("SELF");
        t.setStatus("ACTIVE");
        t.setTenantSecret(UUID.randomUUID().toString().replace("-", ""));
        t.setPrivileges(asMap(meta.get("privileges")));
        t.setConfig(asMap(meta.get("config")));
        t.setOem(Map.of());
        t.setExt(Map.of());
        t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now());
        t.setIsDeleted((short) 0);
        return t;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    private Map<String, Object> readMeta(String code) {
        String json = redis.opsForValue().get(appName + META_PREFIX + code);
        if (json == null) {
            return Map.of();
        }
        return fun.commons.framework4j.tenant.util.Jsons.readMap(json);
    }

    private void revokeKey(String key, String code) {
        redis.delete(key);
        redis.delete(appName + META_PREFIX + code);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isEmpty();
    }
}
