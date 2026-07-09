package fun.commons.framework4j.signature.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 SecretProvider（默认实现，开发/测试用）
 * <p>
 * 生产环境应替换为 DB/配置中心版本。
 *
 * @since 2.1.0
 */
public class InMemorySecretProvider implements SecretProvider {

    private final Map<String, String> store = new ConcurrentHashMap<>();

    public InMemorySecretProvider() {}

    public InMemorySecretProvider(Map<String, String> initial) {
        if (initial != null) store.putAll(initial);
    }

    /** 注册 accessKey → secret */
    public void register(String accessKey, String secret) {
        store.put(accessKey, secret);
    }

    /** 删除 accessKey */
    public void revoke(String accessKey) {
        store.remove(accessKey);
    }

    @Override
    public String getSecret(String accessKey) {
        return store.get(accessKey);
    }
}
