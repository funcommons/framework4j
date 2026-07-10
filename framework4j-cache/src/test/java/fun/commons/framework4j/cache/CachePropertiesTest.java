package fun.commons.framework4j.cache;

import fun.commons.framework4j.cache.config.CacheProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * CacheProperties 配置测试
 */
@DisplayName("CacheProperties 配置测试")
class CachePropertiesTest {

    @Test
    @DisplayName("默认值正确")
    void defaultValues() {
        CacheProperties p = new CacheProperties();
        assertThat(p.isEnabled()).isTrue();
        assertThat(p.getRedisName()).isEqualTo("default");
        assertThat(p.getDefaultTtlSeconds()).isEqualTo(3600);
        assertThat(p.getNullTtlSeconds()).isEqualTo(30);
        assertThat(p.getKeyPrefix()).isEqualTo("cache");
        assertThat(p.getL1().isEnabled()).isTrue();
        assertThat(p.getL1().getMaxSize()).isEqualTo(10000);
        assertThat(p.getL1().getExpireAfterWrite()).isEqualTo(600);
        assertThat(p.getSingleFlight().isEnabled()).isTrue();
        assertThat(p.getSingleFlight().getLockTtlSeconds()).isEqualTo(3);
        assertThat(p.getSingleFlight().getWaitMillis()).isEqualTo(200);
        assertThat(p.getSingleFlight().getMaxRetry()).isEqualTo(10);
    }

    @Test
    @DisplayName("L1Config 可修改")
    void l1ConfigMutable() {
        CacheProperties p = new CacheProperties();
        p.getL1().setEnabled(false);
        p.getL1().setMaxSize(5000);
        p.getL1().setExpireAfterWrite(120);
        assertThat(p.getL1().isEnabled()).isFalse();
        assertThat(p.getL1().getMaxSize()).isEqualTo(5000);
        assertThat(p.getL1().getExpireAfterWrite()).isEqualTo(120);
    }

    @Test
    @DisplayName("SingleFlightConfig 可修改")
    void singleFlightConfigMutable() {
        CacheProperties p = new CacheProperties();
        p.getSingleFlight().setEnabled(false);
        p.getSingleFlight().setLockTtlSeconds(10);
        p.getSingleFlight().setWaitMillis(100);
        p.getSingleFlight().setMaxRetry(5);
        assertThat(p.getSingleFlight().isEnabled()).isFalse();
        assertThat(p.getSingleFlight().getLockTtlSeconds()).isEqualTo(10);
        assertThat(p.getSingleFlight().getWaitMillis()).isEqualTo(100);
        assertThat(p.getSingleFlight().getMaxRetry()).isEqualTo(5);
    }

    @Test
    @DisplayName("顶层属性可修改")
    void topLevelMutable() {
        CacheProperties p = new CacheProperties();
        p.setEnabled(false);
        p.setRedisName("custom-redis");
        p.setDefaultTtlSeconds(1800);
        p.setNullTtlSeconds(60);
        p.setKeyPrefix("my-cache");
        assertThat(p.isEnabled()).isFalse();
        assertThat(p.getRedisName()).isEqualTo("custom-redis");
        assertThat(p.getDefaultTtlSeconds()).isEqualTo(1800);
        assertThat(p.getNullTtlSeconds()).isEqualTo(60);
        assertThat(p.getKeyPrefix()).isEqualTo("my-cache");
    }
}
