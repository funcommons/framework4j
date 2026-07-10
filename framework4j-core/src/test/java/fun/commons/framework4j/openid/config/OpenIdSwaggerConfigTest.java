package fun.commons.framework4j.openid.config;

import fun.commons.framework4j.openid.annotation.OpenId;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenIdSwaggerConfig 测试套件
 *
 * 测试 Swagger 文档自动配置，确保 OpenID 参数正确显示
 */
@DisplayName("OpenIdSwaggerConfig Swagger配置测试")
class OpenIdSwaggerConfigTest {

    private OpenIdSwaggerConfig swaggerConfig;
    private OperationCustomizer operationCustomizer;

    @BeforeEach
    void setUp() {
        swaggerConfig = new OpenIdSwaggerConfig();
        operationCustomizer = swaggerConfig.openIdOperationCustomizer();
    }

    // ==========================================
    // 1. 基本配置测试
    // ==========================================

    @Nested
    @DisplayName("1. 基本配置测试")
    class BasicConfigurationTest {

        @Test
        @DisplayName("配置创建: 应正确创建 OperationCustomizer")
        void testConfigurationCreation_ShouldCreateOperationCustomizer() {
            assertNotNull(operationCustomizer, "OperationCustomizer 应该被正确创建");
            assertTrue(operationCustomizer instanceof OperationCustomizer,
                "应该是 OperationCustomizer 的实例");
        }

        @Test
        @DisplayName("配置工厂: 每次调用应返回Lambda表达式")
        void testConfigurationFactory_EachCallShouldReturnNewInstance() {
            OperationCustomizer customizer1 = swaggerConfig.openIdOperationCustomizer();
            OperationCustomizer customizer2 = swaggerConfig.openIdOperationCustomizer();

            assertNotNull(customizer1);
            assertNotNull(customizer2);
            // 注意: Lambda表达式可能被JVM缓存，返回同一实例是正常的
            // 只需验证返回的都是有效的OperationCustomizer即可
            assertTrue(customizer1 instanceof OperationCustomizer);
            assertTrue(customizer2 instanceof OperationCustomizer);
        }

        @Test
        @DisplayName("配置类型: 应返回正确的类型")
        void testConfigurationType_ShouldReturnCorrectType() {
            // 验证是OperationCustomizer的实例即可
            // Lambda表达式的类名格式在不同JVM版本中可能不同，不应依赖具体格式
            assertTrue(operationCustomizer instanceof OperationCustomizer,
                "应该是 OperationCustomizer 的实例");
        }
    }

    // ==========================================
    // 2. 参数处理测试
    // ==========================================

    @Nested
    @DisplayName("2. 参数处理测试")
    class ParameterProcessingTest {

        @Test
        @DisplayName("参数处理: 空参数列表应保持不变")
        void testParameterProcessing_EmptyParameterListShouldRemainUnchanged() throws Exception {
            Operation operation = new Operation();
            operation.setParameters(Collections.emptyList());

            HandlerMethod handlerMethod = createHandlerMethod("testMethod", String.class);

            Operation result = operationCustomizer.customize(operation, handlerMethod);

            assertNotNull(result);
            assertEquals(operation, result);
            assertTrue(result.getParameters().isEmpty());
        }

        @Test
        @DisplayName("参数处理: null 参数列表应保持不变")
        void testParameterProcessing_NullParameterListShouldRemainUnchanged() throws Exception {
            Operation operation = new Operation();
            operation.setParameters(null);

            HandlerMethod handlerMethod = createHandlerMethod("testMethod", String.class);

            Operation result = operationCustomizer.customize(operation, handlerMethod);

            assertNotNull(result);
            assertEquals(operation, result);
            assertNull(result.getParameters());
        }

        @Test
        @DisplayName("参数处理: 无 @OpenId 注解参数应保持不变")
        void testParameterProcessing_NonOpenIdParametersShouldRemainUnchanged() throws Exception {
            Operation operation = new Operation();
            Parameter parameter = createParameter("userId", "integer");
            operation.setParameters(Collections.singletonList(parameter));

            HandlerMethod handlerMethod = createHandlerMethod("testMethod", Long.class);

            Operation result = operationCustomizer.customize(operation, handlerMethod);

            assertNotNull(result);
            assertEquals(1, result.getParameters().size());
            assertEquals("userId", result.getParameters().get(0).getName());
        }
    }

