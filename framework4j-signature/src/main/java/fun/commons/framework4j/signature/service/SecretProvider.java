package fun.commons.framework4j.signature.service;

/**
 * Secret 查询接口（业务方实现）
 * <p>
 * 根据 AccessKey 查询对应 Secret（HMAC 密钥）。
 *
 * @since 2.1.0
 */
public interface SecretProvider {

    /**
     * 根据 accessKey 查询 secret
     *
     * @param accessKey 应用标识
     * @return secret 字符串；找不到返回 null
     */
    String getSecret(String accessKey);
}
