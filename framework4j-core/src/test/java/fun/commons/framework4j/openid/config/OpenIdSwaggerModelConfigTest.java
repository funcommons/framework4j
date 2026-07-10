package fun.commons.framework4j.openid.config;

import fun.commons.framework4j.openid.annotation.OpenId;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * OpenIdSwaggerModelConfig 测试套件
 *
 * 测试 Swagger 模型转换器，确保 OpenID 字段在文档中正确显示
 */
@DisplayName("OpenIdSwaggerModelConfig Swagger模型配置测试")
class OpenIdSwaggerModelConfigTest {

    private OpenIdSwaggerModelConfig modelConfig;
    private ModelConverter modelConverter;

    @BeforeEach
    void setUp() {
        modelConfig = new OpenIdSwaggerModelConfig();
        modelConverter = modelConfig.openIdModelConverter();
    }

    // ==========================================
    // 1. 基本配置测试
    // ==========================================

    @Nested
    @DisplayName("1. 基本配置测试")
    class BasicConfigurationTest {

        @Test
        @DisplayName("配置创建: 应正确创建 ModelConverter")
        void testConfigurationCreation_ShouldCreateModelConverter() {
            assertNotNull(modelConverter, "ModelConverter 应该被正确创建");
            assertTrue(modelConverter instanceof ModelConverter,
                "应该是 ModelConverter 的实例");
        }

        @Test
        @DisplayName("配置工厂: 每次调用应返回新实例")
        void testConfigurationFactory_EachCallShouldReturnNewInstance() {
            ModelConverter converter1 = modelConfig.openIdModelConverter();
            ModelConverter converter2 = modelConfig.openIdModelConverter();

            assertNotNull(converter1);
            assertNotNull(converter2);
            assertNotSame(converter1, converter2, "每次调用应该返回新实例");
        }

        @Test
        @DisplayName("配置类型: 应返回匿名类实例")
        void testConfigurationType_ShouldReturnAnonymousClassInstance() {
            assertTrue(modelConverter.getClass().isAnonymousClass(),
                "应该是匿名类实例");
            assertTrue(modelConverter.getClass().getName().contains("$"),
                "类名应该包含 $ 符号");
        }
    }

    // ==========================================
    // 2. 模型转换测试
    // ==========================================

    @Nested
    @DisplayName("2. 模型转换测试")
    class ModelConversionTest {

        @Test
        @DisplayName("模型转换: 无注解字段应保持不变")
        void testModelConversion_FieldWithoutAnnotationShouldRemainUnchanged() {
            AnnotatedType annotatedType = createAnnotatedType(Long.class);
            ModelConverterContext context = mock(ModelConverterContext.class);
            Iterator<ModelConverter> chain = createMockChain(createStringSchema());

            Schema<?> result = modelConverter.resolve(annotatedType, context, chain);

            assertNotNull(result);
            assertTrue(result instanceof StringSchema);
        }

        @Test
        @DisplayName("模型转换: null 链应返回 null")
        void testModelConversion_NullChainShouldReturnNull() {
            AnnotatedType annotatedType = createAnnotatedType(Long.class);
            ModelConverterContext context = mock(ModelConverterContext.class);
            Iterator<ModelConverter> chain = null;

            Schema<?> result = modelConverter.resolve(annotatedType, context, chain);

            assertNull(result);
        }

        @Test
        @DisplayName("模型转换: 空链应返回 null")
        void testModelConversion_EmptyChainShouldReturnNull() {
            AnnotatedType annotatedType = createAnnotatedType(Long.class);
            ModelConverterContext context = mock(ModelConverterContext.class);
            Iterator<ModelConverter> chain = java.util.Collections.emptyIterator(); // 空迭代器

            Schema<?> result = modelConverter.resolve(annotatedType, context, chain);

            assertNull(result);
        }
    }

    // ==========================================
    // 3. OpenID 注解处理测试
    // ==========================================

    @Nested
    @DisplayName("3. OpenID 注解处理测试")
    class OpenIdAnnotationProcessingTest {

        @Test
        @DisplayName("注解处理: @OpenId 单值字段应转换为字符串")
        void testAnnotationProcessing_OpenIdSingleValueShouldConvertToString() {
            AnnotatedType annotatedType = createAnnotatedTypeWithOpenId(Long.class);
            ModelConverterContext context = mock(ModelConverterContext.class);
            Iterator<ModelConverter> chain = createMockChain(createIntegerSchema());

            Schema<?> result = modelConverter.resolve(annotatedType, context, chain);

            assertNotNull(result);
            assertTrue(result instanceof StringSchema,
                "字段应该被转换为字符串类型");

            StringSchema stringSchema = (StringSchema) result;
            assertEquals("Xy7Z9aBc...", stringSchema.getExample(),
                "示例应设置为 OpenID 格式");

            assertTrue(stringSchema.getDescription().contains("(OpenID)"),
                "描述应包含 OpenID 标识");
        }

