package fun.commons.framework4j.accesstoken.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AccessToken 配置启动期校验器(GitHub Issue #20):
 * 把原本延迟到首次 generateToken 才暴露的配置错误(policies 未配 NPE / TokenType 缺条目 /
 * key 字段语义误配 / hashSalt 空)提前到启动装配阶段 fail-fast,并给出缺失项清单。
 * <p>
 * 关键语义澄清(误配高发点):{@code Policy.key} 是 <strong>claims 必需字段名列表</strong>
 * (如 {@code [tenant_id]} / {@code [uid]}),<strong>不是签名密钥</strong>;
 * 签名密钥是 {@code framework4j.access-token.secret-key}。
 */
public final class AccessTokenConfigValidator {

    private AccessTokenConfigValidator() {
    }

    /**
     * 校验失败抛 IllegalStateException(启动期 fail-fast),message 含全部缺失项。
     */
    public static void validate(AccessTokenProperties p) {
        List<String> problems = new ArrayList<>();

        if (p.getSecretKey() == null || p.getSecretKey().length() < 32) {
            problems.add("secret-key 缺失或不足 32 字符(256 位)——这是 JWT 签名密钥,"
                    + "建议环境变量注入(如 ${JWT_SECRET})");
        }
        if (p.getHashSalt() == null || p.getHashSalt().isBlank()) {
            problems.add("hash-salt 未配置 —— Redis 会话 key 哈希加盐,防泄露反查");
        }

        Map<String, AccessTokenProperties.Policy> policies = p.getPolicies();
        if (policies == null || policies.isEmpty()) {
            problems.add("policies 未配置 —— policies 是 Map<TokenType, Policy>,至少声明一个型别;"
                    + "每个 Policy.key 是该型别 generateToken 时 claims 必须包含的字段名列表"
                    + "(如 key: [tenant_id]),注意 key 不是签名密钥");
        } else {
            for (Map.Entry<String, AccessTokenProperties.Policy> e : policies.entrySet()) {
                String type = e.getKey();
                AccessTokenProperties.Policy policy = e.getValue();
                if (type == null || type.isBlank() || type.length() > 100) {
                    problems.add("policies 存在非法 TokenType 名[" + type + "](非空且 ≤100 字符)");
                }
                if (policy == null) {
                    problems.add("policies[" + type + "] 为 null");
                    continue;
                }
                if (policy.getKey() == null || policy.getKey().isEmpty()) {
                    problems.add("policies[" + type + "].key 未配置 —— key 是 claims 必需字段名列表"
                            + "(安全要求,决定会话 key 的互斥维度),不是签名密钥");
                }
            }
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "framework4j.access-token 配置不完整,启动 fail-fast(Issue #20):\n - "
                            + String.join("\n - ", problems));
        }
    }

    /**
     * 校验通过标记 Bean(仅作装配占位,无行为)。
     */
    static final class Marker {
        Marker() {
        }
    }
}
