package fun.commons.framework4j.id;

import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import fun.commons.framework4j.id.util.IdObfuscator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * ID 混淆工具测试
 */
@DisplayName("IdObfuscator 测试")
class IdObfuscatorTest {

    @Nested
    @DisplayName("toOpenId 测试")
    class ToOpenIdTests {

        @Test
        @DisplayName("正常 ID 转换")
        void shouldConvertNormalIds() {
            long id = 123456789L;
            String openId = IdObfuscator.toOpenId(id);

            assertThat(openId).isNotEmpty();
            assertThat(openId.length()).isGreaterThan(0);
            System.out.println("ID: " + id + " -> OpenID: " + openId);
        }

        @Test
        @DisplayName("零值 ID 转换")
        void shouldConvertZeroId() {
            String openId = IdObfuscator.toOpenId(0);
            assertThat(openId).isNotEmpty();
        }

        @Test
        @DisplayName("雪花 ID 转换")
        void shouldConvertSnowflakeId() {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);
            long snowflakeId = distributor.nextId();

            String openId = IdObfuscator.toOpenId(snowflakeId);

            assertThat(openId).isNotEmpty();
            // 雪花 ID 很大，转换后的 OpenID 应该有一定长度
            assertThat(openId.length()).isGreaterThanOrEqualTo(5);
            System.out.println("SnowflakeID: " + snowflakeId + " -> OpenID: " + openId);
        }

        @Test
        @DisplayName("带前缀的 OpenID 生成")
        void shouldGenerateOpenIdWithPrefix() {
            long id = 123456789L;

            String orderOpenId = IdObfuscator.toOpenId(id, "ORD");
            String userOpenId = IdObfuscator.toOpenId(id, "USER");

            assertThat(orderOpenId).startsWith("ORD_");
            assertThat(userOpenId).startsWith("USER_");
        }

