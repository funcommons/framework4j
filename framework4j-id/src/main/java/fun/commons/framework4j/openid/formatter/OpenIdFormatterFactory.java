package fun.commons.framework4j.openid.formatter;

import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.openid.util.OpenIdTypeSupport;
import fun.commons.framework4j.openid.util.OpenIdLongCodec;
import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Parser;
import org.springframework.format.Printer;

import java.text.ParseException;
import java.util.Set;

/**
 * OpenID 入参转换工厂(Spring MVC @RequestParam/@PathVariable 标量入参)。
 * <p>
 * 受理的标量类型由 {@link OpenIdTypeSupport} 决定(默认 {@code Long/long};
 * {@code support-integer} 开则含 {@code Integer/int};{@code support-string} 开则含 {@code String})。
 * List/数组入参由 Spring 拆分后逐元素走本工厂的标量 parser。
 * <p>
 * v1.3:经 {@link OpenIdLongCodec} 以 Long 为枢轴,并接入 {@code accept-numeric-fallback} 开关
 * (关掉则 path/query 入参也拒绝纯数字、只吃合法混淆串)。v1.3 起 legacy {@code OpenIdTypeUtils} 已退役删除。
 */
public class OpenIdFormatterFactory implements AnnotationFormatterFactory<OpenId> {

    private final OpenIdTypeSupport typeSupport;

    public OpenIdFormatterFactory(OpenIdTypeSupport typeSupport) {
        this.typeSupport = typeSupport;
    }

    @Override
    public Set<Class<?>> getFieldTypes() {
        return typeSupport.scalarTypes();
    }

    @Override
    public Printer<?> getPrinter(OpenId annotation, Class<?> fieldType) {
        return (object, locale) -> object == null ? null : OpenIdLongCodec.encodeToOpenId(object);
    }

    @Override
    public Parser<?> getParser(OpenId annotation, Class<?> fieldType) {
        return (text, locale) -> {
            if (text == null || text.isEmpty()) {
                return null;
            }
            Long pivoted;
            try {
                pivoted = OpenIdLongCodec.decodeTextToLong(text, typeSupport.isAcceptNumericFallback());
            } catch (IllegalArgumentException e) {
                throw new ParseException("Invalid OpenID format: " + text + " (" + e.getMessage() + ")", 0);
            }
            if (pivoted == null) {
                return null;
            }
            try {
                return OpenIdLongCodec.convertLongToTarget(pivoted, fieldType);
            } catch (ArithmeticException | IllegalArgumentException e) {
                throw new ParseException("OpenID 转换失败: " + e.getMessage(), 0);
            }
        };
    }
}
