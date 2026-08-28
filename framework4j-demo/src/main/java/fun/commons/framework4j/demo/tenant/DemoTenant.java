package fun.commons.framework4j.demo.tenant;

import com.baomidou.mybatisplus.annotation.TableName;
import fun.commons.framework4j.tenant.entity.TenantEntity;

/**
 * demo 租户实体(实体子类 SPI 的项目侧文件,每项目 2 个小文件之一)。
 * 表名 = {table-prefix}tenant = demo_(见 application.yml)。
 */
@TableName(value = "demo_tenant", autoResultMap = true)
public class DemoTenant extends TenantEntity {
}
