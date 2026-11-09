package fun.commons.framework4j.datasource.unit;

import fun.commons.framework4j.datasource.properties.DataSourceProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #28 回归（v1.10）：Druid 缺省 filters 去 wall。
 * <p>
 * wall 的 {@code comment not allow} 策略会拦截注释 SQL——与同仓 framework4j-sql-tracing
 * 的 {@code /*traceid=...*&#47;} 前缀注释互斥（同仓两件互相废掉对方核心功能），也会拦掉
 * Flyway 带注释的迁移脚本。wall 属安全强需求，v1.10 起显式选配。
 */
@DisplayName("issue #28：Druid 缺省 filters 去 wall（显式选配）")
class DruidFiltersDefaultTest {

    @Test
    @DisplayName("缺省 filters = stat,slf4j（不含 wall）")
    void defaultFiltersExcludeWall() {
        DataSourceProperties properties = new DataSourceProperties();
        assertThat(properties.getDruid().getFilters())
                .isEqualTo("stat,slf4j")
                .doesNotContain("wall");
    }

    @Test
    @DisplayName("显式选配 wall 仍可配置（消费方自担与 sql-tracing/Flyway 的注释冲突）")
    void wallRemainsOptIn() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.getDruid().setFilters("stat,wall,slf4j");
        assertThat(properties.getDruid().getFilters()).contains("wall");
    }
}