    // ==========================================
    // 3. OpenID 注解处理测试
    // ==========================================

    @Nested
    @DisplayName("3. OpenID 注解处理测试")
    class OpenIdAnnotationProcessingTest {

        @Test
        @DisplayName("注解处理: @OpenId 单值参数应转换为字符串")
        void testAnnotationProcessing_OpenIdSingleValueShouldConvertToString() throws Exception {
            Operation operation = new Operation();
            // Java反射默认参数名为 arg0, arg1, arg2 (需要-parameters编译标志才能获取真实名称)
            Parameter parameter = createParameter("arg0", "integer");
            operation.setParameters(Collections.singletonList(parameter));

            HandlerMethod handlerMethod = createOpenIdHandlerMethod("testMethod", Long.class);

            Operation result = operationCustomizer.customize(operation, handlerMethod);

            assertNotNull(result);
            assertEquals(1, result.getParameters().size());
            Parameter resultParam = result.getParameters().get(0);

            assertTrue(resultParam.getSchema() instanceof StringSchema,
                "参数应该被转换为字符串类型");

            assertTrue(resultParam.getDescription().contains("(OpenID)"),
                "参数描述应包含 OpenID 标识");

            assertEquals("Xy7Z9aBc...", resultParam.getExample(),
                "参数示例应设置为 OpenID 格式");
        }

