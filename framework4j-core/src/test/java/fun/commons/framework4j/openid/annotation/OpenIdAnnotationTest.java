package fun.commons.framework4j.openid.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenId 注解测试套件
 *
 * 测试 OpenID 注解的定义、属性和行为
 */
@DisplayName("OpenId 注解定义测试")
class OpenIdAnnotationTest {

    // ==========================================
    // 1. 注解定义测试
    // ==========================================

    @Nested
    @DisplayName("1. 注解定义测试")
    class AnnotationDefinitionTest {

        @Test
        @DisplayName("注解存在: OpenId 注解应该存在")
        void testAnnotationExistence_OpenIdAnnotationShouldExist() {
            assertNotNull(OpenId.class, "OpenId 注解应该存在");
        }

        @Test
        @DisplayName("注解类型: 应该是接口类型")
        void testAnnotationType_ShouldBeInterfaceType() {
            assertTrue(OpenId.class.isInterface(), "OpenId 应该是接口类型");
            assertTrue(OpenId.class.isAnnotation(), "OpenId 应该是注解类型");
        }

        @Test
        @DisplayName("注解包名: 应该在正确的包中")
        void testAnnotationPackage_ShouldBeInCorrectPackage() {
            assertEquals("com.ldx2t.commons.openid.annotation", OpenId.class.getPackage().getName(),
                "OpenId 注解应该在正确的包中");
        }

        @Test
        @DisplayName("注解名称: 应该有正确的名称")
        void testAnnotationName_ShouldHaveCorrectName() {
            assertEquals("OpenId", OpenId.class.getSimpleName(),
                "注解名称应该是 OpenId");
        }
    }

    // ==========================================
    // 2. 注解元数据测试
    // ==========================================

    @Nested
    @DisplayName("2. 注解元数据测试")
    class AnnotationMetadataTest {

        @Test
        @DisplayName("目标范围: 应支持字段和参数")
        void testTargetScope_ShouldSupportFieldAndParameter() {
            Target target = OpenId.class.getAnnotation(Target.class);
            assertNotNull(target, "应该有 @Target 注解");

            ElementType[] elementTypes = target.value();
            assertEquals(2, elementTypes.length, "应该支持 2 种元素类型");

            boolean supportsField = false;
            boolean supportsParameter = false;

            for (ElementType elementType : elementTypes) {
                if (elementType == ElementType.FIELD) {
                    supportsField = true;
                }
                if (elementType == ElementType.PARAMETER) {
                    supportsParameter = true;
                }
            }

            assertTrue(supportsField, "应该支持 FIELD 目标");
            assertTrue(supportsParameter, "应该支持 PARAMETER 目标");
        }

        @Test
        @DisplayName("保留策略: 应该是运行时保留")
        void testRetentionPolicy_ShouldBeRuntimeRetention() {
            Retention retention = OpenId.class.getAnnotation(Retention.class);
            assertNotNull(retention, "应该有 @Retention 注解");

            assertEquals(RetentionPolicy.RUNTIME, retention.value(),
                "保留策略应该是 RUNTIME");
        }

        @Test
        @DisplayName("文档化: 应该被文档化")
        void testDocumentation_ShouldBeDocumented() {
            Documented documented = OpenId.class.getAnnotation(Documented.class);
            assertNotNull(documented, "应该有 @Documented 注解");
        }

        @Test
        @DisplayName("继承性: 不应该被继承")
        void testInheritance_ShouldNotBeInherited() {
            assertFalse(OpenId.class.isAnnotationPresent(java.lang.annotation.Inherited.class),
                "OpenId 注解不应该被继承");
        }
    }

    // ==========================================
    // 3. 注解使用测试
    // ==========================================

    @Nested
    @DisplayName("3. 注解使用测试")
    class AnnotationUsageTest {

        @Test
        @DisplayName("字段注解: 可以标记在字段上")
        void testFieldAnnotation_CanBeAppliedToField() throws Exception {
            Field field = TestClass.class.getDeclaredField("id");
            assertTrue(field.isAnnotationPresent(OpenId.class),
                "字段应该有 @OpenId 注解");

            OpenId annotation = field.getAnnotation(OpenId.class);
            assertNotNull(annotation, "应该能够获取 @OpenId 注解");
        }