        @Test
        @DisplayName("注解处理: @OpenId 数组字段应转换为字符串数组")
        void testAnnotationProcessing_OpenIdArrayShouldConvertToStringArray() {
            AnnotatedType annotatedType = createAnnotatedTypeWithOpenId(Long.class);
            ModelConverterContext context = mock(ModelConverterContext.class);
            Iterator<ModelConverter> chain = createMockChain(createIntegerArraySchema());

            Schema<?> result = modelConverter.resolve(annotatedType, context, chain);

            assertNotNull(result);
            assertTrue(result instanceof ArraySchema,
                "字段应该保持为数组类型");

            ArraySchema arraySchema = (ArraySchema) result;
            assertTrue(arraySchema.getItems() instanceof StringSchema,
                "数组元素应该被转换为字符串类型");

            assertTrue(arraySchema.getDescription().contains("(OpenID List)"),
                "数组描述应包含 OpenID List 标识");
        }

        @Test
        @DisplayName("注解处理: 多注解字段应正确识别 OpenID")
        void testAnnotationProcessing_MultipleAnnotationsShouldIdentifyOpenId() {
            OpenId openId = createMockOpenIdAnnotation();
            Deprecated deprecated = mock(Deprecated.class);

            AnnotatedType annotatedType = createAnnotatedTypeWithAnnotations(Long.class, openId, deprecated);
            ModelConverterContext context = mock(ModelConverterContext.class);
            Iterator<ModelConverter> chain = createMockChain(createIntegerSchema());

            Schema<?> result = modelConverter.resolve(annotatedType, context, chain);

            assertNotNull(result);
            assertTrue(result instanceof StringSchema,
                "即使有多个注解，也应该正确识别 OpenID");
        }
    }

    // ==========================================
    // 4. 性能测试
    // ==========================================

    @Nested
    @DisplayName("4. 性能测试")
    @Tag("Performance")
    class PerformanceTest {

        @Test
        @DisplayName("性能测试: 转换器应用性能")
        void testPerformance_ConverterApplicationPerformance() {
            AnnotatedType annotatedType = createAnnotatedType(Long.class);
            ModelConverterContext context = mock(ModelConverterContext.class);
            Iterator<ModelConverter> chain = createMockChain(createIntegerSchema());

            int iterations = 1000;
            long startTime = System.nanoTime();

            for (int i = 0; i < iterations; i++) {
                modelConverter.resolve(annotatedType, context, chain);
            }

            long endTime = System.nanoTime();
            long avgDurationNanos = (endTime - startTime) / iterations;
            long avgDurationMicros = avgDurationNanos / 1000;

            // 平均每次转换器应用应在100微秒内完成
            assertTrue(avgDurationMicros < 100,
                String.format("转换器应用时间 %d μs 超过阈值", avgDurationMicros));

            System.out.printf("转换器应用性能: %d 次操作平均耗时 %d μs%n",
                iterations, avgDurationMicros);
        }

        @Test
        @DisplayName("性能测试: 创建转换器性能")
        void testPerformance_ConverterCreationPerformance() {
            int iterations = 1000;
            long startTime = System.nanoTime();

            for (int i = 0; i < iterations; i++) {
                modelConfig.openIdModelConverter();
            }

            long endTime = System.nanoTime();
            long avgDurationNanos = (endTime - startTime) / iterations;
            long avgDurationMicros = avgDurationNanos / 1000;

            // 转换器创建应在50微秒内完成
            assertTrue(avgDurationMicros < 50,
                String.format("转换器创建时间 %d μs 超过阈值", avgDurationMicros));

            System.out.printf("转换器创建性能: %d 次操作平均耗时 %d μs%n",
                iterations, avgDurationMicros);
        }
    }

    // ==========================================
    // 5. 边界条件测试
    // ==========================================

    @Nested
    @DisplayName("5. 边界条件测试")
    class BoundaryTest {

        @Test
        @DisplayName("边界条件: null 注解数组应处理")
        void testBoundary_NullAnnotationArrayShouldHandle() {
            AnnotatedType annotatedType = createAnnotatedTypeWithNullAnnotations(Long.class);
            ModelConverterContext context = mock(ModelConverterContext.class);
            Iterator<ModelConverter> chain = createMockChain(createIntegerSchema());

            Schema<?> result = modelConverter.resolve(annotatedType, context, chain);

            assertNotNull(result);
            // 应该返回原始 schema，因为没有 OpenID 注解
        }

