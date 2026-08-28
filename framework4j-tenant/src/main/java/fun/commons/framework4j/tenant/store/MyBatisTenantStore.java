package fun.commons.framework4j.tenant.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.commons.framework4j.tenant.entity.TenantEntity;

/**
 * 默认租户存取 —— 复用项目注册的 BaseMapper 子接口(BenefitTenantMapper),零额外声明。
 * <p>
 * 查询用字符串列名 QueryWrapper(基类契约列,不依赖子类 lambda),只查 ACTIVE 租户。
 */
public class MyBatisTenantStore implements TenantStore {

    private final BaseMapper<? extends TenantEntity> mapper;

    public MyBatisTenantStore(BaseMapper<? extends TenantEntity> mapper) {
        this.mapper = mapper;
    }

    @Override
    public TenantEntity findActiveById(long id) {
        QueryWrapper<TenantEntity> qw = new QueryWrapper<>();
        qw.eq("id", id).eq("status", "ACTIVE").eq("is_deleted", 0);
        return selectOne(qw);
    }

    @Override
    public TenantEntity findActiveByName(String name) {
        QueryWrapper<TenantEntity> qw = new QueryWrapper<>();
        qw.eq("name", name).eq("status", "ACTIVE").eq("is_deleted", 0).last("LIMIT 1");
        return selectOne(qw);
    }

    /** 泛型桥接:BaseMapper&lt;capture&gt; 接受基类 Wrapper(raw 调用,契约列已由基类冻结) */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private TenantEntity selectOne(QueryWrapper<TenantEntity> qw) {
        return (TenantEntity) ((BaseMapper) mapper).selectOne(qw);
    }
}