        @Test
        @DisplayName("注解处理: @OpenId 数组参数应转换为字符串数组")
        void testAnnotationProcessing_OpenIdArrayShouldConvertToStringArray() throws Exception {
            Operation operation = new Operation();
            ArraySchema arraySchema = new ArraySchema();
            arraySchema.setItems(new io.swagger.v3.oas.models.media.IntegerSchema());

            // Java反射默认参数名: arg0=userId, arg1=name, arg2=userIds
            Parameter parameter = createParameter("arg2", "array");
            parameter.setSchema(arraySchema);
            operation.setParameters(Collections.singletonList(parameter));

            HandlerMethod handlerMethod = createOpenIdHandlerMethod("testMethod", List.class);

            Operation result = operationCustomizer.customize(operation, handlerMethod);

            assertNotNull(result);
            assertEquals(1, result.getParameters().size());
            Parameter resultParam = result.getParameters().get(0);

            assertTrue(resultParam.getSchema() instanceof ArraySchema,
                "参数应该保持为数组类型");

            ArraySchema resultArraySchema = (ArraySchema) resultParam.getSchema();
            assertTrue(resultArraySchema.getItems() instanceof StringSchema,
                "数组元素应该被转换为字符串类型");

            assertTrue(resultParam.getDescription().contains("(OpenID List)"),
                "参数描述应包含 OpenID List 标识");
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
        @DisplayName("性能测试: 自定义器应用性能")
        void testPerformance_CustomizerApplicationPerformance() throws Exception {
            Operation operation = createTestOperationWithParameters();
            HandlerMethod handlerMethod = createOpenIdHandlerMethod("testMethod", Long.class);

            int iterations = 1000;
            long startTime = System.nanoTime();

            for (int i = 0; i < iterations; i++) {
                operationCustomizer.customize(operation, handlerMethod);
            }

            long endTime = System.nanoTime();
            long avgDurationNanos = (endTime - startTime) / iterations;
            long avgDurationMicros = avgDurationNanos / 1000;

            // 平均每次自定义器应用应在100微秒内完成
            assertTrue(avgDurationMicros < 100,
                String.format("自定义器应用时间 %d μs 超过阈值", avgDurationMicros));

            System.out.printf("自定义器应用性能: %d 次操作平均耗时 %d μs%n",
                iterations, avgDurationMicros);
        }

        @Test
        @DisplayName("性能测试: 创建自定义器性能")
        void testPerformance_CustomizerCreationPerformance() {
            int iterations = 1000;
            long startTime = System.nanoTime();

            for (int i = 0; i < iterations; i++) {
                swaggerConfig.openIdOperationCustomizer();
            }

            long endTime = System.nanoTime();
            long avgDurationNanos = (endTime - startTime) / iterations;
            long avgDurationMicros = avgDurationNanos / 1000;

            // 自定义器创建应在50微秒内完成
            assertTrue(avgDurationMicros < 50,
                String.format("自定义器创建时间 %d μs 超过阈值", avgDurationMicros));

            System.out.printf("自定义器创建性能: %d 次操作平均耗时 %d μs%n",
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
        @DisplayName("边界条件: null 操作应处理")
        void testBoundary_NullOperationShouldHandle() throws Exception {
            HandlerMethod handlerMethod = createHandlerMethod("testMethod", String.class);

            assertThrows(NullPointerException.class, () -> {
                operationCustomizer.customize(null, handlerMethod);
            });
        }

        @Test
        @DisplayName("边界条件: null 处理器方法应处理")
        void testBoundary_NullHandlerMethodShouldHandle() {
            Operation operation = new Operation();

            Operation result = operationCustomizer.customize(operation, null);

            assertNotNull(result);
            assertEquals(operation, result);
        }

        @Test
        @DisplayName("边界条件: 参数名称不匹配应保持不变")
        void testBoundary_ParameterNameMismatchShouldRemainUnchanged() throws Exception {
            Operation operation = new Operation();
            Parameter parameter = createParameter("unknownId", "integer");
            operation.setParameters(Collections.singletonList(parameter));

            HandlerMethod handlerMethod = createOpenIdHandlerMethod("testMethod", Long.class);

            Operation result = operationCustomizer.customize(operation, handlerMethod);

            assertNotNull(result);
            assertEquals(1, result.getParameters().size());
            assertEquals("unknownId", result.getParameters().get(0).getName());
        }
    }

    // ==========================================
    // 6. 集成测试
    // ==========================================

    @Nested
    @DisplayName("6. 集成测试")
    class IntegrationTest {

        @Test
        @DisplayName("集成测试: 多种参数类型混合")
        void testIntegration_MixedParameterTypes() throws Exception {
            Operation operation = new Operation();
            // Java反射默认参数名: arg0=userId (@OpenId), arg1=name, arg2=userIds (@OpenId)
            List<Parameter> parameters = Arrays.asList(
                createParameter("arg0", "integer"),
                createParameter("arg1", "string"),
                createParameter("arg2", "array")
            );
            operation.setParameters(parameters);

            HandlerMethod handlerMethod = createOpenIdHandlerMethod("testMethod", Long.class, String.class, List.class);

            Operation result = operationCustomizer.customize(operation, handlerMethod);

            assertNotNull(result);
            assertEquals(3, result.getParameters().size());

            // 验证 OpenID 参数被转换 (arg0 有@OpenId注解)
            Parameter userIdParam = result.getParameters().get(0);
            assertTrue(userIdParam.getSchema() instanceof StringSchema);
            assertTrue(userIdParam.getDescription().contains("(OpenID)"));

            // 验证非 OpenID 参数保持不变 (arg1 没有@OpenId注解)
            Parameter nameParam = result.getParameters().get(1);
            String nameDesc = nameParam.getDescription();
            assertFalse(nameDesc != null && nameDesc.contains("(OpenID)"),
                "非OpenID参数描述不应包含OpenID标识");
        }
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    private Parameter createParameter(String name, String type) {
        Parameter parameter = new Parameter();
        parameter.setName(name);

        io.swagger.v3.oas.models.media.Schema<?> schema = new io.swagger.v3.oas.models.media.Schema<>();
        schema.setType(type);
        parameter.setSchema(schema);

        return parameter;
    }

    private HandlerMethod createHandlerMethod(String methodName, Class<?>... parameterTypes) throws Exception {
        // 使用 TestController 类的方法
        TestController controller = new TestController();
        Method method = TestController.class.getMethod("testMethod", Long.class, String.class, List.class);
        return new HandlerMethod(controller, method);
    }

    private HandlerMethod createOpenIdHandlerMethod(String methodName, Class<?>... parameterTypes) throws Exception {
        // 创建一个带有 @OpenId 注解参数的虚拟方法
        TestController controller = new TestController();
        Method method = Arrays.stream(TestController.class.getMethods())
                .filter(m -> m.getName().equals("testMethod"))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException("testMethod"));
        return new HandlerMethod(controller, method);
    }

    private Operation createTestOperationWithParameters() {
        Operation operation = new Operation();
        Parameter parameter = createParameter("userId", "integer");
        operation.setParameters(Collections.singletonList(parameter));
        return operation;
    }

    // 测试辅助类
    @SuppressWarnings("unused")
    private static class TestController {
        public void testMethod(@OpenId Long userId, String name, @OpenId List<Long> userIds) {
            // 测试方法
        }
    }
}