        @Test
        @DisplayName("参数注解: 可以标记在参数上")
        void testParameterAnnotation_CanBeAppliedToParameter() throws Exception {
            Method method = TestClass.class.getDeclaredMethod("testMethod", Long.class);
            Parameter parameter = method.getParameters()[0];

            assertTrue(parameter.isAnnotationPresent(OpenId.class),
                "参数应该有 @OpenId 注解");

            OpenId annotation = parameter.getAnnotation(OpenId.class);
            assertNotNull(annotation, "应该能够获取 @OpenId 注解");
        }

        @Test
        @DisplayName("多重使用: 可以同时标记多个字段")
        void testMultipleUsage_CanBeAppliedToMultipleFields() throws Exception {
            Field userIdField = TestClass.class.getDeclaredField("userId");
            Field orderIdField = TestClass.class.getDeclaredField("orderId");

            assertTrue(userIdField.isAnnotationPresent(OpenId.class),
                "userId 字段应该有 @OpenId 注解");
            assertTrue(orderIdField.isAnnotationPresent(OpenId.class),
                "orderId 字段应该有 @OpenId 注解");
        }

        @Test
        @DisplayName("混合使用: 可以和其他注解组合使用")
        void testMixedUsage_CanBeCombinedWithOtherAnnotations() throws Exception {
            Field field = TestClass.class.getDeclaredField("deprecatedId");
            assertTrue(field.isAnnotationPresent(OpenId.class),
                "字段应该有 @OpenId 注解");
            assertTrue(field.isAnnotationPresent(Deprecated.class),
                "字段应该有 @Deprecated 注解");
        }

        @Test
        @DisplayName("未注解字段: 无注解字段应返回 false")
        void testNonAnnotatedField_ShouldReturnFalse() throws Exception {
            Field field = TestClass.class.getDeclaredField("name");
            assertFalse(field.isAnnotationPresent(OpenId.class),
                "无注解字段不应该有 @OpenId 注解");
        }
    }

    // ==========================================
    // 4. 反射测试
    // ==========================================

    @Nested
    @DisplayName("4. 反射测试")
    class ReflectionTest {

        @Test
        @DisplayName("反射获取: 通过类获取注解")
        void testReflection_GetAnnotationByClass() {
            OpenId annotation = OpenId.class.getAnnotation(OpenId.class);
            assertNull(annotation, "注解本身不应该有自己注解");
        }

        @Test
        @DisplayName("反射获取: 通过字段获取注解")
        void testReflection_GetAnnotationByField() throws Exception {
            Field field = TestClass.class.getDeclaredField("id");
            Annotation[] annotations = field.getAnnotations();

            boolean hasOpenId = false;
            for (Annotation annotation : annotations) {
                if (annotation instanceof OpenId) {
                    hasOpenId = true;
                    break;
                }
            }

            assertTrue(hasOpenId, "字段注解数组应该包含 OpenId 注解");
        }

        @Test
        @DisplayName("反射获取: 通过参数获取注解")
        void testReflection_GetAnnotationByParameter() throws Exception {
            Method method = TestClass.class.getDeclaredMethod("testMethod", Long.class);
            Parameter parameter = method.getParameters()[0];
            Annotation[] annotations = parameter.getAnnotations();

            boolean hasOpenId = false;
            for (Annotation annotation : annotations) {
                if (annotation instanceof OpenId) {
                    hasOpenId = true;
                    break;
                }
            }

            assertTrue(hasOpenId, "参数注解数组应该包含 OpenId 注解");
        }

        @Test
        @DisplayName("反射类型: 注解类型验证")
        void testReflection_AnnotationTypeValidation() throws Exception {
            Field field = TestClass.class.getDeclaredField("id");
            OpenId annotation = field.getAnnotation(OpenId.class);

            assertEquals(OpenId.class, annotation.annotationType(),
                "注解类型应该是 OpenId.class");
        }
    }

    // ==========================================
    // 5. 边界条件测试
    // ==========================================

    @Nested
    @DisplayName("5. 边界条件测试")
    class BoundaryTest {

