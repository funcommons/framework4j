package fun.commons.framework4j.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体类
 */
@Getter
@Setter
@ToString
@TableName("t_product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private BigDecimal price;

    private Integer stock;

    private String category;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
