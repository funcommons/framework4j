package fun.commons.framework4j.sensitive.context;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Spring 容器静态访问器（TypeHandler 场景专用）。
 * <p>
 * 适用场景：非 Spring 管理的对象（如 MyBatis 反射实例化的 {@code BaseTypeHandler}）
 * 需运行时取 Spring Bean —— 这类对象无法构造注入，只能静态访问容器。
 * <p>
 * <b>不是通用 ApplicationContext 替代</b>：能构造注入的场景应优先注入，本类仅服务
 * TypeHandler 等「MyBatis 反射实例化」的例外。aware 回调在 Bean 初始化阶段，
 * 早于 Mapper 解析（Mapper Bean 实例化时才解析注解），故静态引用在 TypeHandler
 * 无参构造调用时已就绪。
 *
 * @since 1.2.9
 */
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    /**
     * 按 name + type 取 Bean。须在容器就绪后调用（TypeHandler 运行时 set/get 必就绪）。
     */
    public static <T> T getBean(String name, Class<T> type) {
        if (context == null) {
            throw new IllegalStateException("Spring 容器未就绪 (SpringContextHolder.context == null)");
        }
        return context.getBean(name, type);
    }
}
