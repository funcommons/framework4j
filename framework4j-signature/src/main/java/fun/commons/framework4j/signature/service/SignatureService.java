package fun.commons.framework4j.signature.service;

import fun.commons.framework4j.api.ApiCode;
import fun.commons.framework4j.signature.config.SignatureProperties;
import fun.commons.framework4j.signature.exception.SignatureException;
import fun.commons.framework4j.signature.util.BodyMd5Util;
import fun.commons.framework4j.signature.util.SignatureUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.util.List;

/**
 * 签名校验服务
 * <p>
 * 五步校验（mc-java-security §6）：
 * <ol>
 *   <li>4 Header 齐全 → 缺失抛 10101</li>
 *   <li>timestamp ±5min → 过期抛 10102</li>
 *   <li>nonce 一次性（Redis SETNX EX 600s）→ 重复抛 10302</li>
 *   <li>查 secret → 找不到抛 10300</li>
 *   <li>HMAC 常量时间比较 → 不匹配抛 10302</li>
 * </ol>
 *
 * @since 2.1.0
 */
@Slf4j
public class SignatureService {

    /**
     * Lua 原子化 nonce 防重放（GET + SET NX EX 原子）
     * <p>KEYS[1] = nonce key; ARGV[1] = ttl seconds
     * <p>返回 1 = 首次（放行），0 = 已存在（重放）
     */
    private static final DefaultRedisScript<Long> NONCE_CHECK = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) then return 0 end; " +
            "redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[1]); " +
            "return 1",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final SignatureProperties properties;
    private final SecretProvider secretProvider;

    public SignatureService(StringRedisTemplate redisTemplate,
                            SignatureProperties properties,
                            SecretProvider secretProvider) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.secretProvider = secretProvider;
    }

    /**
     * 校验请求签名（五步）
     *
     * @param request HTTP 请求
     * @throws SignatureException 校验失败
     */
    public void validate(HttpServletRequest request) {
        SignatureProperties.HeaderNames headers = properties.getHeaderNames();
        String accessKey = request.getHeader(headers.getAccessKey());
        String timestamp = request.getHeader(headers.getTimestamp());
        String nonce = request.getHeader(headers.getNonce());
        String clientSignature = request.getHeader(headers.getSignature());

        // 1) Header 齐全
        if (isBlank(accessKey) || isBlank(timestamp) || isBlank(nonce) || isBlank(clientSignature)) {
            log.debug("[Signature] missing headers: ak={} ts={} nonce={} sig={}",
                    accessKey != null, timestamp != null, nonce != null, clientSignature != null);
            throw new SignatureException(ApiCode.PARAM_MISSING, "签名头缺失");
        }

        // 2) timestamp 容忍度
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new SignatureException(ApiCode.PARAM_FORMAT_ERROR, "timestamp 格式错误");
        }
        long now = System.currentTimeMillis();
        if (Math.abs(now - ts) > properties.getTimestampToleranceMs()) {
            log.debug("[Signature] timestamp expired: ts={} now={} drift={}", ts, now, now - ts);
            throw new SignatureException(ApiCode.PARAM_FORMAT_ERROR,
                    "签名时间戳过期或超前（容忍 ±" + properties.getTimestampToleranceMs() / 1000 + "s）");
        }

        // 3) nonce 一次性（Lua 原子）
        String nonceKey = properties.getNonceKeyPrefix() + ":" + accessKey + ":" + nonce;
        Long result;
        try {
            result = redisTemplate.execute(
                    NONCE_CHECK,
                    List.of(nonceKey),
                    String.valueOf(properties.getNonceTtlSeconds()));
        } catch (Exception e) {
            log.warn("[Signature] Redis nonce check failed: {}", e.getMessage());
            throw new SignatureException(ApiCode.MIDDLEWARE_ERROR, "签名校验中间件异常");
        }
        if (result == null || result == 0) {
            log.debug("[Signature] nonce replay detected: ak={} nonce={}", accessKey, nonce);
            throw new SignatureException(ApiCode.SIGNATURE_ERROR, "nonce 已被使用（重放攻击）");
        }

        // 4) 查 secret
        String secret = secretProvider.getSecret(accessKey);
        if (isBlank(secret)) {
            log.debug("[Signature] unknown accessKey: {}", accessKey);
            throw new SignatureException(ApiCode.UNAUTHORIZED, "未知的 AccessKey");
        }

        // 5) HMAC 常量时间比较
        String bodyMd5 = computeBodyMd5(request);
        String stringToSign = SignatureUtil.buildStringToSign(
                request.getMethod(),
                request.getRequestURI(),
                timestamp,
                nonce,
                bodyMd5);
        String serverSignature = SignatureUtil.sign(secret, stringToSign);

        if (!SignatureUtil.constantTimeEquals(clientSignature, serverSignature)) {
            log.debug("[Signature] mismatch: ak={} path={} expected={} got={}",
                    accessKey, request.getRequestURI(), serverSignature, clientSignature);
            throw new SignatureException(ApiCode.SIGNATURE_ERROR, "签名值不匹配");
        }
    }

    /** 从 ContentCachingRequestWrapper 取 body 算 MD5 hex */
    private String computeBodyMd5(HttpServletRequest request) {
        ContentCachingRequestWrapper wrapper = resolveCachingWrapper(request);
        return BodyMd5Util.md5Hex(wrapper);
    }

    /** 沿 wrapper 链找 ContentCachingRequestWrapper */
    private static ContentCachingRequestWrapper resolveCachingWrapper(HttpServletRequest request) {
        HttpServletRequest cur = request;
        for (int i = 0; i < 5 && cur != null; i++) {
            if (cur instanceof ContentCachingRequestWrapper w) return w;
            if (cur instanceof jakarta.servlet.http.HttpServletRequestWrapper w) {
                cur = (HttpServletRequest) w.getRequest();
            } else {
                break;
            }
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
