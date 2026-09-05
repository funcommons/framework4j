package fun.commons.framework4j.accesstoken.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * createToken 业务 claims 嵌入(Issue #23)—— 纯 JWT 构造行为,不依赖 Redis。
 *
 * 契约:
 * 1. businessClaims 以嵌套 payload.claims 键写入(不拍平)
 * 2. 业务 claim 与系统字段同名时不得覆盖系统值(type/jti/nonce/hash/iss/sub/iat/exp)
 * 3. null/空 claims → 无 claims 键(1.6.x 行为不变)
 * 4. 签名覆盖含 claims 的 payload(篡改 claims → 校验失败)
 */
class TokenUtilsClaimsPayloadTest {

    private static final String SECRET = "unit-test-secret-key-must-be-long-enough!!";
    private static final String TOKEN = "unit-token";
    private static final String NONCE = "n-123";
    private static final String HASH = "h-abc";
    /** parseToken 校验 exp > now — 时间戳必须动态 */
    private static final long IAT = System.currentTimeMillis();
    private static final long EXP = IAT + 3600_000;

    private Map<String, Object> parse(String jwt) {
        return TokenUtils.parseToken(jwt, SECRET);
    }

    @Test
    @DisplayName("businessClaims 嵌套写入 payload.claims;系统字段不受影响")
    @SuppressWarnings("unchecked")
    void claimsNestedUnderClaimsKey() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tenant_id", 1001);
        String jwt = TokenUtils.createToken(TOKEN, NONCE, HASH, "app", IAT, EXP, "jti-1",
                null, claims, SECRET);

        Map<String, Object> payload = parse(jwt);
        assertThat(payload.get("claims")).isInstanceOf(Map.class);
        assertThat((Map<String, Object>) payload.get("claims")).containsEntry("tenant_id", 1001);
        // 系统字段原样
        assertThat(payload).containsEntry("type", TOKEN)
                .containsEntry("nonce", NONCE)
                .containsEntry("hash", HASH)
                .containsEntry("iss", "app")
                .containsEntry("jti", "jti-1");
    }

    @Test
    @DisplayName("业务 claim 与系统字段同名 → 只存在于嵌套键,顶层系统值不被覆盖")
    @SuppressWarnings("unchecked")
    void systemFieldsNotOverwritten() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("hash", "evil-override");   // 恶意/误配:与系统 hash 同名
        claims.put("jti", "evil-jti");
        String jwt = TokenUtils.createToken(TOKEN, NONCE, HASH, "app", IAT, EXP, "jti-1",
                null, claims, SECRET);

        Map<String, Object> payload = parse(jwt);
        assertThat(payload).containsEntry("hash", HASH)         // 顶层系统值不被覆盖
                .containsEntry("jti", "jti-1");
        assertThat((Map<String, Object>) payload.get("claims"))
                .containsEntry("hash", "evil-override");        // 原样留在嵌套键
    }

    @Test
    @DisplayName("null / 空 claims → 无 claims 键(1.6.x 行为)")
    void nullOrEmptyClaims_noClaimsKey() {
        String withNull = TokenUtils.createToken(TOKEN, NONCE, HASH, "app", IAT, EXP, "jti-1",
                null, null, SECRET);
        assertThat(parse(withNull)).doesNotContainKey("claims");

        String withEmpty = TokenUtils.createToken(TOKEN, NONCE, HASH, "app", IAT, EXP, "jti-1",
                null, Map.of(), SECRET);
        assertThat(parse(withEmpty)).doesNotContainKey("claims");
    }

    @Test
    @DisplayName("8 参 / 9 参旧重载行为不变(无 claims 键;family 保留)")
    void legacyOverloadsUnchanged() {
        String eight = TokenUtils.createToken(TOKEN, NONCE, HASH, "app", IAT, EXP, "jti-1", SECRET);
        assertThat(parse(eight)).doesNotContainKey("claims").doesNotContainKey("family");

        String nine = TokenUtils.createToken(TOKEN, NONCE, HASH, "app", IAT, EXP, "jti-1", "fam-1", SECRET);
        Map<String, Object> payload = parse(nine);
        assertThat(payload).doesNotContainKey("claims").containsEntry("family", "fam-1");
    }

    @Test
    @DisplayName("签名覆盖 claims:篡改 payload.claims → 10202 签名验证失败")
    void signatureCoversClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tenant_id", 1001);
        String jwt = TokenUtils.createToken(TOKEN, NONCE, HASH, "app", IAT, EXP, "jti-1",
                null, claims, SECRET);

        // 真·篡改:解出 payload 改值重编码,保留原签名 → 签名不匹配
        String[] parts = jwt.split("\\.");
        String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]),
                java.nio.charset.StandardCharsets.UTF_8);
        String tamperedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.replace("\"tenant_id\":1001", "\"tenant_id\":9")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        org.assertj.core.api.Assertions.assertThatExceptionOfType(
                        fun.commons.framework4j.accesstoken.exception.AuthException.class)
                .isThrownBy(() -> TokenUtils.parseToken(tampered, SECRET))
                .extracting(e -> ((fun.commons.framework4j.accesstoken.exception.AuthException) e).getCode())
                .isEqualTo(10202);
    }
}
