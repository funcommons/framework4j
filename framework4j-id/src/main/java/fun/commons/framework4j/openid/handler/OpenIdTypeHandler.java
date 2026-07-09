package fun.commons.framework4j.openid.handler;

import fun.commons.framework4j.id.util.IdObfuscator;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * OpenID 类型转换器
 * <p>
 * 用于 MyBatis/MyBatis-Plus 实体类字段的类型转换。
 * 实现 Java String (OpenID) 与 Database BigInt/Integer (Long/Int ID) 的自动互转。
 * <p>
 * 支持的数据库类型:
 * <ul>
 * <li>BIGINT (对应 Java Long)</li>
 * <li>INT / INTEGER (对应 Java Integer)</li>
 * <li>SERIAL (PostgreSQL, 对应 Integer)</li>
 * </ul>
 * <p>
 * 使用方式:
 * <pre>
 * // 方式 1: 在实体类字段上指定 (推荐)
 * @TableField(typeHandler = OpenIdTypeHandler.class)
 * private String id;
 *
 * // 方式 2: 在 XML ResultMap 中指定
 * <result column="id" property="id" typeHandler="fun.commons.framework4j.openid.handler.OpenIdTypeHandler"/>
 * </pre>
 *
 * @since 1.0.0
 */
@MappedTypes(String.class)
@MappedJdbcTypes({JdbcType.BIGINT, JdbcType.INTEGER})
public class OpenIdTypeHandler extends BaseTypeHandler<String> {

    /**
     * 写入数据库: String (OpenID) -> Long/Integer (DB)
     * 将实体类中的 OpenID 字符串反解为 Long 存入数据库。
     * 即使数据库字段是 INT，使用 setLong 也是安全的（只要数值不溢出 INT 范围）。
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null || parameter.trim().isEmpty()) {
            // 根据配置的 jdbcType 设置 null，默认兜底为 BIGINT
            JdbcType type = (jdbcType == null) ? JdbcType.BIGINT : jdbcType;
            ps.setNull(i, type.TYPE_CODE);
            return;
        }
        long id;
        try {
            id = IdObfuscator.fromOpenId(parameter);
        } catch (Exception e) {
            throw new SQLException("Failed to convert OpenID '" + parameter + "' to numeric ID", e);
        }
        // 无论是 BIGINT 还是 INT，都通过 setLong 设置
        // JDBC 驱动会自动处理 Long 到数据库 Integer 的转换（前提是数值在范围内）
        ps.setLong(i, id);
    }

    /**
     * 读取数据库: Long/Integer (DB) -> String (OpenID)
     * 从 ResultSet 获取列名对应的值
     */
    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        // getLong 可以兼容 SQL 的 BIGINT 和 INTEGER
        long id = rs.getLong(columnName);
        // 检查是否为数据库NULL（必须在getLong之后立即调用wasNull）
        if (rs.wasNull()) {
            return null;
        }
        return convertToOpenId(id);
    }

    /**
     * 读取数据库: Long/Integer (DB) -> String (OpenID)
     * 从 ResultSet 获取索引对应的值
     */
    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        long id = rs.getLong(columnIndex);
        // 检查是否为数据库NULL（必须在getLong之后立即调用wasNull）
        if (rs.wasNull()) {
            return null;
        }
        return convertToOpenId(id);
    }

    /**
     * 读取数据库: Long/Integer (DB) -> String (OpenID)
     * 从 CallableStatement 获取值 (存储过程)
     */
    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        long id = cs.getLong(columnIndex);
        // 检查是否为数据库NULL（必须在getLong之后立即调用wasNull）
        if (cs.wasNull()) {
            return null;
        }
        return convertToOpenId(id);
    }

    /**
     * 转换逻辑封装，方便子类扩展（如添加前缀）
     * * @param id 数据库原始 ID
     * @return 混淆后的 OpenID
     */
    protected String convertToOpenId(long id) {
        // 默认使用无前缀转换
        return IdObfuscator.toOpenId(id);
        // 如果需要统一前缀，可以修改为:
        // return IdObfuscator.toOpenId(id, "PREFIX");
    }
}