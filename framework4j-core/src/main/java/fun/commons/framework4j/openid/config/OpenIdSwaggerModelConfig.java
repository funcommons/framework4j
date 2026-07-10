package fun.commons.framework4j.openid.config;

import fun.commons.framework4j.openid.annotation.OpenId;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Swagger 出参模型适配
 * <p>
 * 作用: 修正 DTO/VO 字段的文档类型。
 * 扫描到 @OpenId 时，强制将 Schema 类型修正为 string。
 * <p>
 * 支持类型:
 * 1. 基本类型: Long, long, Integer, int -> string
 * 2. 集合/数组: List<Long>, Long[], int[] -> Array[string]
 */
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConditionalOnClass(ModelConverter.class)
@ConditionalOnProperty(name = "ldx2t.commons.openid.swagger.enabled", matchIfMissing = true)
public class OpenIdSwaggerModelConfig {

    @Bean
    public ModelConverter openIdModelConverter() {
        log.info("【OpenID】openIdModelConverter，Swagger出参模型适配，@OpenId注解DTO字段在API文档中显示为string");
        return new ModelConverter() {
            @Override
            public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
                if (chain != null && chain.hasNext()) {
                    Schema<?> schema = chain.next().resolve(type, context, chain);
                    if (schema == null) return null;

                    // 检查字段上的 @OpenId 注解
                    if (type.getCtxAnnotations() != null) {
                        boolean hasOpenId = Arrays.stream(type.getCtxAnnotations())
                                .anyMatch(annotation -> annotation instanceof OpenId);

                        if (hasOpenId) {
                            // 场景 1: 集合或数组 (List<Long>, Long[], int[] 等)
                            // 此时 schema 是 ArraySchema，我们需要修改其内部元素的类型为 String
                            if (schema instanceof ArraySchema) {
                                ArraySchema arraySchema = (ArraySchema) schema;

                                // 创建元素的 String Schema
                                StringSchema itemSchema = new StringSchema();
                                itemSchema.setExample("Xy7Z9aBc...");
                                itemSchema.setDescription("OpenID");

                                // 替换数组的 items
                                arraySchema.setItems(itemSchema);
                                // 追加描述到数组本身
                                arraySchema.setDescription((schema.getDescription() == null ? "" : schema.getDescription()) + " (OpenID List)");
                                return arraySchema;
                            }

                            // 场景 2: 单个数值 (Long, Integer, int 等)
                            // 直接将当前字段的 Schema 替换为 StringSchema
                            StringSchema newSchema = new StringSchema();
                            newSchema.setDescription(schema.getDescription());
                            newSchema.setName(schema.getName());
                            newSchema.setNullable(schema.getNullable());
                            newSchema.setExample("Xy7Z9aBc...");
                            newSchema.setDescription((schema.getDescription() == null ? "" : schema.getDescription()) + " (OpenID)");
                            return newSchema;
                        }
                    }
                    return schema;
                }
                return null;
            }
        };
    }
}