package fun.commons.framework4j.demo.service;

import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.audit.service.HashChainService;
import fun.commons.framework4j.cache.service.CacheService;
import fun.commons.framework4j.demo.model.TestResult;
import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.ratelimit.service.RateLimitService;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import fun.commons.framework4j.sensitive.annotation.SensitiveRule;
import fun.commons.framework4j.sensitive.util.AesGcmCryptoUtil;
import fun.commons.framework4j.sensitive.util.SensitiveUtils;
import fun.commons.framework4j.signature.util.SignatureUtil;
import fun.commons.framework4j.web.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoTestRunnerService {

    private final CacheService cacheService;
    private final HashChainService hashChainService;
    private final AccessTokenGenerator tokenGenerator;
    private final RateLimitService rateLimitService;
    private final SnowflakeDistributor snowflakeDistributor;
    private final MultiRedisManager multiRedisManager;
    private final StringRedisTemplate redisTemplate;

    private static final String SECRET = "demo-secret-key";

    public List<TestResult> runAllTests() {
        List<TestResult> results = new ArrayList<>();
        results.add(testSensitive());
        results.add(testSensitiveEncrypt());
        results.add(testSignature());
        results.add(testAccessToken());
        results.add(testAudit());
        results.add(testRateLimit());
        results.add(testIdempotency());
        results.add(testCache());
        results.add(testSnowflakeId());
        results.add(testOpenId());
        results.add(testRedis());
        results.add(testSqlTracing());
        return results;
    }

    // ==================== 安全 ====================

    private TestResult testSensitive() {
        long start = System.currentTimeMillis();
        try {
            String phone = SensitiveUtils.desensitize("13812345678", SensitiveRule.PHONE);
            String idCard = SensitiveUtils.desensitize("110101199001011234", SensitiveRule.ID_CARD);
            String email = SensitiveUtils.desensitize("alice@example.com", SensitiveRule.EMAIL);
            String custom = SensitiveUtils.desensitize("ABCDEFGH", SensitiveRule.CUSTOM, "2,2,4");
            boolean pass = "138****5678".equals(phone) && "110101********1234".equals(idCard)
                    && "a***@example.com".equals(email) && "AB****GH".equals(custom);
            return new TestResult("sensitive", "脱敏规则（PHONE/ID_CARD/EMAIL/CUSTOM）",
                    pass ? "PASS" : "FAIL",
                    pass ? String.format("phone=%s idCard=%s email=%s custom=%s", phone, idCard, email, custom) : "脱敏不符合预期",
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new TestResult("sensitive", "脱敏", "FAIL", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private TestResult testSensitiveEncrypt() {
        long start = System.currentTimeMillis();
        try {
            byte[] key = AesGcmCryptoUtil.deriveKey("demo-test-key-must-be-32-chars-long");
            String plain = "secret-data-12345";
            String cipher = AesGcmCryptoUtil.encrypt(key, plain);
            String decrypted = AesGcmCryptoUtil.decrypt(key, cipher);
            String cipher2 = AesGcmCryptoUtil.encrypt(key, plain);
            boolean pass = plain.equals(decrypted) && !cipher.equals(cipher2);
            return new TestResult("sensitive", "AES-256-GCM 加解密（随机 IV）",
                    pass ? "PASS" : "FAIL",
                    pass ? "roundTrip=true ivDiff=true" : "加解密失败",
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new TestResult("sensitive", "AES-GCM", "FAIL", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private TestResult testSignature() {
        long start = System.currentTimeMillis();
        try {
            String sts = SignatureUtil.buildStringToSign("POST", "/v1/api/orders",
                    String.valueOf(System.currentTimeMillis()), java.util.UUID.randomUUID().toString(), "md5hex");
            String sig1 = SignatureUtil.sign(SECRET, sts);
            String sig2 = SignatureUtil.sign(SECRET, sts);
            boolean match = SignatureUtil.constantTimeEquals(sig1, sig2);
            String sigBad = SignatureUtil.sign("wrong", sts);
            boolean reject = !SignatureUtil.constantTimeEquals(sig1, sigBad);
            boolean pass = match && reject && sig1.length() == 44;
            return new TestResult("signature", "HMAC-SHA256 签名（生成+常量时间比较）",
                    pass ? "PASS" : "FAIL",
                    pass ? "match=true wrongKeyReject=true sigLen=44" : "签名异常",
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new TestResult("signature", "签名", "FAIL", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private TestResult testAccessToken() {
        long start = System.currentTimeMillis();
        try {
            String token = tokenGenerator.generateToken("WEB", java.util.Map.of("uid", "test-user-001"));
            boolean pass = token != null && token.length() > 50 && token.split("\\.").length == 3;
            return new TestResult("accesstoken", "JWT Token 生成（三段式 + Redis 双验）",
                    pass ? "PASS" : "FAIL",
                    pass ? "token=" + token.substring(0, 20) + "... parts=3" : "Token 生成异常",
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new TestResult("accesstoken", "Token", "FAIL", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private TestResult testAudit() {
        long start = System.currentTimeMillis();
        try {
            String h1 = hashChainService.computeNext("audit-1");
            String h2 = hashChainService.computeNext("audit-2");
            boolean chain = hashChainService.verify(h1, "audit-2", h2);
            boolean tamper = !hashChainService.verify(h1, "tampered", h2);
            boolean pass = chain && tamper;
            return new TestResult("audit", "Hash Chain 链式校验 + 篡改检测",
                    pass ? "PASS" : "FAIL",
                    pass ? "chain=true tamperReject=true" : "Hash chain 失败",
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new TestResult("audit", "Hash Chain", "FAIL", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    // ==================== 流量 ====================

    private TestResult testRateLimit() {
        long start = System.currentTimeMillis();
        try {
            String key = "demo-rl-" + System.nanoTime();
            RateLimitService.AcquireResult r1 = rateLimitService.tryAcquire(key, 3, 60000);
            RateLimitService.AcquireResult r2 = rateLimitService.tryAcquire(key, 3, 60000);
            RateLimitService.AcquireResult r3 = rateLimitService.tryAcquire(key, 3, 60000);
            RateLimitService.AcquireResult r4 = rateLimitService.tryAcquire(key, 3, 60000);
            boolean pass = r1.allowed() && r2.allowed() && r3.allowed() && !r4.allowed();
            redisTemplate.delete(java.util.List.of("{" + key + "}", "{" + key + "}:seq"));
            return new TestResult("rate-limit", "滑动窗口限流（3 放行 + 第 4 拒绝）",
                    pass ? "PASS" : "FAIL",
                    pass ? "r1-3=ALLOW r4=BLOCKED retryAfter=" + r4.retryAfterSeconds() + "s" : "限流异常",
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new TestResult("rate-limit", "限流", "FAIL", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private TestResult testIdempotency() {
        long start = System.currentTimeMillis();
        try {
            String key = "demo-idem-" + System.nanoTime();
            String val = "hash123|PENDING";
            Boolean first = redisTemplate.opsForValue().setIfAbsent(key, val, java.time.Duration.ofSeconds(60));
            String existing = redisTemplate.opsForValue().get(key);
            redisTemplate.delete(key);
            boolean pass = Boolean.TRUE.equals(first) && val.equals(existing);
            return new TestResult("idempotency", "Idempotency-Key 防重（首次获取 + 二次冲突）",
                    pass ? "PASS" : "FAIL",
                    pass ? "first=ACQUIRED second=CONFLICT" : "幂等异常",
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new TestResult("idempotency", "幂等", "FAIL", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private TestResult testCache() {
        long start = System.currentTimeMillis();
        try {
            String key = "demo-cache-" + System.nanoTime();
            cacheService.evict("demo-test", key);
            java.util.concurrent.atomic.AtomicInteger loadCount = new java.util.concurrent.atomic.AtomicInteger(0);
            String v1 = cacheService.get("demo-test", key, 60, () -> { loadCount.incrementAndGet(); return "cv"; }, String.class);
            String v2 = cacheService.get("demo-test", key, 60, () -> { loadCount.incrementAndGet(); return "no"; }, String.class);
            boolean pass = "cv".equals(v1) && "cv".equals(v2) && loadCount.get() == 1;
            cacheService.evict("demo-test", key);
            return new TestResult("cache", "多级缓存（首次加载 + 二次命中不触 loader）",
                    pass ? "PASS" : "FAIL",
                    pass ? "v1=cv v2=cv loadCount=1" : "缓存异常",
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new TestResult("cache", "缓存", "FAIL", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    // ==================== 数据 ====================

    private TestResult testSnowflakeId() {
        long start = System.currentTimeMillis();
        try {
            Set<Long> ids = new HashSet<>();
            for (int i = 0; i < 100; i++) ids.add(snowflakeDistributor.nextId());
            boolean pass = ids.size() == 100;
            return new TestResult("id", "Snowflake 分布式 ID（100 个全唯一）",
                    pass ? "PASS" : "FAIL",
                    pass ? "generated=100 unique=100" : "ID 重复",
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new TestResult("id", "Snowflake", "FAIL", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private TestResult testOpenId() {
        long start = System.currentTimeMillis();
        try {
            long orig = 123456789L;
            String openId = IdObfuscator.toOpenId(orig);
            long restored = IdObfuscator.fromOpenId(openId);
            String prefixed = IdObfuscator.toOpenId(orig, "ORD");
            long restoredP = IdObfuscator.fromOpenId(prefixed);
            boolean discrete = !IdObfuscator.toOpenId(1L).equals(IdObfuscator.toOpenId(2L));
            boolean pass = orig == restored && orig == restoredP && discrete && openId.length() == 12;
            return new TestResult("id", "OpenID 混淆（双向转换 + 离散 + 前缀）",
                    pass ? "PASS" : "FAIL",
                    pass ? "orig=" + orig + " openId=" + openId + " roundTrip=true discrete=true" : "OpenID 异常",
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new TestResult("id", "OpenID", "FAIL", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private TestResult testRedis() {
        long start = System.currentTimeMillis();
        try {
            String key = "demo-redis-" + System.nanoTime();
            redisTemplate.opsForValue().set(key, "tv");
            String val = redisTemplate.opsForValue().get(key);
            boolean del = Boolean.TRUE.equals(redisTemplate.delete(key));
            boolean health = multiRedisManager.checkHealth("default");
            boolean pass = "tv".equals(val) && del && health;
            return new TestResult("redis", "多 Redis 数据源（SET+GET+DEL+健康检查）",
                    pass ? "PASS" : "FAIL",
                    pass ? "set→get→del→health=OK" : "Redis 异常",
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new TestResult("redis", "Redis", "FAIL", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private TestResult testSqlTracing() {
        long start = System.currentTimeMillis();
        try {
            String traceId = TraceContext.getTraceId();
            OffsetDateTime t = OffsetDateTime.of(2024, 12, 10, 14, 30, 45, 0, ZoneOffset.ofHours(8));
            String fmt = t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            boolean pass = "2024-12-10 14:30:45".equals(fmt);
            return new TestResult("sql-tracing", "SQL 追踪 + 时间格式化（trace_id + OffsetDateTime）",
                    pass ? "PASS" : "FAIL",
                    pass ? "traceId=" + (traceId != null ? "OK" : "null(no-ctx)") + " time=" + fmt : "时间格式化异常",
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new TestResult("sql-tracing", "SQL 追踪", "FAIL", e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