        @Test
        @DisplayName("边界条件: 静态字段可以注解")
        void testBoundary_StaticFieldCanBeAnnotated() throws Exception {
            Field field = TestClass.class.getDeclaredField("staticId");
            assertTrue(field.isAnnotationPresent(OpenId.class),
                "静态字段应该可以标记 @OpenId 注解");
        }

        @Test
        @DisplayName("边界条件: 私有字段可以注解")
        void testBoundary_PrivateFieldCanBeAnnotated() throws Exception {
            Field field = TestClass.class.getDeclaredField("privateId");
            assertTrue(field.isAnnotationPresent(OpenId.class),
                "私有字段应该可以标记 @OpenId 注解");
        }

        @Test
        @DisplayName("边界条件: Final 字段可以注解")
        void testBoundary_FinalFieldCanBeAnnotated() throws Exception {
            Field field = TestClass.class.getDeclaredField("finalId");
            assertTrue(field.isAnnotationPresent(OpenId.class),
                "Final 字段应该可以标记 @OpenId 注解");
        }

        @Test
        @DisplayName("边界条件: 不支持的方法注解")
        void testBoundary_MethodAnnotationNotSupported() throws Exception {
            Method method = TestClass.class.getDeclaredMethod("annotatedMethod");
            assertFalse(method.isAnnotationPresent(OpenId.class),
                "方法不应该支持 @OpenId 注解（@Target 不包含 METHOD）");
        }

        @Test
        @DisplayName("边界条件: 不支持的类注解")
        void testBoundary_ClassAnnotationNotSupported() {
            assertFalse(TestClass.class.isAnnotationPresent(OpenId.class),
                "类不应该支持 @OpenId 注解（@Target 不包含 TYPE）");
        }
    }

    // ==========================================
    // 6. 性能测试
    // ==========================================

    @Nested
    @DisplayName("6. 性能测试")
    @Tag("Performance")
    class PerformanceTest {

        @Test
        @DisplayName("性能测试: 注解检查性能")
        void testPerformance_AnnotationCheckingPerformance() throws Exception {
            Field field = TestClass.class.getDeclaredField("id");
            int iterations = 100000;

            long startTime = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                field.isAnnotationPresent(OpenId.class);
            }
            long endTime = System.nanoTime();

            long avgDurationNanos = (endTime - startTime) / iterations;
            double avgDurationMicros = avgDurationNanos / 1000.0;

            // 平均每次检查应小于1微秒
            assertTrue(avgDurationMicros < 1.0,
                String.format("注解检查时间 %.2f μs 超过阈值", avgDurationMicros));

            System.out.printf("注解检查性能: %d 次检查平均耗时 %.2f μs%n",
                iterations, avgDurationMicros);
        }

        @Test
        @DisplayName("性能测试: 注解获取性能")
        void testPerformance_AnnotationRetrievalPerformance() throws Exception {
            Field field = TestClass.class.getDeclaredField("id");
            int iterations = 100000;

            long startTime = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                field.getAnnotation(OpenId.class);
            }
            long endTime = System.nanoTime();

            long avgDurationNanos = (endTime - startTime) / iterations;
            double avgDurationMicros = avgDurationNanos / 1000.0;

            // 平均每次获取应小于1微秒
            assertTrue(avgDurationMicros < 1.0,
                String.format("注解获取时间 %.2f μs 超过阈值", avgDurationMicros));

            System.out.printf("注解获取性能: %d 次获取平均耗时 %.2f μs%n",
                iterations, avgDurationMicros);
        }
    }

    // ==========================================
    // 测试辅助类
    // ==========================================

    /**
     * 用于测试的类，包含各种注解组合的字段和方法
     */
    @SuppressWarnings("unused")
    private static class TestClass {

        @OpenId
        private Long id;

        @OpenId
        private Long userId;

        @OpenId
        private Long orderId;

        @OpenId
        @Deprecated
        private String deprecatedId;

        private String name; // 无注解

        @OpenId
        private static Long staticId;

        @OpenId
        private Long privateId;

        @OpenId
        private final Long finalId = 123L;

        // 测试参数注解的方法
        public void testMethod(@OpenId Long parameter) {
            // 测试方法
        }

        // 用于测试不支持方法注解 - 注解在编译时会被忽略
        public void annotatedMethod() {
            // 测试方法
        }
    }
}