package fun.commons.framework4j.id.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the auto-configuration metadata points to the actual configuration class.
 */
class IdSdkAutoConfigurationMetadataTest {

    private static final String IMPORTS_RESOURCE =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    @Test
    void importsFileShouldReferenceExistingAutoConfiguration() throws IOException, ClassNotFoundException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        assertThat(classLoader).as("Context class loader").isNotNull();

        try (InputStream input = classLoader.getResourceAsStream(IMPORTS_RESOURCE)) {
            assertThat(input).as("AutoConfiguration imports resource").isNotNull();

            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
            String[] lines = content.split("\\r?\\n");

            // 验证第一个配置是IdSdkAutoConfiguration
            String firstLine = lines[0].trim();
            assertThat(firstLine).isEqualTo(IdSdkAutoConfiguration.class.getName());

            Class<?> autoConfiguration = Class.forName(firstLine, false, classLoader);
            assertThat(autoConfiguration).isEqualTo(IdSdkAutoConfiguration.class);

            // 验证其他配置类也能正常加载
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    Class<?> config = Class.forName(line.trim(), false, classLoader);
                    assertThat(config).isNotNull();
                }
            }
        }
    }
}
