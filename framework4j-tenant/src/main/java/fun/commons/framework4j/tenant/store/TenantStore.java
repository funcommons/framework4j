package fun.commons.framework4j.tenant.store;

import fun.commons.framework4j.tenant.entity.TenantEntity;

/**
 * 租户存取 SPI —— 认证/密钥/注册码各服务共用的租户查询面。
 * <p>
 * 模块默认实现 {@link MyBatisTenantStore}(复用项目注册的 {@code BaseMapper} 子接口,即
 * 实体子类 SPI 的第二个文件);项目可自定义覆盖(如加缓存)。
 */
public interface TenantStore {

    /**
     * 按 id 查 ACTIVE 租户;不存在/非 ACTIVE 返回 null。
     */
    TenantEntity findActiveById(long id);

    /**
     * 按 name 查 ACTIVE 租户(client_id 兼容 name 形态);不存在返回 null。
     */
    TenantEntity findActiveByName(String name);
}
