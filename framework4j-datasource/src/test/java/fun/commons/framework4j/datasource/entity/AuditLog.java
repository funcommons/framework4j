package fun.commons.framework4j.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 审计日志实体类
 */
@Getter
@Setter
@ToString
@TableName("t_audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String operation;

    private String tableName;

    private Long recordId;

    private String oldValue;

    private String newValue;

    private Long userId;

    private String userName;

    private String ipAddress;

    private LocalDateTime createTime;
}
