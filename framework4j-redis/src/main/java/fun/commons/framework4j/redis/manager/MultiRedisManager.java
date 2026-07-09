package fun.commons.framework4j.redis.manager;

import fun.commons.framework4j.redis.enums.TemplateType;
import fun.commons.framework4j.redis.exception.RedisDataSourceException;
import fun.commons.framework4j.redis.properties.RedisDataSourceProperties;
import fun.commons.framework4j.redis.serializer.JsonRedisSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class MultiRedisManager implements ApplicationContextAware {

    private final Map<String, RedisTemplate<?, ?>> localTemplates = new ConcurrentHashMap<>();
    private final Map<String, RedissonClient> localRedissons = new ConcurrentHashMap<>();
    private final Map<String, RedisTemplate<String, Object>> objectTemplateCache = new ConcurrentHashMap<>();
    private ApplicationContext applicationContext;
    private final Set<String> springDataSourceNames = ConcurrentHashMap.newKeySet();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public void registerRedisTemplate(String name, RedisTemplate<?, ?> template) {
        if (name != null && template != null) localTemplates.put(name, template);
    }

    public void registerRedissonClient(String name, RedissonClient client) {
        if (name != null && client != null) localRedissons.put(name, client);
    }

    // [新增] 快捷获取默认 RedisTemplate
    public RedisTemplate<?, ?> getDefaultRedisTemplate() {
        return getRedisTemplate("default");
    }

    // [新增] 快捷获取默认 RedissonClient
    public RedissonClient getDefaultRedissonClient() {
        RedissonClient client = getRedissonClient("default");
        if (client != null) return client;

        if (applicationContext != null) {
            try {
                return applicationContext.getBean(RedissonClient.class);
            } catch (Exception e) { }
        }

        List<String> names = getAllDatasourceNames();
        for (String name : names) {
            client = getRedissonClient(name);
            if (client != null) return client;
        }
        return null;
    }

    /**
     * 获取 RedisTemplate（基类，兼容 StringRedisTemplate 和 RedisTemplate&lt;String, Object&gt;）
     *
     * @param name 数据源名称
     * @return RedisTemplate 实例
     */
    public RedisTemplate<?, ?> getRedisTemplate(String name) {
        if (localTemplates.containsKey(name)) return localTemplates.get(name);

        if (applicationContext != null) {
            // 1. 直接查 Bean (支持 Spring Alias)
            if (applicationContext.containsBean(name)) {
                if (applicationContext.isTypeMatch(name, RedisTemplate.class)) {
                    return applicationContext.getBean(name, RedisTemplate.class);
                }
            }

            // 2. 查 {name}RedisTemplate
            String beanName = name + "RedisTemplate";
            if (applicationContext.containsBean(beanName)) {
                return applicationContext.getBean(beanName, RedisTemplate.class);
            }

            // 3. 降级 default
            if ("default".equals(name) && applicationContext.containsBean("stringRedisTemplate")) {
                return applicationContext.getBean("stringRedisTemplate", RedisTemplate.class);
            }
        }
        throw new RedisDataSourceException("Redis数据源 [" + name + "] 不存在");
    }

    /**
     * 类型安全：获取 StringRedisTemplate（默认数据源）
     *
     * @return StringRedisTemplate 实例
     * @throws RedisDataSourceException 如果默认数据源不是 STRING 类型
     */
    public StringRedisTemplate getStringRedisTemplate() {
        return getStringRedisTemplate("default");
    }

    /**
     * 类型安全：获取 StringRedisTemplate
     *
     * @param name 数据源名称
     * @return StringRedisTemplate 实例
     * @throws RedisDataSourceException 如果数据源不是 STRING 类型
     */
    public StringRedisTemplate getStringRedisTemplate(String name) {
        RedisTemplate<?, ?> template = getRedisTemplate(name);
        if (!(template instanceof StringRedisTemplate)) {
            throw new RedisDataSourceException(String.format(
                "数据源 [%s] 配置为 RedisTemplate<String, Object> (template-type: object)，" +
                "无法转换为 StringRedisTemplate。请使用 getObjectRedisTemplate() 或重新配置为 template-type: string",
                name
            ));
        }
        return (StringRedisTemplate) template;
    }

    /**
     * 类型安全：获取 RedisTemplate&lt;String, Object&gt;（默认数据源）
     *
     * @return RedisTemplate&lt;String, Object&gt; 实例
     * @throws RedisDataSourceException 如果默认数据源是 STRING 类型
     */
    public RedisTemplate<String, Object> getObjectRedisTemplate() {
        return getObjectRedisTemplate("default");
    }

    /**
     * 类型安全：获取 RedisTemplate&lt;String, Object&gt;
     *
     * @param name 数据源名称
     * @return RedisTemplate&lt;String, Object&gt; 实例
     * @throws RedisDataSourceException 如果数据源是 STRING 类型
     */
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, Object> getObjectRedisTemplate(String name) {
        RedisTemplate<?, ?> template = getRedisTemplate(name);
        if (template instanceof StringRedisTemplate) {
            throw new RedisDataSourceException(String.format(
                "数据源 [%s] 配置为 StringRedisTemplate (template-type: string)，" +
                "无法转换为 RedisTemplate<String, Object>。请使用 getStringRedisTemplate() 或重新配置为 template-type: object",
                name
            ));
        }
        return (RedisTemplate<String, Object>) template;
    }

    /**
     * 动态获取或创建 RedisTemplate&lt;String, Object&gt;（默认数据源）
     * <p>
     * 即使数据源配置为 STRING 类型，也能动态创建 OBJECT 类型的模板
     *
     * @return RedisTemplate&lt;String, Object&gt; 实例
     */
    public RedisTemplate<String, Object> getOrCreateObjectTemplate() {
        return getOrCreateObjectTemplate("default");
    }

    /**
     * 动态获取或创建 RedisTemplate&lt;String, Object&gt;（方案 B 补充）
     * <p>
     * 即使数据源配置为 STRING 类型，也能动态创建 OBJECT 类型的模板
     *
     * @param name 数据源名称
     * @return RedisTemplate&lt;String, Object&gt; 实例
     */
    public RedisTemplate<String, Object> getOrCreateObjectTemplate(String name) {
        // 1. 尝试从配置化 Bean 获取
        try {
            RedisTemplate<?, ?> template = getRedisTemplate(name);
            if (!(template instanceof StringRedisTemplate)) {
                @SuppressWarnings("unchecked")
                RedisTemplate<String, Object> objectTemplate = (RedisTemplate<String, Object>) template;
                return objectTemplate;
            }
        } catch (Exception e) {
            log.debug("从 Spring 容器获取数据源 [{}] 失败，尝试动态创建", name);
        }

        // 2. 动态创建并缓存
        return objectTemplateCache.computeIfAbsent(name, key -> {
            log.info("动态创建 RedisTemplate<String, Object> for datasource: {}", key);

            // 获取连接工厂
            RedisTemplate<?, ?> stringTemplate = getRedisTemplate(key);
            RedisConnectionFactory factory = stringTemplate.getConnectionFactory();

            // 创建对象模板
            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(factory);
            template.setKeySerializer(RedisSerializer.string());
            template.setValueSerializer(new JsonRedisSerializer<>(Object.class));
            template.setHashKeySerializer(RedisSerializer.string());
            template.setHashValueSerializer(new JsonRedisSerializer<>(Object.class));
            template.afterPropertiesSet();

            return template;
        });
    }

    public RedissonClient getRedissonClient(String name) {
        if (localRedissons.containsKey(name)) return localRedissons.get(name);

        if (applicationContext != null) {
            String aliasName = name + "RedissonClient";
            if (applicationContext.containsBean(aliasName)) {
                return applicationContext.getBean(aliasName, RedissonClient.class);
            }
            if (applicationContext.containsBean(name) && applicationContext.isTypeMatch(name, RedissonClient.class)) {
                return applicationContext.getBean(name, RedissonClient.class);
            }
        }
        return null;
    }

    public boolean containsDatasource(String name) {
        if (localTemplates.containsKey(name)) return true;
        if (applicationContext != null) {
            if (springDataSourceNames.contains(name)) return true;
            // 只要容器里有这个名字 (无论是主名、别名、还是 {name}RedisTemplate)，都算存在
            if (applicationContext.containsBean(name) || applicationContext.containsBean(name + "RedisTemplate")) {
                springDataSourceNames.add(name);
                return true;
            }
        }
        return false;
    }

    public List<String> getAllDatasourceNames() {
        Set<String> names = new HashSet<>(localTemplates.keySet());
        if (applicationContext != null) names.addAll(springDataSourceNames);
        return new ArrayList<>(names);
    }

    public void addDataSource(RedisDataSourceProperties config) {
        String name = config.getName();
        LettuceConnectionFactory factory = fun.commons.framework4j.redis.factory.RedisComponentFactory.createConnectionFactory(config);
        factory.afterPropertiesSet();
        factory.start();

        try {
            // 根据配置的模板类型创建不同的模板
            RedisTemplate<?, ?> template;
            if (config.getTemplateType() == TemplateType.OBJECT) {
                template = createObjectRedisTemplate(factory);
                log.info("动态添加 RedisTemplate<String, Object> 数据源: {}", name);
            } else {
                template = new StringRedisTemplate(factory);
                log.info("动态添加 StringRedisTemplate 数据源: {}", name);
            }

            // 原子注册 template
            localTemplates.put(name, template);

            // Redisson 创建失败时回滚 template + factory
            if (config.getRedisson().isEnabled()) {
                try {
                    RedissonClient redisson = fun.commons.framework4j.redis.factory.RedisComponentFactory.createRedissonClient(config);
                    localRedissons.put(name, redisson);
                } catch (Exception e) {
                    // 回滚已注册的 template
                    localTemplates.remove(name);
                    factory.destroy();
                    log.error("动态添加 Redis 数据源 [{}] 的 Redisson 失败，已回滚", name, e);
                    throw new fun.commons.framework4j.redis.exception.RedisDataSourceException(
                            "动态添加 Redis 数据源 [" + name + "] 的 Redisson 失败: " + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            // factory 已 start 但整体失败时回滚
            if (e instanceof fun.commons.framework4j.redis.exception.RedisDataSourceException) {
                throw (RuntimeException) e;
            }
            factory.destroy();
            log.error("动态添加 Redis 数据源 [{}] 失败，已回滚", name, e);
            throw new fun.commons.framework4j.redis.exception.RedisDataSourceException(
                    "动态添加 Redis 数据源 [" + name + "] 失败: " + e.getMessage(), e);
        }
    }

    public void removeDatasource(String name) {
        if (localTemplates.containsKey(name)) {
            RedisTemplate<?, ?> t = localTemplates.remove(name);
            if (t.getConnectionFactory() instanceof LettuceConnectionFactory) {
                ((LettuceConnectionFactory) t.getConnectionFactory()).destroy();
            }
        }
        if (localRedissons.containsKey(name)) {
            localRedissons.remove(name).shutdown();
        }
        // 清理动态创建的对象模板缓存
        objectTemplateCache.remove(name);
    }

    public boolean checkHealth(String name) {
        // v2.1: 用 try-with-resources 关闭 RedisConnection（原 getConnection().ping() 未关闭连接，泄漏）
        try (var conn = getRedisTemplate(name).getConnectionFactory().getConnection()) {
            return "PONG".equalsIgnoreCase(conn.ping());
        } catch (Exception e) {
            return false;
        }
    }

    public void destroy() {
        // v2.1 修复：复制 keySet 后迭代（removeDatasource 会修改 map），避免迭代器问题
        // removeDatasource 内部已对 Redisson shutdown，这里不再二次 shutdown（避免 IllegalStateException）
        new java.util.ArrayList<>(localTemplates.keySet()).forEach(this::removeDatasource);
    }

    public void addSpringDataSourceNames(Collection<String> names) {
        this.springDataSourceNames.addAll(names);
    }

    // v2.1: createConnectionFactory / createRedissonClient 已抽到 RedisComponentFactory，删除重复代码

    /**
     * 创建 RedisTemplate&lt;String, Object&gt; - 使用 Jackson 序列化
     *
     * @param factory Redis 连接工厂
     * @return RedisTemplate&lt;String, Object&gt; 实例
     */
    private RedisTemplate<String, Object> createObjectRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(new JsonRedisSerializer<>(Object.class));
        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(new JsonRedisSerializer<>(Object.class));
        template.afterPropertiesSet();
        return template;
    }
}