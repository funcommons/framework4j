package fun.commons.framework4j.datasource.mapper.business;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.commons.framework4j.datasource.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品 Mapper
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
