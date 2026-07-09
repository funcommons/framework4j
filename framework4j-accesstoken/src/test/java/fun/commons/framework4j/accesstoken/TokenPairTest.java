package fun.commons.framework4j.accesstoken;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.framework4j.accesstoken.util.TokenUtils;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenUtils 9-arg overload（family claim）单元测试
 */
class TokenPairTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SECRET = "test-secret-key-for-jwt-signing-must-be-at-least-32-characters-long-xx";

    @Test
    void test8ArgOverloadHasNoFamily() throws Exception {
        String token = TokenUtils.createToken(
                "access", "nonce-1", "hash-1",
                "app-1", 1700000000000L, 1700003600000L, "jti-1",
                SECRET);
        Map<String, Object> payload = decodePayload(token);
        assertEquals("access", payload.get("type"));
        assertFalse(payload.containsKey("family"), "8-arg overload must not include family claim");
    }

    @Test
    void test9ArgOverloadIncludesFamily() throws Exception {
        String token = TokenUtils.createToken(
                "refresh", "nonce-2", "hash-2",
                "app-2", 1700000000000L, 1700003600000L, "jti-2",
                "family-XYZ", SECRET);
        Map<String, Object> payload = decodePayload(token);
        assertEquals("refresh", payload.get("type"));
        assertEquals("family-XYZ", payload.get("family"));
    }

    @Test
    void test9ArgOverloadWithNullFamily() throws Exception {
        String token = TokenUtils.createToken(
                "refresh", "nonce-3", "hash-3",
                "app-3", 1700000000000L, 1700003600000L, "jti-3",
                null, SECRET);
        Map<String, Object> payload = decodePayload(token);
        assertFalse(payload.containsKey("family"));
    }

    @Test
    void testParseTokenExtractsFamily() throws Exception {
        String token = TokenUtils.createToken(
                "refresh", "nonce-4", "hash-4",
                "app-4", System.currentTimeMillis(),
                System.currentTimeMillis() + 3_600_000L, "jti-4",
                "family-ABC", SECRET);
        Map<String, Object> payload = TokenUtils.parseToken(token, SECRET);
        assertEquals("family-ABC", payload.get("family"));
    }

    private Map<String, Object> decodePayload(String token) throws Exception {
        String[] parts = token.split("\\.");
        byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
        return MAPPER.readValue(decoded, new TypeReference<Map<String, Object>>() {});
    }
}
