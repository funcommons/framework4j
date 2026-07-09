package fun.commons.framework4j.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日报表实体类
 */
@Getter
@Setter
@ToString
@TableName("t_daily_report")
public class DailyReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate reportDate;

    private Integer totalUsers;

    private Integer newUsers;

    private Integer totalOrders;

    private BigDecimal totalAmount;

    private LocalDateTime createTime;
}
