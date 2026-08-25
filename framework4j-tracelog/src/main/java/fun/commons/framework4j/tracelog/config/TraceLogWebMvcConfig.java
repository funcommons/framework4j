package fun.commons.framework4j.tracelog.config;

import fun.commons.framework4j.tracelog.switcher.SwitchRuleCache;
import fun.commons.framework4j.tracelog.switcher.TraceLogSwitchInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 集成：注册 {@link TraceLogSwitchInterceptor}。
 * <p>
 * v1.2.5+ 注册模式（修复孤儿类问题）：{@code TraceLogAutoConfiguration} 通过 {@code @Import} 引入本类。
 * 消费方不要自行注册。
 *
 * @see fun.commons.framework4j.accesstoken.AccessTokenAutoConfiguration 类似模式
 */
@Configuration
@RequiredArgsConstructor
public class TraceLogWebMvcConfig implements WebMvcConfigurer {

    private final TraceLogProperties props;
    private final SwitchRuleCache switchRuleCache;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        TraceLogSwitchInterceptor interceptor = new TraceLogSwitchInterceptor(props, switchRuleCache);
        registry.addInterceptor(interceptor)
                // 仅在查询 / 开关 / 导出路径上生效，避免对静态资源、actuator 干扰
                .addPathPatterns(props.getApi().getQueryPathPatterns())
                .addPathPatterns(props.getApi().getSwitchPathPatterns())
                .addPathPatterns(props.getApi().getExportPathPatterns());
    }
}