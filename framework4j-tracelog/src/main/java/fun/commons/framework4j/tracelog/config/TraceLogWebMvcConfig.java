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
                // 提权标记注入器: 必须覆盖业务请求路径 (URL/USER/ORDER/TRACE 维度开关
                // 提权的对象就是业务请求)。排除 tracelog 自身 API 与控制台静态页。
                .addPathPatterns("/**")
                .excludePathPatterns(props.getApi().getQueryPathPatterns())
                .excludePathPatterns(props.getApi().getSwitchPathPatterns())
                .excludePathPatterns(props.getApi().getExportPathPatterns())
                .excludePathPatterns("/tracelog", "/tracelog/**", "/tracelog.html",
                        "/actuator/**", "/error", "/favicon.ico");
    }
}