package fun.commons.framework4j.tenant.schema;

import fun.commons.framework4j.tenant.entity.TenantEntity;

/**
 * 租户 Schema SPI —— 项目把自有租户实体注册给框架(实体子类模式,每项目 2 个小文件)。
 * <p>
 * 用法(项目任一 @Configuration):
 * <pre>{@code
 * @Bean
 * TenantSchema tenantSchema() {
 *     return () -> BenefitTenant.class;
 * }
 * }</pre>
 * <p>
 * 框架在校验 {@code @TableName} 值必须等于 {@code {table-prefix}tenant} 且
 * {@code autoResultMap = true}(否则密文读不回明文),不一致启动即失败 —— 防结构漂移第一道闸。
 */
@FunctionalInterface
public interface TenantSchema {

    /**
     * 项目租户实体子类(继承 {@link TenantEntity},标注 @TableName)。
     */
    Class<? extends TenantEntity> entityClass();
}
