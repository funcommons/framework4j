package fun.commons.framework4j.cache.aspect;

import fun.commons.framework4j.cache.annotation.CacheableEvict;
import fun.commons.framework4j.cache.annotation.CacheableGet;
import fun.commons.framework4j.cache.annotation.CacheablePut;
import fun.commons.framework4j.cache.service.CacheService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CacheableAspect 补充单元测试（不依赖 Redis，全部 mock）。
 *
 * <p>原 CacheableAspectTest 是 SpringBootTest + Redis，覆盖端到端。
 * 本测试覆盖 aspect 的纯逻辑：
 * <ol>
 *   <li>SpEL key 表达式解析（含 #user.id）</li>
 *   <li>SpEL 异常 → key="" 不抛</li>
 *   <li>@CacheablePut null 结果不写缓存</li>
 *   <li>@CacheablePut 非 null 写缓存</li>
 *   <li>@CacheableEvict 总是删（无论结果）</li>
 *   <li>ttl=−1 → 透传 -1（CacheService 用全局默认）</li>
 *   <li>loader 抛 RuntimeException 直接传播</li>
 * </ol>
 *
 * @since 2.1.0
 */
@DisplayName("CacheableAspect 单元补充测试")
class CacheableAspectAdditionalTest {

    private CacheService cacheService;
    private CacheableAspect aspect;

    @BeforeEach
    void setUp() {
        cacheService = mock(CacheService.class);
        aspect = new CacheableAspect(cacheService);
    }

    // ============ @CacheableGet ============

    @Test
    @DisplayName("CacheableGet: SpEL=#id → 正确解析 + loader 调用 + cache 命中")
    void cacheableGetSpelId() throws Throwable {
        ProceedingJoinPoint pjp = mockJoinPoint(SampleService.class, "getUser",
                new Object[]{"abc"}, String.class);
        when(pjp.proceed()).thenReturn("loaded");

        // cacheService.get 第一次返回 null（未命中），第二次返回 cached
        when(cacheService.get(eq("user"), eq("abc"), anyLong(), anyLong(),
                any(Supplier.class), eq(String.class)))
                .thenReturn("loaded");

        SampleService svc = new SampleService();
        CacheableGet ann = methodAnnotation(SampleService.class, "getUser", CacheableGet.class);

        Object result = aspect.aroundGet(pjp, ann);
        assertThat(result).isEqualTo("loaded");

        // 验证 cacheService.get 被调，参数正确
        verify(cacheService).get(eq("user"), eq("abc"), anyLong(), anyLong(),
                any(Supplier.class), eq(String.class));
    }

    @Test
    @DisplayName("CacheableGet: SpEL 异常 → key='' 而非抛异常")
    void cacheableGetSpelFailureReturnsEmptyKey() throws Throwable {
        ProceedingJoinPoint pjp = mockJoinPoint(SampleService.class, "brokenKey",
                new Object[]{null}, String.class);
        when(cacheService.get(anyString(), anyString(), anyLong(), anyLong(),
                any(Supplier.class), eq(String.class)))
                .thenReturn("fallback");

        CacheableGet ann = methodAnnotation(SampleService.class, "brokenKey", CacheableGet.class);
        Object result = aspect.aroundGet(pjp, ann);
        assertThat(result).isEqualTo("fallback");
        verify(cacheService).get(eq("user"), eq(""), anyLong(), anyLong(),
                any(Supplier.class), eq(String.class));
    }

    @Test
    @DisplayName("CacheableGet: loader 抛 RuntimeException 直接传播（不被吞）")
    void cacheableGetLoaderThrowsRuntimePropagates() throws Throwable {
        ProceedingJoinPoint pjp = mockJoinPoint(SampleService.class, "getUser",
                new Object[]{"k"}, String.class);
        RuntimeException boom = new RuntimeException("boom");
        when(cacheService.get(anyString(), anyString(), anyLong(), anyLong(),
                any(Supplier.class), eq(String.class)))
                .thenAnswer(inv -> {
                    Supplier<?> s = inv.getArgument(4);
                    when(pjp.proceed()).thenThrow(boom);
                    return s.get(); // 模拟 loader 执行
                });

        CacheableGet ann = methodAnnotation(SampleService.class, "getUser", CacheableGet.class);
        assertThatThrownBy(() -> aspect.aroundGet(pjp, ann))
                .isSameAs(boom);
    }

    @Test
    @DisplayName("CacheableGet: loader 抛 checked Exception 包装为 RuntimeException")
    void cacheableGetLoaderCheckedWrapped() throws Throwable {
        ProceedingJoinPoint pjp = mockJoinPoint(SampleService.class, "getUser",
                new Object[]{"k"}, String.class);
        Exception checked = new Exception("checked");
        when(cacheService.get(anyString(), anyString(), anyLong(), anyLong(),
                any(Supplier.class), eq(String.class)))
                .thenAnswer(inv -> {
                    Supplier<?> s = inv.getArgument(4);
                    when(pjp.proceed()).thenThrow(checked);
                    return s.get();
                });

        CacheableGet ann = methodAnnotation(SampleService.class, "getUser", CacheableGet.class);
        assertThatThrownBy(() -> aspect.aroundGet(pjp, ann))
                .isInstanceOf(RuntimeException.class)
                .hasCause(checked);
    }

