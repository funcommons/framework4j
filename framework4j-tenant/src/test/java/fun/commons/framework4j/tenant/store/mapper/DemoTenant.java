package fun.commons.framework4j.tenant.store.mapper;

import com.baomidou.mybatisplus.annotation.TableName;
import fun.commons.framework4j.tenant.entity.TenantEntity;

/**
 * 测试用租户实体(实体子类 SPI 演示)。
 */
@TableName(value = "demo_tenant", autoResultMap = true)
public class DemoTenant extends TenantEntity {
}
