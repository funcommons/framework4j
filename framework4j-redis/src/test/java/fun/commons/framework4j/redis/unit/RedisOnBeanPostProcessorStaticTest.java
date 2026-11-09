package fun.commons.framework4j.redis.unit;

import fun.commons.framework4j.redis.config.MultiRedisAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #32 回归（v1.10.1）：redisOnBeanPostProcessor 非静态 @Bean——BPPChecker
 * 「not eligible for auto-proxying」WARN。#29-3 同族修复（datasource 侧已修，redis 侧遗漏）。
 */
@DisplayName("issue #32：redis BPP @Bean 方法静态化（#29-3 同族）")
class RedisOnBeanPostProcessorStaticTest {

    @Test
    @DisplayName("redisOnBeanPostProcessor 为 static @Bean")
    void beanPostProcessorBeanMethodIsStatic() throws Exception {
        assertThat(Modifier.isStatic(MultiRedisAutoConfiguration.class
                .getDeclaredMethod("redisOnBeanPostProcessor",
                        fun.commons.framework4j.redis.manager.MultiRedisManager.class,
                        org.springframework.core.env.Environment.class)
                .getModifiers())).isTrue();
    }
}
