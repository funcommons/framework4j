package fun.commons.framework4j.openid.util;

import com.fasterxml.jackson.databind.JavaType;
import org.springframework.core.env.Environment;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@code @OpenId} 受理类型的运行时配置(v1.3 三开关)。
 * <p>
 * 三个全局开关(扁平属性,贴 {@code framework4j.openid.*} 既有风格):
 * <ul>
 *   <li>{@code framework4j.openid.support-integer}(默认 <b>false</b>):打开则 {@code @OpenId} 受理
 *       {@code Integer/int}(含集合/数组),三通道补齐</li>
 *   <li>{@code framework4j.openid.support-string}(默认 <b>false</b>):打开则 {@code @OpenId} 受理
 *       {@code String}(含 {@code List<String>} 等),以 Long 为枢轴双向转</li>
 *   <li>{@code framework4j.openid.accept-numeric-fallback}(默认 <b>true</b>):关掉则所有 {@code @OpenId}
 *       字段拒绝纯数字、只吃合法混淆串(迁移后强制收口,反枚举)</li>
 * </ul>
 * {@code Long/long} 恒受理。{@code Integer/Map} 等在开关关闭时不受理(由 fail-fast 检出误用)。
 * <p>
 * 对外是三个布尔属性(消费方直觉),对内折叠成一个"支持的标量类型集合" + 一个 fallback 标志,
 * 供 Jackson modifier / Formatter / PathVariable resolver 三层共用,避免散落的布尔谓词。
 *
 * @since 1.3.0
 */
public final class OpenIdTypeSupport {

    /** 属性 key(集中声明,供 wiring 与 fail-fast 提示复用) */
    public static final String PROP_SUPPORT_INTEGER = "framework4j.openid.support-integer";
    public static final String PROP_SUPPORT_STRING = "framework4j.openid.support-string";
    public static final String PROP_ACCEPT_NUMERIC_FALLBACK = "framework4j.openid.accept-numeric-fallback";

    private final Set<Class<?>> scalarTypes;
    private final boolean supportInteger;
    private final boolean supportString;
    private final boolean acceptNumericFallback;

    private OpenIdTypeSupport(Set<Class<?>> scalarTypes, boolean supportInteger,
                              boolean supportString, boolean acceptNumericFallback) {
        this.scalarTypes = scalarTypes;
        this.supportInteger = supportInteger;
        this.supportString = supportString;
        this.acceptNumericFallback = acceptNumericFallback;
    }

    /**
     * 从 {@link Environment} 读三个开关构造。各消费方(Jackson BPP / Formatter / Resolver)分别调用,
     * 纯读取、无 bean 顺序依赖。
     */
    public static OpenIdTypeSupport from(Environment env) {
        boolean supportInteger = env.getProperty(PROP_SUPPORT_INTEGER, Boolean.class, Boolean.FALSE);
        boolean supportString = env.getProperty(PROP_SUPPORT_STRING, Boolean.class, Boolean.FALSE);
        boolean acceptNumericFallback = env.getProperty(PROP_ACCEPT_NUMERIC_FALLBACK, Boolean.class, Boolean.TRUE);

        Set<Class<?>> scalars = new LinkedHashSet<>();
        scalars.add(Long.class);
        scalars.add(long.class);
        if (supportInteger) {
            scalars.add(Integer.class);
            scalars.add(int.class);
        }
        if (supportString) {
            scalars.add(String.class);
        }
        return new OpenIdTypeSupport(Collections.unmodifiableSet(scalars),
                supportInteger, supportString, acceptNumericFallback);
    }

    /** 默认配置(Long only + 接受数字回退),供不关心开关的测试/离线场景 */
    public static OpenIdTypeSupport defaults() {
        return new OpenIdTypeSupport(
                Set.of(Long.class, long.class), false, false, true);
    }

    /** 全开(Long + Integer + String + 接受数字回退),供测试覆盖所有路径 */
    public static OpenIdTypeSupport allEnabled() {
        Set<Class<?>> scalars = new LinkedHashSet<>();
        scalars.add(Long.class);
        scalars.add(long.class);
        scalars.add(Integer.class);
        scalars.add(int.class);
        scalars.add(String.class);
        return new OpenIdTypeSupport(Collections.unmodifiableSet(scalars), true, true, true);
    }

    /** 测试用构建器:按需组合开关。 */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean integer;
        private boolean string;
        private boolean fallback = true;

        public Builder integer(boolean enabled) {
            this.integer = enabled;
            return this;
        }

        public Builder string(boolean enabled) {
            this.string = enabled;
            return this;
        }

        public Builder fallback(boolean enabled) {
            this.fallback = enabled;
            return this;
        }

        public OpenIdTypeSupport build() {
            Set<Class<?>> scalars = new LinkedHashSet<>();
            scalars.add(Long.class);
            scalars.add(long.class);
            if (integer) {
                scalars.add(Integer.class);
                scalars.add(int.class);
            }
            if (string) {
                scalars.add(String.class);
            }
            return new OpenIdTypeSupport(Collections.unmodifiableSet(scalars), integer, string, fallback);
        }
    }

    public boolean supportsScalar(Class<?> raw) {
        return scalarTypes.contains(raw);
    }

    public boolean isSupportInteger() {
        return supportInteger;
    }

    public boolean isSupportString() {
        return supportString;
    }

    public boolean isAcceptNumericFallback() {
        return acceptNumericFallback;
    }

    /** Formatter 注册用:声明受理的标量类型集合 */
    public Set<Class<?>> scalarTypes() {
        return scalarTypes;
    }

    /**
     * Jackson 侧:给定属性的 {@link JavaType},若它是受支持标量、或"受支持标量的集合/数组",
     * 返回该标量元素类型(标量本身即返回自身);否则返回 {@code null}(不受理)。
     * <p>Map 不是 collection-like({@link JavaType#isCollectionLikeType()} 对 Map 返回 false),天然排除。
     */
    public Class<?> supportedScalarOf(JavaType type) {
        Class<?> raw = type.getRawClass();
        if (supportsScalar(raw)) {
            return raw;
        }
        if (type.isCollectionLikeType() || type.isArrayType()) {
            JavaType contentType = type.getContentType();
            if (contentType != null && supportsScalar(contentType.getRawClass())) {
                return contentType.getRawClass();
            }
        }
        return null;
    }
}
