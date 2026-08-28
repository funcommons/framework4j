package fun.commons.framework4j.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.sensitive.annotation.Sensitive;
import fun.commons.framework4j.sensitive.annotation.SensitiveRule;
import fun.commons.framework4j.sensitive.typehandler.LazyEncryptedFieldTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 基类字段契约冻结 —— 字段集 = 租户设计 §3.1(四类配置/生命周期/宽限期) + 审计列。
 * 任何增删都意味着结构契约变化,须过设计文档并升版本。
 */
class TenantEntityContractTest {

    /** §3.1 全量列 + §5.5 宽限期两列 + benefit4j 验证过的描述/审计列 */
    private static final Set<String> CONTRACT_FIELDS = Set.of(
            "id",                   // = tenant_id(雪花),表 id 即租户 id
            "name", "description", "email", "channel", "status",
            "tenantSecret",         // 安全类:AES-256-GCM 加密
            "tenantSecretPrev",     // §5.5 宽限期旧钥
            "tenantSecretPrevAt",
            "privileges",           // 权限类 JSONB
            "config",               // 配置类 JSONB
            "oem",                  // OEM 类 JSONB
            "ext",                  // 扩展 JSONB
            "createdAt", "updatedAt", "createBy", "updateBy", "isDeleted");

    @Test
    @DisplayName("字段集恰为契约集(多一列少一列都算漂移)")
    void fieldSet_frozen() {
        Set<String> actual = java.util.Arrays.stream(TenantEntity.class.getDeclaredFields())
                .map(Field::getName)
                .filter(n -> !n.startsWith("$"))
                .collect(Collectors.toSet());
        assertThat(actual).as("基类字段漂移(对照 租户设计 §3.1 + §5.5)").isEqualTo(CONTRACT_FIELDS);
    }

    @Test
    @DisplayName("密钥两列:AES-GCM 加密 TypeHandler + 响应脱敏;当前钥保留前2后4,旧钥永不外显")
    void secretColumns_encryptedAndMasked() throws Exception {
        Field secret = TenantEntity.class.getDeclaredField("tenantSecret");
        assertThat(secret.getAnnotation(TableField.class).typeHandler())
                .isEqualTo(LazyEncryptedFieldTypeHandler.class);
        Sensitive sensitive = secret.getAnnotation(Sensitive.class);
        assertThat(sensitive.value()).isEqualTo(SensitiveRule.CUSTOM);
        assertThat(sensitive.pattern()).isEqualTo("2,4,0");

        Field prev = TenantEntity.class.getDeclaredField("tenantSecretPrev");
        assertThat(prev.getAnnotation(TableField.class).typeHandler())
                .isEqualTo(LazyEncryptedFieldTypeHandler.class);
        assertThat(prev.isAnnotationPresent(Sensitive.class))
                .as("旧密钥不脱敏——整个字段永不对外返回,由序列化层排除").isFalse();
    }

    @Test
    @DisplayName("四类配置 JSONB:JacksonTypeHandler + JdbcType.OTHER,形态为 Map(禁裸数组)")
    void jsonbColumns_mapTyped() {
        for (String col : new String[]{"privileges", "config", "oem", "ext"}) {
            Field f = assertField(col);
            assertThat(f.getType()).as(col + " 应为 Map<String,Object>").isEqualTo(Map.class);
            assertThat(f.getAnnotation(TableField.class).typeHandler())
                    .isEqualTo(JacksonTypeHandler.class);
            assertThat(f.getAnnotation(TableField.class).jdbcType()).isEqualTo(JdbcType.OTHER);
        }
    }

    @Test
    @DisplayName("id = 雪花(ASSIGN_ID)+ OpenID 混淆;逻辑删除;基类抽象强制子类定表名")
    void idAndBasics() throws Exception {
        Field id = TenantEntity.class.getDeclaredField("id");
        assertThat(id.getAnnotation(TableId.class).type()).isEqualTo(IdType.ASSIGN_ID);
        assertThat(id.isAnnotationPresent(OpenId.class)).isTrue();

        assertThat(TenantEntity.class.getDeclaredField("isDeleted")
                .isAnnotationPresent(TableLogic.class)).isTrue();
        assertThat(java.lang.reflect.Modifier.isAbstract(TenantEntity.class.getModifiers()))
                .as("基类抽象:表名必须由项目子类 @TableName 给出").isTrue();
    }

    @Test
    @DisplayName("实体字段 ↔ DDL 契约列 双 SSOT 一致(驼峰↔snake,防一处改另一处漏)")
    void entityFields_matchDdlContractColumns() {
        Set<String> entityCols = java.util.Arrays.stream(TenantEntity.class.getDeclaredFields())
                .map(Field::getName)
                .filter(n -> !n.startsWith("$"))
                .map(n -> n.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase())
                .collect(Collectors.toSet());
        assertThat(entityCols)
                .as("TenantEntity 字段(snake)须与 TenantDdlGenerator.contractColumns() 完全一致")
                .isEqualTo(new java.util.HashSet<>(fun.commons.framework4j.tenant.ddl.TenantDdlGenerator.contractColumns()));
    }

    private static Field assertField(String name) {
        try {
            return TenantEntity.class.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new AssertionError("契约字段缺失: " + name, e);
        }
    }
}
