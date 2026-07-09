package fun.commons.framework4j.accesstoken.context;

import java.util.Map;
import java.util.Optional;

/**
 * 令牌上下文
 * 基于 ThreadLocal，注意在异步线程中需手动传递
 */
public class TokenContext {

    private static final ThreadLocal<ContextData> CONTEXT = new ThreadLocal<>();

    /**
     * 轻量级上下文数据对象
     * 避免存储大对象，防止虚拟线程场景下 OOM
     */
    public record ContextData(String tokenType, Map<String, Object> claims, long expireSeconds) {}

    public static void set(String tokenType, Map<String, Object> claims) {
        CONTEXT.set(new ContextData(tokenType, claims, -1));
    }

    /**
     * 设置上下文 + Token 剩余有效期（用于 X-Token-Expire-At 响应头）
     */
    public static void set(String tokenType, Map<String, Object> claims, long expireSeconds) {
        CONTEXT.set(new ContextData(tokenType, claims, expireSeconds));
    }

    /**
     * 获取当前上下文
     * @return ContextData
     */
    public static ContextData getContext() {
        return CONTEXT.get();
    }

    /**
     * 手动设置上下文 (用于异步线程传递)
     */
    public static void setContext(ContextData data) {
        if (data != null) {
            CONTEXT.set(data);
        }
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static String getTokenType() {
        return Optional.ofNullable(CONTEXT.get()).map(ContextData::tokenType).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getClaim(String key) {
        ContextData data = CONTEXT.get();
        if (data == null || data.claims() == null) {
            return null;
        }
        return (T) data.claims().get(key);
    }
    
    public static Map<String, Object> getClaims() {
        return Optional.ofNullable(CONTEXT.get()).map(ContextData::claims).orElse(Map.of());
    }

    /**
     * 获取 Token 剩余有效期（秒），-1 表示未设置
     */
    public static long getExpireSeconds() {
        return Optional.ofNullable(CONTEXT.get()).map(ContextData::expireSeconds).orElse(-1L);
    }
}