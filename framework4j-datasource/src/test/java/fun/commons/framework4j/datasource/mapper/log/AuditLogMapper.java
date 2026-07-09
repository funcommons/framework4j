package fun.commons.framework4j.datasource.mapper.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.commons.framework4j.datasource.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志 Mapper 接口
 * 使用 log 数据源
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
