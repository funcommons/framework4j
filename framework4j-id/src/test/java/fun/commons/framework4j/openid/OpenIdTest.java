package fun.commons.framework4j.openid;

import fun.commons.framework4j.openid.annotation.OpenId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;

// 测试用类定义
class TestClass {
    @OpenId
    private String userId;

    void testMethod(@OpenId String userId) {}
}

class UserEntity {
    @OpenId
    private String id;

    @OpenId
    private String orderId;

    private String name; // 非ID字段
}

class TestController {
    public void getUserInfo(@OpenId String userId) {}
    public void getOrderDetail(@OpenId String orderId) {}
}

/**
 * OpenId注解测试
 */
@DisplayName("OpenId注解测试")
class OpenIdTest {

    @Test
    @DisplayName("注解定义验证")
    void shouldHaveCorrectAnnotationDefinition() {
        // 验证注解存在
        Class<OpenId> openIdClass = OpenId.class;
        assertThat(openIdClass).isNotNull();

        // 验证注解目标
        Target targetAnnotation = openIdClass.getAnnotation(Target.class);
        assertThat(targetAnnotation).isNotNull();
        ElementType[] targets = targetAnnotation.value();
        assertThat(targets).containsExactlyInAnyOrder(
            ElementType.PARAMETER,
            ElementType.FIELD
        );

        // 验证注解保留策略
        Retention retentionAnnotation = openIdClass.getAnnotation(Retention.class);
        assertThat(retentionAnnotation).isNotNull();
        assertThat(retentionAnnotation.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }

    @Test
    @DisplayName("注解可应用于字段")
    void shouldBeApplicableToField() throws NoSuchFieldException {
        java.lang.reflect.Field field = TestClass.class.getDeclaredField("userId");
        OpenId annotation = field.getAnnotation(OpenId.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    @DisplayName("注解可应用于参数")
    void shouldBeApplicableToParameter() throws NoSuchMethodException {
        java.lang.reflect.Parameter parameter = TestClass.class
            .getDeclaredMethod("testMethod", String.class)
            .getParameters()[0];

        OpenId annotation = parameter.getAnnotation(OpenId.class);
        assertThat(annotation).isNotNull();
    }

    @Nested
    @DisplayName("注解应用场景测试")
    class AnnotationUsageScenariosTest {

        @Test
        @DisplayName("实体类字段使用场景")
        void shouldWorkInEntityFieldScenario() {
            java.lang.reflect.Field[] fields = UserEntity.class.getDeclaredFields();

            boolean idFieldAnnotated = false;
            boolean orderIdFieldAnnotated = false;
            boolean nameFieldNotAnnotated = true;

            for (java.lang.reflect.Field field : fields) {
                if ("id".equals(field.getName())) {
                    idFieldAnnotated = field.isAnnotationPresent(OpenId.class);
                } else if ("orderId".equals(field.getName())) {
                    orderIdFieldAnnotated = field.isAnnotationPresent(OpenId.class);
                } else if ("name".equals(field.getName())) {
                    nameFieldNotAnnotated = !field.isAnnotationPresent(OpenId.class);
                }
            }

            assertThat(idFieldAnnotated).isTrue();
            assertThat(orderIdFieldAnnotated).isTrue();
            assertThat(nameFieldNotAnnotated).isTrue();
        }

        @Test
        @DisplayName("控制器参数使用场景")
        void shouldWorkInControllerParameterScenario() throws NoSuchMethodException {
            class TempController {
                void getUser(@OpenId String userId) {}

                void getOrder(@OpenId String orderId, String param) {}
            }

            // 测试单个参数
            java.lang.reflect.Parameter userIdParam = TempController.class
                .getDeclaredMethod("getUser", String.class)
                .getParameters()[0];

            assertThat(userIdParam.isAnnotationPresent(OpenId.class)).isTrue();

            // 测试混合参数
            java.lang.reflect.Parameter[] orderParams = TempController.class
                .getDeclaredMethod("getOrder", String.class, String.class)
                .getParameters();

            assertThat(orderParams[0].isAnnotationPresent(OpenId.class)).isTrue();
            assertThat(orderParams[1].isAnnotationPresent(OpenId.class)).isFalse();
        }
    }
}