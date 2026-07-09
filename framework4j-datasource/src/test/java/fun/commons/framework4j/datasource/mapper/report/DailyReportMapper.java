package fun.commons.framework4j.datasource.mapper.report;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.commons.framework4j.datasource.entity.DailyReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日报表 Mapper
 */
@Mapper
public interface DailyReportMapper extends BaseMapper<DailyReport> {
}
