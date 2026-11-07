package fun.commons.framework4j.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.XMLConstants;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #27 防回归：SDK 各模块不得以 compile 非 optional 传递 web/servlet 面。
 * <p>
 * 背景：token-agent 的 WebFlux 反应式网关（amp-gateway）消费 framework4j-all 时，
 * 任一模块把 spring-boot-starter-web（含 tomcat-embed-core）以 compile scope 带入，
 * Boot 反应式服务工厂判定翻转（Tomcat 抢注 → Netty 让位）→ WSS 握手 ClassCastException 500。
 * <p>
 * 本测试解析仓库内全部模块目录下的 {@code pom.xml}（surefire CWD = 模块 basedir，
 * 兄弟模块在上级目录），锁定契约：
 * <ul>
 *   <li>{@code spring-boot-starter-web} 声明即必须 {@code <optional>true</optional>}</li>
 *   <li>{@code jakarta.servlet-api} 声明即必须 {@code provided} 或 {@code optional}
 *       （主代码在用的模块用 provided 对齐 accesstoken 口径）</li>
 * </ul>
 */
@DisplayName("issue #27：web/servlet 依赖面泄漏防护（结构契约）")
class DependencyLeakGuardTest {

    /** 应用/消费方模拟模块——自身就是最终应用，合法直接依赖 web，不受本契约约束 */
    private static final List<String> EXEMPT_MODULES = List.of("framework4j-demo");

    private record Dep(String artifactId, String scope, String optional) {}

    private List<Dep> parseDeps(Path pom) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try (InputStream in = Files.newInputStream(pom)) {
            Element root = dbf.newDocumentBuilder().parse(in).getDocumentElement();
            List<Dep> deps = new ArrayList<>();
            NodeList nodes = root.getElementsByTagName("dependency");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element d = (Element) nodes.item(i);
                deps.add(new Dep(
                        text(d, "artifactId"),
                        text(d, "scope") == null ? "compile" : text(d, "scope"),
                        text(d, "optional") == null ? "false" : text(d, "optional")));
            }
            return deps;
        }
    }

    private String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    private Path repoRoot() {
        // surefire CWD = 模块 basedir（framework4j-compat-test），仓库根在上级
        return Paths.get("..").toAbsolutePath().normalize();
    }

    @Test
    @DisplayName("全部模块：spring-boot-starter-web 必须 optional（不得 compile 泄漏 tomcat）")
    void starterWebMustBeOptionalEverywhere() throws Exception {
        try (Stream<Path> modules = Files.list(repoRoot())) {
            List<Path> poms = modules
                    .filter(p -> p.getFileName().toString().startsWith("framework4j-"))
                    .map(p -> p.resolve("pom.xml"))
                    .filter(Files::exists)
                    .toList();
            assertThat(poms).as("应扫描到 21 个模块 pom（不含根 pom）").hasSize(21);

            for (Path pom : poms) {
                String module = pom.getParent().getFileName().toString();
                if (EXEMPT_MODULES.contains(module)) {
                    continue;
                }
                for (Dep dep : parseDeps(pom)) {
                    if ("spring-boot-starter-web".equals(dep.artifactId())) {
                        assertThat(dep.optional())
                                .as("%s 的 spring-boot-starter-web 必须 <optional>true</optional>（issue #27）",
                                        module)
                                .isEqualTo("true");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("全部模块：jakarta.servlet-api 必须 provided 或 optional（不得 compile 泄漏）")
    void servletApiMustNotBePlainCompile() throws Exception {
        try (Stream<Path> modules = Files.list(repoRoot())) {
            List<Path> poms = modules
                    .filter(p -> p.getFileName().toString().startsWith("framework4j-"))
                    .map(p -> p.resolve("pom.xml"))
                    .filter(Files::exists)
                    .toList();

            for (Path pom : poms) {
                String module = pom.getParent().getFileName().toString();
                for (Dep dep : parseDeps(pom)) {
                    if ("jakarta.servlet-api".equals(dep.artifactId())) {
                        assertThat(dep.scope())
                                .as("%s 的 jakarta.servlet-api 不得为 compile（issue #27），应为 provided/optional",
                                        module)
                                .isIn("provided", "test");
                        if ("provided".equals(dep.scope())) {
                            assertThat(dep.optional())
                                    .as("%s 的 provided servlet-api 无需再 optional（provided 本就不传递）",
                                            module)
                                    .isEqualTo("false");
                        }
                    }
                }
            }
        }
    }
}
