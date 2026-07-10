package fun.commons.framework4j.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * 传递依赖冲突检测
 * <p>
 * 模拟客户环境：framework4j + 旧版 MyBatis-Plus 3.5.5 + jsqlparser 4.6。
 * 如果 framework4j 硬传递了 jsqlparser 5.x，旧版 PaginationInnerInterceptor 类加载会崩溃。
 */
@DisplayName("传递依赖冲突检测")
class DependencyConflictTest {

    @Test
    @DisplayName("jsqlparser 4.6 SelectExpressionItem 类可加载（旧版 MyBatis-Plus 兼容）")
    void jsqlparser4xClassLoadable() throws Exception {
        // jsqlparser 4.x 的类路径
        // 如果 framework4j 传递了 5.x，这个类不存在 → NoClassDefFoundError
        assertThatCode(() -> Class.forName("net.sf.jsqlparser.statement.select.SelectExpressionItem"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("jsqlparser 版本为 4.x（非 5.x 传递覆盖）")
    void jsqlparserVersionIs4x() throws Exception {
        // 加载 jsqlparser 的实际版本
        String version = Class.forName("net.sf.jsqlparser.parser.CCJSqlParser")
                .getPackage().getImplementationVersion();
        // 4.6 应被保留（framework4j 的 jsqlparser 应 optional）
        if (version != null) {
            assertThat(version).startsWith("4.");
        }
    }

    @Test
    @DisplayName("MyBatis-Plus PaginationInnerInterceptor 类可加载")
    void paginationInterceptorClassLoadable() throws Exception {
        // mybatis-plus 3.5.5 的 PaginationInnerInterceptor 在 extension 包
        // 如果 jsqlparser 版本不对 → 静态初始化崩溃
        assertThatCode(() -> Class.forName(
                "com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor"))
                .doesNotThrowAnyException();
    }
}