        @Test
        @DisplayName("边界条件: 空注解数组应处理")
        void testBoundary_EmptyAnnotationArrayShouldHandle() {
            AnnotatedType annotatedType = createAnnotatedTypeWithEmptyAnnotations(Long.class);
            ModelConverterContext context = mock(ModelConverterContext.class);
            Iterator<ModelConverter> chain = createMockChain(createIntegerSchema());

            Schema<?> result = modelConverter.resolve(annotatedType, context, chain);

            assertNotNull(result);
            // 应该返回原始 schema，因为没有 OpenID 注解
        }

        @Test
        @DisplayName("边界条件: 链返回 null 应处理")
        void testBoundary_ChainReturnsNullShouldHandle() {
            AnnotatedType annotatedType = createAnnotatedTypeWithOpenId(Long.class);
            ModelConverterContext context = mock(ModelConverterContext.class);
            Iterator<ModelConverter> chain = createMockChain(null); // 返回 null

            Schema<?> result = modelConverter.resolve(annotatedType, context, chain);

            assertNull(result, "链返回 null 时转换器应该返回 null");
        }
    }

    // ==========================================
    // 6. 集成测试
    // ==========================================

    @Nested
    @DisplayName("6. 集成测试")
    class IntegrationTest {

        @Test
        @DisplayName("集成测试: 多次转换一致性")
        void testIntegration_MultipleConversionConsistency() {
            AnnotatedType annotatedType = createAnnotatedTypeWithOpenId(Long.class);
            ModelConverterContext context = mock(ModelConverterContext.class);

            // Iterator只能消费一次，需要为每次调用创建新的chain
            Iterator<ModelConverter> chain1 = createMockChain(createIntegerSchema());
            Iterator<ModelConverter> chain2 = createMockChain(createIntegerSchema());

            Schema<?> result1 = modelConverter.resolve(annotatedType, context, chain1);
            Schema<?> result2 = modelConverter.resolve(annotatedType, context, chain2);

            assertNotNull(result1);
            assertNotNull(result2);
            assertEquals(result1.getClass(), result2.getClass(),
                "多次转换应该返回相同类型的结果");

            if (result1 instanceof StringSchema && result2 instanceof StringSchema) {
                StringSchema string1 = (StringSchema) result1;
                StringSchema string2 = (StringSchema) result2;
                assertEquals(string1.getExample(), string2.getExample(),
                    "多次转换的示例应该一致");
            }
        }
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    private AnnotatedType createAnnotatedType(Class<?> type) {
        return createAnnotatedTypeWithAnnotations(type, new Annotation[0]);
    }

    private AnnotatedType createAnnotatedTypeWithOpenId(Class<?> type) {
        OpenId openId = createMockOpenIdAnnotation();
        return createAnnotatedTypeWithAnnotations(type, openId);
    }

    private AnnotatedType createAnnotatedTypeWithAnnotations(Class<?> type, Annotation... annotations) {
        AnnotatedType annotatedType = mock(AnnotatedType.class);
        when(annotatedType.getType()).thenReturn(type);
        when(annotatedType.getCtxAnnotations()).thenReturn(annotations);
        return annotatedType;
    }

    private AnnotatedType createAnnotatedTypeWithNullAnnotations(Class<?> type) {
        AnnotatedType annotatedType = mock(AnnotatedType.class);
        when(annotatedType.getType()).thenReturn(type);
        when(annotatedType.getCtxAnnotations()).thenReturn(null);
        return annotatedType;
    }

    private AnnotatedType createAnnotatedTypeWithEmptyAnnotations(Class<?> type) {
        AnnotatedType annotatedType = mock(AnnotatedType.class);
        when(annotatedType.getType()).thenReturn(type);
        when(annotatedType.getCtxAnnotations()).thenReturn(new Annotation[0]);
        return annotatedType;
    }

    private OpenId createMockOpenIdAnnotation() {
        return mock(OpenId.class);
    }

    private Iterator<ModelConverter> createMockChain(Schema<?> schemaToReturn) {
        ModelConverter nextConverter = mock(ModelConverter.class);
        when(nextConverter.resolve(any(), any(), any())).thenReturn(schemaToReturn);
        return java.util.Collections.singletonList(nextConverter).iterator();
    }

    private Schema<?> createIntegerSchema() {
        Schema<?> schema = new Schema<>();
        schema.setType("integer");
        return schema;
    }

    private Schema<?> createStringSchema() {
        return new StringSchema();
    }

    private Schema<?> createIntegerArraySchema() {
        ArraySchema arraySchema = new ArraySchema();
        Schema<?> itemSchema = new Schema<>();
        itemSchema.setType("integer");
        arraySchema.setItems(itemSchema);
        return arraySchema;
    }
}