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
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 租户主表实体基类 —— 中间件中台租户设计 §3.1 的代码化(契约层冻结)。
 * <p>
 * 全库唯一没有 tenant_id 的表:<strong>表 id 即租户 id</strong>,它自己是租户的本体,不隔离自己。
 * 字段集由 tck 契约测试冻结,增删列 = 结构契约变化,须过设计文档并升版本。
 * <p>
 * 项目侧接入(实体子类 SPI,每项目 2 个小文件):
 * <pre>{@code
 * @TableName(value = "ubma_tenant", autoResultMap = true)   // = {table-prefix}tenant
 * public class BenefitTenant extends TenantEntity {}
 *
 * public interface BenefitTenantMapper extends BaseMapper<BenefitTenant> {}
 * }</pre>
 * {@code autoResultMap = true} 必须 —— 密钥列依赖 typeHandler 在 select 时解密,
 * 漏配则读到的是密文(启动校验会 fail-fast,勿绕过)。
 */
@Getter
@Setter
public abstract class TenantEntity {

    /**
     * 主键 = tenant_id(雪花)。表 id 即租户 id;对外展示用 OpenID 混淆。
     */
    @TableId(type = IdType.ASSIGN_ID)
    @OpenId
    private Long id;

    /**
     * 租户名。
     */
    private String name;

    /**
     * 租户描述(用途 / 业务范围)。
     */
    private String description;

    /**
     * 联系邮箱(可选;凭码注册时可留,无验证流程 —— §6.2)。
     */
    private String email;

    /**
     * 来源:OPS 运营创建 | SELF 自助注册(§6.2 通道 B)。
     */
    private String channel;

    /**
     * 生命周期状态机(§6.0/§6.3):PENDING → SANDBOX → ACTIVE ⇄ SUSPEND → CLOSED。
     */
    private String status;

    /**
     * 租户密钥(OAuth2 client_secret / HMAC 签名密钥)。
     * 写入 DB 自动 AES-256-GCM 加密,读取自动解密;响应序列化脱敏(保留前2后4),明文只在发放时可见一次。
     */
    @TableField(typeHandler = LazyEncryptedFieldTypeHandler.class)
    @Sensitive(value = SensitiveRule.CUSTOM, pattern = "2,4,0")
    private String tenantSecret;

    /**
     * 轮换宽限期内的旧密钥(§5.5 双版本过渡):reset 时旧 secret 挪入此列,
     * 宽限期内两把皆可换 token;过期后旧密钥自然失效(懒校验,无需清理任务)。
     * 同款加密存储;永不对外返回(接口层不得序列化此字段)。
     */
    @TableField(typeHandler = LazyEncryptedFieldTypeHandler.class)
    private String tenantSecretPrev;

    /**
     * 旧密钥存入时间(宽限期起点)。
     */
    @TableField("tenant_secret_prev_at")
    private OffsetDateTime tenantSecretPrevAt;

    /**
     * 权限类:功能开关矩阵,如 {"billing":true,"webhook":false}。JSONB,禁裸数组。
     */
    @TableField(typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> privileges;

    /**
     * 配置类:参数与默认值,如 {"quotaDefault":1000}。JSONB,禁裸数组。
     */
    @TableField(typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> config;

    /**
     * OEM 类:主题 / 名称 / logo,如 {"theme":"dark","title":"XX 控制台"}。JSONB,禁裸数组。
     */
    @TableField(typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> oem;

    /**
     * 预留扩展(验证 token、注册 IP 等)。JSONB,禁裸数组;PG 下建 GIN 索引。
     */
    @TableField(typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> ext;

    /**
     * 创建时间。
     */
    private OffsetDateTime createdAt;

    /**
     * 更新时间。
     */
    private OffsetDateTime updatedAt;

    /**
     * 创建人。
     */
    private String createBy;

    /**
     * 更新人。
     */
    private String updateBy;

    /**
     * 逻辑删除标志(0:未删, 1:已删)。
     */
    @TableLogic
    private Short isDeleted;
}
