package fun.commons.framework4j.demo.tenant;

import fun.commons.framework4j.tenant.schema.TenantSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * demo 租户 Schema 注册(实体子类 SPI 的项目侧文件之二)。
 * 真实项目通常再配 BenefitTenantMapper extends BaseMapper&lt;BenefitTenant&gt; 供业务查询(demo 无业务查询,略)。
 */
@Configuration
public class DemoTenantSchemaConfig {

    @Bean
    TenantSchema tenantSchema() {
        return () -> DemoTenant.class;
    }
}
