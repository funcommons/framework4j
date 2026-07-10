package fun.commons.framework4j.openid.config;

import fun.commons.framework4j.openid.annotation.OpenId;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Swagger 入参文档适配
 * <p>
 * 作用: 修正 Controller 参数 (@PathVariable/@RequestParam) 的文档类型。
 * 将 @OpenId Long/Integer/List 显示为 string 或 array[string]，并给出示例。
 */
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConditionalOnClass(OperationCustomizer.class)
@ConditionalOnProperty(name = "ldx2t.commons.openid.swagger.enabled", matchIfMissing = true)
public class OpenIdSwaggerConfig {

    @Bean
    public OperationCustomizer openIdOperationCustomizer() {
        log.info("【OpenID】openIdOperationCustomizer，Swagger入参文档适配，@OpenId注解Long类型在API文档中显示为string");
        return (operation, handlerMethod) -> {
            if (operation.getParameters() == null) {
                return operation;
            }

            java.lang.reflect.Parameter[] methodParameters = handlerMethod.getMethod().getParameters();

            for (Parameter swaggerParam : operation.getParameters()) {
                Arrays.stream(methodParameters)
                        .filter(p -> p.getName().equals(swaggerParam.getName()))
                        .filter(p -> p.isAnnotationPresent(OpenId.class))
                        .findFirst()
                        .ifPresent(javaParam -> {
                            Schema<?> schema = swaggerParam.getSchema();

                            // 1. 处理集合/数组类型 (List<Long>, Long[], int[] 等)
                            if (schema instanceof ArraySchema) {
                                ArraySchema arraySchema = (ArraySchema) schema;
                                // 将数组元素的类型改为 String (OpenID)
                                StringSchema itemSchema = new StringSchema();
                                itemSchema.setDescription("OpenID");
                                itemSchema.setExample("Xy7Z9aBc...");

                                arraySchema.setItems(itemSchema);

                                // 更新参数描述
                                String desc = swaggerParam.getDescription();
                                swaggerParam.setDescription((desc == null ? "" : desc + " ") + "(OpenID List)");
                            }
                            // 2. 处理单值类型 (Long, Integer, int)
                            else {
                                swaggerParam.setSchema(new StringSchema());
                                String desc = swaggerParam.getDescription();
                                swaggerParam.setDescription((desc == null ? "" : desc + " ") + "(OpenID)");
                                swaggerParam.setExample("Xy7Z9aBc...");
                            }
                        });
            }
            return operation;
        };
    }
}