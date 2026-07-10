package fun.commons.framework4j.audit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuditProperties 默认值 / 配置覆盖 / 注解元信息测试。
 *
 * <p>覆盖维度：
 * <ol>
 *   <li>默认值（enabled、tableName、hashChainEnabled、hashAlgorithm、actorHeader、actorFallback、ipHeader）</li>
 *   <li>setter/getter 往返一致性</li>
 *   <li>边界值（null、空字符串、自定义值）</li>
 *   <li>注解元信息（@ConfigurationProperties prefix 正确）</li>
 * </ol>
 *
 * @since 2.1.0
 */
@DisplayName("AuditProperties 配置默认值与覆盖测试")
class AuditPropertiesTest {

    @Test
    @DisplayName("默认值：所有字段符合文档约定")
    void defaultsAreAsDocumented() {
        AuditProperties p = new AuditProperties();

        assertThat(p.isEnabled()).isTrue();
        assertThat(p.getTableName()).isEqualTo("audit_log");
        assertThat(p.isHashChainEnabled()).isTrue();
        assertThat(p.getHashAlgorithm()).isEqualTo("SHA-256");
        assertThat(p.getActorHeader()).isEqualTo("X-User-Id");
        assertThat(p.getActorFallback()).isEqualTo("anonymous");
        assertThat(p.getIpHeader()).isEqualTo("X-Forwarded-For");
    }

    @Test
    @DisplayName("每个字段都可以通过 setter 覆盖默认值（往返一致）")
    void setterRoundTrip() {
        AuditProperties p = new AuditProperties();
        p.setEnabled(false);
        p.setTableName("t_audit");
        p.setHashChainEnabled(false);
        p.setHashAlgorithm("SHA-512");
        p.setActorHeader("X-Actor");
        p.setActorFallback("system");
        p.setIpHeader("X-Real-IP");

        assertThat(p.isEnabled()).isFalse();
        assertThat(p.getTableName()).isEqualTo("t_audit");
        assertThat(p.isHashChainEnabled()).isFalse();
        assertThat(p.getHashAlgorithm()).isEqualTo("SHA-512");
        assertThat(p.getActorHeader()).isEqualTo("X-Actor");
        assertThat(p.getActorFallback()).isEqualTo("system");
        assertThat(p.getIpHeader()).isEqualTo("X-Real-IP");
    }

    @Test
    @DisplayName("边界：tableName 设为空字符串应被保留（不强制非空）")
    void emptyStringsAreKept() {
        AuditProperties p = new AuditProperties();
        p.setTableName("");
        p.setHashAlgorithm("");
        p.setActorHeader("");
        p.setActorFallback("");
        p.setIpHeader("");

        assertThat(p.getTableName()).isEmpty();
        assertThat(p.getHashAlgorithm()).isEmpty();
        assertThat(p.getActorHeader()).isEmpty();
        assertThat(p.getActorFallback()).isEmpty();
        assertThat(p.getIpHeader()).isEmpty();
    }

    @Test
    @DisplayName("边界：null 值应被保留（Lombok @Data 不做防御）")
    void nullsAreKept() {
        AuditProperties p = new AuditProperties();
        p.setTableName(null);
        p.setHashAlgorithm(null);
        p.setActorHeader(null);
        p.setActorFallback(null);
        p.setIpHeader(null);

        assertThat(p.getTableName()).isNull();
        assertThat(p.getHashAlgorithm()).isNull();
        assertThat(p.getActorHeader()).isNull();
        assertThat(p.getActorFallback()).isNull();
        assertThat(p.getIpHeader()).isNull();
    }

    @Test
    @DisplayName("注解：@ConfigurationProperties prefix = framework4j.audit")
    void annotationPrefix() {
        ConfigurationProperties ann =
                AuditProperties.class.getAnnotation(ConfigurationProperties.class);
        assertThat(ann).isNotNull();
        assertThat(ann.prefix()).isEqualTo("framework4j.audit");
    }

    @Test
    @DisplayName("字段集合稳定：避免新增字段时悄悄破坏绑定契约")
    void fieldSetIsStable() {
        AuditProperties p = new AuditProperties();
        // 期望字段集合（v2.1.0）
        var expected = java.util.Set.of(
                "enabled", "tableName", "hashChainEnabled", "hashAlgorithm",
                "actorHeader", "actorFallback", "ipHeader");
        var actual = new java.util.HashSet<String>();
        for (Field f : AuditProperties.class.getDeclaredFields()) {
            actual.add(f.getName());
        }
        assertThat(actual).isEqualTo(expected);
        // 触发一次 hashCode 防止编译器警告
        assertThat(p.hashCode()).isNotZero();
    }
}
