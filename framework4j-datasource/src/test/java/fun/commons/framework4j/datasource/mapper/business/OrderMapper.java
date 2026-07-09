package fun.commons.framework4j.datasource.mapper.business;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.commons.framework4j.datasource.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单 Mapper 接口
 * 使用 business 数据源
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
