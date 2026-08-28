package fun.commons.framework4j.tenant.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.TableName;
import fun.commons.framework4j.tenant.config.TenantAutoConfiguration;
import fun.commons.framework4j.tenant.entity.TenantEntity;
import fun.commons.framework4j.tenant.store.mapper.DemoTenant;
import fun.commons.framework4j.tenant.store.mapper.DemoTenantMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TenantStore 泛型解析:H2 + MyBatis-Plus + 项目 Mapper(实体子类 SPI 第二文件)全链 ——
 * 自动配置从 BaseMapper&lt;DemoTenant&gt; 解析出 TenantStore,DDL 建表后真实读写。
 */
class TenantStoreMyBatisTest {

    /** 测试启动前直连建全契约表(H2 TEXT 代替 JSONB);独立于 Spring 生命周期,避免时序问题 */
    @org.junit.jupiter.api.BeforeAll
    static void createTable() throws Exception {
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(
                "jdbc:h2:mem:storetest;DB_CLOSE_DELAY=-1", "sa", "");
             java.sql.Statement st = c.createStatement()) {
            // 与 TenantDdlGenerator.createTable(table,false) 列集一致(H2 降级形态)
            for (String ddl : fun.commons.framework4j.tenant.ddl.TenantDdlGenerator
                    .createTable("demo_tenant", false)) {
                st.execute(ddl);
            }
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
                    fun.commons.framework4j.sensitive.config.SensitiveAutoConfiguration.class,
                    TenantAutoConfiguration.class))
            .withUserConfiguration(MapperConfig.class)
            .withPropertyValues(
                    "framework4j.tenant.enabled=true",
                    "framework4j.tenant.table-prefix=demo_",
                    "framework4j.tenant.ddl-mode=PROVIDED",
                    "framework4j.sensitive.enabled=true",
                    "framework4j.sensitive.encryption-key=test-aes-key-32-bytes-for-test!!",
                    "spring.datasource.url=jdbc:h2:mem:storetest;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=");

    @Configuration
    @MapperScan(basePackages = "fun.commons.framework4j.tenant.store.mapper")
    static class MapperConfig {
    }

    private static DemoTenant row(long id, String name, String status) {
        DemoTenant t = new DemoTenant();
        t.setId(id);
        t.setName(name);
        t.setStatus(status);
        t.setChannel("OPS");
        t.setTenantSecret("secret-" + id);   // SensitiveAutoConfiguration 已注册 key Bean,加密 TypeHandler 可用
        t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now());
        t.setIsDeleted((short) 0);
        return t;
    }

    @Test
    @DisplayName("泛型 mapper 解析出 TenantStore;只回 ACTIVE")
    void resolvesStoreAndQueries() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(TenantStore.class);

            DemoTenantMapper mapper = ctx.getBean(DemoTenantMapper.class);
            mapper.insert(row(1L, "acme", "ACTIVE"));
            mapper.insert(row(2L, "gone", "SUSPEND"));

            TenantStore store = ctx.getBean(TenantStore.class);
            TenantEntity active = store.findActiveById(1L);
            assertThat(active).isNotNull();
            assertThat(active.getName()).isEqualTo("acme");

            assertThat(store.findActiveById(2L)).as("SUSPEND 不可认证").isNull();
            assertThat(store.findActiveByName("acme")).isNotNull();
            assertThat(store.findActiveByName("gone")).isNull();
            assertThat(store.findActiveById(999L)).isNull();
        });
    }

    @Test
    @DisplayName("无 BaseMapper 子接口 → 认证栈静默不装(null bean),DDL/守卫不受影响")
    void missingMapper_authStackSilent() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
                        TenantAutoConfiguration.class))
                .withPropertyValues(
                        "framework4j.tenant.enabled=true",
                        "framework4j.tenant.table-prefix=demo_",
                        "spring.datasource.url=jdbc:h2:mem:storemiss;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.username=sa")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    // @Bean 返回 null:定义仍在(ctx.containsBean=true),但实例不可获取
                    assertThat(ctx.containsBean("tenantStore")).isTrue();
                    org.assertj.core.api.Assertions.assertThatThrownBy(() -> ctx.getBean(TenantStore.class))
                            .isInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
                });
    }
}
