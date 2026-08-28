package fun.commons.framework4j.tenant.ddl;

import com.baomidou.mybatisplus.annotation.TableName;
import fun.commons.framework4j.tenant.config.Framework4jTenantProperties;
import fun.commons.framework4j.tenant.entity.TenantEntity;
import fun.commons.framework4j.tenant.enums.DdlMode;
import fun.commons.framework4j.tenant.schema.TenantSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 租户表 DDL 初始化器(framework4j-tenant模块设计 v1.1 D-1):
 * <ul>
 *   <li><b>校验</b> SPI 实体的 @TableName == {table-prefix}tenant 且 autoResultMap=true,不一致启动失败(防结构漂移第一道闸)</li>
 *   <li><b>AUTO</b>:启动幂等建表(CREATE IF NOT EXISTS)+ 存量表缺列补列 + 索引(PG)—— 项目零迁移工具可用</li>
 *   <li><b>PROVIDED</b>:不执行任何 DDL,仅输出 SQL 模板日志,由项目迁移工具(Flyway 等)管理</li>
 * </ul>
 * 无 DataSource(纯 PROVIDED 项目)时 WARN 跳过,不阻断启动。
 */
@Slf4j
public class TenantDdlInitializer implements InitializingBean {

    private final Framework4jTenantProperties properties;
    private final TenantSchema schema;      // 可空:未注册 SPI 的项目仅按配置表名执行
    private final DataSource dataSource;    // 可空:AUTO 但无库 → WARN 跳过

    public TenantDdlInitializer(Framework4jTenantProperties properties,
                                TenantSchema schema, DataSource dataSource) {
        this.properties = properties;
        this.schema = schema;
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        String table = properties.tenantTableName();
        if (schema != null) {
            validateSchema(schema.entityClass(), table);
        }

        if (properties.getDdlMode() == DdlMode.PROVIDED) {
            log.info("【Tenant】ddl-mode=PROVIDED —— 不执行 DDL,SQL 模板如下(交项目迁移工具管理):\n{}",
                    TenantDdlGenerator.providedTemplate(table));
            return;
        }

        if (dataSource == null) {
            log.warn("【Tenant】ddl-mode=AUTO 但未检测到 DataSource —— 跳过 DDL(如需模板请切 ddl-mode=PROVIDED)");
            return;
        }

        apply(dataSource, table);
    }

    /** SPI 一致性校验:表名对齐 + autoResultMap(密文读不回明文的头号坑) */
    private void validateSchema(Class<? extends TenantEntity> entityClass, String expectedTable) {
        TableName tn = entityClass.getAnnotation(TableName.class);
        if (tn == null || tn.value().isEmpty()) {
            throw new IllegalStateException(String.format(
                    "TenantSchema 实体 %s 缺 @TableName —— 须标注 @TableName(value = \"%s\", autoResultMap = true)",
                    entityClass.getSimpleName(), expectedTable));
        }
        if (!expectedTable.equals(tn.value())) {
            throw new IllegalStateException(String.format(
                    "TenantSchema 实体表名 %s 与配置不符 —— @TableName 须为 {table-prefix}tenant = %s"
                            + "(framework4j.tenant.table-prefix=%s)",
                    tn.value(), expectedTable, properties.getTablePrefix()));
        }
        if (!tn.autoResultMap()) {
            throw new IllegalStateException(String.format(
                    "TenantSchema 实体 %s 的 @TableName 缺 autoResultMap = true —— "
                            + "密钥列(typeHandler 解密)在 select 时将读回密文,严禁省略",
                    entityClass.getSimpleName()));
        }
    }

    private void apply(DataSource ds, String table) throws SQLException {
        try (Connection conn = ds.getConnection()) {
            boolean postgres = isPostgres(conn);
            int added = 0;
            try (Statement st = conn.createStatement()) {
                for (String ddl : TenantDdlGenerator.createTable(table, postgres)) {
                    st.execute(ddl);
                }
                added = backfillMissingColumns(st, conn, table, postgres);
                for (String idx : TenantDdlGenerator.indexes(table, postgres)) {
                    st.execute(idx);
                }
            }
            log.info("【Tenant】DDL 完成 —— 表 {} 就绪(方言={}, 补列 {});契约列 {} 个",
                    table, postgres ? "PostgreSQL" : "H2", added,
                    TenantDdlGenerator.contractColumns().size());
        }
    }

    private int backfillMissingColumns(Statement st, Connection conn, String table, boolean postgres)
            throws SQLException {
        Set<String> existing = existingColumns(conn, table);
        if (existing.isEmpty()) {
            return 0;   // 表刚建,无缺列
        }
        int added = 0;
        for (String col : TenantDdlGenerator.contractColumns()) {
            if (!existing.contains(col)) {
                st.execute(TenantDdlGenerator.addColumn(table, col, postgres));
                added++;
            }
        }
        return added;
    }

    private static Set<String> existingColumns(Connection conn, String table) throws SQLException {
        Set<String> out = new HashSet<>();
        // H2 默认大写存储元数据,PG 小写 —— 双查合并,列名归一小写
        for (String variant : new String[]{table, table.toUpperCase(Locale.ROOT)}) {
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, variant, "%")) {
                while (rs.next()) {
                    out.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        return out;
    }

    private static boolean isPostgres(Connection conn) throws SQLException {
        String product = conn.getMetaData().getDatabaseProductName();
        return product != null && product.toLowerCase(Locale.ROOT).contains("postgres");
    }
}