        @Test
        @DisplayName("不同 ID 生成不同 OpenID")
        void shouldGenerateUniqueOpenIds() {
            Set<String> openIds = new HashSet<>();
            for (long i = 1; i <= 10000; i++) {
                openIds.add(IdObfuscator.toOpenId(i));
            }
            assertThat(openIds).hasSize(10000);
        }
    }

    @Nested
    @DisplayName("fromOpenId 测试")
    class FromOpenIdTests {

        @Test
        @DisplayName("正常 OpenID 还原")
        void shouldRestoreOriginalId() {
            long originalId = 123456789L;
            String openId = IdObfuscator.toOpenId(originalId);
            long restoredId = IdObfuscator.fromOpenId(openId);

            assertThat(restoredId).isEqualTo(originalId);
        }

        @Test
        @DisplayName("雪花 ID 双向转换")
        void shouldRestoreSnowflakeId() {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);

            for (int i = 0; i < 1000; i++) {
                long originalId = distributor.nextId();
                String openId = IdObfuscator.toOpenId(originalId);
                long restoredId = IdObfuscator.fromOpenId(openId);

                assertThat(restoredId).isEqualTo(originalId);
            }
        }

        @Test
        @DisplayName("带前缀的 OpenID 还原")
        void shouldRestoreFromPrefixedOpenId() {
            long originalId = 987654321L;

            String orderOpenId = IdObfuscator.toOpenId(originalId, "ORD");
            String userOpenId = IdObfuscator.toOpenId(originalId, "USER");
            String complexOpenId = IdObfuscator.toOpenId(originalId, "COMPLEX_PREFIX");

            assertThat(IdObfuscator.fromOpenId(orderOpenId)).isEqualTo(originalId);
            assertThat(IdObfuscator.fromOpenId(userOpenId)).isEqualTo(originalId);
            assertThat(IdObfuscator.fromOpenId(complexOpenId)).isEqualTo(originalId);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("空值输入应抛出异常")
        void shouldRejectNullOrEmptyInput(String input) {
            assertThatThrownBy(() -> IdObfuscator.fromOpenId(input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("OpenID cannot be null or empty");
        }

        @Test
        @DisplayName("无效字符应抛出异常")
        void shouldRejectInvalidCharacters() {
            assertThatThrownBy(() -> IdObfuscator.fromOpenId("INVALID!@#$%"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("isValid 测试")
    class IsValidTests {

        @Test
        @DisplayName("有效 OpenID 验证")
        void shouldValidateValidOpenIds() {
            long id = 123456789L;
            String openId = IdObfuscator.toOpenId(id);

            assertThat(IdObfuscator.isValid(openId)).isTrue();
        }

        @Test
        @DisplayName("带前缀的有效 OpenID 验证")
        void shouldValidatePrefixedOpenIds() {
            long id = 123456789L;
            String prefixedOpenId = IdObfuscator.toOpenId(id, "ORD");

            assertThat(IdObfuscator.isValid(prefixedOpenId)).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("空值返回 false")
        void shouldReturnFalseForNullOrEmpty(String input) {
            assertThat(IdObfuscator.isValid(input)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"INVALID!@#", "test$%^", "hello world", "123!456"})
        @DisplayName("无效字符返回 false")
        void shouldReturnFalseForInvalidCharacters(String input) {
            assertThat(IdObfuscator.isValid(input)).isFalse();
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryTests {

        @ParameterizedTest
        @ValueSource(longs = {0, 1, Long.MAX_VALUE, Long.MAX_VALUE / 2})
        @DisplayName("边界值双向转换")
        void shouldHandleBoundaryValues(long id) {
            String openId = IdObfuscator.toOpenId(id);
            long restored = IdObfuscator.fromOpenId(openId);
            System.out.println("ID "+  id +" -> " + openId);

            assertThat(restored).isEqualTo(id);
        }

        @Test
        @DisplayName("连续 ID 转换")
        void shouldHandleSequentialIds() {
            for (long i = 0; i < 100; i++) {
                String openId = IdObfuscator.toOpenId(i);
                long restored = IdObfuscator.fromOpenId(openId);
                assertThat(restored).isEqualTo(i);
            }
        }
    }

    @Nested
    @DisplayName("安全性测试")
    class SecurityTests {

        @Test
        @DisplayName("OpenID 不应暴露 ID 顺序")
        void shouldNotExposeIdOrder() {
            String openId1 = IdObfuscator.toOpenId(1L);
            String openId2 = IdObfuscator.toOpenId(2L);
            String openId3 = IdObfuscator.toOpenId(3L);

            // 连续的 ID 生成的 OpenID 不应该有明显的顺序关系
            // (这是通过字符集乱序和 XOR 实现的)
            System.out.println("ID 1 -> " + openId1);
            System.out.println("ID 2 -> " + openId2);
            System.out.println("ID 3 -> " + openId3);
        }

        @Test
        @DisplayName("OpenID 长度适中")
        void shouldGenerateReasonableLengthOpenIds() {
            // 雪花 ID 通常是 19 位数字，转换为 Base62 应该更短
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);
            long snowflakeId = distributor.nextId();

            String openId = IdObfuscator.toOpenId(snowflakeId);

            // Base62 编码后应该比原始数字短
            assertThat(openId.length()).isLessThan(String.valueOf(snowflakeId).length());
            System.out.println("SnowflakeID length: " + String.valueOf(snowflakeId).length()
                    + ", OpenID length: " + openId.length());
        }
    }

    @Nested
    @DisplayName("一致性测试")
    class ConsistencyTests {

        @Test
        @DisplayName("相同 ID 多次转换结果一致")
        void shouldGenerateConsistentOpenIds() {
            long id = 123456789L;

            String openId1 = IdObfuscator.toOpenId(id);
            String openId2 = IdObfuscator.toOpenId(id);
            String openId3 = IdObfuscator.toOpenId(id);

            assertThat(openId1).isEqualTo(openId2).isEqualTo(openId3);
        }

        @Test
        @DisplayName("多次还原结果一致")
        void shouldRestoreConsistently() {
            String openId = IdObfuscator.toOpenId(123456789L);

            long restored1 = IdObfuscator.fromOpenId(openId);
            long restored2 = IdObfuscator.fromOpenId(openId);
            long restored3 = IdObfuscator.fromOpenId(openId);

            assertThat(restored1).isEqualTo(restored2).isEqualTo(restored3);
        }
    }
}
