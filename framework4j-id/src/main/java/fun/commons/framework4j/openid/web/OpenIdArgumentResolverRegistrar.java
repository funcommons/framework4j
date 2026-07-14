package fun.commons.framework4j.openid.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 {@link OpenIdPathVariableArgumentResolver} 注入到 {@link RequestMappingHandlerAdapter}
 * 的解析器链表头部
 * <p>
 * v2.2：Spring 的 {@code WebMvcConfigurer.addArgumentResolvers()} 只能追加在末尾，
 * 而内置 {@code PathVariableMethodArgumentResolver} 已经匹配 {@code @PathVariable}，
 * 末尾追加的解析器永远不会触发。本 BeanPostProcessor 在 adapter 初始化完成后，
 * 把 OpenId 解析器插入到第 0 位，确保它先于内置 resolver 被命中。
 *
 * @since 2.2.0
 */
@Slf4j
public class OpenIdArgumentResolverRegistrar implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof RequestMappingHandlerAdapter adapter) {
            List<HandlerMethodArgumentResolver> existing = adapter.getArgumentResolvers();
            if (existing == null || existing.isEmpty()) {
                return bean;
            }
            for (HandlerMethodArgumentResolver r : existing) {
                if (r instanceof OpenIdPathVariableArgumentResolver) {
                    return bean;
                }
            }
            List<HandlerMethodArgumentResolver> reordered = new ArrayList<>(existing.size() + 1);
            reordered.add(new OpenIdPathVariableArgumentResolver());
            reordered.addAll(existing);
            adapter.setArgumentResolvers(reordered);
            log.info("【OpenID】OpenIdPathVariableArgumentResolver 已前置注入（共 {} 个解析器）",
                    reordered.size());
        }
        return bean;
    }
}