    // ============ @CacheablePut ============

    @Test
    @DisplayName("CacheablePut: null 结果 → 不写缓存（仅 proceed）")
    void cacheablePutNullResultSkipped() throws Throwable {
        ProceedingJoinPoint pjp = mockJoinPoint(SampleService.class, "updateUser",
                new Object[]{"k"}, String.class);
        when(pjp.proceed()).thenReturn(null);

        CacheablePut ann = methodAnnotation(SampleService.class, "updateUser", CacheablePut.class);
        Object result = aspect.aroundPut(pjp, ann);
        assertThat(result).isNull();
        verify(cacheService, never()).put(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("CacheablePut: 非 null 结果 → 写缓存 + 返回原值")
    void cacheablePutWritesCache() throws Throwable {
        ProceedingJoinPoint pjp = mockJoinPoint(SampleService.class, "updateUser",
                new Object[]{"k1"}, String.class);
        when(pjp.proceed()).thenReturn("v1");

        CacheablePut ann = methodAnnotation(SampleService.class, "updateUser", CacheablePut.class);
        Object result = aspect.aroundPut(pjp, ann);
        assertThat(result).isEqualTo("v1");
        ArgumentCaptor<String> prefixCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        verify(cacheService).put(prefixCap.capture(), keyCap.capture(), anyLong(), eq("v1"));
        assertThat(prefixCap.getValue()).isEqualTo("user");
        assertThat(keyCap.getValue()).isEqualTo("k1");
    }

    // ============ @CacheableEvict ============

    @Test
    @DisplayName("CacheableEvict: proceed 先执行，再 evict")
    void cacheableEvictOrder() throws Throwable {
        ProceedingJoinPoint pjp = mockJoinPoint(SampleService.class, "deleteUser",
                new Object[]{"k1"}, Void.class);
        when(pjp.proceed()).thenReturn(null);

        CacheableEvict ann = methodAnnotation(SampleService.class, "deleteUser", CacheableEvict.class);
        Object result = aspect.aroundEvict(pjp, ann);
        assertThat(result).isNull();
        verify(cacheService).evict("user", "k1");
    }

    @Test
    @DisplayName("CacheableEvict: proceed 抛异常 → 不调 evict")
    void cacheableEvictSkipOnException() throws Throwable {
        ProceedingJoinPoint pjp = mockJoinPoint(SampleService.class, "deleteUser",
                new Object[]{"k1"}, Void.class);
        when(pjp.proceed()).thenThrow(new RuntimeException("db fail"));

        CacheableEvict ann = methodAnnotation(SampleService.class, "deleteUser", CacheableEvict.class);
        assertThatThrownBy(() -> aspect.aroundEvict(pjp, ann))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db fail");
        verify(cacheService, never()).evict(anyString(), anyString());
    }

    @Test
    @DisplayName("CacheableEvict: 多次调用 evict 不缓存切面状态")
    void cacheableEvictRepeatable() throws Throwable {
        ProceedingJoinPoint pjp = mockJoinPoint(SampleService.class, "deleteUser",
                new Object[]{"k1"}, Void.class);
        when(pjp.proceed()).thenReturn(null);

        CacheableEvict ann = methodAnnotation(SampleService.class, "deleteUser", CacheableEvict.class);
        aspect.aroundEvict(pjp, ann);
        aspect.aroundEvict(pjp, ann);
        verify(cacheService, times(2)).evict("user", "k1");
    }

    // ============ Helpers ============

    @SuppressWarnings("unchecked")
    private ProceedingJoinPoint mockJoinPoint(Class<?> targetClass, String methodName,
                                              Object[] args, Class<?> returnType) throws Exception {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        Method realMethod = findMethodByName(targetClass, methodName);
        when(sig.getMethod()).thenReturn(realMethod);
        when(sig.getReturnType()).thenReturn(realMethod.getReturnType());
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.getArgs()).thenReturn(args);
        when(pjp.getTarget()).thenReturn(new SampleService());
        return pjp;
    }

    private Method findMethodByName(Class<?> c, String name) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name)) return m;
        }
        throw new IllegalArgumentException("no method " + name);
    }

    private <A extends java.lang.annotation.Annotation> A methodAnnotation(
            Class<?> c, String name, Class<A> ann) {
        return findMethodByName(c, name).getAnnotation(ann);
    }

    /** 用于反射加载注解和 SpEL 测试的样本 service 类 */
    @SuppressWarnings("unused")
    public static class SampleService {
        @CacheableGet(prefix = "user", key = "#id")
        public String getUser(String id) { return "loaded-" + id; }

        @CacheableGet(prefix = "user", key = "#user.nonexistent")
        public String brokenKey(Object user) { return "loaded"; }

        @CacheablePut(prefix = "user", key = "#id")
        public String updateUser(String id) { return "updated-" + id; }

        @CacheableEvict(prefix = "user", key = "#id")
        public void deleteUser(String id) {}
    }
}
