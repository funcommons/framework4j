package fun.commons.framework4j.tenant.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.commons.framework4j.accesstoken.config.AccessTokenProperties;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.tenant.auth.TenantAuthEndpoint;
import fun.commons.framework4j.tenant.auth.TenantAuthTemplate;
import fun.commons.framework4j.tenant.auth.TenantSecretService;
import fun.commons.framework4j.tenant.auth.TenantSessionRevoker;
import fun.commons.framework4j.tenant.auth.RegistrationKeyService;
import fun.commons.framework4j.tenant.auth.RegistrationKeyEndpoint;
import fun.commons.framework4j.tenant.ddl.TenantDdlInitializer;
import fun.commons.framework4j.tenant.entity.TenantEntity;
import fun.commons.framework4j.tenant.rls.RlsAssistant;
import fun.commons.framework4j.tenant.store.MyBatisTenantStore;
import fun.commons.framework4j.tenant.store.TenantStore;
import fun.commons.framework4j.tenant.schema.TenantSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.util.List;

/**
 * framework4j-tenant 自动配置。
 * <p>
 * 实施计划(framework4j-tenant模块设计 v1.1 §7,共 7 步):
 * Step1 骨架 ✅ → Step2 实体 SPI/DDL ✅ → Step3 双面守卫 ✅ → Step4 认证端点(本步)
 * → Step5 密钥/注册码 → Step6 UserIdContext/RLS → Step7 tenant-tck。
 * <p>
 * 与多数模块不同:本模块默认关闭({@code framework4j.tenant.enabled=false}),
 * 必须显式开启 —— 启用即含 DDL 执行({@code ddl-mode: AUTO})与认证端点注册。
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(Framework4jTenantProperties.class)
@ConditionalOnProperty(prefix = "framework4j.tenant", name = "enabled", havingValue = "true")
public class TenantAutoConfiguration {

    public TenantAutoConfiguration(Framework4jTenantProperties properties) {
        log.info("【Tenant】framework4j-tenant 已启用 —— 租户表={}, ddl-mode={}, 认证端点={}(型别 {}, {}s), "
                        + "注册码通道={}, rls={}",
                properties.tenantTableName(), properties.getDdlMode(),
                properties.getAuth().getPath(), properties.getAuth().getTokenType(),
                properties.getAuth().getExpireSeconds(),
                properties.getRegistrationKey().isEnabled() ? "开" : "关",
                properties.getRls().getMode());
    }

    /**
     * DDL 初始化器:SPI 一致性校验(fail-fast)+ AUTO 幂等建表/补列 或 PROVIDED 模板输出。
     * DataSource 用 ObjectProvider 软依赖 —— 无库项目(纯 PROVIDED)跳过不炸。
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantDdlInitializer tenantDdlInitializer(Framework4jTenantProperties properties,
                                                     ObjectProvider<TenantSchema> schema,
                                                     ObjectProvider<DataSource> dataSource) {
        return new TenantDdlInitializer(properties, schema.getIfAvailable(), dataSource.getIfAvailable());
    }

    /**
     * 租户存取:复用项目注册的 BaseMapper 子接口(实体子类 SPI 的第二个文件)。
     * 泛型解析注入 —— {@code interface BenefitTenantMapper extends BaseMapper<BenefitTenant>}。
     * 未注册 Mapper 时返回 null(不注册 bean)—— 认证栈(TenantAuthTemplate)随之静默不装,
     * 缺失接入清单见模块 README;tenant-tck 断言兜底。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(BaseMapper.class)
    public TenantStore tenantStore(ObjectProvider<BaseMapper<? extends TenantEntity>> tenantMappers) {
        BaseMapper<? extends TenantEntity> mapper = tenantMappers.getIfAvailable();
        return mapper == null ? null : new MyBatisTenantStore(mapper);
    }

    /**
     * 认证模板:平台合成租户/防爆破/宽限期双版本全套;项目自带端点时直接注入本模板。
     * 前置(TenantStore + AccessTokenGenerator)未就绪时返回 null —— 只用 DDL/守卫的项目不受影响。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(AccessTokenGenerator.class)
    public TenantAuthTemplate tenantAuthTemplate(Framework4jTenantProperties properties,
                                                 ObjectProvider<TenantStore> tenantStore,
                                                 ObjectProvider<StringRedisTemplate> redis,
                                                 ObjectProvider<AccessTokenGenerator> tokenGenerator,
                                                 org.springframework.beans.factory.BeanFactory beanFactory,
                                                 @Value("${spring.application.name:}") String appName) {
        TenantStore store = tenantStore.getIfAvailable();
        AccessTokenGenerator generator = tokenGenerator.getIfAvailable();
        if (store == null || generator == null) {
            return null;
        }
        return new TenantAuthTemplate(properties, store, resolveRedis(redis, beanFactory), generator, appName);
    }

    /**
     * Redis 模板解析:项目里常有两个 StringRedisTemplate(spring-boot 默认 + accesstoken 私有),
     * 多候选时按名取 —— 与 accesstoken 同源优先(同一数据源,防爆破 key 与会话 key 同库)。
     */
    static StringRedisTemplate resolveRedis(ObjectProvider<StringRedisTemplate> provider,
                                            org.springframework.beans.factory.BeanFactory beanFactory) {
        try {
            StringRedisTemplate template = provider.getIfAvailable();
            if (template != null) {
                return template;
            }
        } catch (org.springframework.beans.factory.NoUniqueBeanDefinitionException e) {
            for (String name : new String[]{"accessTokenStringRedisTemplate", "stringRedisTemplate"}) {
                if (beanFactory.containsBean(name)) {
                    return beanFactory.getBean(name, StringRedisTemplate.class);
                }
            }
            throw e;
        }
        throw new IllegalStateException("TenantAuthTemplate 需要 StringRedisTemplate(防爆破计数/会话 key)");
    }

    /**
     * 内置认证端点(auth.enabled 可关)。注册时代填两处,消费方零配置:
     * <ol>
     *   <li>auth.path → accesstoken 的 exclude-path(免 token 拦截;MVC 注册远晚于本 bean 实例化,代填必达)</li>
     *   <li>token-type 对应 policy(key=[tenant_id], expire=auth.expire-seconds) —— 不覆盖项目显式配置</li>
     * </ol>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(AccessTokenGenerator.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "framework4j.tenant.auth", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public TenantAuthEndpoint tenantAuthEndpoint(Framework4jTenantProperties properties,
                                                 ObjectProvider<TenantAuthTemplate> authTemplate,
                                                 ObjectProvider<AccessTokenProperties> accessTokenProperties) {
        TenantAuthTemplate template = authTemplate.getIfAvailable();
        if (template == null) {
            return null;   // 认证栈未就绪(缺 Mapper / accesstoken 模块),端点不注册
        }
        AccessTokenProperties atProps = accessTokenProperties.getIfAvailable();
        if (atProps != null) {
            fillExcludePath(properties, atProps);
            fillTokenTypePolicy(properties, atProps);
        }
        return new TenantAuthEndpoint(template);
    }

    /**
     * 会话撤销器(密钥 reset 后撤销该租户全部存量会话,§5.5)。
     * 型别 = auth.token-type + 兼容存量 APP/OPS。
     * hashSalt 从 AccessTokenProperties 直取(accesstoken 模块的配置)。
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantSessionRevoker tenantSessionRevoker(Framework4jTenantProperties properties,
                                                     ObjectProvider<StringRedisTemplate> redis,
                                                     ObjectProvider<AccessTokenGenerator> tokenGenerator,
                                                     ObjectProvider<AccessTokenProperties> accessTokenProperties,
                                                     org.springframework.beans.factory.BeanFactory beanFactory,
                                                     @Value("${spring.application.name:}") String appName) {
        AccessTokenGenerator generator = tokenGenerator.getIfAvailable();
        AccessTokenProperties atProps = accessTokenProperties.getIfAvailable();
        if (generator == null || atProps == null) {
            return null;
        }
        List<String> types = new java.util.ArrayList<>(
                List.of(properties.getAuth().getTokenType(), "APP", "OPS"));
        return new TenantSessionRevoker(resolveRedis(redis, beanFactory), appName, atProps, types);
    }

    /**
     * 密钥生命周期服务(reset:双版本过渡 + 明文只显一次)。
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantSecretService tenantSecretService(ObjectProvider<TenantStore> tenantStore,
                                                   ObjectProvider<TenantSessionRevoker> revoker) {
        TenantStore store = tenantStore.getIfAvailable();
        TenantSessionRevoker r = revoker.getIfAvailable();
        return store == null || r == null ? null : new TenantSecretService(store, r);
    }

    /**
     * 注册码服务(通道 B,registration-key.enabled 才注册)。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "framework4j.tenant.registration-key", name = "enabled", havingValue = "true")
    public RegistrationKeyService registrationKeyService(Framework4jTenantProperties properties,
                                                         ObjectProvider<TenantStore> tenantStore,
                                                         ObjectProvider<StringRedisTemplate> redis,
                                                         org.springframework.beans.factory.BeanFactory beanFactory,
                                                         @Value("${spring.application.name:}") String appName) {
        TenantStore store = tenantStore.getIfAvailable();
        return store == null ? null : new RegistrationKeyService(properties, store,
                resolveRedis(redis, beanFactory), appName);
    }

    /**
     * 注册码开放域端点(注册码通道开启 + Servlet 环境)。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "framework4j.tenant.registration-key", name = "enabled", havingValue = "true")
    public RegistrationKeyEndpoint registrationKeyEndpoint(RegistrationKeyService service,
                                                           ObjectProvider<AccessTokenProperties> accessTokenProperties) {
        AccessTokenProperties atProps = accessTokenProperties.getIfAvailable();
        if (atProps != null) {
            String openPath = "/open/api/v1/tenants/register";
            List<String> excludes = atProps.getExcludePathPatterns();
            if (excludes == null) {
                excludes = new java.util.ArrayList<>();
            }
            if (!excludes.contains(openPath)) {
                List<String> next = new java.util.ArrayList<>(excludes);
                next.add(openPath);
                atProps.setExcludePathPatterns(next);
                log.info("【Tenant】注册码端点 {} 已代填进 access-token exclude-path(免 token 拦截)", openPath);
            }
        }
        return new RegistrationKeyEndpoint(service);
    }

    /**
     * RLS 助手:POLICY 就位不 FORCE / FULL 连接层强制。业务表清单由项目配置(rls.tables)。
     * 无 DataSource 时返回 null。
     */
    @Bean
    @ConditionalOnMissingBean
    public RlsAssistant rlsAssistant(Framework4jTenantProperties properties,
                                     ObjectProvider<DataSource> dataSource) {
        DataSource ds = dataSource.getIfAvailable();
        return ds == null ? null : new RlsAssistant(properties, ds);
    }

    private void fillExcludePath(Framework4jTenantProperties properties, AccessTokenProperties atProps) {
        String authPath = properties.getAuth().getPath();
        List<String> excludes = atProps.getExcludePathPatterns();
        if (excludes == null) {
            excludes = new java.util.ArrayList<>();
        }
        if (!excludes.contains(authPath)) {
            List<String> next = new java.util.ArrayList<>(excludes);
            next.add(authPath);
            atProps.setExcludePathPatterns(next);   // List.of() 不可变,不可原地 add
            log.info("【Tenant】认证端点 {} 已代填进 access-token exclude-path(免 token 拦截)", authPath);
        }
    }

    private void fillTokenTypePolicy(Framework4jTenantProperties properties, AccessTokenProperties atProps) {
        String tokenType = properties.getAuth().getTokenType();
        if (atProps.getPolicies() == null) {
            atProps.setPolicies(new java.util.LinkedHashMap<>());
        }
        if (atProps.getPolicies().containsKey(tokenType)) {
            return;   // 项目显式配置优先,不覆盖
        }
        AccessTokenProperties.Policy policy = new AccessTokenProperties.Policy();
        policy.setKey(List.of(TENANT_ID_CLAIM));
        policy.setExpireTime(properties.getAuth().getExpireSeconds());
        policy.setMaxUsage(-1);
        atProps.getPolicies().put(tokenType, policy);
        log.info("【Tenant】token 型别 {} 无显式 policy,已代填(key=[{}], expire={}s, maxUsage=-1)",
                tokenType, TENANT_ID_CLAIM, properties.getAuth().getExpireSeconds());
    }

    static final String TENANT_ID_CLAIM = "tenant_id";
}
