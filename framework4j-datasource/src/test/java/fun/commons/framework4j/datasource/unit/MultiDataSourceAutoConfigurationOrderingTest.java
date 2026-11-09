package fun.commons.framework4j.datasource.unit;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import fun.commons.framework4j.datasource.config.MultiDataSourceAutoConfiguration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.lang.reflect.Modifier;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #29 回归（v1.10）：
 * <ul>
 *   <li><b>#29-1 排序</b>：MultiDataSourceAutoConfiguration 声明 @AutoConfigureBefore
 *       (DataSourceAutoConfiguration / DruidDataSourceAutoConfigure)——框架 datasources
 *       配置存在时五件套先注册，Boot/Druid 装配让位，消费方不再被迫双 exclude</li>
 *   <li><b>#29-2 拦截器 wiring 功能修复</b>：MybatisPlusInterceptor（框架方案 C 或用户自定义）
 *       按类型 wire 进框架 SqlSessionFactory——此前 plugins 属性从未设置，方案 C 注册的
 *       分页拦截器是"惰性 bean"，从未真正生效</li>
 *   <li><b>#29-3 BPP 静态化</b>：两个 BeanPostProcessor @Bean 方法为 static，
 *       消除 BPPChecker「not eligible for auto-proxying」WARN</li>
 * </ul>
 */
@DisplayName("issue #29：datasource 装配排序 + MP 拦截器 wiring + BPP 静态化")
class MultiDataSourceAutoConfigurationOrderingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MultiDataSourceAutoConfiguration.class,
                    DataSourceAutoConfiguration.class))
            .withPropertyValues(
                    "spring.application.name=ordering-test",
                    "framework4j.datasource.enabled=true",
                    "framework4j.datasource.datasources.default.url=jdbc:h2:mem:ordering-db;DB_CLOSE_DELAY=-1",
                    "framework4j.datasource.datasources.default.driver-class-name=org.h2.Driver",
                    "framework4j.datasource.datasources.default.username=sa",
                    "framework4j.datasource.datasources.default.password=");

    @Nested
    @DisplayName("#29-1 排序治理")
    class OrderingTest {

        @Test
        @DisplayName("框架 datasources 存在时：DataSource 归框架（Druid），Boot DSauto 让位不产生第二个 DataSource")
        void frameworkDatasourceWinsOverBootAutoConfiguration() {
            runner.run(context -> {
                assertThat(context).hasNotFailed();

                // @Primary 注入的是框架 Druid 数据源
                DataSource primary = context.getBean(DataSource.class);
                assertThat(primary).isInstanceOf(DruidDataSource.class);
                assertThat(context.getBean("defaultDataSource")).isSameAs(primary);

                // DSauto 让位：容器中 DataSource 类型 bean 只有一组框架注册的
                Map<String, DataSource> all = context.getBeansOfType(DataSource.class);
                assertThat(all).as("DSauto 应 @ConditionalOnMissingBean 让位，不应出现 Hikari/内嵌数据源")
                        .hasSize(1);
            });
        }

        @Test
        @DisplayName("结构契约：@AutoConfigureBefore 显式声明 Boot/Druid 两个自动配置")
        void autoConfigureBeforeDeclared() throws Exception {
            AutoConfigureBefore anno = MultiDataSourceAutoConfiguration.class
                    .getAnnotation(AutoConfigureBefore.class);
            assertThat(anno).isNotNull();
            assertThat(anno.name()).containsExactlyInAnyOrder(
                    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                    "com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure");
        }
    }

    @Nested
    @DisplayName("#29-2 MP 拦截器 wiring（方案 C 惰性 bean 修复）")
    class InterceptorWiringTest {

        @Test
        @DisplayName("框架 SqlSessionFactory 的 Configuration 必须持有 MybatisPlusInterceptor（分页等真正生效）")
        void pluginsWiredIntoFrameworkFactory() {
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);

                SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);
                assertThat(factory.getConfiguration().getInterceptors())
                        .as("v1.10 修复前 plugins 从未 wire 进工厂——方案 C 拦截器是惰性 bean")
                        .anySatisfy(i -> assertThat(i).isInstanceOf(MybatisPlusInterceptor.class));
            });
        }

        @Test
        @DisplayName("用户自定义 MybatisPlusInterceptor 时：框架方案 C 让位，工厂按类型引用用户的 bean")
        void userDefinedInterceptorTakesOver() {
            runner.withBean("myInterceptor", MybatisPlusInterceptor.class, MybatisPlusInterceptor::new)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        MybatisPlusInterceptor userBean = context.getBean("myInterceptor",
                                MybatisPlusInterceptor.class);
                        // @ConditionalOnMissingBean：方案 C 未注册第二实例
                        assertThat(context.getBeansOfType(MybatisPlusInterceptor.class)).hasSize(1);
                        SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);
                        assertThat(factory.getConfiguration().getInterceptors())
                                .contains(userBean);
                    });
        }
    }

    @Nested
    @DisplayName("#29-3 BPP @Bean 方法静态化")
    class StaticBeanMethodTest {

        @Test
        @DisplayName("dataSourceOnBeanPostProcessor 与 sqlTracingBeanPostProcessor 均为 static @Bean")
        void beanPostProcessorBeanMethodsAreStatic() throws Exception {
            assertThat(Modifier.isStatic(MultiDataSourceAutoConfiguration.class
                    .getDeclaredMethod("dataSourceOnBeanPostProcessor",
                            fun.commons.framework4j.datasource.manager.MultiDataSourceManager.class)
                    .getModifiers())).isTrue();
            assertThat(Modifier.isStatic(fun.commons.framework4j.datasource.tracing.SqlTracingAutoConfiguration.class
                    .getDeclaredMethod("sqlTracingBeanPostProcessor")
                    .getModifiers())).isTrue();
        }
    }
}
